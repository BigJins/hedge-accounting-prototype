package com.hedge.prototype.valuation.application.event;

import java.time.LocalDate;

/**
 * 통화선도 공정가치 평가 완료 내부 이벤트.
 *
 * <p>Zero-Payload 원칙: ID만 전달, 핸들러에서 DB 재조회.
 *
 * @param valuationId   평가 결과 ID
 * @param contractId    통화선도 계약 ID
 * @param valuationDate 평가 기준일
 * @see K-IFRS 1109호 6.5.8 (공정가치헤지 회계처리)
 * @see K-IFRS 1113호 (공정가치 측정)
 */
public record ValuationCompletedEvent(
        Long valuationId,
        String contractId,
        LocalDate valuationDate
) {}

