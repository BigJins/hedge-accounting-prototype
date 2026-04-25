package com.hedge.prototype.hedge.domain.common;

/**
 * 위험회피회계 중단 사유 코드 체계.
 *
 * <p>K-IFRS 1109호 6.5.6은 자발적 헤지 중단을 명시적으로 금지합니다.
 * "위험회피관계 또는 위험회피관계의 일부가 적용조건을 충족하지 않는 경우에만
 * 전진적으로 위험회피회계를 중단한다"는 원칙에 따라
 * 허용되는 사유를 이 enum으로 코드화합니다.
 *
 * <p>허용 사유 분류:
 * <ul>
 *   <li><b>비자발적 중단</b>: 헤지수단 만기/소멸, 피헤지항목 소멸 — 외부 사건에 의한 중단</li>
 *   <li><b>조건 미충족 중단</b>: 적격요건 미충족, 위험관리 목적 변경 — 조건 변화에 의한 중단</li>
 * </ul>
 *
 * <p>K-IFRS 1109호 6.5.6에 따라 단순 의사결정에 의한 자발적 중단
 * ({@link #VOLUNTARY_DISCONTINUATION})은 허용되지 않으며,
 * 이를 사유로 중단 요청 시 {@link com.hedge.prototype.common.exception.BusinessException}이 발생합니다.
 *
 * @see K-IFRS 1109호 6.5.6 (위험회피관계 자발적 취소 불가 원칙)
 * @see K-IFRS 1109호 B6.5.26 (중단 후 OCI 잔액 처리)
 * @see K-IFRS 1109호 6.5.7 (현금흐름 헤지 중단 시 OCI 처리)
 */
public enum HedgeDiscontinuationReason {

    /**
     * 위험관리 목적 변경 — K-IFRS 1109호 6.5.6 허용 사유.
     *
     * <p>기업의 위험관리 전략 자체가 변경되어 기존 헤지관계가
     * 더 이상 위험관리 목적에 부합하지 않는 경우.
     * 단순 의사결정 변경이 아닌 위험관리 정책의 실질적 변경이어야 합니다.
     *
     * @see K-IFRS 1109호 6.5.6 (위험관리 목적 변경 시 중단)
     */
    RISK_MANAGEMENT_OBJECTIVE_CHANGED,

    /**
     * 헤지수단 만기/소멸 — 비자발적 중단.
     *
     * <p>통화선도, IRS, CRS 등 헤지수단 계약이 만기 도래 또는 조기 소멸로
     * 더 이상 존재하지 않는 경우. 외부 사건에 의한 비자발적 중단입니다.
     *
     * @see K-IFRS 1109호 6.5.6 (헤지수단 소멸로 인한 중단)
     */
    HEDGE_INSTRUMENT_EXPIRED,

    /**
     * 피헤지항목 소멸 — 비자발적 중단.
     *
     * <p>헤지대상 자산/부채/예상거래가 더 이상 존재하지 않거나
     * 예상거래 발생가능성이 더 이상 높지 않은 경우.
     *
     * @see K-IFRS 1109호 6.5.6 (피헤지항목 소멸로 인한 중단)
     * @see K-IFRS 1109호 6.3.3 (예상거래 발생가능성)
     */
    HEDGE_ITEM_NO_LONGER_EXISTS,

    /**
     * 적격요건 미충족 — 조건 미충족에 의한 중단.
     *
     * <p>K-IFRS 1109호 6.4.1의 3가지 적격요건(경제적 관계 존재,
     * 신용위험 지배 아님, 헤지비율 적정) 중 하나 이상을 충족하지 못하여
     * 재조정(Rebalancing)으로도 회복이 불가능한 경우.
     *
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적격요건)
     * @see K-IFRS 1109호 6.5.6 (적용조건 미충족 시 전진 중단)
     */
    ELIGIBILITY_CRITERIA_NOT_MET,

    /**
     * 자발적 중단 시도 — K-IFRS 1109호 6.5.6 위반 (허용 불가).
     *
     * <p><b>이 사유로 중단 요청 시 즉시 {@link com.hedge.prototype.common.exception.BusinessException}이 발생합니다.</b>
     * K-IFRS 1109호 6.5.6은 위험회피관계의 자발적 취소를 명시적으로 금지하고 있습니다.
     * "적용조건을 충족하지 않는 경우에만 전진적으로 위험회피회계를 중단한다"는 원칙에 따라
     * 단순 의사결정에 의한 중단은 허용되지 않습니다.
     *
     * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가 명시)
     */
    VOLUNTARY_DISCONTINUATION;

    /**
     * 허용되는 중단 사유인지 확인.
     *
     * <p>{@link #VOLUNTARY_DISCONTINUATION}은 K-IFRS 1109호 6.5.6에 따라 허용되지 않습니다.
     *
     * @return true이면 허용 사유, false이면 차단 대상
     * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가)
     */
    public boolean isAllowed() {
        return this != VOLUNTARY_DISCONTINUATION;
    }

    /**
     * 비자발적 중단 사유인지 확인.
     *
     * <p>비자발적 중단은 외부 사건(헤지수단 만기/소멸, 피헤지항목 소멸)에 의한 중단입니다.
     *
     * @return true이면 비자발적 중단 (외부 사건)
     */
    public boolean isInvoluntary() {
        return this == HEDGE_INSTRUMENT_EXPIRED || this == HEDGE_ITEM_NO_LONGER_EXISTS;
    }
}
