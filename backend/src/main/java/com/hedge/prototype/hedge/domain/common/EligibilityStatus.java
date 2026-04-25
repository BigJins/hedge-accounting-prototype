package com.hedge.prototype.hedge.domain.common;

/**
 * K-IFRS 1109호 6.4.1 적격요건 검증 상태.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 */
public enum EligibilityStatus {

    /** 3가지 적격요건(경제적 관계, 신용위험, 헤지비율) 모두 충족 */
    ELIGIBLE,

    /** 1가지 이상 적격요건 미충족 — 헤지회계 적용 불가 */
    INELIGIBLE,

    /** 검증 대기 중 (검증 미실시) */
    PENDING
}
