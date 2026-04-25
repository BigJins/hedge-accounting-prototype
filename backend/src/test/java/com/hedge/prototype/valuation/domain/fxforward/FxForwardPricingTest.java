package com.hedge.prototype.valuation.domain.fxforward;

import com.hedge.prototype.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;

/**
 * 통화선도 공정가치 계산 단위 테스트.
 *
 * <h3>검증 기준</h3>
 * <p>요구사항 명세서(requirements/fair-value-fx-forward.md) §8 데모 시나리오:
 * <pre>
 * 명목원금:        USD 10,000,000
 * 계약 선물환율:   1,380.00 KRW/USD
 * 현물환율(S₀):    1,350.00 KRW/USD
 * 원화이자율:      3.50% (0.035) — KRW Actual/365 Fixed
 * 달러이자율:      5.30% (0.053) — USD Actual/360
 * 잔존일수:        92일
 *
 * 기대 선물환율:   ≈ 1,343.7098 KRW/USD (scale=4 정밀 계산)
 * 기대 공정가치:   ≈ -359,728,422 원 (±300,000 허용)
 * </pre>
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 평가기법)
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 */
@DisplayName("FxForwardPricing — IRP 공정가치 계산")
class FxForwardPricingTest {

    // 데모 시나리오 상수
    private static final BigDecimal SPOT_RATE = new BigDecimal("1350.00");
    private static final BigDecimal KRW_RATE = new BigDecimal("0.035");
    private static final BigDecimal USD_RATE = new BigDecimal("0.053");
    private static final int REMAINING_DAYS = 92;
    private static final BigDecimal CONTRACT_FORWARD_RATE = new BigDecimal("1380.00");
    private static final BigDecimal NOTIONAL_USD = new BigDecimal("10000000");

    // =========================================================================
    // 선물환율 계산 (calculateForwardRate)
    // =========================================================================

    @Nested
    @DisplayName("선물환율 계산")
    class CalculateForwardRate {

        @Test
        @DisplayName("데모 시나리오: 선물환율 ≈ 1,343.71 (USD Actual/360 적용)")
        void demoScenario() {
            BigDecimal result = FxForwardPricing.calculateForwardRate(SPOT_RATE, KRW_RATE, USD_RATE, REMAINING_DAYS);

            // 기대값: 1,343.71 ±0.05 허용 (USD ACT/360 기준)
            assertThat(result)
                    .usingComparator(BigDecimal::compareTo)
                    .isBetween(new BigDecimal("1343.65"), new BigDecimal("1343.80"));
        }

        @Test
        @DisplayName("원화이자율 = 달러이자율 → 선물환율 ≈ 현물환율")
        void equalRates_shouldReturnNearSpotRate() {
            BigDecimal rate = new BigDecimal("0.04");

            BigDecimal result = FxForwardPricing.calculateForwardRate(SPOT_RATE, rate, rate, REMAINING_DAYS);

            // 같은 이자율이면 선물환율 ≈ 현물환율 (약간 차이 있을 수 있음)
            assertThat(result.subtract(SPOT_RATE).abs())
                    .isLessThan(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("원화이자율 > 달러이자율 → 선물환율 > 현물환율")
        void krwRateHigher_shouldGiveForwardPremium() {
            BigDecimal highKrwRate = new BigDecimal("0.08");
            BigDecimal lowUsdRate = new BigDecimal("0.02");

            BigDecimal result = FxForwardPricing.calculateForwardRate(SPOT_RATE, highKrwRate, lowUsdRate, REMAINING_DAYS);

            assertThat(result).usingComparator(BigDecimal::compareTo).isGreaterThan(SPOT_RATE);
        }

        @Test
        @DisplayName("현물환율 0 이하 → FX_002 예외")
        void invalidSpotRate_shouldThrowFX_002() {
            assertThatThrownBy(() ->
                    FxForwardPricing.calculateForwardRate(BigDecimal.ZERO, KRW_RATE, USD_RATE, REMAINING_DAYS))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("현물환율")
                    .extracting("errorCode").isEqualTo("FX_002");
        }

        @Test
        @DisplayName("음수 현물환율 → FX_002 예외")
        void negativeSpotRate_shouldThrowFX_002() {
            assertThatThrownBy(() ->
                    FxForwardPricing.calculateForwardRate(new BigDecimal("-100"), KRW_RATE, USD_RATE, REMAINING_DAYS))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_002");
        }

        @Test
        @DisplayName("음수 이자율 → FX_003 예외")
        void negativeInterestRate_shouldThrowFX_003() {
            assertThatThrownBy(() ->
                    FxForwardPricing.calculateForwardRate(SPOT_RATE, new BigDecimal("-0.01"), USD_RATE, REMAINING_DAYS))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_003");
        }

        @Test
        @DisplayName("잔존일수 0 → FX_001 예외 (만기 초과)")
        void zeroDays_shouldThrowFX_001() {
            assertThatThrownBy(() ->
                    FxForwardPricing.calculateForwardRate(SPOT_RATE, KRW_RATE, USD_RATE, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_001");
        }

        @Test
        @DisplayName("이자율 0% 허용 — 영(0) 이자율 환경")
        void zeroInterestRate_shouldBeAllowed() {
            assertThatCode(() ->
                    FxForwardPricing.calculateForwardRate(SPOT_RATE, BigDecimal.ZERO, BigDecimal.ZERO, REMAINING_DAYS))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 현가계수 계산 (calculateDiscountFactor)
    // =========================================================================

    @Nested
    @DisplayName("현가계수 계산")
    class CalculateDiscountFactor {

        @Test
        @DisplayName("데모 시나리오: 현가계수 ≈ 0.991240")
        void demoScenario() {
            BigDecimal result = FxForwardPricing.calculateDiscountFactor(KRW_RATE, REMAINING_DAYS);

            // 기대값: 0.991240 ±0.000100 허용
            assertThat(result)
                    .usingComparator(BigDecimal::compareTo)
                    .isBetween(new BigDecimal("0.991100"), new BigDecimal("0.991400"));
        }

        @Test
        @DisplayName("이자율 0 → 현가계수 = 1.0")
        void zeroRate_shouldReturnOne() {
            BigDecimal result = FxForwardPricing.calculateDiscountFactor(BigDecimal.ZERO, REMAINING_DAYS);

            assertThat(result.setScale(2, RoundingMode.HALF_UP))
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(new BigDecimal("1.00"));
        }
    }

    // =========================================================================
    // 공정가치 계산 (calculateFairValue)
    // =========================================================================

    @Nested
    @DisplayName("공정가치 계산")
    class CalculateFairValue {

        @Test
        @DisplayName("데모 시나리오: 공정가치 ≈ -359,728,422 원 (±300,000) — USD Actual/360 적용")
        void demoScenario() {
            BigDecimal currentForwardRate = FxForwardPricing.calculateForwardRate(SPOT_RATE, KRW_RATE, USD_RATE, REMAINING_DAYS);
            BigDecimal discountFactor = FxForwardPricing.calculateDiscountFactor(KRW_RATE, REMAINING_DAYS);

            BigDecimal result = FxForwardPricing.calculateFairValue(
                    currentForwardRate, CONTRACT_FORWARD_RATE, NOTIONAL_USD, discountFactor);

            // 기대: 약 -359,728,422 원 (IRP 정밀계산 기준, USD ACT/360 적용)
            // KRW ACT/365: fraction = 92/365, USD ACT/360: fraction = 92/360
            BigDecimal expected = new BigDecimal("-359728422");
            BigDecimal tolerance = new BigDecimal("300000");

            assertThat(result.subtract(expected).abs())
                    .usingComparator(BigDecimal::compareTo)
                    .isLessThanOrEqualTo(tolerance);
        }

        @Test
        @DisplayName("현재선물환율 = 계약선물환율 → 공정가치 = 0")
        void sameRates_shouldGiveZeroFairValue() {
            BigDecimal discountFactor = new BigDecimal("0.99");

            BigDecimal result = FxForwardPricing.calculateFairValue(
                    CONTRACT_FORWARD_RATE, CONTRACT_FORWARD_RATE, NOTIONAL_USD, discountFactor);

            assertThat(result.compareTo(BigDecimal.ZERO)).isZero();
        }

        @Test
        @DisplayName("현재선물환율 > 계약선물환율 → 공정가치 양수 (이익)")
        void forwardRateHigher_shouldGivePositiveFairValue() {
            BigDecimal higherForwardRate = new BigDecimal("1400.00");
            BigDecimal discountFactor = new BigDecimal("0.99");

            BigDecimal result = FxForwardPricing.calculateFairValue(
                    higherForwardRate, CONTRACT_FORWARD_RATE, NOTIONAL_USD, discountFactor);

            assertThat(result.compareTo(BigDecimal.ZERO)).isPositive();
        }

        @Test
        @DisplayName("null 입력 → NullPointerException")
        void nullInput_shouldThrow() {
            assertThatThrownBy(() ->
                    FxForwardPricing.calculateFairValue(null, CONTRACT_FORWARD_RATE, NOTIONAL_USD, BigDecimal.ONE))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("scale=2 정밀도 보장 — 소수점 3자리 이하 반올림")
        void resultShouldHaveScale2() {
            BigDecimal currentForwardRate = new BigDecimal("1343.9700");
            BigDecimal discountFactor = new BigDecimal("0.991240");

            BigDecimal result = FxForwardPricing.calculateFairValue(
                    currentForwardRate, CONTRACT_FORWARD_RATE, NOTIONAL_USD, discountFactor);

            assertThat(result.scale()).isEqualTo(2);
        }
    }
}
