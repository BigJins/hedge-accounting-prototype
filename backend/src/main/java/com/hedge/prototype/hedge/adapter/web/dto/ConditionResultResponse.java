package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.policy.ConditionResult;

/**
 * 개별 적격요건 조건 결과 응답 DTO.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 */
public record ConditionResultResponse(

        /** PASS / FAIL */
        String result,

        /** 검증 상세 내역 */
        String details,

        /** 관련 K-IFRS 조항 */
        String kifrsReference
) {
    public static ConditionResultResponse from(ConditionResult conditionResult) {
        return new ConditionResultResponse(
                conditionResult.isResult() ? "PASS" : "FAIL",
                conditionResult.getDetails(),
                conditionResult.getKifrsReference()
        );
    }
}
