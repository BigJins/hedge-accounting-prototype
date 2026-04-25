package com.hedge.prototype.valuation.domain.common;

/**
 * 통화선도 계약 상태.
 *
 * @see K-IFRS 1109호 6.5.10 (공정가치위험회피 중단)
 * @see K-IFRS 1109호 6.5.14 (현금흐름위험회피 중단)
 */
public enum ContractStatus {

    /** 헤지 지정 활성 상태 */
    ACTIVE,

    /** 만기 도래 — 결제 완료 */
    MATURED,

    /** 헤지 관계 조기 중단 (K-IFRS 6.5.10/6.5.14) */
    TERMINATED
}
