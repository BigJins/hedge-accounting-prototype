package com.hedge.prototype.hedge.domain.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

/**
 * K-IFRS 1109호 6.4.1 적격요건 종합 검증 결과 값 객체 (Value Object).
 *
 * <p>3가지 적격요건(경제적 관계, 신용위험, 헤지비율) 검증 결과를 종합하여
 * 전체 PASS/FAIL 여부를 결정하는 불변(Immutable) 값 객체입니다.
 *
 * <p>중간에 실패가 발생하더라도 모든 조건을 검증 후 종합 결과를 반환합니다
 * (fail-fast 금지) — 프론트엔드에서 상세 피드백을 제공하기 위함입니다.
 *
 * <p>팩토리 메서드 {@link #of}로만 생성합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건 3가지)
 * @see K-IFRS 1109호 6.4.1(3)(가) (경제적 관계 — B6.4.1 참조)
 * @see K-IFRS 1109호 6.4.1(3)(나) (신용위험 지배적 아님 — B6.4.7~B6.4.8 참조)
 * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 — B6.4.9~B6.4.11 참조)
 */
public final class EligibilityCheckResult {

    /** 3개 조건 모두 PASS인 경우에만 true */
    private final boolean overallResult;

    /** 조건 1: 경제적 관계 존재 (6.4.1(3)(가)) */
    private final ConditionResult condition1EconomicRelationship;

    /** 조건 2: 신용위험 지배적 아님 (6.4.1(3)(나)) */
    private final ConditionResult condition2CreditRisk;

    /** 조건 3: 헤지비율 적절 (6.4.1(3)(다)) */
    private final ConditionResult condition3HedgeRatio;

    /** 헤지비율 수치 (조건 3 상세 — 예: 1.00 = 100%) */
    private final BigDecimal hedgeRatioValue;

    /** 검증 수행 시각 */
    private final LocalDateTime checkedAt;

    /** K-IFRS 조항 참조 */
    private static final String KIFRS_REFERENCE = "K-IFRS 1109호 6.4.1";

    private EligibilityCheckResult(
            ConditionResult condition1EconomicRelationship,
            ConditionResult condition2CreditRisk,
            ConditionResult condition3HedgeRatio,
            BigDecimal hedgeRatioValue,
            LocalDateTime checkedAt) {

        this.condition1EconomicRelationship = requireNonNull(condition1EconomicRelationship, "조건1 결과는 필수입니다.");
        this.condition2CreditRisk = requireNonNull(condition2CreditRisk, "조건2 결과는 필수입니다.");
        this.condition3HedgeRatio = requireNonNull(condition3HedgeRatio, "조건3 결과는 필수입니다.");
        this.hedgeRatioValue = requireNonNull(hedgeRatioValue, "헤지비율 수치는 필수입니다.");
        this.checkedAt = requireNonNull(checkedAt, "검증 시각은 필수입니다.");

        // 3개 조건 모두 통과해야 전체 통과
        this.overallResult = condition1EconomicRelationship.isResult()
                && condition2CreditRisk.isResult()
                && condition3HedgeRatio.isResult();
    }

    /**
     * 적격요건 종합 검증 결과 생성.
     *
     * @param condition1EconomicRelationship 조건 1 결과 (경제적 관계)
     * @param condition2CreditRisk           조건 2 결과 (신용위험)
     * @param condition3HedgeRatio           조건 3 결과 (헤지비율)
     * @param hedgeRatioValue                헤지비율 수치
     * @param checkedAt                      검증 수행 시각
     * @return 종합 검증 결과
     */
    public static EligibilityCheckResult of(
            ConditionResult condition1EconomicRelationship,
            ConditionResult condition2CreditRisk,
            ConditionResult condition3HedgeRatio,
            BigDecimal hedgeRatioValue,
            LocalDateTime checkedAt) {

        return new EligibilityCheckResult(
                condition1EconomicRelationship,
                condition2CreditRisk,
                condition3HedgeRatio,
                hedgeRatioValue,
                checkedAt
        );
    }

    /** @return 전체 적격요건 통과 여부 (3개 조건 모두 PASS 시 true) */
    public boolean isOverallResult() {
        return overallResult;
    }

    /** @return 조건 1 (경제적 관계) 결과 */
    public ConditionResult getCondition1EconomicRelationship() {
        return condition1EconomicRelationship;
    }

    /** @return 조건 2 (신용위험 지배적 아님) 결과 */
    public ConditionResult getCondition2CreditRisk() {
        return condition2CreditRisk;
    }

    /** @return 조건 3 (헤지비율 적절) 결과 */
    public ConditionResult getCondition3HedgeRatio() {
        return condition3HedgeRatio;
    }

    /** @return 헤지비율 수치 (예: 1.00 = 100%) */
    public BigDecimal getHedgeRatioValue() {
        return hedgeRatioValue;
    }

    /** @return 검증 수행 시각 */
    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    /** @return K-IFRS 조항 참조 */
    public String getKifrsReference() {
        return KIFRS_REFERENCE;
    }
}
