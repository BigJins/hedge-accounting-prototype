package com.hedge.prototype.hedge.domain.common;

/**
 * 회피 대상 위험 유형.
 *
 * <p>K-IFRS 1109호 6.4.1에 따라 위험회피관계 지정 시 회피 대상 위험을 명확히 식별해야 합니다.
 *
 * @see K-IFRS 1109호 6.4.1(2) (위험관리 목적 및 위험 식별 문서화 의무)
 */
public enum HedgedRisk {

    /**
     * 외화위험 (Foreign Currency Risk).
     * USD/KRW 환율 변동 위험.
     *
     * @see K-IFRS 1109호 6.5.3 (공정가치 헤지 대상 항목 — 외화위험 노출)
     */
    FOREIGN_CURRENCY,

    /**
     * 이자율위험 (Interest Rate Risk).
     * 고정금리/변동금리 전환에 따른 공정가치 또는 현금흐름 변동 위험.
     */
    INTEREST_RATE,

    /**
     * 상품가격위험 (Commodity Price Risk).
     */
    COMMODITY,

    /**
     * 신용위험 (Credit Risk).
     */
    CREDIT
}
