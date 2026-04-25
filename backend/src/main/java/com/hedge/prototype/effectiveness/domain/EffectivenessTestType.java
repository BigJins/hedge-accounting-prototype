package com.hedge.prototype.effectiveness.domain;

/**
 * Dollar-offset 유효성 테스트 방법 유형.
 *
 * <p>K-IFRS 1109호 B6.4.12는 기간별(Periodic) 및 누적(Cumulative)
 * Dollar-offset 방법 모두를 허용합니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 — Dollar-offset 방법)
 * @see K-IFRS 1109호 B6.4.13 (유효성 평가 방법 선택)
 */
public enum EffectivenessTestType {

    /**
     * 기간별 Dollar-offset.
     *
     * <p>당기(보고기간) 변동액만을 기준으로 유효성 비율을 계산합니다.
     * 단기 변동에 민감하게 반응합니다.
     *
     * @see K-IFRS 1109호 B6.4.12 (기간별 Dollar-offset 허용)
     */
    DOLLAR_OFFSET_PERIODIC,

    /**
     * 누적 Dollar-offset.
     *
     * <p>헤지 지정 이후 누적 변동액을 기준으로 유효성 비율을 계산합니다.
     * 일시적 변동의 영향을 완화합니다.
     *
     * @see K-IFRS 1109호 B6.4.12 (누적 Dollar-offset 허용)
     */
    DOLLAR_OFFSET_CUMULATIVE
}
