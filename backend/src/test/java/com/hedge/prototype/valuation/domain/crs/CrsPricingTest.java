package com.hedge.prototype.valuation.domain.crs;

import com.hedge.prototype.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * CrsPricing 단위 테스트 — DB 없이 순수 계산 검증.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 공정가치 측정)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑 헤지비율 산정)
 */
@DisplayName("CrsPricing 공정가치 계산 테스트")
class CrsPricingTest {

    // -----------------------------------------------------------------------
    // 할인계수 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("할인계수 계산 — r=5%, T=180일 → df < 1.0")
    void calculateDiscountFactor_normalCase() {
        BigDecimal discountRate = new BigDecimal("0.05");
        int days = 180;

        BigDecimal df = CrsPricing.calculateDiscountFactor(discountRate, days);

        assertThat(df).isGreaterThan(BigDecimal.ZERO)
                      .isLessThan(BigDecimal.ONE);
        // 1/(1+0.05×180/365) = 1/1.02466 ≈ 0.97593
        assertThat(df).isBetween(new BigDecimal("0.970"), new BigDecimal("0.985"));
    }

    @Test
    @DisplayName("할인계수 계산 — T≤0이면 1.0 반환")
    void calculateDiscountFactor_zeroDays_returnsOne() {
        BigDecimal df = CrsPricing.calculateDiscountFactor(new BigDecimal("0.05"), 0);
        assertThat(df).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("할인계수 계산 — r=0이면 1.0 반환")
    void calculateDiscountFactor_zeroRate_returnsOne() {
        BigDecimal df = CrsPricing.calculateDiscountFactor(BigDecimal.ZERO, 365);
        assertThat(df).isEqualByComparingTo(BigDecimal.ONE);
    }

    // -----------------------------------------------------------------------
    // 결제 주기 변환 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("결제 주기 변환 — QUARTERLY → 4")
    void resolvePeriodsPerYear_quarterly() {
        assertThat(CrsPricing.resolvePeriodsPerYear("QUARTERLY")).isEqualTo(4);
    }

    @Test
    @DisplayName("결제 주기 변환 — SEMI_ANNUAL → 2")
    void resolvePeriodsPerYear_semiAnnual() {
        assertThat(CrsPricing.resolvePeriodsPerYear("SEMI_ANNUAL")).isEqualTo(2);
    }

    @Test
    @DisplayName("결제 주기 변환 — 지원하지 않는 값이면 BusinessException")
    void resolvePeriodsPerYear_unsupported_throws() {
        assertThatThrownBy(() -> CrsPricing.resolvePeriodsPerYear("WEEKLY"))
                .isInstanceOf(BusinessException.class);
    }

    // -----------------------------------------------------------------------
    // 외화 다리 PV 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("외화 다리 PV — USD 1백만, 5%, 환율 1350, 연간 결제, 1년 잔존")
    void calculateForeignLegPv_annual_oneYear() {
        BigDecimal foreignNotional = new BigDecimal("1000000"); // USD 100만
        BigDecimal foreignCouponRate = new BigDecimal("0.05");  // 5%
        BigDecimal spotRate = new BigDecimal("1350.0");          // KRW/USD
        int remainingDays = 365;
        String freq = "ANNUAL";
        BigDecimal foreignDiscountRate = new BigDecimal("0.05");

        BigDecimal pv = CrsPricing.calculateForeignLegPv(
                foreignNotional, foreignCouponRate, spotRate, remainingDays, freq, foreignDiscountRate);

        assertThat(pv).isGreaterThan(BigDecimal.ZERO);
        // 원금 1백만 USD × 1350 = 13.5억 원이 포함되어 있으므로 큰 값 기대
        assertThat(pv).isGreaterThan(new BigDecimal("1000000000")); // 10억 이상
    }

    @Test
    @DisplayName("외화 다리 PV — 환율 0이면 BusinessException")
    void calculateForeignLegPv_zeroSpotRate_throws() {
        assertThatThrownBy(() -> CrsPricing.calculateForeignLegPv(
                new BigDecimal("1000000"), new BigDecimal("0.05"),
                BigDecimal.ZERO, 365, "ANNUAL", new BigDecimal("0.05")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("외화 다리 PV — 잔존일수 0이면 BusinessException")
    void calculateForeignLegPv_zeroDays_throws() {
        assertThatThrownBy(() -> CrsPricing.calculateForeignLegPv(
                new BigDecimal("1000000"), new BigDecimal("0.05"),
                new BigDecimal("1350"), 0, "ANNUAL", new BigDecimal("0.05")))
                .isInstanceOf(BusinessException.class);
    }

    // -----------------------------------------------------------------------
    // 원화 다리 PV 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("원화 다리 PV — 13.5억 KRW, 3.5%, 반기 결제, 1년 잔존")
    void calculateKrwLegPv_semiAnnual_oneYear() {
        BigDecimal krwNotional = new BigDecimal("1350000000"); // 13.5억
        BigDecimal krwCouponRate = new BigDecimal("0.035");    // 3.5%
        int remainingDays = 365;
        String freq = "SEMI_ANNUAL";
        BigDecimal krwDiscountRate = new BigDecimal("0.033");

        BigDecimal pv = CrsPricing.calculateKrwLegPv(krwNotional, krwCouponRate, remainingDays, freq, krwDiscountRate);

        assertThat(pv).isGreaterThan(BigDecimal.ZERO);
        // 원금 PV(약 13.07억) + 쿠폰 PV 합산 — 실제 계산 기간에 따라 13.5억 초과 가능 (반기 2~3기간 처리)
        assertThat(pv).isGreaterThan(new BigDecimal("1200000000")); // 12억 이상
        assertThat(pv).isLessThan(new BigDecimal("1500000000"));    // 15억 이하
    }

    @Test
    @DisplayName("원화 다리 PV — 이자율 0이면 원금 PV만 반환")
    void calculateKrwLegPv_zeroRate_principalOnly() {
        BigDecimal krwNotional = new BigDecimal("1000000000");
        BigDecimal krwCouponRate = BigDecimal.ZERO;
        int remainingDays = 365;
        String freq = "ANNUAL";
        BigDecimal krwDiscountRate = new BigDecimal("0.03");

        BigDecimal pv = CrsPricing.calculateKrwLegPv(krwNotional, krwCouponRate, remainingDays, freq, krwDiscountRate);

        // 이자 없음 → 원금 PV만: 1000000000 / 1.03 ≈ 970873786
        assertThat(pv).isGreaterThan(new BigDecimal("960000000"))
                      .isLessThan(new BigDecimal("980000000"));
    }

    // -----------------------------------------------------------------------
    // 공정가치 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("공정가치 — 외화다리PV > 원화다리PV → 양수(자산)")
    void calculateFairValue_positive() {
        BigDecimal foreignLegPvKrw = new BigDecimal("1400000000"); // 14억
        BigDecimal krwLegPv = new BigDecimal("1350000000");         // 13.5억

        BigDecimal fv = CrsPricing.calculateFairValue(foreignLegPvKrw, krwLegPv);

        assertThat(fv).isEqualByComparingTo(new BigDecimal("50000000")); // 5천만
    }

    @Test
    @DisplayName("공정가치 — 외화다리PV < 원화다리PV → 음수(부채)")
    void calculateFairValue_negative() {
        BigDecimal foreignLegPvKrw = new BigDecimal("1300000000"); // 13억
        BigDecimal krwLegPv = new BigDecimal("1350000000");         // 13.5억

        BigDecimal fv = CrsPricing.calculateFairValue(foreignLegPvKrw, krwLegPv);

        assertThat(fv).isLessThan(BigDecimal.ZERO);
        assertThat(fv).isEqualByComparingTo(new BigDecimal("-50000000"));
    }

    @Test
    @DisplayName("공정가치 — 양 다리 PV 동일하면 FV=0")
    void calculateFairValue_equal_zero() {
        BigDecimal legPv = new BigDecimal("1350000000");

        BigDecimal fv = CrsPricing.calculateFairValue(legPv, legPv);

        assertThat(fv).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("공정가치 — null 파라미터면 NullPointerException")
    void calculateFairValue_nullParam_throws() {
        assertThatThrownBy(() -> CrsPricing.calculateFairValue(null, new BigDecimal("1350000000")))
                .isInstanceOf(NullPointerException.class);
    }

    // -----------------------------------------------------------------------
    // 엔드투엔드 계산 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("CRS 공정가치 종합 계산 — 환율 상승 시 외화다리PV(KRW) 증가 → FV 개선")
    void fullCalculation_exchangeRateIncrease_improvesFV() {
        BigDecimal foreignNotional = new BigDecimal("1000000"); // USD 100만
        BigDecimal krwNotional = new BigDecimal("1350000000");  // 13.5억 KRW
        BigDecimal foreignRate = new BigDecimal("0.05");
        BigDecimal krwRate = new BigDecimal("0.035");
        int remainingDays = 365;
        String freq = "ANNUAL";
        BigDecimal krwDiscountRate = new BigDecimal("0.033");
        BigDecimal foreignDiscountRate = new BigDecimal("0.05");

        // 현재 환율 1350
        BigDecimal spotRate1 = new BigDecimal("1350.0");
        BigDecimal foreignLegPv1 = CrsPricing.calculateForeignLegPv(
                foreignNotional, foreignRate, spotRate1, remainingDays, freq, foreignDiscountRate);
        BigDecimal krwLegPv = CrsPricing.calculateKrwLegPv(
                krwNotional, krwRate, remainingDays, freq, krwDiscountRate);
        BigDecimal fv1 = CrsPricing.calculateFairValue(foreignLegPv1, krwLegPv);

        // 환율 상승 1400
        BigDecimal spotRate2 = new BigDecimal("1400.0");
        BigDecimal foreignLegPv2 = CrsPricing.calculateForeignLegPv(
                foreignNotional, foreignRate, spotRate2, remainingDays, freq, foreignDiscountRate);
        BigDecimal fv2 = CrsPricing.calculateFairValue(foreignLegPv2, krwLegPv);

        // 환율 상승 시 외화 다리 PV(원화 환산) 증가 → CRS FV 개선
        assertThat(foreignLegPv2).isGreaterThan(foreignLegPv1);
        assertThat(fv2).isGreaterThan(fv1);
    }
}
