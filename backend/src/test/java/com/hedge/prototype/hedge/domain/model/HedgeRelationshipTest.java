package com.hedge.prototype.hedge.domain.model;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import com.hedge.prototype.hedge.domain.policy.ConditionResult;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.valuation.domain.common.ContractStatus;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * HedgeRelationship 도메인 메서드 단위 테스트.
 *
 * <p>K-IFRS 1109호 6.4.1 적격요건 3가지 조건 각각의 PASS/FAIL 케이스를 검증합니다.
 *
 * @see K-IFRS 1109호 6.4.1(3)(가) (경제적 관계 존재)
 * @see K-IFRS 1109호 6.4.1(3)(나) (신용위험 지배적 아님)
 * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 적절)
 */
@DisplayName("HedgeRelationship — K-IFRS 1109호 6.4.1 적격요건 검증")
class HedgeRelationshipTest {

    // =========================================================================
    // 테스트 픽스처 — 데모 시나리오 (hedge-designation.md 섹션 8)
    // =========================================================================

    private static final LocalDate DESIGNATION_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 7, 1);

    private HedgedItem defaultHedgedItem;
    private FxForwardContract defaultInstrument;

    @BeforeEach
    void setUp() {
        // 헤지대상: USD 예금 $10,000,000 (신용등급 A)
        defaultHedgedItem = HedgedItem.of(
                "HI-2026-001",
                HedgedItemType.FX_DEPOSIT,
                "USD",
                new BigDecimal("10000000"),
                new BigDecimal("13500000000"), // 1350원 × 10M
                MATURITY_DATE,
                "미국은행",
                CreditRating.A,
                null,
                null,
                "USD 정기예금"
        );

        // 헤지수단: 통화선도 $10,000,000 (거래상대방 AA등급, 가나은행)
        defaultInstrument = FxForwardContract.designate(
                "INS-2026-001",
                new BigDecimal("10000000"),
                new BigDecimal("1350.0000"),
                DESIGNATION_DATE,
                MATURITY_DATE,
                DESIGNATION_DATE,
                "가나은행",
                CreditRating.AA
        );
    }

    // =========================================================================
    // 데모 시나리오 — 전체 PASS
    // =========================================================================

    @Nested
    @DisplayName("데모 시나리오: 전체 PASS — overallResult=PASS, eligibilityStatus=ELIGIBLE")
    class DemoScenarioAllPass {

        @Test
        @DisplayName("USD 예금 $10M + 통화선도 $10M (A등급/AA등급) — 3조건 모두 PASS")
        void demoScenario_allConditionsPass() {
            EligibilityCheckResult result = HedgeRelationship.performEligibilityCheck(
                    defaultHedgedItem, defaultInstrument, new BigDecimal("1.00"));

            assertThat(result.isOverallResult()).isTrue();
            assertThat(result.getCondition1EconomicRelationship().isResult()).isTrue();
            assertThat(result.getCondition2CreditRisk().isResult()).isTrue();
            assertThat(result.getCondition3HedgeRatio().isResult()).isTrue();
        }
    }

    // =========================================================================
    // 조건 1: 경제적 관계 존재 (6.4.1(3)(가), B6.4.1)
    // =========================================================================

    @Nested
    @DisplayName("조건 1: 경제적 관계 존재 (K-IFRS 1109호 6.4.1(3)(가), B6.4.1)")
    class Condition1EconomicRelationship {

        @Test
        @DisplayName("PASS — 동일 통화(USD), 명목금액 1:1 매칭, 만기 일치")
        void pass_sameUsdCurrencyMatchingNotional() {
            ConditionResult result = new HedgeRelationship()
                    .checkEconomicRelationship(defaultHedgedItem, defaultInstrument);

            assertThat(result.isResult()).isTrue();
            assertThat(result.getDetails()).contains("100%");
            assertThat(result.getKifrsReference()).contains("6.4.1(3)(가)");
        }

        @Test
        @DisplayName("PASS — 명목금액 커버율 150% (허용범위 50%~200% 이내)")
        void pass_notionalCoverage150Percent() {
            HedgedItem smallHedgedItem = HedgedItem.of(
                    "HI-TEST-001", HedgedItemType.FX_DEPOSIT, "USD",
                    new BigDecimal("6666667"), // $10M / $6.67M ≈ 150%
                    null, MATURITY_DATE, null, CreditRating.A,
                    null, null, "테스트");

            ConditionResult result = new HedgeRelationship()
                    .checkEconomicRelationship(smallHedgedItem, defaultInstrument);

            assertThat(result.isResult()).isTrue();
        }

        @Test
        @DisplayName("FAIL — 헤지대상 통화가 EUR (기초변수 불일치)")
        void fail_differentCurrencyEUR() {
            HedgedItem eurItem = HedgedItem.of(
                    "HI-TEST-002", HedgedItemType.FX_DEPOSIT, "EUR",
                    new BigDecimal("10000000"),
                    null, MATURITY_DATE, null, CreditRating.A,
                    null, null, "EUR 예금");

            ConditionResult result = new HedgeRelationship()
                    .checkEconomicRelationship(eurItem, defaultInstrument);

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("EUR");
            assertThat(result.getKifrsReference()).contains("B6.4.1");
        }

        @Test
        @DisplayName("FAIL — 명목금액 커버율 40% (하한 50% 미달)")
        void fail_notionalCoverageBelow50Percent() {
            HedgedItem largeHedgedItem = HedgedItem.of(
                    "HI-TEST-003", HedgedItemType.FX_DEPOSIT, "USD",
                    new BigDecimal("30000000"), // $10M / $30M = 33.3%
                    null, MATURITY_DATE, null, CreditRating.A,
                    null, null, "대형 예금");

            ConditionResult result = new HedgeRelationship()
                    .checkEconomicRelationship(largeHedgedItem, defaultInstrument);

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("50%");
        }

        @Test
        @DisplayName("FAIL — 헤지수단 만기가 헤지대상 만기보다 이전")
        void fail_instrumentMaturityBeforeHedgedItemMaturity() {
            FxForwardContract earlyMaturityInstrument = FxForwardContract.designate(
                    "INS-EARLY-001",
                    new BigDecimal("10000000"),
                    new BigDecimal("1350.0000"),
                    DESIGNATION_DATE,
                    LocalDate.of(2026, 5, 1), // 만기가 헤지대상보다 2개월 이른
                    DESIGNATION_DATE,
                    "가나은행",
                    CreditRating.AA
            );

            ConditionResult result = new HedgeRelationship()
                    .checkEconomicRelationship(defaultHedgedItem, earlyMaturityInstrument);

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("만기");
        }

        @Test
        @DisplayName("FAIL — 기본 자산과 같은 방향의 FX Forward 포지션이면 상쇄 관계가 아니다")
        void fail_sameDirectionPositionDoesNotOffset() {
            FxForwardContract sameDirectionInstrument = FxForwardContract.designate(
                    "INS-SAME-DIR-001",
                    new BigDecimal("10000000"),
                    new BigDecimal("1350.0000"),
                    DESIGNATION_DATE,
                    MATURITY_DATE,
                    DESIGNATION_DATE,
                    "가나은행",
                    CreditRating.AA,
                    FxForwardPosition.BUY_USD_SELL_KRW
            );

            ConditionResult result = new HedgeRelationship()
                    .checkEconomicRelationship(defaultHedgedItem, sameDirectionInstrument);

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("반대 방향");
        }
    }

    // =========================================================================
    // 조건 2: 신용위험 지배적 아님 (6.4.1(3)(나), B6.4.7~B6.4.8)
    // =========================================================================

    @Nested
    @DisplayName("조건 2: 신용위험 지배적 아님 (K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8)")
    class Condition2CreditRisk {

        @Test
        @DisplayName("PASS — 헤지대상 A등급, 헤지수단 AA등급 (양측 모두 투자등급)")
        void pass_bothInvestmentGrade_AandAA() {
            ConditionResult result = new HedgeRelationship()
                    .checkCreditRiskNotDominant(CreditRating.A, CreditRating.AA);

            assertThat(result.isResult()).isTrue();
            assertThat(result.getDetails()).contains("투자등급");
            assertThat(result.getDetails()).contains("A");
            assertThat(result.getDetails()).contains("AA");
            assertThat(result.getKifrsReference()).contains("B6.4.7");
        }

        @Test
        @DisplayName("PASS — 헤지대상 BBB등급, 헤지수단 BBB등급 (투자등급 최하위이지만 통과)")
        void pass_bothBBB_investmentGradeMinimum() {
            ConditionResult result = new HedgeRelationship()
                    .checkCreditRiskNotDominant(CreditRating.BBB, CreditRating.BBB);

            assertThat(result.isResult()).isTrue();
        }

        @Test
        @DisplayName("FAIL — 헤지대상 BB등급 (비투자등급) — 신용위험 지배 가능성")
        void fail_hedgedItemBelowInvestmentGrade_BB() {
            ConditionResult result = new HedgeRelationship()
                    .checkCreditRiskNotDominant(CreditRating.BB, CreditRating.AA);

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("BB");
            assertThat(result.getDetails()).contains("비투자등급");
            assertThat(result.getKifrsReference()).contains("B6.4.7");
        }

        @Test
        @DisplayName("FAIL — 헤지수단 거래상대방 B등급 (비투자등급)")
        void fail_counterpartyBelowInvestmentGrade_B() {
            ConditionResult result = new HedgeRelationship()
                    .checkCreditRiskNotDominant(CreditRating.A, CreditRating.B);

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("B");
            assertThat(result.getDetails()).contains("비투자등급");
        }

        @Test
        @DisplayName("FAIL — 헤지수단 거래상대방 D등급 (부도)")
        void fail_counterpartyDefault() {
            ConditionResult result = new HedgeRelationship()
                    .checkCreditRiskNotDominant(CreditRating.A, CreditRating.D);

            assertThat(result.isResult()).isFalse();
        }
    }

    // =========================================================================
    // 조건 3: 헤지비율 적절 (6.4.1(3)(다), B6.4.9~B6.4.11, B6.4.12)
    // =========================================================================

    @Nested
    @DisplayName("조건 3: 헤지비율 적절 (K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11, BC6.234)")
    class Condition3HedgeRatio {

        @Test
        @DisplayName("PASS — 헤지비율 1.00 (100%, 이상적 — 참고 범위 이내)")
        void pass_hedgeRatio100Percent() {
            // K-IFRS 1109호 B6.4.9: 위험관리 목적 부합성 판단 기준
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("1.00"));

            assertThat(result.isResult()).isTrue();
            assertThat(result.getDetails()).contains("100");
        }

        @Test
        @DisplayName("PASS — 헤지비율 0.80 (80%, 참고 범위 하한 경계 이내)")
        void pass_hedgeRatioAtLowerBound_80Percent() {
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("0.80"));

            assertThat(result.isResult()).isTrue();
        }

        @Test
        @DisplayName("PASS — 헤지비율 1.25 (125%, 참고 범위 상한 경계 이내)")
        void pass_hedgeRatioAtUpperBound_125Percent() {
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("1.25"));

            assertThat(result.isResult()).isTrue();
        }

        @Test
        @DisplayName("PASS(WARNING) — 헤지비율 0.79 (79%, 참고 범위 이탈 — BC6.234: FAIL 사유 아님)")
        void passWithWarning_hedgeRatioBelowReferenceRange_79Percent() {
            // K-IFRS 1109호 BC6.234: 80~125% 정량 기준 폐지 — 범위 이탈은 자동 FAIL 아님
            // 참고 범위 이탈 시 WARNING 메시지와 함께 PASS(ConditionResult.result=true) 반환
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("0.79"));

            assertThat(result.isResult()).isTrue();  // FAIL 아님 — BC6.234 적용
            assertThat(result.getDetails()).contains("WARNING");  // 경고 메시지 포함
            assertThat(result.getDetails()).contains("하한");
        }

        @Test
        @DisplayName("PASS(WARNING) — 헤지비율 1.26 (126%, 참고 범위 이탈 — BC6.234: FAIL 사유 아님)")
        void passWithWarning_hedgeRatioAboveReferenceRange_126Percent() {
            // K-IFRS 1109호 BC6.234: 80~125% 정량 기준 폐지 — 범위 이탈은 자동 FAIL 아님
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("1.26"));

            assertThat(result.isResult()).isTrue();  // FAIL 아님 — BC6.234 적용
            assertThat(result.getDetails()).contains("WARNING");
            assertThat(result.getDetails()).contains("상한");
        }

        @Test
        @DisplayName("PASS(WARNING) — 헤지비율 0.50 (50%, 참고 범위 크게 이탈 — 위험관리 목적 유지되면 적격)")
        void passWithWarning_hedgeRatioFarBelowReferenceRange() {
            // K-IFRS 1109호 BC6.234: 범위 이탈 자체가 FAIL 사유 아님
            // 위험관리 목적이 유지되면 이 비율도 적격 인정 가능
            // 단, 10% 미만이면 극단적 비율로 FAIL (위험관리 목적 부합성 의심)
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("0.50"));

            assertThat(result.isResult()).isTrue();  // 50%는 WARNING이지 FAIL 아님
            assertThat(result.getDetails()).contains("WARNING");
        }

        @Test
        @DisplayName("FAIL — 헤지비율 0.05 (5%, 극단적 저비율 — 위험관리 목적 부합성 의심)")
        void fail_extremelyLowHedgeRatio() {
            // K-IFRS 1109호 B6.4.9: 이익 극대화 목적 배제 — 10% 미만은 FAIL
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("0.05"));

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("극단적");
        }

        @Test
        @DisplayName("FAIL — 헤지비율 4.00 (400%, 극단적 고비율 — 투기적 성격)")
        void fail_extremelyHighHedgeRatio() {
            // K-IFRS 1109호 B6.4.9: 이익 극대화 목적 배제 — 300% 초과는 FAIL
            ConditionResult result = new HedgeRelationship()
                    .checkHedgeRatio(new BigDecimal("4.00"));

            assertThat(result.isResult()).isFalse();
            assertThat(result.getDetails()).contains("극단적");
        }
    }

    // =========================================================================
    // 종합 검증 — validateEligibility (fail-fast 금지)
    // =========================================================================

    @Nested
    @DisplayName("종합 검증: validateEligibility — fail-fast 금지 (모든 조건 검증)")
    class ValidateEligibility {

        @Test
        @DisplayName("조건 2 실패 시에도 조건 1,3 결과는 검증됨 — fail-fast 금지 원칙 확인")
        void noFailFast_allConditionsCheckedEvenIfSomeFail() {
            // K-IFRS 1109호 BC6.234: 헤지비율 0.70은 참고 범위(80~125%) 이탈이지만 FAIL 아님 (WARNING)
            // 신용등급 BB (조건 2 실패), 헤지비율 0.70 (참고 범위 이탈 — WARNING, 조건 3 PASS)
            HedgedItem lowRatedItem = HedgedItem.of(
                    "HI-TEST-FAIL", HedgedItemType.FX_DEPOSIT, "USD",
                    new BigDecimal("10000000"),
                    null, MATURITY_DATE, null,
                    CreditRating.BB, // 비투자등급 → 조건 2 실패
                    null, null, "저신용 예금");

            EligibilityCheckResult result = HedgeRelationship.performEligibilityCheck(
                    lowRatedItem, defaultInstrument, new BigDecimal("0.70")); // 참고 범위 이탈 — WARNING

            // 전체 결과는 조건 2 실패로 FAIL
            assertThat(result.isOverallResult()).isFalse();

            // 하지만 모든 조건이 검증됨 (fail-fast 금지)
            assertThat(result.getCondition1EconomicRelationship().isResult()).isTrue();  // 조건 1은 통과
            assertThat(result.getCondition2CreditRisk().isResult()).isFalse();           // 조건 2 실패 (비투자등급)
            // K-IFRS 1109호 BC6.234: 조건 3 헤지비율 0.70은 참고 범위 이탈이지만 PASS(WARNING)
            assertThat(result.getCondition3HedgeRatio().isResult()).isTrue();            // 조건 3 PASS(WARNING)
            assertThat(result.getCondition3HedgeRatio().getDetails()).contains("WARNING"); // 경고 확인
        }

        @Test
        @DisplayName("전체 PASS — overallResult=true, 3조건 모두 PASS")
        void allPass_overallResultTrue() {
            EligibilityCheckResult result = HedgeRelationship.performEligibilityCheck(
                    defaultHedgedItem, defaultInstrument, new BigDecimal("1.00"));

            assertThat(result.isOverallResult()).isTrue();
            assertThat(result.getCheckedAt()).isNotNull();
            assertThat(result.getKifrsReference()).contains("6.4.1");
        }
    }

    // =========================================================================
    // CreditRating.isInvestmentGrade() 검증
    // =========================================================================

    @Nested
    @DisplayName("CreditRating — isInvestmentGrade() 투자등급 판정")
    class CreditRatingInvestmentGrade {

        @Test
        @DisplayName("AAA, AA, A, BBB — 투자등급 (true)")
        void investmentGrades_returnTrue() {
            assertThat(CreditRating.AAA.isInvestmentGrade()).isTrue();
            assertThat(CreditRating.AA.isInvestmentGrade()).isTrue();
            assertThat(CreditRating.A.isInvestmentGrade()).isTrue();
            assertThat(CreditRating.BBB.isInvestmentGrade()).isTrue();
        }

        @Test
        @DisplayName("BB, B, CCC, D — 비투자등급 (false)")
        void nonInvestmentGrades_returnFalse() {
            assertThat(CreditRating.BB.isInvestmentGrade()).isFalse();
            assertThat(CreditRating.B.isInvestmentGrade()).isFalse();
            assertThat(CreditRating.CCC.isInvestmentGrade()).isFalse();
            assertThat(CreditRating.D.isInvestmentGrade()).isFalse();
        }
    }
}
