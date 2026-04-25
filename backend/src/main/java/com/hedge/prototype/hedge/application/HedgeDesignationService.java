package com.hedge.prototype.hedge.application;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.adapter.web.HedgeResponseMapper;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.domain.model.HedgedItem;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.policy.ConditionResult;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDiscontinuationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeRelationshipSummaryResponse;
import com.hedge.prototype.hedge.application.port.HedgedItemRepository;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipSpec;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import com.hedge.prototype.valuation.application.port.FxForwardContractRepository;
import com.hedge.prototype.valuation.application.port.IrsContractRepository;
import com.hedge.prototype.valuation.application.port.CrsContractRepository;
import com.hedge.prototype.hedge.application.event.HedgeDesignatedEvent;
import com.hedge.prototype.journal.application.port.JournalEntryRepository;
import com.hedge.prototype.journal.domain.AccountCode;
import com.hedge.prototype.journal.domain.JournalEntry;
import com.hedge.prototype.journal.domain.OciReclassificationJournalGenerator;
import com.hedge.prototype.journal.domain.ReclassificationReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 헤지 지정 오케스트레이션 서비스.
 *
 * <p>레포지토리 호출, 트랜잭션 관리, DTO 변환을 담당합니다.
 * 적격요건 검증 등 핵심 비즈니스 로직은 {@link HedgeRelationship} 도메인 메서드에 위임합니다.
 *
 * <p>instrumentType 필드를 통해 FX Forward / IRS / CRS 다형성을 지원합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — FX Forward / IRS / CRS)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeDesignationService implements HedgeCommandUseCase {

    private final HedgeRelationshipRepository hedgeRelationshipRepository;
    private final HedgedItemRepository hedgedItemRepository;
    private final FxForwardContractRepository fxForwardContractRepository;
    private final IrsContractRepository irsContractRepository;
    private final CrsContractRepository crsContractRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JournalEntryRepository journalEntryRepository;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * 헤지 지정 — K-IFRS 1109호 6.4.1 적격요건 자동 검증 포함.
     *
     * <p>instrumentType에 따라 FX Forward 경로와 IRS/CRS 경로로 분기합니다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>instrumentType 결정 (null이면 FX_FORWARD 기본값)</li>
     *   <li>헤지기간 사전 검증 (종료일, FX Forward만 USD 통화 확인)</li>
     *   <li>예상거래 발생가능성 검증 (K-IFRS 1109호 6.3.3)</li>
     *   <li>HedgedItem 생성</li>
     *   <li>헤지수단 유형별 분기 — 적격요건 검증 및 저장</li>
     * </ol>
     *
     * @param request 헤지 지정 요청
     * @return 헤지 지정 응답 (적격요건 검증 결과 포함)
     * @throws BusinessException HD_001 — 존재하지 않는 헤지수단 계약
     * @throws BusinessException HD_005 — 동일 FX Forward 계약이 이미 DESIGNATED 상태 (중복 지정 차단)
     * @throws BusinessException HD_006 — 만기 초과 계약 지정 시도
     * @throws BusinessException HD_007 — 헤지기간 종료일이 지정일보다 이전
     * @throws BusinessException HD_008 — 헤지대상과 헤지수단 통화 미매칭 (FX Forward만)
     * @throws BusinessException HD_013 — 예상거래 발생가능성 24개월 초과
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     * @see K-IFRS 1109호 6.3.3 (예상거래 발생가능성 높음 요건)
     */
    @Transactional
    public HedgeDesignationResponse designate(HedgeDesignationRequest request) {
        InstrumentType instrumentType = resolveInstrumentType(request);
        String instrumentContractId = resolveInstrumentContractId(request);

        validateHedgePeriod(request, instrumentType);
        validateForecastTransactionProbability(request);
        validateContractIdProvided(instrumentContractId, instrumentType);
        validateItemTypeCompatibility(request);

        HedgedItem hedgedItem = buildHedgedItem(request);

        if (instrumentType == InstrumentType.FX_FORWARD) {
            return designateFxForward(request, instrumentContractId, hedgedItem);
        } else {
            return designateIrsCrs(request, instrumentType, instrumentContractId, hedgedItem);
        }
    }

    /**
     * 헤지회계 중단 — K-IFRS 1109호 6.5.6 허용 사유 코드 검증 포함.
     *
     * <p>K-IFRS 1109호 6.5.6: 자발적 중단은 허용되지 않습니다.
     * 허용된 사유 코드({@link HedgeDiscontinuationReason})로만 중단 가능합니다.
     * 도메인 {@link HedgeRelationship#discontinue} 메서드가 사유 코드 검증을 담당합니다.
     *
     * <p>현금흐름헤지(CFH) 중단 시 K-IFRS 1109호 6.5.12에 따라 예상거래 발생 가능성에 따른
     * OCI 후속 처리를 자동으로 수행합니다:
     * <ul>
     *   <li>예상거래 여전히 발생 가능({@code forecastTransactionExpected=true}):
     *       OCI 유지, 분개 미생성 — 예상거래 실현 시점까지 OCI 보유 (6.5.12(1))</li>
     *   <li>예상거래 발생 불가 확정({@code forecastTransactionExpected=false}):
     *       OCI 잔액 즉시 P&L 재분류 (6.5.12(2)). {@code currentOciBalance=ZERO}이면 분개 미생성</li>
     * </ul>
     * 일부 발생 가능 케이스의 안분(비율) 처리는 시연 범위 외로 수동 처리.
     *
     * <p>공정가치헤지(FVH) 중단 시 OCI 분기에 진입하지 않습니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param request             중단 요청 (사유 코드 + 상세 설명 + CFH OCI 처리 정보)
     * @throws BusinessException HD_009 — 존재하지 않는 위험회피관계
     * @throws BusinessException HD_011 — 이미 중단된 경우
     * @throws BusinessException HD_012 — 자발적 중단 시도 (6.5.6 위반)
     * @throws BusinessException HD_017 — CFH 중단인데 forecastTransactionExpected가 null
     * @see K-IFRS 1109호 6.5.6  (위험회피관계 자발적 취소 불가)
     * @see K-IFRS 1109호 6.5.7  (현금흐름 헤지 중단)
     * @see K-IFRS 1109호 6.5.12 (CFH 중단 후 OCI 후속 처리)
     * @see K-IFRS 1109호 6.5.12(2) (예상거래 발생불가 시 즉시 P&L 재분류)
     * @see K-IFRS 1109호 B6.5.26 (중단 후 OCI 잔액 처리)
     */
    @Transactional
    public void discontinue(String hedgeRelationshipId, HedgeDiscontinuationRequest request) {
        HedgeRelationship relationship = loadHedgeRelationshipForDiscontinuation(hedgeRelationshipId);

        LocalDate effectiveDate = resolveEffectiveDate(request);

        // 도메인 메서드에서 사유 코드 검증 (VOLUNTARY_DISCONTINUATION 차단 포함)
        relationship.discontinue(effectiveDate, request.reason(), request.details());
        hedgeRelationshipRepository.save(relationship);

        log.info("헤지회계 중단 처리 완료: hedgeRelationshipId={}, 사유코드={}, hedgeType={}",
                hedgeRelationshipId, request.reason(), relationship.getHedgeType());

        // CFH 전용 OCI 후속 처리 (FVH는 분기 진입 안 함)
        if (relationship.getHedgeType() == HedgeType.CASH_FLOW) {
            processCfhOciAfterDiscontinuation(relationship, request, effectiveDate);
        }
    }

    /**
     * CFH 중단 후 OCI 후속 처리.
     *
     * <p>K-IFRS 1109호 6.5.12: 예상거래 발생 가능 여부에 따라 OCI 처리 방향이 결정됩니다.
     * <ul>
     *   <li>forecastTransactionExpected=true : OCI 유지, 분개 미생성 (6.5.12(1))</li>
     *   <li>forecastTransactionExpected=false: OCI 즉시 P&L 재분류 (6.5.12(2))</li>
     *   <li>forecastTransactionExpected=null : HD_017 예외 — CFH는 반드시 명시해야 함</li>
     * </ul>
     *
     * @param relationship  중단 처리된 위험회피관계
     * @param request       중단 요청 (OCI 처리 정보 포함)
     * @param effectiveDate 중단 유효 기준일
     * @throws BusinessException HD_017 — CFH 중단 시 forecastTransactionExpected가 null
     * @see K-IFRS 1109호 6.5.12   (CFH 중단 후 OCI 후속 처리)
     * @see K-IFRS 1109호 6.5.12(2) (예상거래 발생불가 시 즉시 P&L)
     */
    private void processCfhOciAfterDiscontinuation(
            HedgeRelationship relationship,
            HedgeDiscontinuationRequest request,
            LocalDate effectiveDate) {

        if (request.forecastTransactionExpected() == null) {
            throw new BusinessException("HD_017",
                    String.format("현금흐름위험회피(CFH) 중단 시 예상거래 발생 가능 여부(forecastTransactionExpected)를 "
                            + "반드시 명시해야 합니다. K-IFRS 1109호 6.5.12. "
                            + "hedgeRelationshipId=%s", relationship.getHedgeRelationshipId()));
        }

        if (request.forecastTransactionExpected()) {
            // 6.5.12(1): 예상거래 여전히 발생 가능 → OCI 유지, 분개 미생성
            log.info("CFH 중단 — 예상거래 발생 가능: OCI 유지, 분개 미생성. "
                            + "hedgeRelationshipId={}, K-IFRS 1109호 6.5.12(1)",
                    relationship.getHedgeRelationshipId());
            return;
        }

        // 6.5.12(2): 예상거래 발생 불가 확정 → OCI 즉시 P&L 재분류
        reclassifyOciToPlOnTransactionNoLongerExpected(relationship, request, effectiveDate);
    }

    /**
     * 예상거래 발생 불가 확정 시 OCI → P&L 즉시 재분류 분개 생성 및 저장.
     *
     * <p>K-IFRS 1109호 6.5.12(2): 예상거래가 더 이상 발생하지 않을 것으로 예상되는 경우
     * 현금흐름위험회피적립금에 누적된 금액을 즉시 당기손익으로 재분류합니다.
     *
     * <p>{@code currentOciBalance == ZERO} 이면 분개를 생성하지 않고 로그만 기록합니다.
     *
     * @param relationship  중단 처리된 위험회피관계
     * @param request       중단 요청 (currentOciBalance, plAccount 포함)
     * @param effectiveDate 재분류 기준일
     * @see K-IFRS 1109호 6.5.12(2) (예상거래 발생불가 시 즉시 P&L 재분류 의무)
     */
    private void reclassifyOciToPlOnTransactionNoLongerExpected(
            HedgeRelationship relationship,
            HedgeDiscontinuationRequest request,
            LocalDate effectiveDate) {

        BigDecimal ociBalance = request.currentOciBalance() != null
                ? request.currentOciBalance() : BigDecimal.ZERO;

        if (ociBalance.compareTo(BigDecimal.ZERO) == 0) {
            // OCI 잔액 없음 — 분개 불필요, 로그만 기록
            log.info("CFH 중단 — 예상거래 발생 불가, OCI 잔액 ZERO: 분개 미생성. "
                            + "hedgeRelationshipId={}, K-IFRS 1109호 6.5.12(2)",
                    relationship.getHedgeRelationshipId());
            return;
        }

        AccountCode plAccountCode = resolvePlAccount(request.plAccount(), relationship.getHedgeRelationshipId());

        JournalEntry ociReclassEntry = OciReclassificationJournalGenerator.generate(
                relationship.getHedgeRelationshipId(),
                effectiveDate,
                ociBalance,
                plAccountCode,
                ReclassificationReason.TRANSACTION_NO_LONGER_EXPECTED,
                null   // 최초 OCI 인식일 — 중단 시점에는 추적 불가, null 허용
        );

        journalEntryRepository.save(ociReclassEntry);

        log.info("CFH 중단 OCI 재분류 분개 저장 완료: hedgeRelationshipId={}, "
                        + "ociBalance={}, plAccount={}, K-IFRS 1109호 6.5.12(2)",
                relationship.getHedgeRelationshipId(), ociBalance, plAccountCode);
    }

    /**
     * P&L 계정 코드 문자열을 AccountCode enum으로 변환합니다.
     *
     * <p>plAccount가 null이거나 유효하지 않은 경우 기본값 {@link AccountCode#RECLASSIFY_PL}을 사용합니다.
     *
     * @param plAccountName       요청에서 전달된 P&L 계정 코드 이름 (nullable)
     * @param hedgeRelationshipId 위험회피관계 ID (로깅용)
     * @return AccountCode enum 값
     */
    private AccountCode resolvePlAccount(String plAccountName, String hedgeRelationshipId) {
        if (plAccountName == null) {
            log.warn("CFH 중단 OCI 재분류: plAccount 미지정 — 기본값 RECLASSIFY_PL 사용. "
                    + "hedgeRelationshipId={}", hedgeRelationshipId);
            return AccountCode.RECLASSIFY_PL;
        }
        try {
            return AccountCode.valueOf(plAccountName);
        } catch (IllegalArgumentException e) {
            log.warn("CFH 중단 OCI 재분류: 알 수 없는 plAccount='{}' — 기본값 RECLASSIFY_PL 사용. "
                    + "hedgeRelationshipId={}", plAccountName, hedgeRelationshipId);
            return AccountCode.RECLASSIFY_PL;
        }
    }

    /**
     * 위험회피관계 조회 — 중단 처리 전용.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 조회된 위험회피관계
     * @throws BusinessException HD_009 — 존재하지 않는 위험회피관계
     */
    private HedgeRelationship loadHedgeRelationshipForDiscontinuation(String hedgeRelationshipId) {
        return hedgeRelationshipRepository
                .findById(hedgeRelationshipId)
                .orElseThrow(() -> new BusinessException("HD_009",
                        "위험회피관계를 찾을 수 없습니다. hedgeRelationshipId=" + hedgeRelationshipId));
    }

    /**
     * 중단 유효 기준일 결정 — 요청에 날짜가 있으면 사용, 없으면 오늘.
     *
     * @param request 중단 요청
     * @return 중단 유효 기준일
     */
    private LocalDate resolveEffectiveDate(HedgeDiscontinuationRequest request) {
        return request.discontinuationDate() != null
                ? request.discontinuationDate()
                : LocalDate.now();
    }

    /**
     * 위험회피관계 단건 조회.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 헤지 지정 상세 응답
     * @throws BusinessException HD_009 — 존재하지 않는 위험회피관계
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    @Transactional(readOnly = true)
    public HedgeDesignationResponse findById(String hedgeRelationshipId) {
        HedgeRelationship relationship = hedgeRelationshipRepository
                .findById(hedgeRelationshipId)
                .orElseThrow(() -> new BusinessException("HD_009",
                        "위험회피관계를 찾을 수 없습니다. hedgeRelationshipId=" + hedgeRelationshipId));

        HedgedItem hedgedItem = hedgedItemRepository
                .findById(relationship.getHedgedItemId())
                .orElseThrow(() -> new BusinessException("HD_010",
                        "연계된 위험회피대상항목을 찾을 수 없습니다. hedgedItemId=" + relationship.getHedgedItemId()));

        InstrumentType instrumentType = relationship.getInstrumentType() != null
                ? relationship.getInstrumentType() : InstrumentType.FX_FORWARD;

        if (instrumentType == InstrumentType.FX_FORWARD) {
            FxForwardContract instrument = fxForwardContractRepository
                    .findById(relationship.getFxForwardContractId())
                    .orElseThrow(() -> new BusinessException("HD_001",
                            "연계된 위험회피수단 계약을 찾을 수 없습니다. contractId=" + relationship.getFxForwardContractId()));

            EligibilityCheckResult result = HedgeRelationship.performEligibilityCheck(
                    hedgedItem, instrument, relationship.getHedgeRatio());

            if (relationship.getEligibilityStatus() == EligibilityStatus.ELIGIBLE) {
                return HedgeResponseMapper.toSuccess(relationship, hedgedItem, instrument, result);
            } else {
                return HedgeResponseMapper.toIneligible(result, hedgedItem, instrument);
            }
        } else {
            // IRS/CRS — EligibilityCheckResult 재구성 (간이)
            EligibilityCheckResult result = checkEligibilitySimple(hedgedItem, relationship.getHedgeRatio());
            String instrumentId = relationship.getInstrumentId();

            if (relationship.getEligibilityStatus() == EligibilityStatus.ELIGIBLE) {
                return HedgeResponseMapper.toSuccessWithInstrument(
                        relationship, hedgedItem, instrumentType, instrumentId, result);
            } else {
                return HedgeResponseMapper.toIneligibleWithInstrument(
                        result, hedgedItem, instrumentType, instrumentId);
            }
        }
    }

    /**
     * 위험회피관계 목록 조회 (필터 + 페이지네이션).
     *
     * <p>3개 필터의 모든 조합을 {@link HedgeRelationshipSpec}으로 처리합니다.
     * null 필터는 자동으로 무시됩니다.
     *
     * @param hedgeType         헤지 유형 필터 (nullable)
     * @param status            상태 필터 (nullable)
     * @param eligibilityStatus 적격요건 상태 필터 (nullable)
     * @param pageable          페이지네이션
     * @return 위험회피관계 요약 페이지
     */
    @Transactional(readOnly = true)
    public Page<HedgeRelationshipSummaryResponse> findAll(
            HedgeType hedgeType,
            HedgeStatus status,
            EligibilityStatus eligibilityStatus,
            Pageable pageable) {

        Specification<HedgeRelationship> spec = Specification
                .where(HedgeRelationshipSpec.hasHedgeType(hedgeType))
                .and(HedgeRelationshipSpec.hasStatus(status))
                .and(HedgeRelationshipSpec.hasEligibilityStatus(eligibilityStatus));

        return hedgeRelationshipRepository.findAll(spec, pageable)
                .map(HedgeResponseMapper::toSummary);
    }

    // -----------------------------------------------------------------------
    // Private — 분기 처리
    // -----------------------------------------------------------------------

    /**
     * FX Forward 헤지 지정 처리.
     *
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    private HedgeDesignationResponse designateFxForward(
            HedgeDesignationRequest request,
            String contractId,
            HedgedItem hedgedItem) {

        FxForwardContract instrument = loadAndValidateFxForwardInstrument(request, contractId);
        EligibilityCheckResult eligibility = checkEligibility(hedgedItem, instrument, request.hedgeRatio());
        if (!eligibility.isOverallResult()) {
            log.warn("헤지 지정 적격요건 미충족(FX Forward): contractId={}", contractId);
            return HedgeResponseMapper.toIneligible(eligibility, hedgedItem, instrument);
        }
        return saveDesignation(request, hedgedItem, instrument, eligibility);
    }

    /**
     * IRS/CRS 헤지 지정 처리.
     *
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — IRS / CRS)
     */
    private HedgeDesignationResponse designateIrsCrs(
            HedgeDesignationRequest request,
            InstrumentType instrumentType,
            String contractId,
            HedgedItem hedgedItem) {

        validateIrsCrsContract(instrumentType, contractId, request.designationDate());
        EligibilityCheckResult eligibility = checkEligibilitySimple(hedgedItem, request.hedgeRatio());
        if (!eligibility.isOverallResult()) {
            log.warn("헤지 지정 적격요건 미충족: instrumentType={}, contractId={}", instrumentType, contractId);
            return HedgeResponseMapper.toIneligibleWithInstrument(eligibility, hedgedItem, instrumentType, contractId);
        }
        return saveDesignationWithInstrument(request, hedgedItem, instrumentType, contractId, eligibility);
    }

    // -----------------------------------------------------------------------
    // Private — instrumentType / contractId 결정
    // -----------------------------------------------------------------------

    /**
     * 요청으로부터 위험회피수단 유형을 결정합니다.
     *
     * <p>instrumentType이 명시되면 그대로 사용하고,
     * null이면 FX_FORWARD로 기본값 처리합니다 (하위 호환).
     *
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
     */
    private InstrumentType resolveInstrumentType(HedgeDesignationRequest request) {
        if (request.instrumentType() != null) {
            return request.instrumentType();
        }
        return InstrumentType.FX_FORWARD;
    }

    /**
     * 요청으로부터 위험회피수단 계약 ID를 결정합니다.
     *
     * <p>instrumentContractId가 있으면 우선 사용하고,
     * null이면 하위 호환을 위해 fxForwardContractId를 사용합니다.
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화)
     */
    private String resolveInstrumentContractId(HedgeDesignationRequest request) {
        if (request.instrumentContractId() != null) {
            return request.instrumentContractId();
        }
        return request.fxForwardContractId();
    }

    // -----------------------------------------------------------------------
    // Private — 사전 검증
    // -----------------------------------------------------------------------

    /**
     * 헤지기간 사전 검증.
     *
     * <p>헤지기간 종료일(HD_007)과, FX Forward인 경우 헤지대상 통화 매칭(HD_008)을 검증합니다.
     * IRS/CRS는 원화 채권 또는 외화차입금을 대상으로 하므로 USD 통화 검증을 수행하지 않습니다.
     *
     * @param request        헤지 지정 요청
     * @param instrumentType 위험회피수단 유형
     * @throws BusinessException HD_007 — 헤지기간 종료일이 지정일보다 이전
     * @throws BusinessException HD_008 — 헤지대상과 헤지수단 통화 미매칭 (FX Forward만)
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    private void validateHedgePeriod(HedgeDesignationRequest request, InstrumentType instrumentType) {
        if (!request.hedgePeriodEnd().isAfter(request.designationDate())) {
            throw new BusinessException("HD_007",
                    String.format("위험회피기간 종료일(%s)은 지정일(%s)보다 이후여야 합니다.",
                            request.hedgePeriodEnd(), request.designationDate()));
        }

        // FX Forward만 USD 통화 매칭 검증 (IRS는 KRW, CRS는 USD+KRW 복합)
        if (instrumentType == InstrumentType.FX_FORWARD) {
            if (!"USD".equalsIgnoreCase(request.hedgedItem().currency())) {
                throw new BusinessException("HD_008",
                        String.format("헤지대상 통화(%s)가 헤지수단 기초통화(USD)와 다릅니다.",
                                request.hedgedItem().currency()));
            }
        }
    }

    /**
     * 예상거래 발생가능성 검증.
     *
     * <p>K-IFRS 1109호 6.3.3: 예상거래는 발생가능성이 매우 높아야 헤지대상 적격성 충족.
     * PoC에서는 만기일이 지정일 기준 24개월 이내인 예상거래를 발생가능성 높음으로 간주합니다.
     *
     * @param request 헤지 지정 요청
     * @throws BusinessException HD_013 — 예상거래 발생 예상일이 24개월 초과
     * @see K-IFRS 1109호 6.3.3 (예상거래 헤지대상 적격성 — 발생가능성 높음)
     * @see K-IFRS 1109호 B6.3.1~B6.3.5 (발생가능성 판단 기준)
     */
    private void validateForecastTransactionProbability(HedgeDesignationRequest request) {
        if (request.hedgedItem().itemType() != HedgedItemType.FORECAST_TRANSACTION) {
            return;
        }

        LocalDate maturityDate = request.hedgedItem().maturityDate();
        LocalDate cutoff = request.designationDate().plusMonths(24);

        if (maturityDate.isAfter(cutoff)) {
            throw new BusinessException("HD_013",
                    String.format("예상거래 발생 예상일(%s)이 24개월 초과입니다. " +
                                    "발생가능성이 매우 높아야 합니다 (K-IFRS 1109호 6.3.3, B6.3.1). " +
                                    "현재 기준: 지정일 기준 24개월 이내.",
                            maturityDate));
        }

        log.info("예상거래 발생가능성 검증 통과: maturityDate={}, cutoff={}", maturityDate, cutoff);
    }

    /**
     * 위험회피수단 계약 ID가 실제로 입력되었는지 검증.
     *
     * <p>instrumentContractId와 fxForwardContractId 모두 null/blank인 경우,
     * 하위 계약 조회에서 NPE가 발생하거나 "계약 없음(HD_001)"으로 오해되는 것을 방지합니다.
     *
     * @param contractId     결정된 계약 ID (null 가능)
     * @param instrumentType 위험회피수단 유형 (에러 메시지용)
     * @throws BusinessException HD_002 — 계약 ID 미입력
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화 의무)
     */
    private void validateContractIdProvided(String contractId, InstrumentType instrumentType) {
        if (contractId == null || contractId.isBlank()) {
            throw new BusinessException("HD_002",
                    String.format("%s 계약 ID가 입력되지 않았습니다. " +
                            "instrumentContractId 또는 fxForwardContractId 중 하나를 입력하세요.",
                            instrumentType.getKoreanName()));
        }
    }

    /**
     * itemType과 hedgeType, hedgedRisk의 조합 유효성 검증.
     *
     * <p>{@link HedgedItemType}에 정의된 허용 목록을 기준으로 검증합니다.
     * 예: KRW_FIXED_BOND는 FAIR_VALUE만 허용 — CASH_FLOW 지정 시 HD_014 예외.
     * FORECAST_TRANSACTION은 FOREIGN_CURRENCY만 허용 — INTEREST_RATE 지정 시 HD_015 예외.
     *
     * <p>itemType이 null이면 @NotNull 검증이 먼저 처리하므로 이 메서드에서 skip합니다.
     *
     * @param request 헤지 지정 요청
     * @throws BusinessException HD_014 — itemType이 hedgeType을 지원하지 않음
     * @throws BusinessException HD_015 — itemType이 hedgedRisk를 지원하지 않음
     * @see K-IFRS 1109호 6.3.7 (위험 구성요소 지정 요건)
     * @see K-IFRS 1109호 6.5.2 (위험회피관계 3종류)
     */
    private void validateItemTypeCompatibility(HedgeDesignationRequest request) {
        HedgedItemType itemType = request.hedgedItem().itemType();
        if (itemType == null) {
            return; // @NotNull이 먼저 처리
        }

        if (!itemType.isHedgeTypeAllowed(request.hedgeType())) {
            String allowed = Arrays.stream(itemType.getAllowedHedgeTypes())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new BusinessException("HD_014",
                    String.format("항목 유형 %s(%s)은 %s 헤지 유형을 지원하지 않습니다. " +
                            "허용 헤지 유형: [%s]. K-IFRS 1109호 6.5.2",
                            itemType.name(), itemType.getKoreanName(),
                            request.hedgeType(), allowed));
        }

        if (!itemType.isRiskAllowed(request.hedgedRisk())) {
            String allowed = Arrays.stream(itemType.getAllowedRisks())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new BusinessException("HD_015",
                    String.format("항목 유형 %s(%s)은 %s 위험 유형을 지원하지 않습니다. " +
                            "허용 위험 유형: [%s]. K-IFRS 1109호 6.3.7",
                            itemType.name(), itemType.getKoreanName(),
                            request.hedgedRisk(), allowed));
        }
    }

    /**
     * IRS/CRS 계약 존재 및 유효성 검증.
     *
     * @param instrumentType 위험회피수단 유형 (IRS / CRS)
     * @param contractId     계약 ID
     * @param designationDate 지정일
     * @throws BusinessException HD_001 — 존재하지 않는 IRS/CRS 계약
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 공식 지정)
     */
    private void validateIrsCrsContract(
            InstrumentType instrumentType, String contractId, LocalDate designationDate) {

        if (instrumentType == InstrumentType.IRS) {
            irsContractRepository.findById(contractId)
                    .orElseThrow(() -> new BusinessException("HD_001",
                            "IRS 계약을 찾을 수 없습니다. contractId=" + contractId));
            log.info("IRS 계약 유효성 확인: contractId={}", contractId);
        } else if (instrumentType == InstrumentType.CRS) {
            crsContractRepository.findById(contractId)
                    .orElseThrow(() -> new BusinessException("HD_001",
                            "CRS 계약을 찾을 수 없습니다. contractId=" + contractId));
            log.info("CRS 계약 유효성 확인: contractId={}", contractId);
        }

        // HD_016: IRS/CRS 활성 중복 지정 차단 (FX Forward의 HD_005와 동일 원칙)
        // K-IFRS 1109호 6.4.1(2): 한 헤지수단은 동시에 하나의 위험회피관계에만 지정 가능
        hedgeRelationshipRepository
                .findByInstrumentIdAndStatus(contractId, HedgeStatus.DESIGNATED)
                .ifPresent(r -> {
                    throw new BusinessException("HD_016",
                            String.format("해당 %s 계약은 이미 활성 위험회피관계(%s)에 지정되어 있습니다. " +
                                    "재지정 전에 기존 관계를 먼저 중단(discontinue)하십시오. " +
                                    "contractId=%s [K-IFRS 1109호 6.4.1(2)]",
                                    instrumentType.getKoreanName(),
                                    r.getHedgeRelationshipId(),
                                    contractId));
                });
    }

    // -----------------------------------------------------------------------
    // Private — FX Forward 계약 로드 및 검증
    // -----------------------------------------------------------------------

    /**
     * FxForwardContract 조회 및 유효성 검증.
     *
     * <p>계약 존재 여부(HD_001), ACTIVE 상태 및 만기 초과(HD_006), 중복 지정(HD_005)을 순서대로 확인합니다.
     *
     * @param request    헤지 지정 요청
     * @param contractId FX Forward 계약 ID
     * @return 유효성이 확인된 FxForwardContract
     * @throws BusinessException HD_001 — 존재하지 않는 FxForwardContract
     * @throws BusinessException HD_005 — 동일 계약이 이미 DESIGNATED 상태로 활성 중복 지정 시도
     * @throws BusinessException HD_006 — 만기 초과 계약 지정 시도
     * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
     * @see K-IFRS 1109호 6.5.6   (자발적 취소 불가 — 이력 보존 원칙)
     */
    private FxForwardContract loadAndValidateFxForwardInstrument(
            HedgeDesignationRequest request, String contractId) {

        FxForwardContract instrument = fxForwardContractRepository
                .findById(contractId)
                .orElseThrow(() -> new BusinessException("HD_001",
                        "위험회피수단 계약을 찾을 수 없습니다. contractId=" + contractId));

        // ACTIVE 상태 확인 + 만기 초과 검증 (HD_006) — 도메인 메서드 위임
        // K-IFRS 1109호 6.4.1(2): 헤지 지정 요건 — 활성 계약이어야 하며 만기 전이어야 함
        instrument.validateForHedgeDesignation(request.designationDate());

        // HD_005: active 중복 지정 차단
        //
        // [이력 보존 vs 현재 상태 통제 구분]
        // - 이력 보존 (Append-Only): DISCONTINUED / MATURED 등 종료된 위험회피관계 레코드는
        //   K-IFRS 1109호 6.5.6(자발적 취소 불가 원칙)에 따라 영구 보존되며 삭제하지 않습니다.
        //   동일 계약에 대해 과거에 종료된 관계가 있더라도 신규 지정은 허용됩니다.
        //
        // - 현재 상태 통제: 동일 FX Forward 계약에 DESIGNATED(활성) 관계가 이미 존재하는 경우,
        //   신규 지정은 반드시 차단해야 합니다. 중복 지정은 K-IFRS 회계처리상 허용되지 않으며,
        //   먼저 기존 관계를 중단(discontinue)한 후 재지정해야 합니다.
        hedgeRelationshipRepository
                .findByFxForwardContractIdAndStatus(contractId, HedgeStatus.DESIGNATED)
                .ifPresent(r -> {
                    throw new BusinessException("HD_005",
                            "해당 FX Forward 계약은 이미 활성 위험회피관계(" + r.getHedgeRelationshipId()
                            + ")에 지정되어 있습니다. 재지정 전에 기존 관계를 먼저 중단(discontinue)하십시오."
                            + " contractId=" + contractId
                            + " [K-IFRS 1109호 6.5.6: 자발적 취소 불가 원칙]");
                });

        return instrument;
    }

    // -----------------------------------------------------------------------
    // Private — HedgedItem 생성
    // -----------------------------------------------------------------------

    /**
     * HedgedItem 도메인 객체 생성 (저장 전).
     *
     * @param request 헤지 지정 요청
     * @return 생성된 HedgedItem (미저장)
     */
    private HedgedItem buildHedgedItem(HedgeDesignationRequest request) {
        String hedgedItemId = generateHedgedItemId();
        return HedgedItem.of(
                hedgedItemId,
                request.hedgedItem().itemType(),
                request.hedgedItem().currency(),
                request.hedgedItem().notionalAmount(),
                request.hedgedItem().notionalAmountKrw(),
                request.hedgedItem().maturityDate(),
                request.hedgedItem().counterpartyName(),
                request.hedgedItem().counterpartyCreditRating(),
                request.hedgedItem().interestRateType(),
                request.hedgedItem().interestRate(),
                request.hedgedItem().description()
        );
    }

    // -----------------------------------------------------------------------
    // Private — 적격요건 검증
    // -----------------------------------------------------------------------

    /**
     * FX Forward 적격요건 검증 — HedgeRelationship 도메인 메서드 위임.
     *
     * @param hedgedItem 위험회피대상항목
     * @param instrument 위험회피수단 계약
     * @param hedgeRatio 헤지비율
     * @return 적격요건 검증 결과
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    private EligibilityCheckResult checkEligibility(
            HedgedItem hedgedItem, FxForwardContract instrument, BigDecimal hedgeRatio) {
        return HedgeRelationship.performEligibilityCheck(hedgedItem, instrument, hedgeRatio);
    }

    /**
     * IRS/CRS 간이 적격요건 검증 — 조건 3(헤지비율)만 수행.
     *
     * <p>IRS/CRS는 FxForwardContract와 동일한 통화 기반 검증이 아닌
     * 금리/통화 구조 기반 검증이 필요하나, PoC에서는 헤지비율만 검증합니다.
     *
     * @param hedgedItem 위험회피대상항목
     * @param hedgeRatio 헤지비율
     * @return 적격요건 검증 결과
     * @see K-IFRS 1109호 6.4.1(3) (적격요건 3가지)
     */
    private EligibilityCheckResult checkEligibilitySimple(
            HedgedItem hedgedItem, BigDecimal hedgeRatio) {

        // IRS/CRS는 경제적 관계 조건 — 금리/통화 헤지 구조상 반대방향 성립 간주 (PoC)
        ConditionResult condition1 = ConditionResult.pass(
                "IRS/CRS 구조상 금리/통화 위험 경제적 관계 성립 (PoC 간이 검증)",
                "K-IFRS 1109호 6.4.1(3)(가)"
        );

        // 신용위험 — 헤지대상 발행자 신용등급만 확인 (거래상대방은 PoC에서 투자등급 간주)
        CreditRating effectiveRating = hedgedItem.getCounterpartyCreditRating() != null
                ? hedgedItem.getCounterpartyCreditRating() : CreditRating.BBB;
        ConditionResult condition2 = effectiveRating.isInvestmentGrade()
                ? ConditionResult.pass("투자등급 확인: " + effectiveRating, "K-IFRS 1109호 6.4.1(3)(나)")
                : ConditionResult.fail("비투자등급: " + effectiveRating, "K-IFRS 1109호 6.4.1(3)(나)");

        // 헤지비율 — 위험관리 목적 부합성 기반 검증 (K-IFRS 1109호 B6.4.9~B6.4.11)
        // BC6.234: 80~125% 정량 기준은 폐지됨. HedgeRelationship.checkHedgeRatio()와 동일 원칙 적용.
        ConditionResult condition3 = buildHedgeRatioCondition(hedgeRatio);

        return EligibilityCheckResult.of(condition1, condition2, condition3, hedgeRatio, LocalDateTime.now());
    }

    /**
     * 헤지비율 적정성 조건 결과 생성 (IRS/CRS 간이 검증용).
     *
     * <p>K-IFRS 1109호 B6.4.9~B6.4.11: 헤지비율의 핵심 기준은 "위험관리 목적 부합성"입니다.
     * BC6.234에 따라 80~125% 정량 기준은 폐지되었으므로, 참고 범위 이탈은 WARNING으로 처리합니다.
     * HedgeRelationship.checkHedgeRatio()와 동일 원칙을 적용합니다.
     *
     * @param hedgeRatio 헤지비율
     * @return 조건 3 검증 결과
     * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 적정성 — 위험관리 목적 부합)
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 원칙)
     * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
     */
    private ConditionResult buildHedgeRatioCondition(BigDecimal hedgeRatio) {
        BigDecimal hedgeRatioPercent = hedgeRatio
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        // 극단적 비율 — 위험관리 목적 부합성 의심 (FAIL)
        BigDecimal EXTREME_LOWER = new BigDecimal("0.10");
        BigDecimal EXTREME_UPPER = new BigDecimal("3.00");

        if (hedgeRatio.compareTo(BigDecimal.ZERO) <= 0
                || hedgeRatio.compareTo(EXTREME_LOWER) < 0) {
            return ConditionResult.fail(
                    String.format("헤지비율 %s%% — 극단적으로 낮은 비율로 위험관리 목적 부합성 의심. B6.4.9 위반.",
                            hedgeRatioPercent),
                    "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
            );
        }
        if (hedgeRatio.compareTo(EXTREME_UPPER) > 0) {
            return ConditionResult.fail(
                    String.format("헤지비율 %s%% — 극단적으로 높은 비율로 위험관리 목적 부합성 의심(투기적 성격). B6.4.9 위반.",
                            hedgeRatioPercent),
                    "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
            );
        }

        // 참고 범위(80~125%) 이탈 — WARNING, 자동 FAIL 아님 (BC6.234)
        BigDecimal REFERENCE_LOWER = new BigDecimal("0.80");
        BigDecimal REFERENCE_UPPER = new BigDecimal("1.25");
        boolean withinReferenceRange = hedgeRatio.compareTo(REFERENCE_LOWER) >= 0
                && hedgeRatio.compareTo(REFERENCE_UPPER) <= 0;

        if (!withinReferenceRange) {
            String direction = hedgeRatio.compareTo(REFERENCE_LOWER) < 0
                    ? "참고 하한(80%) 미달" : "참고 상한(125%) 초과";
            return ConditionResult.pass(
                    String.format("[WARNING] 헤지비율 %s%% — %s. BC6.234: 범위 이탈은 자동 FAIL 사유가 아님. "
                                    + "위험관리 목적 부합성이 유지되면 적격. 재조정 검토 권고.",
                            hedgeRatioPercent, direction),
                    "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11, BC6.234"
            );
        }

        return ConditionResult.pass(
                String.format("헤지비율 %s%% — 위험관리 목적 부합. 참고 범위(80%%~125%%) 이내. 6.4.1(3)(다) 충족.",
                        hedgeRatioPercent),
                "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
        );
    }

    // -----------------------------------------------------------------------
    // Private — 저장
    // -----------------------------------------------------------------------

    /**
     * FX Forward 헤지 지정 저장 — HedgedItem, HedgeRelationship, FxForwardContract 연계를 원자적으로 처리.
     *
     * @param request     헤지 지정 요청
     * @param hedgedItem  생성된 위험회피대상항목
     * @param instrument  유효성 확인된 FxForwardContract
     * @param eligibility 적격요건 검증 결과
     * @return 헤지 지정 응답 DTO
     * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
     */
    private HedgeDesignationResponse saveDesignation(
            HedgeDesignationRequest request,
            HedgedItem hedgedItem,
            FxForwardContract instrument,
            EligibilityCheckResult eligibility) {

        String hedgeRelationshipId = generateHedgeRelationshipId();

        HedgeRelationship relationship = HedgeRelationship.designate(
                hedgeRelationshipId,
                request.hedgeType(),
                request.hedgedRisk(),
                request.designationDate(),
                request.hedgePeriodEnd(),
                request.hedgeRatio(),
                request.riskManagementObjective(),
                request.hedgeStrategy(),
                instrument.getContractId(),
                hedgedItem.getHedgedItemId(),
                eligibility);

        hedgedItemRepository.save(hedgedItem);
        hedgeRelationshipRepository.save(relationship);

        eventPublisher.publishEvent(new HedgeDesignatedEvent(hedgeRelationshipId, request.hedgeType()));

        log.info("헤지 지정 저장 완료(FX Forward): hedgeRelationshipId={}, hedgeType={}, contractId={}",
                hedgeRelationshipId, request.hedgeType(), instrument.getContractId());

        return HedgeResponseMapper.toSuccess(relationship, hedgedItem, instrument, eligibility);
    }

    /**
     * IRS/CRS 헤지 지정 저장 — HedgedItem, HedgeRelationship 연계를 원자적으로 처리.
     *
     * <p>IRS/CRS는 별도 Contract 엔티티 상태 변경 없이 관계만 저장합니다.
     *
     * @param request        헤지 지정 요청
     * @param hedgedItem     생성된 위험회피대상항목
     * @param instrumentType 위험회피수단 유형 (IRS / CRS)
     * @param contractId     위험회피수단 계약 ID
     * @param eligibility    적격요건 검증 결과
     * @return 헤지 지정 응답 DTO
     * @see K-IFRS 1109호 6.2.1 (IRS/CRS 위험회피수단 적격성)
     */
    private HedgeDesignationResponse saveDesignationWithInstrument(
            HedgeDesignationRequest request,
            HedgedItem hedgedItem,
            InstrumentType instrumentType,
            String contractId,
            EligibilityCheckResult eligibility) {

        String hedgeRelationshipId = generateHedgeRelationshipId();

        HedgeRelationship relationship = HedgeRelationship.designateWithInstrument(
                hedgeRelationshipId,
                request.hedgeType(),
                request.hedgedRisk(),
                request.designationDate(),
                request.hedgePeriodEnd(),
                request.hedgeRatio(),
                request.riskManagementObjective(),
                request.hedgeStrategy(),
                instrumentType,
                contractId,
                hedgedItem.getHedgedItemId(),
                eligibility);

        hedgedItemRepository.save(hedgedItem);
        hedgeRelationshipRepository.save(relationship);

        eventPublisher.publishEvent(new HedgeDesignatedEvent(hedgeRelationshipId, request.hedgeType()));

        log.info("헤지 지정 저장 완료({}): hedgeRelationshipId={}, hedgeType={}, contractId={}",
                instrumentType, hedgeRelationshipId, request.hedgeType(), contractId);

        return HedgeResponseMapper.toSuccessWithInstrument(relationship, hedgedItem, instrumentType, contractId, eligibility);
    }

    // -----------------------------------------------------------------------
    // Private — ID 생성
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 ID 생성 (HR-{yyyyMMdd}-{UUID 앞 8자리}).
     */
    private String generateHedgeRelationshipId() {
        String datePart = LocalDate.now().toString().replace("-", "");
        String uuidPart = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "HR-" + datePart + "-" + uuidPart;
    }

    /**
     * 피헤지항목 ID 생성 (HI-{yyyyMMdd}-{UUID 앞 8자리}).
     */
    private String generateHedgedItemId() {
        String datePart = LocalDate.now().toString().replace("-", "");
        String uuidPart = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "HI-" + datePart + "-" + uuidPart;
    }
}
