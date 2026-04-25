package com.hedge.prototype.journal.domain;

/**
 * 현금흐름위험회피적립금 OCI 재분류 사유.
 *
 * <p>K-IFRS 1109호 6.5.11(다)에 따라 현금흐름위험회피적립금(OCI)을
 * 당기손익으로 재분류하는 경우의 사유를 분류합니다.
 *
 * @see K-IFRS 1109호 6.5.11(다) (재분류 조정 사유)
 * @see K-IFRS 1109호 6.5.12    (현금흐름 헤지 중단 시 OCI 처리)
 */
public enum ReclassificationReason {

    /**
     * 예상거래 실현.
     * 헤지 대상 예상거래가 실현되어 손익에 영향을 미칠 때 재분류.
     *
     * @see K-IFRS 1109호 6.5.11(다)(i) (예상거래 손익 영향 시 재분류)
     */
    TRANSACTION_REALIZED,

    /**
     * 헤지 중단.
     * 위험회피관계 지정을 중단하였으나 예상거래는 여전히 발생 예상 시,
     * 예상거래 손익 실현 시점까지 OCI에 계속 보유하거나 즉시 재분류 선택.
     *
     * @see K-IFRS 1109호 6.5.12 (헤지 중단 시 OCI 잔액 처리)
     */
    HEDGE_DISCONTINUED,

    /**
     * 예상거래 미발생.
     * 헤지 대상 예상거래가 더 이상 발생하지 않을 것으로 예상되어 즉시 P&L 재분류.
     *
     * @see K-IFRS 1109호 6.5.12 (예상거래 미발생 시 즉시 재분류 의무)
     */
    TRANSACTION_NO_LONGER_EXPECTED
}
