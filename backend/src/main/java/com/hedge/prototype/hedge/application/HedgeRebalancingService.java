package com.hedge.prototype.hedge.application;

import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.journal.application.JournalEntryUseCase;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 헤지비율 재조정(Rebalancing) 서비스.
 *
 * <p>K-IFRS 1109호 6.5.5는 재조정을 선택이 아닌 의무로 규정합니다.
 * "위험관리 목적이 동일하게 유지되는 경우 재조정이 가능하다면 재조정을 해야 한다."
 *
 * <p>재조정 처리 순서:
 * <ol>
 *   <li>재조정 전 비효과성 당기손익 인식 (B6.5.8 — 선행 의무)</li>
 *   <li>헤지비율 변경 내용을 HedgeRelationship 엔티티에 기록</li>
 *   <li>재조정은 헤지관계 종료 없이 연속성 유지 (새 지정이 아님)</li>
 * </ol>
 *
 * <p>이벤트 핸들러({@link com.hedge.prototype.effectiveness.application.event.EffectivenessTestCompletedEventHandler})에서
 * {@link com.hedge.prototype.effectiveness.domain.ActionRequired#REBALANCE} 액션 시 이 서비스를 호출합니다.
 *
 * @see K-IFRS 1109호 6.5.5 (재조정 의무)
 * @see K-IFRS 1109호 B6.5.7~B6.5.21 (재조정 상세 지침)
 * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 당기손익 인식 선행)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeRebalancingService {

    private final HedgeRelationshipRepository hedgeRelationshipRepository;
    private final EffectivenessTestRepository effectivenessTestRepository;
    private final JournalEntryUseCase journalEntryUseCase;

    /**
     * 재조정 처리 — 비효과성 분개 선행 후 헤지비율 갱신.
     *
     * <p>K-IFRS 1109호 B6.5.8에 따라 재조정 전 비효과성을 먼저 당기손익으로 인식하고,
     * 이후 목표 헤지비율로 헤지관계를 갱신합니다.
     * 재조정 후에도 헤지관계의 연속성은 유지됩니다 (새 지정이 아님).
     *
     * @param effectivenessTestId 재조정 트리거가 된 유효성 테스트 ID
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param rebalancingDate     재조정 기준일
     * @throws BusinessException ET_002 — 존재하지 않는 유효성 테스트
     * @throws BusinessException HD_009 — 존재하지 않는 위험회피관계
     * @see K-IFRS 1109호 6.5.5 (재조정 의무)
     * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 선행 인식)
     */
    @Transactional
    public void processRebalancing(
            Long effectivenessTestId,
            String hedgeRelationshipId,
            LocalDate rebalancingDate) {

        EffectivenessTest test = loadEffectivenessTest(effectivenessTestId);
        validateTestRelationshipMatch(test, hedgeRelationshipId);
        HedgeRelationship relationship = loadHedgeRelationship(hedgeRelationshipId);

        // Step 1: 재조정 전 비효과성 당기손익 인식 (B6.5.8 선행 의무)
        // K-IFRS 1109호 B6.5.8: 재조정 전 비효과적 부분을 먼저 당기손익으로 인식해야 합니다.
        recognizePreRebalancingIneffectiveness(test, relationship.getHedgeType(), rebalancingDate);

        // Step 2: 헤지비율 재조정
        // Dollar-offset 참고 비율을 기반으로 목표 헤지비율 계산
        // K-IFRS 1109호 6.5.5: 위험관리 목적이 동일한 경우 재조정 의무
        BigDecimal newHedgeRatio = calculateTargetHedgeRatio(test, relationship);
        String rebalancingReason = buildRebalancingReason(test, newHedgeRatio);

        relationship.rebalance(newHedgeRatio, rebalancingReason);
        hedgeRelationshipRepository.save(relationship);

        log.info("재조정 처리 완료: hedgeRelationshipId={}, 신규헤지비율={}, 기준일={}",
                hedgeRelationshipId, newHedgeRatio, rebalancingDate);
    }

    private void validateTestRelationshipMatch(EffectivenessTest test, String hedgeRelationshipId) {
        if (!test.getHedgeRelationshipId().equals(hedgeRelationshipId)) {
            throw new BusinessException("HD_018",
                    String.format("effectiveness test does not belong to hedgeRelationshipId. test=%s, request=%s",
                            test.getHedgeRelationshipId(), hedgeRelationshipId));
        }
    }

    // -----------------------------------------------------------------------
    // Private — 단계별 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 재조정 전 비효과성 당기손익 인식 분개 생성.
     *
     * <p>K-IFRS 1109호 B6.5.8: 재조정 전에 비효과적 부분이 있다면
     * 재조정 시점에 당기손익으로 먼저 인식해야 합니다.
     *
     * @param test      재조정 트리거 유효성 테스트 결과
     * @param hedgeType 헤지 유형
     * @param rebalancingDate 재조정 기준일
     * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 선행 인식)
     */
    private void recognizePreRebalancingIneffectiveness(
            EffectivenessTest test,
            HedgeType hedgeType,
            LocalDate rebalancingDate) {

        BigDecimal ineffectiveAmount = test.getIneffectiveAmount();
        if (ineffectiveAmount == null || ineffectiveAmount.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("재조정 전 비효과성 없음 — 분개 생략: hedgeRelationshipId={}", test.getHedgeRelationshipId());
            return;
        }

        // 비효과성이 있는 경우 분개 생성 (재조정 전 비효과성 P&L 인식)
        // K-IFRS 1109호 B6.5.8: 재조정 전 비효과성은 헤지유형과 관계없이 당기손익 인식
        log.info("재조정 전 비효과성 인식 분개 생성: hedgeRelationshipId={}, 비효과액={}, 기준일={}",
                test.getHedgeRelationshipId(), ineffectiveAmount, rebalancingDate);

        JournalEntryRequest journalRequest = hedgeType == HedgeType.FAIR_VALUE
                ? JournalEntryRequest.forAutoGenerationFvh(
                        test.getHedgeRelationshipId(),
                        rebalancingDate,
                        test.getInstrumentFvChange(),
                        test.getHedgedItemPvChange())
                : JournalEntryRequest.forAutoGenerationCfh(
                        test.getHedgeRelationshipId(),
                        rebalancingDate,
                        test.getInstrumentFvChange(),
                        test.getHedgedItemPvChange(),
                        test.getEffectiveAmount(),
                        test.getIneffectiveAmount());

        journalEntryUseCase.createEntries(journalRequest);

        log.info("재조정 전 비효과성 분개 생성 완료: hedgeRelationshipId={}, K-IFRS 1109호 B6.5.8",
                test.getHedgeRelationshipId());
    }

    /**
     * 목표 헤지비율 계산.
     *
     * <p>현재 Dollar-offset 비율(instrumentFvChange / hedgedItemPvChange의 절대값)을 기반으로
     * 80~125% 참고 범위 내로 조정하는 목표 비율을 계산합니다.
     * K-IFRS 1109호 6.5.5에 따라 재조정은 위험관리 목적이 유지되는 범위 내에서 수행합니다.
     *
     * <p>계산 원칙:
     * <ul>
     *   <li>현재 헤지비율을 Dollar-offset 결과 방향에 맞게 조정</li>
     *   <li>극단적 조정은 회피 — 현 비율 대비 최대 20% 범위 내 조정</li>
     *   <li>최종 비율은 10%~300% 범위 내 (B6.4.9 위험관리 목적 부합)</li>
     * </ul>
     *
     * @param test         유효성 테스트 결과 (Dollar-offset 비율 포함)
     * @param relationship 현재 위험회피관계 (현재 헤지비율 기준)
     * @return 목표 헤지비율
     * @see K-IFRS 1109호 6.5.5 (재조정 의무)
     * @see K-IFRS 1109호 B6.5.9~B6.5.10 (재조정 방법)
     */
    private BigDecimal calculateTargetHedgeRatio(EffectivenessTest test, HedgeRelationship relationship) {
        BigDecimal currentRatio = relationship.getHedgeRatio();
        BigDecimal effectivenessRatio = test.getEffectivenessRatio();

        // Dollar-offset 비율이 없거나 0이면 현 비율 유지
        if (effectivenessRatio == null || effectivenessRatio.compareTo(BigDecimal.ZERO) == 0) {
            log.info("Dollar-offset 비율 없음 — 현재 헤지비율 유지: hedgeRelationshipId={}, ratio={}",
                    relationship.getHedgeRelationshipId(), currentRatio);
            return currentRatio;
        }

        // 절대값 기준 Dollar-offset 비율 (반대방향 전제: abs 사용)
        // K-IFRS 1109호 B6.5.9: 재조정 방향은 위험관리 목적에 따라 결정
        BigDecimal absEffRatio = effectivenessRatio.abs();

        // 이상적 헤지비율 = 1.00 (100%) — 가능한 한 1.00에 가깝게 조정
        // Dollar-offset 비율이 1.00보다 낮으면 헤지 과소 → 비율 상향 신호
        // Dollar-offset 비율이 1.00보다 높으면 헤지 과대 → 비율 하향 신호
        BigDecimal ONE = BigDecimal.ONE;
        BigDecimal adjustment = ONE.divide(absEffRatio, 6, java.math.RoundingMode.HALF_UP);

        // 현재 비율에 조정 계수 적용 (현 비율 대비 최대 20% 내 조정)
        BigDecimal rawNewRatio = currentRatio.multiply(adjustment).setScale(4, java.math.RoundingMode.HALF_UP);

        // 극단적 조정 방지: 현 비율 대비 ±20% 범위 내 클램핑
        BigDecimal MAX_ADJUSTMENT = new BigDecimal("0.20");
        BigDecimal maxNewRatio = currentRatio.multiply(ONE.add(MAX_ADJUSTMENT)).setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal minNewRatio = currentRatio.multiply(ONE.subtract(MAX_ADJUSTMENT)).setScale(4, java.math.RoundingMode.HALF_UP);

        BigDecimal clampedRatio = rawNewRatio.max(minNewRatio).min(maxNewRatio);

        // 최종 범위 보장: 10%~300% (B6.4.9 위험관리 목적 부합)
        BigDecimal ABSOLUTE_MIN = new BigDecimal("0.10");
        BigDecimal ABSOLUTE_MAX = new BigDecimal("3.00");
        BigDecimal finalRatio = clampedRatio.max(ABSOLUTE_MIN).min(ABSOLUTE_MAX);

        log.info("재조정 목표 헤지비율 계산: hedgeRelationshipId={}, 현재={}, Dollar-offset참고={}, 목표={}",
                relationship.getHedgeRelationshipId(), currentRatio, absEffRatio, finalRatio);

        return finalRatio;
    }

    /**
     * 재조정 사유 문자열 생성 (감사 추적용).
     *
     * @param test         유효성 테스트 결과
     * @param newHedgeRatio 목표 헤지비율
     * @return 감사 추적용 사유 문자열
     */
    private String buildRebalancingReason(EffectivenessTest test, BigDecimal newHedgeRatio) {
        return String.format(
                "유효성 테스트 기반 재조정 (K-IFRS 1109호 6.5.5): "
                + "테스트ID=%d, 기준일=%s, Dollar-offset비율=%s, 목표비율=%s",
                test.getEffectivenessTestId(),
                test.getTestDate(),
                test.getEffectivenessRatio(),
                newHedgeRatio);
    }

    /**
     * 유효성 테스트 로드.
     *
     * @param effectivenessTestId 유효성 테스트 ID
     * @return 유효성 테스트 엔티티
     * @throws BusinessException ET_002 — 존재하지 않는 유효성 테스트
     */
    private EffectivenessTest loadEffectivenessTest(Long effectivenessTestId) {
        return effectivenessTestRepository.findById(effectivenessTestId)
                .orElseThrow(() -> new BusinessException("ET_002",
                        "유효성 테스트 결과를 찾을 수 없습니다. id=" + effectivenessTestId));
    }

    /**
     * 위험회피관계 로드.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 위험회피관계 엔티티
     * @throws BusinessException HD_009 — 존재하지 않는 위험회피관계
     */
    private HedgeRelationship loadHedgeRelationship(String hedgeRelationshipId) {
        return hedgeRelationshipRepository.findById(hedgeRelationshipId)
                .orElseThrow(() -> new BusinessException("HD_009",
                        "위험회피관계를 찾을 수 없습니다. hedgeRelationshipId=" + hedgeRelationshipId));
    }
}

