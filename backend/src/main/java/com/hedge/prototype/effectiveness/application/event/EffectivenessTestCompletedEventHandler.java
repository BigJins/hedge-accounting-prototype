package com.hedge.prototype.effectiveness.application.event;

import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.hedge.application.HedgeRebalancingService;
import com.hedge.prototype.journal.application.JournalEntryUseCase;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.domain.ActionRequired;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestResult;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유효성 테스트 완료 이벤트 핸들러.
 *
 * <p>유효성 테스트 결과에 따라 분개 생성과 재조정 처리를 수행합니다.
 * {@code BEFORE_COMMIT} 단계에서 실행되므로
 * 분개 저장 실패 시 유효성 테스트 저장도 함께 롤백됩니다.
 *
 * <p><b>분개 생성 책임 분리 (AVM-015 이중 분개 방지):</b>
 * <ul>
 *   <li>PASS → 이 핸들러가 분개 생성 (정상 헤지회계 처리)</li>
 *   <li>WARNING + REBALANCE → 분개 생성 없이 {@link HedgeRebalancingService}로 위임.
 *       RebalancingService가 B6.5.8에 따라 재조정 전 비효과성 분개를 단독으로 생성.
 *       이 핸들러가 분개를 추가로 생성하면 동일 사건에 대해 분개가 이중 저장되는 구조적 결함이 발생함.</li>
 *   <li>FAIL / DISCONTINUE → 분개 생성 없음, 헤지 중단 검토 필요 (K-IFRS 1109호 6.5.6)</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.4.4 (유효성 요건)
 * @see K-IFRS 1109호 6.5.5 (헤지비율 재조정 의무)
 * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가)
 * @see K-IFRS 1109호 6.5.8 (공정가치헤지 회계처리)
 * @see K-IFRS 1109호 6.5.11 (현금흐름헤지 — OCI 재분류)
 * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 당기손익 인식 선행)
 * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EffectivenessTestCompletedEventHandler {

    private final EffectivenessTestRepository effectivenessTestRepository;
    private final JournalEntryUseCase journalEntryUseCase;
    private final HedgeRebalancingService hedgeRebalancingService;

    /**
     * 유효성 테스트 완료 후 자동 분개 생성.
     *
     * @param event 유효성 테스트 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(EffectivenessTestCompletedEvent event) {
        log.info("[이벤트] 유효성 테스트 완료 — effectivenessTestId={}, hedgeRelationshipId={}, result={}, action={}",
                event.effectivenessTestId(), event.hedgeRelationshipId(),
                event.testResult(), event.actionRequired());

        // FAIL: 경제적 관계 훼손 (동방향 비율) — 분개 생성 불가, 헤지 중단 검토 필요
        // K-IFRS 1109호 6.5.6: 적용조건 미충족 시 전진 중단
        if (event.testResult() == EffectivenessTestResult.FAIL
                || event.actionRequired() == ActionRequired.DISCONTINUE) {
            log.warn("[이벤트] 유효성 테스트 FAIL (경제적 관계 훼손) — 분개 생성 생략, 헤지 중단 검토 필요. "
                    + "hedgeRelationshipId={}, K-IFRS 1109호 6.5.6",
                    event.hedgeRelationshipId());
            return;
        }

        // WARNING + REBALANCE: 분개 생성 없이 재조정 서비스에 위임 (AVM-015 이중 분개 방지)
        // K-IFRS 1109호 B6.5.8: 재조정 전 비효과성 분개는 HedgeRebalancingService가 단독 생성.
        // 이 핸들러가 분개를 먼저 생성한 뒤 RebalancingService도 분개를 생성하면
        // 동일 사건(유효성 테스트 결과)에 대한 분개가 2회 저장되는 구조적 이중 분개가 발생합니다.
        // WARNING + REBALANCE 케이스에서 분개 책임은 RebalancingService에 있습니다.
        if (event.testResult() == EffectivenessTestResult.WARNING
                && event.actionRequired() == ActionRequired.REBALANCE) {
            delegateToRebalancingService(event);
            return;
        }

        // PASS: 이 핸들러가 분개 생성 전담
        // K-IFRS 1109호 6.5.8 (공정가치헤지) / 6.5.11 (현금흐름헤지)
        EffectivenessTest test = effectivenessTestRepository.findById(event.effectivenessTestId())
                .orElseThrow(() -> new BusinessException(
                        "EFFECTIVENESS_TEST_NOT_FOUND",
                        "유효성 테스트 결과를 찾을 수 없습니다. id=" + event.effectivenessTestId()));

        // 분개 요청 생성: hedgeType + instrumentType 조합으로 라우팅 결정
        // - IRS + FAIR_VALUE  → forAutoGenerationIrsFvh  (2단계: IRS FVH 전용 분개)
        // - null/FX_FORWARD + FAIR_VALUE → forAutoGenerationFvh (1단계: FX Forward FVH)
        // - CASH_FLOW (수단 무관) → forAutoGenerationCfh
        // K-IFRS 1109호 6.5.8: FVH 분개 구조는 수단 유형 무관, 적요·참조조항만 다름
        JournalEntryRequest request;

        if (event.hedgeType() == HedgeType.FAIR_VALUE
                && InstrumentType.IRS == event.instrumentType()) {
            // 2단계: IRS FVH — IRS 전용 분개 생성기로 라우팅
            request = JournalEntryRequest.forAutoGenerationIrsFvh(
                    test.getHedgeRelationshipId(),
                    test.getTestDate(),
                    test.getInstrumentFvChange(),
                    test.getHedgedItemPvChange());
            log.info("[이벤트] IRS FVH 분개 생성 라우팅 — hedgeRelationshipId={}",
                    event.hedgeRelationshipId());

        } else if (event.hedgeType() == HedgeType.FAIR_VALUE) {
            // 1단계: FX Forward FVH (null = FX_FORWARD 하위호환) — 기존 경로 유지
            request = JournalEntryRequest.forAutoGenerationFvh(
                    test.getHedgeRelationshipId(),
                    test.getTestDate(),
                    test.getInstrumentFvChange(),
                    test.getHedgedItemPvChange());

        } else {
            // CFH: instrumentType 무관 — OCI/P&L 분리 분개
            request = JournalEntryRequest.forAutoGenerationCfh(
                    test.getHedgeRelationshipId(),
                    test.getTestDate(),
                    test.getInstrumentFvChange(),
                    test.getHedgedItemPvChange(),
                    test.getEffectiveAmount(),
                    test.getIneffectiveAmount());
        }

        journalEntryUseCase.createEntries(request);

        log.info("[이벤트] 자동 분개 생성 완료 (PASS) — hedgeRelationshipId={}, entryDate={}",
                event.hedgeRelationshipId(), test.getTestDate());
    }

    // -----------------------------------------------------------------------
    // Private — 단계별 헬퍼
    // -----------------------------------------------------------------------

    /**
     * WARNING + REBALANCE 케이스: 재조정 서비스에 분개 생성 및 헤지비율 변경 위임.
     *
     * <p>이 핸들러에서 분개를 생성하지 않는 이유:
     * {@link HedgeRebalancingService#processRebalancing}이
     * K-IFRS 1109호 B6.5.8에 따라 재조정 전 비효과성 분개를 내부에서 생성합니다.
     * 이 핸들러가 동일 테스트 결과로 분개를 추가 생성하면 이중 분개가 발생합니다.
     *
     * @param event 유효성 테스트 완료 이벤트
     * @see K-IFRS 1109호 6.5.5 (재조정 의무)
     * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 당기손익 인식 선행)
     */
    private void delegateToRebalancingService(EffectivenessTestCompletedEvent event) {
        log.info("[이벤트] WARNING + REBALANCE — 분개 생성 없이 재조정 서비스 위임. "
                + "hedgeRelationshipId={}, K-IFRS 1109호 6.5.5 / B6.5.8",
                event.hedgeRelationshipId());

        EffectivenessTest test = effectivenessTestRepository.findById(event.effectivenessTestId())
                .orElseThrow(() -> new BusinessException(
                        "EFFECTIVENESS_TEST_NOT_FOUND",
                        "유효성 테스트 결과를 찾을 수 없습니다. id=" + event.effectivenessTestId()));

        hedgeRebalancingService.processRebalancing(
                event.effectivenessTestId(),
                event.hedgeRelationshipId(),
                test.getTestDate());

        log.info("[이벤트] 재조정 처리 완료 — hedgeRelationshipId={}",
                event.hedgeRelationshipId());
    }
}

