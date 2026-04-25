package com.hedge.prototype.hedge.domain.common;

/**
 * 위험회피관계 상태.
 *
 * @see K-IFRS 1109호 6.5.5 (헤지비율 재조정)
 * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가 원칙)
 */
public enum HedgeStatus {

    /** 정상 지정 상태 — K-IFRS 6.4.1 적격요건 충족 */
    DESIGNATED,

    /**
     * 헤지회계 중단.
     * K-IFRS 1109호 6.5.6에 따라 위험관리 목적이 변경된 경우에만 가능.
     */
    DISCONTINUED,

    /**
     * 헤지비율 재조정(Rebalancing).
     * K-IFRS 1109호 6.5.5에 따라 위험관리 목적은 동일하나 헤지비율을 조정한 상태.
     */
    REBALANCED,

    /** 위험회피기간 만기 도래 — 정상 종료 */
    MATURED
}
