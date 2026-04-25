package com.hedge.prototype.journal.domain;

/**
 * 계정과목 유형.
 *
 * <p>재무상태표 및 포괄손익계산서 상의 계정 분류를 나타냅니다.
 * 차대변 방향 결정 및 재무제표 표시 목적에 사용됩니다.
 *
 * @see K-IFRS 1109호 (금융상품 — 계정 분류 기준)
 */
public enum AccountType {

    /** 자산 — 차변 잔액 계정 */
    ASSET,

    /** 부채 — 대변 잔액 계정 */
    LIABILITY,

    /** 자산 차감 계정 (피헤지항목 장부가액 조정) */
    ASSET_CONTRA,

    /**
     * 기타포괄손익 — 현금흐름위험회피적립금.
     *
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피적립금 OCI 처리)
     */
    OCI,

    /** 수익 — 대변 잔액 계정 */
    REVENUE,

    /** 비용 — 차변 잔액 계정 */
    EXPENSE,

    /**
     * 손익 재분류 조정 — OCI에서 당기손익으로 재분류.
     *
     * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 조정)
     */
    PL
}
