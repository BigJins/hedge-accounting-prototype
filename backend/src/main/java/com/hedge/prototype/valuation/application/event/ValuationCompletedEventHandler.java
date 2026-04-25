package com.hedge.prototype.valuation.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공정가치 평가 완료 이벤트 핸들러.
 *
 * <p>{@code BEFORE_COMMIT} 단계에서 실행되므로 부모 트랜잭션 내에서 추가 작업이 가능하다.
 * 현재는 감사 로그 기록에 사용하며, 향후 시장 리스크 모니터링 연동으로 확장 가능.
 *
 * @see K-IFRS 1113호 (공정가치 측정)
 * @see K-IFRS 1109호 6.5.8 (공정가치헤지 회계처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValuationCompletedEventHandler {

    /**
     * 공정가치 평가 완료 시 감사 로그 기록.
     *
     * @param event 공정가치 평가 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ValuationCompletedEvent event) {
        log.info("[이벤트] 공정가치 평가 완료 — valuationId={}, contractId={}, valuationDate={}",
                event.valuationId(), event.contractId(), event.valuationDate());
    }
}

