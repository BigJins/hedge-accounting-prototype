package com.hedge.prototype.effectiveness.application;

import com.hedge.prototype.effectiveness.application.event.EffectivenessTestCompletedEvent;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.domain.*;
import com.hedge.prototype.effectiveness.adapter.web.dto.EffectivenessTestRequest;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 위험회피 유효성 테스트 오케스트레이션 서비스.
 *
 * <p>Dollar-offset 방법으로 유효성을 평가하고
 * 비효과적 부분(P&L)과 유효 부분(OCI)을 계산합니다.
 * 모든 테스트 결과는 Append-Only로 저장됩니다.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>HedgeRelationship 존재 여부 검증</li>
 *   <li>이전 이력에서 누적값 계산</li>
 *   <li>Dollar-offset 비율 계산 (기간별 + 누적)</li>
 *   <li>testType에 따라 판정 기준 적용</li>
 *   <li>HedgeType에 따라 비효과성 분리 (FVH vs CFH)</li>
 *   <li>ActionRequired 결정</li>
 *   <li>Append-Only INSERT 후 반환</li>
 * </ol>
 *
 * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 유효성 평가)
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 비효과성 P&L)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L — Lower of Test)
 * @see K-IFRS 1109호 6.5.5  (재조정)
 * @see K-IFRS 1109호 6.5.6  (전진 중단)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectivenessTestService implements EffectivenessTestUseCase {

    private final EffectivenessTestRepository effectivenessTestRepository;
    private final HedgeRelationshipRepository hedgeRelationshipRepository;
    private final ApplicationEventPublisher eventPublisher;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * 유효성 테스트 실행 및 결과 저장.
     *
     * <p>K-IFRS 1109호 B6.4.12에 따라 매 보고기간 말 Dollar-offset 방법으로
     * 유효성을 평가하고, 비효과적 부분을 산정하여 Append-Only로 저장합니다.
     *
     * @param request 유효성 테스트 요청 (위험회피관계 ID, 평가기준일, 당기 변동액)
     * @return 저장된 유효성 테스트 결과 엔티티
     * @throws BusinessException ET_001 — 존재하지 않는 위험회피관계
     * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
     * @see K-IFRS 1109호 6.5.5  (재조정)
     * @see K-IFRS 1109호 6.5.6  (전진 중단)
     */
    @Transactional
    public EffectivenessTest runTest(EffectivenessTestRequest request) {

        // 1. HedgeRelationship 존재 여부 검증
        //    hedgeType은 요청에서 직접 수신합니다 (프론트엔드 명시적 전달).
        var relationship = hedgeRelationshipRepository
                .findById(request.hedgeRelationshipId())
                .orElseThrow(() -> new BusinessException("ET_001",
                        "위험회피관계를 찾을 수 없습니다. hedgeRelationshipId=" + request.hedgeRelationshipId()));

        validateRequestedHedgeType(request.hedgeType(), relationship.getHedgeType(), request.hedgeRelationshipId());
        HedgeType hedgeType = relationship.getHedgeType();

        // 1b. IRS instrumentType 조합 검증
        //     IRS FVH: 금리위험 + 공정가치 헤지 조합만 허용 (KRW_FIXED_BOND 대상)
        //     IRS CFH: 금리위험 + 현금흐름 헤지 조합만 허용 (KRW_FLOATING_BOND 대상)
        //     TODO: RAG 교차검증 필요 — K-IFRS 1109호 6.2.1, 6.3.7
        InstrumentType instrumentType = resolveInstrumentType(request.instrumentType());
        if (instrumentType == InstrumentType.IRS) {
            validateIrsHedgeTypeCombination(hedgeType, request.hedgeRelationshipId());
        }

        // 1c. 수단·피헤지항목 당기 변동이 모두 0이면 차단 (저장 전 선제 검증)
        //     0/0 입력은 Dollar-offset 계산 자체가 무의미하며 항상 PASS가 도출되어 K-IFRS 취지에 반함.
        validateChangesNotBothZero(request.instrumentFvChange(), request.hedgedItemPvChange());

        // 2. 이전 테스트 이력에서 누적값 계산
        //    이전 마지막 레코드의 누적값 + 당기 변동 = 새 누적값
        CumulativeValues cumulatives = computeCumulatives(
                request.hedgeRelationshipId(),
                request.instrumentFvChange(),
                request.hedgedItemPvChange());

        BigDecimal instrumentFvCumulative = cumulatives.instrumentFvCumulative();
        BigDecimal hedgedItemPvCumulative = cumulatives.hedgedItemPvCumulative();

        // 3. Dollar-offset 비율 계산
        //    testType에 따라 기간별(당기) 또는 누적 변동액 사용
        BigDecimal referenceInstrument = resolveReferenceValue(
                request.testType(), request.instrumentFvChange(), instrumentFvCumulative);
        BigDecimal referenceHedgedItem = resolveReferenceValue(
                request.testType(), request.hedgedItemPvChange(), hedgedItemPvCumulative);

        // 4. 유효성 비율 및 최종 판정
        //    K-IFRS 1109호 BC6.234: 80~125% 정량 기준 폐지.
        //    Dollar-offset 결과는 "참고 등급"으로만 사용하며, 실제 판정은 아래 원칙을 따릅니다.
        //    - 동방향(비율 양수): 경제적 관계 훼손 → FAIL (6.5.6 중단 검토)
        //    - 반대방향 + 참고범위 이탈: 경제적 관계 유지 + 비율 조정 필요 → WARNING (6.5.5 재조정 검토)
        //    - 반대방향 + 참고범위 이내: 완전 유효 → PASS
        EffectivenessTestResult testResult;
        BigDecimal effectivenessRatio;
        String failureReason = null;

        if (DollarOffsetCalculator.isHedgedItemChangeNegligible(referenceHedgedItem)) {
            // 피헤지항목 변동 없음 → 분모 0 처리 → PASS (K-IFRS 1109호 B6.4.12)
            effectivenessRatio = BigDecimal.ZERO;
            testResult = EffectivenessTestResult.PASS;
            log.debug("피헤지항목 변동 근사 0 처리 — PASS: hedgeRelationshipId={}", request.hedgeRelationshipId());
        } else {
            effectivenessRatio = DollarOffsetCalculator.calculateRatio(referenceInstrument, referenceHedgedItem);
            // evaluateReferenceGrade(): 참고 등급 반환 (BC6.234 — 단독 합격/불합격 기준 아님)
            // PASS/WARNING/FAIL 3단계 중 WARNING은 재조정 신호, FAIL만 중단 검토 대상
            testResult = DollarOffsetCalculator.evaluateReferenceGrade(effectivenessRatio);
            if (testResult == EffectivenessTestResult.FAIL || testResult == EffectivenessTestResult.WARNING) {
                failureReason = DollarOffsetCalculator.buildReferenceGradeMessage(effectivenessRatio);
            }
        }

        // 5. HedgeType에 따라 비효과성 분리
        //    현금흐름 헤지: OCI 잔액 누적 관리를 위해 이전 최근 레코드 참조
        //    K-IFRS 1109호 6.5.11: OCI 적립금은 누적 관리
        BigDecimal previousOciBalance = resolvePreviousOciBalance(request.hedgeRelationshipId(), hedgeType);
        IneffectivenessResult ineffectivenessResult = calculateIneffectiveness(
                hedgeType,
                request.instrumentFvChange(),
                request.hedgedItemPvChange(),
                instrumentFvCumulative,
                hedgedItemPvCumulative,
                previousOciBalance);

        // 6. ActionRequired 결정
        //    K-IFRS 1109호 6.5.5: 비율 이탈이지만 위험관리 목적 유지 → REBALANCE
        //    K-IFRS 1109호 6.5.6: 적용조건 미충족 → DISCONTINUE
        //    PASS → NONE
        ActionRequired actionRequired = determineAction(testResult, effectivenessRatio);

        // 7. 결과 생성 및 저장 (Append-Only INSERT)
        //    instrumentType: IRS 포함 모든 수단 유형 저장 (null이면 FX_FORWARD 하위호환)
        EffectivenessTest result = EffectivenessTest.of(
                request.hedgeRelationshipId(),
                request.testDate(),
                request.testType(),
                hedgeType,
                request.instrumentFvChange(),
                request.hedgedItemPvChange(),
                instrumentFvCumulative,
                hedgedItemPvCumulative,
                effectivenessRatio,
                testResult,
                ineffectivenessResult.effectiveAmount(),
                ineffectivenessResult.ineffectiveAmount(),
                ineffectivenessResult.ociReserveBalance(),
                actionRequired,
                failureReason,
                instrumentType);

        EffectivenessTest saved = effectivenessTestRepository.save(result);

        log.info("유효성 테스트 완료: hedgeRelationshipId={}, testDate={}, result={}, action={}",
                request.hedgeRelationshipId(), request.testDate(), testResult, actionRequired);

        eventPublisher.publishEvent(new EffectivenessTestCompletedEvent(
                saved.getEffectivenessTestId(),
                request.hedgeRelationshipId(),
                testResult,
                actionRequired,
                hedgeType,
                instrumentType));  // 2단계: IRS 분기 라우팅에 사용 (null = FX_FORWARD 하위호환)

        return saved;
    }

    /**
     * 수단·피헤지항목 당기 변동이 모두 0(무시 가능 수준)인 경우 차단.
     *
     * <p>두 변동이 모두 0이면 Dollar-offset 비율 계산이 무의미(0/0)하고
     * 결과가 항상 PASS로 도출되어 K-IFRS 1109호 B6.4.12 취지에 반합니다.
     * 실질적 가치 변동이 없는 기간은 유효성 테스트 대상이 아닙니다.
     * Append-Only 저장 전에 선제 차단합니다.
     *
     * @throws BusinessException ET_004 — 수단·피헤지항목 당기 변동 모두 0
     * @see K-IFRS 1109호 B6.4.12 (유효성 평가는 실질적 변동이 전제)
     */
    private void validateChangesNotBothZero(BigDecimal instrumentFvChange, BigDecimal hedgedItemPvChange) {
        boolean instrumentNegligible = instrumentFvChange.abs().compareTo(new BigDecimal("0.0001")) <= 0;
        boolean hedgedItemNegligible = DollarOffsetCalculator.isHedgedItemChangeNegligible(hedgedItemPvChange);
        if (instrumentNegligible && hedgedItemNegligible) {
            throw new BusinessException("ET_004",
                    "위험회피수단과 피헤지항목의 당기 변동이 모두 0(또는 무시 가능 수준)입니다. " +
                    "실질적 가치 변동이 없으면 유효성 테스트를 실행할 수 없습니다. " +
                    "(K-IFRS 1109호 B6.4.12: 유효성 평가는 실질적 변동이 전제)");
        }
    }

    private void validateRequestedHedgeType(
            HedgeType requestedHedgeType,
            HedgeType persistedHedgeType,
            String hedgeRelationshipId) {
        if (requestedHedgeType != persistedHedgeType) {
            throw new BusinessException("ET_003",
                    String.format("hedgeType mismatch for hedgeRelationshipId=%s. request=%s, persisted=%s",
                            hedgeRelationshipId, requestedHedgeType, persistedHedgeType));
        }
    }

    /**
     * instrumentType이 null이면 FX_FORWARD 기본값으로 처리 (1단계 하위호환).
     *
     * <p>1단계 기존 클라이언트는 instrumentType을 전달하지 않습니다.
     * null 입력을 FX_FORWARD로 처리하여 기존 동작을 유지합니다.
     *
     * @param requested 요청의 instrumentType (null 허용)
     * @return 결정된 InstrumentType (null 불허)
     */
    private InstrumentType resolveInstrumentType(InstrumentType requested) {
        return requested != null ? requested : InstrumentType.FX_FORWARD;
    }

    /**
     * IRS 수단과 헤지 유형 조합이 K-IFRS 상 유효한지 검증.
     *
     * <p>IRS 허용 조합:
     * <ul>
     *   <li>IRS + FAIR_VALUE  — 고정금리채권(KRW_FIXED_BOND) 공정가치 헤지</li>
     *   <li>IRS + CASH_FLOW   — 변동금리부채(KRW_FLOATING_BOND) 현금흐름 헤지</li>
     * </ul>
     *
     * <p>Dollar-offset 계산 자체는 수단 유형에 무관하게 동일하게 동작합니다.
     * (K-IFRS 1109호 B6.4.13: Dollar-offset 방법은 수단 유형을 구분하지 않음)
     * 이 검증은 회계 조합 무결성을 보장하기 위한 추가 방어 레이어입니다.
     *
     * <p>TODO: RAG 교차검증 필요 — K-IFRS 1109호 6.2.1 (IRS 수단 적격성), 6.3.7 (위험 구성요소)
     *
     * @param hedgeType           위험회피 유형
     * @param hedgeRelationshipId 위험회피관계 ID (예외 메시지용)
     * @throws BusinessException ET_005 — IRS + 허용되지 않는 hedgeType 조합
     * @see K-IFRS 1109호 6.2.1  (IRS 위험회피수단 적격성)
     * @see K-IFRS 1109호 6.3.7  (금리위험 구성요소 지정)
     * @see K-IFRS 1109호 6.5.8  (IRS FVH — 공정가치 헤지)
     * @see K-IFRS 1109호 6.5.11 (IRS CFH — 현금흐름 헤지)
     */
    private void validateIrsHedgeTypeCombination(HedgeType hedgeType, String hedgeRelationshipId) {
        // IRS는 FAIR_VALUE (고정금리채권 헤지) 또는 CASH_FLOW (변동금리채권/부채 헤지)만 허용
        // 두 유형 모두 금리위험(INTEREST_RATE) 헤지 목적 — K-IFRS 1109호 6.3.7
        if (hedgeType != HedgeType.FAIR_VALUE && hedgeType != HedgeType.CASH_FLOW) {
            throw new BusinessException("ET_005",
                    String.format("IRS 수단은 FAIR_VALUE 또는 CASH_FLOW hedgeType만 지원합니다. " +
                            "hedgeRelationshipId=%s, hedgeType=%s " +
                            "(K-IFRS 1109호 6.5.8 FVH, 6.5.11 CFH)",
                            hedgeRelationshipId, hedgeType));
        }
        log.debug("IRS 조합 검증 PASS: hedgeRelationshipId={}, hedgeType={}", hedgeRelationshipId, hedgeType);
    }

    /**
     * 유효성 테스트 단건 조회.
     *
     * @param effectivenessTestId 유효성 테스트 ID
     * @return 유효성 테스트 결과 엔티티
     * @throws BusinessException ET_002 — 존재하지 않는 유효성 테스트
     */
    @Transactional(readOnly = true)
    public EffectivenessTest findById(Long effectivenessTestId) {
        return effectivenessTestRepository.findById(effectivenessTestId)
                .orElseThrow(() -> new BusinessException("ET_002",
                        "유효성 테스트 결과를 찾을 수 없습니다. effectivenessTestId=" + effectivenessTestId));
    }

    /**
     * 헤지관계별 유효성 테스트 이력 조회 (페이지네이션, 최신순).
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param pageable            페이지네이션
     * @return 유효성 테스트 이력 페이지
     * @see K-IFRS 1107호 (헤지회계 공시 — 유효성 테스트 이력)
     */
    @Transactional(readOnly = true)
    public Page<EffectivenessTest> findByHedgeRelationshipId(
            String hedgeRelationshipId, Pageable pageable) {
        return effectivenessTestRepository
                .findByHedgeRelationshipIdOrderByTestDateDesc(hedgeRelationshipId, pageable);
    }

    // -----------------------------------------------------------------------
    // 내부 값 객체
    // -----------------------------------------------------------------------

    /**
     * 누적값 계산 결과.
     *
     * @param instrumentFvCumulative 위험회피수단 누적 변동
     * @param hedgedItemPvCumulative 피헤지항목 누적 변동
     */
    private record CumulativeValues(
            BigDecimal instrumentFvCumulative,
            BigDecimal hedgedItemPvCumulative
    ) {}

    /**
     * 비효과성 계산 결과.
     *
     * @param effectiveAmount   유효 부분
     * @param ineffectiveAmount 비효과적 부분 (P&L)
     * @param ociReserveBalance OCI 적립금 잔액 (CFH만)
     */
    private record IneffectivenessResult(
            BigDecimal effectiveAmount,
            BigDecimal ineffectiveAmount,
            BigDecimal ociReserveBalance
    ) {}

    // -----------------------------------------------------------------------
    // 내부 계산 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 이전 이력 마지막 레코드의 누적값에 당기 변동을 더하여 새 누적값 계산.
     *
     * <p>이전 이력이 없으면 당기 변동이 곧 누적 변동입니다.
     *
     * @param hedgeRelationshipId    위험회피관계 ID
     * @param instrumentFvChange     위험회피수단 당기 변동
     * @param hedgedItemPvChange     피헤지항목 당기 변동
     * @return 새 누적값
     * @see K-IFRS 1109호 B6.4.12 (누적 Dollar-offset — 지정 이후 누적)
     */
    private CumulativeValues computeCumulatives(
            String hedgeRelationshipId,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange) {

        return effectivenessTestRepository
                .findTopByHedgeRelationshipIdOrderByTestDateDesc(hedgeRelationshipId)
                .map(prev -> new CumulativeValues(
                        prev.getInstrumentFvCumulative().add(instrumentFvChange),
                        prev.getHedgedItemPvCumulative().add(hedgedItemPvChange)))
                .orElseGet(() -> new CumulativeValues(
                        instrumentFvChange,
                        hedgedItemPvChange));
    }

    /**
     * testType에 따라 판정에 사용할 기준값 선택.
     *
     * <p>DOLLAR_OFFSET_PERIODIC: 당기 변동 사용
     * DOLLAR_OFFSET_CUMULATIVE: 누적 변동 사용
     *
     * @param testType       테스트 방법
     * @param periodicValue  당기 변동
     * @param cumulativeValue 누적 변동
     * @return 선택된 기준값
     */
    private BigDecimal resolveReferenceValue(
            EffectivenessTestType testType,
            BigDecimal periodicValue,
            BigDecimal cumulativeValue) {
        return testType == EffectivenessTestType.DOLLAR_OFFSET_PERIODIC
                ? periodicValue
                : cumulativeValue;
    }

    /**
     * HedgeType에 따라 비효과성 분리 계산.
     *
     * <p><b>공정가치 헤지 (K-IFRS 1109호 6.5.8)</b>:
     * 위험회피수단과 피헤지항목의 공정가치 변동이 모두 당기손익(P&L)에 인식됩니다.
     * 비효과성 = 수단 변동 + 대상 변동의 순합계 (상계 후 잔액).
     * 완전히 유효한 헤지이면 두 변동의 합이 0에 수렴합니다.
     *
     * <p><b>현금흐름 헤지 (K-IFRS 1109호 6.5.11)</b>:
     * Lower of Test로 OCI(유효분)와 P&L(비효과분)을 분리합니다.
     * OCI Reserve 잔액은 이전 누적 잔액 + 당기 유효 부분으로 누적 계산합니다.
     * 과대헤지인 경우에만 비효과적 부분이 발생합니다.
     *
     * @param hedgeType              위험회피 유형
     * @param instrumentFvChange     위험회피수단 당기 변동
     * @param hedgedItemPvChange     피헤지항목 당기 변동
     * @param instrumentFvCumulative 위험회피수단 누적 변동
     * @param hedgedItemPvCumulative 피헤지항목 누적 변동
     * @param previousOciBalance     이전 기간 OCI 잔액 (CFH만 사용, FVH는 null)
     * @return 비효과성 계산 결과
     * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 회계처리)
     * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L 분리)
     */
    private IneffectivenessResult calculateIneffectiveness(
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange,
            BigDecimal instrumentFvCumulative,
            BigDecimal hedgedItemPvCumulative,
            BigDecimal previousOciBalance) {

        if (hedgeType == HedgeType.FAIR_VALUE) {
            return calculateFairValueIneffectiveness(instrumentFvChange, hedgedItemPvChange);
        } else {
            return calculateCashFlowIneffectiveness(
                    instrumentFvChange, instrumentFvCumulative, hedgedItemPvCumulative, previousOciBalance);
        }
    }

    /**
     * 공정가치 헤지 비효과성 계산.
     *
     * <p>K-IFRS 1109호 6.5.8에 따라 위험회피수단 변동과 피헤지항목 변동 모두
     * 당기손익(P&L)에 인식됩니다. OCI는 사용되지 않습니다.
     * 완전헤지라면 두 변동의 합(netEffect)이 0에 수렴하며, 잔액이 비효과적 부분입니다.
     *
     * <pre>
     *   net = instrumentFvChange + hedgedItemPvChange
     *   - net > 0: 수단 이익 > 대상 손실 → 초과 이익 P&L 차변 인식
     *   - net < 0: 수단 손실 > 대상 이익 → 초과 손실 P&L 대변 인식
     *   - net = 0: 완전 상계 → 완전헤지
     * </pre>
     *
     * <p><b>주의</b>: {@code ineffectiveAmount}는 부호를 유지합니다 ({@code .abs()} 적용 금지).
     * 양수(+) = 차변(손익 인식), 음수(-) = 대변(손익 인식).
     * 부호를 제거하면 방향 정보가 소실되어 분개 처리에서 오류가 발생합니다.
     *
     * <p><b>{@code effectiveAmount}</b>는 헤지 효율성 분석 목적으로만 사용합니다.
     * OCI가 아닌 P&L 인식이며, 이 값 자체를 OCI로 처리하면 안 됩니다.
     *
     * @param instrumentFvChange 위험회피수단 당기 변동
     * @param hedgedItemPvChange 피헤지항목 당기 변동
     * @return 비효과성 결과 (공정가치 헤지용)
     * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 — 수단·대상 변동 모두 P&L)
     */
    private IneffectivenessResult calculateFairValueIneffectiveness(
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange) {

        // 수단 + 대상 변동의 순합 (상계 효과, 부호 유지 필수)
        // K-IFRS 1109호 6.5.8: 양수=차변, 음수=대변 — .abs() 금지
        BigDecimal netEffect = instrumentFvChange.add(hedgedItemPvChange)
                .setScale(2, RoundingMode.HALF_UP);

        // 분석용 유효 부분: 두 변동 중 절대값이 작은 쪽 (반대방향이면 작은 쪽만큼 상계됨)
        // 이 값은 헤지 효율성 분석 목적으로만 사용됩니다 — P&L 인식이며 OCI 아님
        BigDecimal absInstrument = instrumentFvChange.abs();
        BigDecimal absHedgedItem = hedgedItemPvChange.abs();
        BigDecimal effectiveAmount = absInstrument.min(absHedgedItem)
                .setScale(2, RoundingMode.HALF_UP);

        // 비효과적 부분 = 부호 있는 순합 (netEffect 그대로 사용, .abs() 금지)
        // 양수: 초과 이익 → P&L 차변 / 음수: 초과 손실 → P&L 대변
        BigDecimal ineffectiveAmount = netEffect;

        // 공정가치 헤지는 OCI 적립금 없음 (K-IFRS 1109호 6.5.8)
        return new IneffectivenessResult(effectiveAmount, ineffectiveAmount, null);
    }

    /**
     * 현금흐름 헤지 비효과성 계산 (Lower of Test) + OCI 잔액 누적 계산.
     *
     * <p>OCI 인식 한도 = MIN(|피헤지항목 누적|, |위험회피수단 누적|)
     * 비효과적 부분 = 과대헤지 초과분 (과소헤지 시 0)
     *
     * <p>OCI Reserve 잔액 = 이전 OCI 잔액 + 당기 유효 부분
     * K-IFRS 1109호 6.5.11: 현금흐름위험회피적립금은 누적 개념으로 관리합니다.
     * 이전 기간 잔액을 무시하고 당기 금액만 기록하면 OCI 잔액이 잘못 계산됩니다.
     *
     * @param instrumentFvChange     위험회피수단 당기 변동
     * @param instrumentFvCumulative 위험회피수단 누적 변동
     * @param hedgedItemPvCumulative 피헤지항목 누적 변동
     * @param previousOciBalance     이전 기간 OCI Reserve 잔액 (없으면 BigDecimal.ZERO)
     * @return 비효과성 결과 (현금흐름 헤지용)
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피적립금 — 누적 관리)
     * @see K-IFRS 1109호 6.5.12 (OCI 잔액 재분류 조정 시점)
     * @see K-IFRS 1109호 BC6.280 (Lower of Test 근거)
     */
    private IneffectivenessResult calculateCashFlowIneffectiveness(
            BigDecimal instrumentFvChange,
            BigDecimal instrumentFvCumulative,
            BigDecimal hedgedItemPvCumulative,
            BigDecimal previousOciBalance) {

        // K-IFRS 1109호 6.5.11⑴: 유효 부분 부호는 헤지수단 누적 방향을 따름
        // BC6.280: Lower of Test는 규모 제한 장치이며 부호를 변환하지 않음
        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentFvCumulative, hedgedItemPvCumulative);

        // K-IFRS 1109호 6.5.11⑵: 비효과적 부분(과대헤지 초과분)도 수단 방향 부호 적용
        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentFvCumulative, effectiveAmount);

        // OCI Reserve 잔액 누적 계산
        // K-IFRS 1109호 6.5.11: "현금흐름위험회피적립금"은 이전 잔액 + 당기 유효분으로 누적 관리
        // previousOciBalance: 직전 기간 누적 OCI Reserve 잔액 (첫 기간이면 0)
        // effectiveAmount: 당기 유효 부분 (이번 기간 OCI로 인식할 금액)
        // cumulativeOciBalance = 이전 잔액 + 당기 유효 부분
        BigDecimal cumulativeOciBalance = previousOciBalance
                .add(effectiveAmount)
                .setScale(2, RoundingMode.HALF_UP);

        return new IneffectivenessResult(effectiveAmount, ineffectiveAmount, cumulativeOciBalance);
    }

    /**
     * 이전 기간 OCI Reserve 잔액 조회.
     *
     * <p>현금흐름 헤지인 경우에만 사용됩니다.
     * 이전 테스트 레코드의 OCI 잔액을 조회하여 누적 계산에 사용합니다.
     * 이전 이력이 없으면 0(최초 지정 기간)으로 처리합니다.
     *
     * <p>공정가치 헤지는 OCI를 사용하지 않으므로 BigDecimal.ZERO를 반환합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param hedgeType           헤지 유형 (FAIR_VALUE이면 즉시 ZERO 반환)
     * @return 이전 기간 OCI Reserve 잔액 (없으면 ZERO)
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피적립금 누적 관리)
     */
    private BigDecimal resolvePreviousOciBalance(String hedgeRelationshipId, HedgeType hedgeType) {
        if (hedgeType == HedgeType.FAIR_VALUE) {
            // 공정가치 헤지는 OCI 적립금 없음 (K-IFRS 1109호 6.5.8)
            return BigDecimal.ZERO;
        }

        return effectivenessTestRepository
                .findTopByHedgeRelationshipIdOrderByTestDateDesc(hedgeRelationshipId)
                .map(prev -> {
                    BigDecimal prevOci = prev.getOciReserveBalance();
                    return prevOci != null ? prevOci : BigDecimal.ZERO;
                })
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 유효성 테스트 결과에 따른 필요 조치 결정.
     *
     * <p><b>K-IFRS 1109호 기준 판단 로직</b>:
     * <ul>
     *   <li>PASS → NONE (위험회피관계 현행 유지)</li>
     *   <li>WARNING → REBALANCE (참고범위 이탈, 재조정 검토 — K-IFRS 1109호 6.5.5)</li>
     *   <li>FAIL → DISCONTINUE (동방향, 경제적 관계 훼손 — K-IFRS 1109호 6.5.6)</li>
     * </ul>
     *
     * @param testResult        판정 결과 (PASS / WARNING / FAIL)
     * @param effectivenessRatio Dollar-offset 비율 (음수 = 반대방향)
     * @return 필요 조치 (NONE / REBALANCE / DISCONTINUE)
     * @see K-IFRS 1109호 6.5.5 (위험회피관계 재조정)
     * @see K-IFRS 1109호 6.5.6 (위험회피관계 중단)
     */
    private ActionRequired determineAction(EffectivenessTestResult testResult, BigDecimal effectivenessRatio) {
        return switch (testResult) {
            case PASS -> ActionRequired.NONE;
            case WARNING -> ActionRequired.REBALANCE;
            case FAIL -> ActionRequired.DISCONTINUE;
        };
    }
}

