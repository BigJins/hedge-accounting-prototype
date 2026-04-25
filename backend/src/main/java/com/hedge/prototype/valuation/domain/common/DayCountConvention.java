package com.hedge.prototype.valuation.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 이자 계산 일수 관행 (Day Count Convention).
 *
 * <p>통화선도 IRP 공식에서 통화별로 상이한 일수 계산 기준을 적용합니다.
 *
 * <ul>
 *   <li>KRW — Actual/365 Fixed: 한국 자금시장 표준 (CD금리, 통안채, 국고채)</li>
 *   <li>USD — Actual/360: 미 달러 자금시장 표준 (SOFR, Fed Funds)</li>
 * </ul>
 *
 * <p>윤년(366일)에도 분모는 각 통화의 고정 기준을 유지합니다.
 * Actual/Actual(윤년 적용)은 국고채 장기물 쿠폰 계산에만 해당되며
 * FX Forward IRP 계산에는 적용하지 않습니다.
 *
 * @see K-IFRS 1113호 81항    (Level 2 — 관측가능한 투입변수: 시장 표준 day count 준수)
 * @see K-IFRS 1113호 61~66항 (시장참여자 가격결정기법 적용 원칙)
 * @see K-IFRS 1109호 6.5.8   (공정가치위험회피 — 위험회피수단 공정가치 측정)
 * @see K-IFRS 1109호 예시 주석 37 (일수 계산방법 차이)
 */
public enum DayCountConvention {

    /**
     * Actual/365 Fixed — KRW, JPY 자금시장 표준.
     * 분모를 윤년 여부와 관계없이 항상 365로 고정.
     */
    ACT_365(new BigDecimal("365")),

    /**
     * Actual/360 — USD, EUR 자금시장 표준 (SOFR, EURIBOR).
     * 분모를 윤년 여부와 관계없이 항상 360으로 고정.
     */
    ACT_360(new BigDecimal("360"));

    private final BigDecimal daysInYear;

    DayCountConvention(BigDecimal daysInYear) {
        this.daysInYear = daysInYear;
    }

    /**
     * 잔존일수를 연환산 비율로 변환 (T / daysInYear).
     *
     * @param remainingDays 잔존일수 (양수)
     * @param scale         계산 정밀도
     * @return T / daysInYear
     */
    public BigDecimal fraction(int remainingDays, int scale) {
        return new BigDecimal(remainingDays)
                .divide(daysInYear, scale, RoundingMode.HALF_UP);
    }
}
