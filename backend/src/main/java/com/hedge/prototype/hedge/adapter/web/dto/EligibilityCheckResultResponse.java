package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * K-IFRS 1109호 6.4.1 적격요건 종합 검증 결과 응답 DTO.
 *
 * <p>데모 화면의 "K-IFRS 1109호 6.4.1 적격요건 검증" 박스를 구현하는 응답 구조입니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건 3가지)
 */
public record EligibilityCheckResultResponse(

        /** PASS / FAIL */
        String overallResult,

        /** 검증 수행 시각 */
        LocalDateTime checkedAt,

        /** K-IFRS 조항 참조 */
        String kifrsReference,

        /** 조건 1: 경제적 관계 존재 */
        ConditionResultResponse condition1EconomicRelationship,

        /** 조건 2: 신용위험 지배적 아님 */
        ConditionResultResponse condition2CreditRisk,

        /** 조건 3: 헤지비율 적절 */
        ConditionResultResponse condition3HedgeRatio,

        /** 헤지비율 수치 (예: 1.00 = 100%) */
        BigDecimal hedgeRatioValue
) {
    public static EligibilityCheckResultResponse from(EligibilityCheckResult result) {
        return new EligibilityCheckResultResponse(
                result.isOverallResult() ? "PASS" : "FAIL",
                result.getCheckedAt(),
                result.getKifrsReference(),
                ConditionResultResponse.from(result.getCondition1EconomicRelationship()),
                ConditionResultResponse.from(result.getCondition2CreditRisk()),
                ConditionResultResponse.from(result.getCondition3HedgeRatio()),
                result.getHedgeRatioValue()
        );
    }
}
