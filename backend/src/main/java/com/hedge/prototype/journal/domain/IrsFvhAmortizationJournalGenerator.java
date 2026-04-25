package com.hedge.prototype.journal.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * K-IFRS 1109호 §6.5.9 IRS FVH 장부금액 조정 상각(Amortization) 분개 생성기.
 *
 * <p>공정가치 위험회피(FVH) 관계가 중단되거나 만기도래한 후,
 * HEDGED_ITEM_ADJ 계정의 누계 잔액은 피헤지항목(고정금리채권)의 잔여 만기에 걸쳐
 * 직선법(straight-line)으로 상각되어 당기손익에 반영됩니다.
 *
 * <p><b>상각 방향 결정 원칙</b>:
 *
 * <p>HEDGED_ITEM_ADJ 차변 잔액(양수 = 채권 장부 상향조정 — 금리 하락 시 발생):
 * <pre>
 *   차변: 위험회피손실(HEDGE_LOSS_PL) / 대변: 피헤지항목장부조정(HEDGED_ITEM_ADJ)
 * </pre>
 * → 장부가치를 원가(par)쪽으로 되돌리는 과정에서 손실 인식.
 * 경제적 의미: 헤지 중단 후 채권 프리미엄(장부상향분)이 만기까지 소멸.
 *
 * <p>HEDGED_ITEM_ADJ 대변 잔액(음수 = 채권 장부 하향조정 — 금리 상승 시 발생):
 * <pre>
 *   차변: 피헤지항목장부조정(HEDGED_ITEM_ADJ) / 대변: 위험회피이익(HEDGE_GAIN_PL)
 * </pre>
 * → 장부가치를 원가(par)쪽으로 되돌리는 과정에서 이익 인식.
 * 경제적 의미: 헤지 중단 후 채권 디스카운트(장부하향분)가 만기까지 소멸.
 *
 * <p><b>상각 계산식 (직선법)</b>:
 * <pre>
 *   periodAmount = |cumulativeAdjBalance| ÷ remainingPeriods  (절대값, 양수)
 * </pre>
 *
 * <p><b>유효이자율법 vs 직선법</b>: §6.5.9는 "유효이자율법"을 요구합니다.
 * 이 클래스는 PoC 단계에서 간소화된 직선법을 사용하며,
 * 실무 시스템에서는 유효이자율(EIR) 기반 스케줄로 교체해야 합니다.
 *
 * <p><b>이 클래스의 책임 범위</b>:
 * <ul>
 *   <li>1회 상각 기간에 대한 단일 분개 생성</li>
 *   <li>전체 상각 스케줄 관리 및 중단 판단은 호출자 책임</li>
 *   <li>누계 상각 후 잔액 추적은 호출자 책임</li>
 * </ul>
 *
 * @see K-IFRS 1109호 §6.5.9  (공정가치헤지 중단 후 장부금액 조정 상각)
 * @see K-IFRS 1109호 §6.5.8(나) (피헤지항목 장부금액 조정 최초 인식 — 상각 대상 발생원)
 * @see IrsFairValueHedgeJournalGenerator (최초 FVH 인식 분개 — 이 클래스가 상각하는 대상)
 */
@Slf4j
public final class IrsFvhAmortizationJournalGenerator {

    private static final String IFRS_REF =
            "K-IFRS 1109호 §6.5.9 [IRS FVH 장부금액 조정 직선상각 — 헤지 중단/만기 후 잔여만기 상각]";

    /**
     * 금액 계산 정밀도 — 소수점 2자리 (KRW 기준).
     */
    private static final int AMOUNT_SCALE = 2;

    private IrsFvhAmortizationJournalGenerator() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * IRS FVH 장부금액 조정 단일 기간 상각 분개 생성.
     *
     * <p>직선법: {@code periodAmount = |cumulativeAdjBalance| / remainingPeriods}
     *
     * <p>부호에 따른 계정과목 결정:
     * <ul>
     *   <li>{@code cumulativeAdjBalance > 0} (차변 잔액, 채권 상향):
     *       차변 HEDGE_LOSS_PL / 대변 HEDGED_ITEM_ADJ</li>
     *   <li>{@code cumulativeAdjBalance < 0} (대변 잔액, 채권 하향):
     *       차변 HEDGED_ITEM_ADJ / 대변 HEDGE_GAIN_PL</li>
     * </ul>
     *
     * @param hedgeRelationshipId  위험회피관계 ID
     * @param amortizationDate     상각 기준일 (기간 말)
     * @param cumulativeAdjBalance HEDGED_ITEM_ADJ 잔여 누계 잔액 (부호 포함).
     *                             양수 = 차변 잔액(채권 상향조정 누계),
     *                             음수 = 대변 잔액(채권 하향조정 누계).
     * @param remainingPeriods     잔여 상각 기간 수 (1 이상). 이번 기간 포함.
     * @return 단일 상각 분개 엔티티
     * @throws NullPointerException     필수 파라미터가 null인 경우
     * @throws IllegalArgumentException cumulativeAdjBalance가 0이거나, remainingPeriods ≤ 0인 경우
     * @see K-IFRS 1109호 §6.5.9
     */
    public static JournalEntry generate(
            String hedgeRelationshipId,
            LocalDate amortizationDate,
            BigDecimal cumulativeAdjBalance,
            int remainingPeriods) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(amortizationDate, "상각 기준일은 필수입니다.");
        requireNonNull(cumulativeAdjBalance, "HEDGED_ITEM_ADJ 누계 잔액은 필수입니다.");

        if (cumulativeAdjBalance.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(
                    "HEDGED_ITEM_ADJ 누계 잔액이 0입니다 — 상각할 금액이 없습니다. " +
                    "hedgeRelationshipId=" + hedgeRelationshipId);
        }
        if (remainingPeriods <= 0) {
            throw new IllegalArgumentException(
                    "잔여 상각 기간은 1 이상이어야 합니다. " +
                    "remainingPeriods=" + remainingPeriods + ", hedgeRelationshipId=" + hedgeRelationshipId);
        }

        // 기간 상각액 계산 (직선법, 절대값)
        BigDecimal periodAmount = cumulativeAdjBalance.abs()
                .divide(new BigDecimal(remainingPeriods), AMOUNT_SCALE, RoundingMode.HALF_UP);

        // 계정과목 및 적요 결정 — 부호에 따른 분기
        int signum = cumulativeAdjBalance.signum();
        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum > 0) {
            // HEDGED_ITEM_ADJ 차변 잔액: 채권 장부 상향조정 누계 → 상각 시 손실 인식
            // K-IFRS §6.5.9: 장부가치 하향 복귀 → P&L 손실 반영
            debit = AccountCode.HEDGE_LOSS_PL;
            credit = AccountCode.HEDGED_ITEM_ADJ;
            description = String.format(
                    "IRS FVH 장부금액 조정 상각 — 채권 상향조정(+) 잔액 KRW %s 중 %d기 분 직선상각 "
                            + "(K-IFRS §6.5.9: 헤지 중단/만기 후 잔여만기 손실 인식)",
                    cumulativeAdjBalance.toPlainString(), remainingPeriods);
        } else {
            // HEDGED_ITEM_ADJ 대변 잔액: 채권 장부 하향조정 누계 → 상각 시 이익 인식
            // K-IFRS §6.5.9: 장부가치 상향 복귀 → P&L 이익 반영
            debit = AccountCode.HEDGED_ITEM_ADJ;
            credit = AccountCode.HEDGE_GAIN_PL;
            description = String.format(
                    "IRS FVH 장부금액 조정 상각 — 채권 하향조정(-) 잔액 KRW %s 중 %d기 분 직선상각 "
                            + "(K-IFRS §6.5.9: 헤지 중단/만기 후 잔여만기 이익 인식)",
                    cumulativeAdjBalance.toPlainString(), remainingPeriods);
        }

        if (description.length() > 500) {
            description = "IRS FVH amortization: straight-line amortization of HEDGED_ITEM_ADJ balance "
                    + cumulativeAdjBalance.toPlainString()
                    + " over remaining periods "
                    + remainingPeriods
                    + " (K-IFRS 1109 6.5.9)";
        }

        JournalEntry entry = JournalEntry.of(
                hedgeRelationshipId,
                amortizationDate,
                JournalEntryType.IRS_FVH_AMORTIZATION,
                debit,
                credit,
                periodAmount,
                description,
                "K-IFRS 1109 6.5.9 IRS FVH");

        log.info("IRS FVH 상각 분개 생성: hedgeRelationshipId={}, amortizationDate={}, "
                        + "cumulativeAdjBalance={}, remainingPeriods={}, periodAmount={}, debit={}, credit={}",
                hedgeRelationshipId, amortizationDate,
                cumulativeAdjBalance, remainingPeriods, periodAmount, debit, credit);

        return entry;
    }
}
