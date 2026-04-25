package com.hedge.prototype.hedge.application.event;

import com.hedge.prototype.hedge.domain.common.HedgeType;

/**
 * 헤지 지정 완료 내부 이벤트.
 *
 * <p>Zero-Payload 원칙: ID만 전달, 핸들러에서 DB 재조회.
 *
 * @param hedgeRelationshipId 위험회피관계 ID
 * @param hedgeType           헤지 유형 (공정가치 / 현금흐름)
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 */
public record HedgeDesignatedEvent(
        String hedgeRelationshipId,
        HedgeType hedgeType
) {}

