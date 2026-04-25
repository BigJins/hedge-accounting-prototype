package com.hedge.prototype.hedge.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 헤지 지정 완료 이벤트 핸들러.
 *
 * <p>{@code BEFORE_COMMIT} 단계에서 실행되므로 부모 트랜잭션 내에서 추가 작업이 가능하다.
 * 현재는 감사 로그 기록에 사용하며, 향후 알림(Notification) 발송 등으로 확장 가능.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HedgeDesignatedEventHandler {

    /**
     * 헤지 지정 완료 시 감사 로그 기록.
     *
     * @param event 헤지 지정 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(HedgeDesignatedEvent event) {
        log.info("[이벤트] 헤지 지정 완료 — hedgeRelationshipId={}, hedgeType={}",
                event.hedgeRelationshipId(), event.hedgeType());
    }
}

