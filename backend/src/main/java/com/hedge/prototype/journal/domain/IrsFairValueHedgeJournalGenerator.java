package com.hedge.prototype.journal.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * K-IFRS 1109호 6.5.8 IRS(금리스왑) 공정가치 위험회피 분개 생성기.
 *
 * <p>IRS FVH 구조: Pay Floating / Receive Fixed — 고정금리채권(KRW_FIXED_BOND)의
 * 금리위험을 공정가치 위험회피합니다.
 *
 * <p>금리 상승 시나리오 (IRS 이익, 채권 하락):
 * <pre>
 *   [헤지수단 IRS 이익]
 *     차변: 파생상품자산     / 대변: 파생상품평가이익(P&L)
 *
 *   [피헤지항목 채권 장부가치 하락]
 *     차변: 공정가치위험회피손실(P&L) / 대변: 위험회피적용채권장부조정
 * </pre>
 *
 * <p>금리 하락 시나리오 (IRS 손실, 채권 상승):
 * <pre>
 *   [헤지수단 IRS 손실]
 *     차변: 파생상품평가손실(P&L) / 대변: 파생상품부채
 *
 *   [피헤지항목 채권 장부가치 상승]
 *     차변: 위험회피적용채권장부조정 / 대변: 공정가치위험회피이익(P&L)
 * </pre>
 *
 * <p><b>FX Forward FVH와의 차이</b>: 계정과목 코드는 동일하지만
 * 적요(description)와 K-IFRS 참조 조항이 IRS 맥락으로 특화됩니다.
 * IRS FVH 분개는 채권 장부가치 조정이 K-IFRS 1109호 6.5.9에 따라
 * 만기까지 상각(amortization)되어야 합니다 — 이 클래스는 인식 시점
 * 분개만 생성하며 상각 분개는 별도로 처리합니다.
 *
 * <p><b>분개 계정과목 구성 근거</b>: K-IFRS 1109호 §6.5.8 PDF 원문 직접 검증 완료 (2025 개정판).
 * 헤지수단(IRS) 손익은 §6.5.8(가)에 따라 P&amp;L 직접 인식,
 * 피헤지항목(채권) 장부금액 조정은 §6.5.8(나)에 따라 HEDGED_ITEM_ADJ 계정 경유 P&amp;L 인식.
 *
 * @see K-IFRS 1109호 6.5.8     (공정가치위험회피 회계처리)
 * @see K-IFRS 1109호 6.5.8(가) (헤지수단 IRS 공정가치 변동 P&L 인식)
 * @see K-IFRS 1109호 6.5.8(나) (피헤지항목 채권 장부금액 조정 및 P&L 인식)
 * @see K-IFRS 1109호 6.5.9     (공정가치 헤지 중단 후 장부금액 조정 상각)
 */
@Slf4j
public final class IrsFairValueHedgeJournalGenerator {

    private static final String IFRS_REF_INSTRUMENT =
            "K-IFRS 1109호 6.5.8(가) [IRS FVH — 헤지수단 공정가치 변동 P&L]";
    private static final String IFRS_REF_HEDGED_ITEM =
            "K-IFRS 1109호 6.5.8(나), 6.5.9 [IRS FVH — 채권장부금액 조정]";

    private IrsFairValueHedgeJournalGenerator() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * IRS 공정가치 위험회피 분개 목록 생성.
     *
     * <p>헤지수단(IRS) 분개와 피헤지항목(채권) 분개를 각각 생성하여 반환합니다.
     * 두 분개를 합산하면 비효과성(instrumentFvChange + hedgedItemFvChange)이
     * 자동으로 당기손익에 반영됩니다.
     *
     * @param hedgeRelationshipId  위험회피관계 ID
     * @param entryDate            분개 기준일 (보고기간 말)
     * @param instrumentFvChange   IRS 공정가치 변동 (양수 = 이익, 음수 = 손실)
     * @param hedgedItemFvChange   채권 공정가치 변동 (양수 = 상승, 음수 = 하락)
     * @return 분개 목록 (헤지수단 분개 + 피헤지항목 분개, 총 2건)
     * @throws NullPointerException 필수 파라미터가 null인 경우
     * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 회계처리)
     */
    public static List<JournalEntry> generate(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemFvChange) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(entryDate, "분개 기준일은 필수입니다.");
        requireNonNull(instrumentFvChange, "IRS 공정가치 변동은 필수입니다.");
        requireNonNull(hedgedItemFvChange, "채권 공정가치 변동은 필수입니다.");

        List<JournalEntry> entries = new ArrayList<>();

        // 1. 헤지수단(IRS 파생상품) 분개 생성
        entries.add(generateIrsInstrumentEntry(hedgeRelationshipId, entryDate, instrumentFvChange));

        // 2. 피헤지항목(고정금리채권) 장부조정 분개 생성
        entries.add(generateBondHedgedItemEntry(hedgeRelationshipId, entryDate, hedgedItemFvChange));

        log.info("IRS FVH 분개 생성 완료: hedgeRelationshipId={}, entryDate={}, "
                + "instrumentFvChange={}, hedgedItemFvChange={}",
                hedgeRelationshipId, entryDate, instrumentFvChange, hedgedItemFvChange);

        return entries;
    }

    /**
     * 헤지수단(IRS) 분개 생성.
     *
     * <p>instrumentFvChange > 0 (금리 상승, IRS 이익):
     *   차변: 파생상품자산(DRV_ASSET) / 대변: 파생상품평가이익(DRV_GAIN_PL)
     *
     * <p>instrumentFvChange < 0 (금리 하락, IRS 손실):
     *   차변: 파생상품평가손실(DRV_LOSS_PL) / 대변: 파생상품부채(DRV_LIAB)
     *
     * @see K-IFRS 1109호 6.5.8(가)
     */
    private static JournalEntry generateIrsInstrumentEntry(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal instrumentFvChange) {

        BigDecimal absAmount = instrumentFvChange.abs();
        int signum = instrumentFvChange.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum > 0) {
            // 금리 상승 → IRS(Pay Floating/Receive Fixed) 공정가치 상승 → 이익 인식
            debit = AccountCode.DRV_ASSET;
            credit = AccountCode.DRV_GAIN_PL;
            description = "IRS(금리스왑) 공정가치 상승 — 금리 상승으로 IRS 평가이익 인식 (Pay Floating/Receive Fixed)";
        } else {
            // 금리 하락 → IRS 공정가치 하락 → 손실 인식
            debit = AccountCode.DRV_LOSS_PL;
            credit = AccountCode.DRV_LIAB;
            description = "IRS(금리스왑) 공정가치 하락 — 금리 하락으로 IRS 평가손실 인식 (Pay Floating/Receive Fixed)";
        }

        return JournalEntry.of(
                hedgeRelationshipId,
                entryDate,
                JournalEntryType.FAIR_VALUE_HEDGE_INSTRUMENT,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REF_INSTRUMENT);
    }

    /**
     * 피헤지항목(고정금리채권) 장부조정 분개 생성.
     *
     * <p>hedgedItemFvChange > 0 (금리 하락, 채권 상승):
     *   차변: 위험회피적용채권장부조정(HEDGED_ITEM_ADJ) / 대변: 공정가치위험회피이익(HEDGE_GAIN_PL)
     *
     * <p>hedgedItemFvChange < 0 (금리 상승, 채권 하락):
     *   차변: 공정가치위험회피손실(HEDGE_LOSS_PL) / 대변: 위험회피적용채권장부조정(HEDGED_ITEM_ADJ)
     *
     * <p>K-IFRS 1109호 6.5.9: 위험회피 중단 또는 만기 시 장부조정 누계액을
     * 잔여 만기 동안 상각하여 당기손익으로 인식합니다.
     * 이 분개는 당기 인식 분개이며, 상각 분개는 별도 프로세스에서 처리합니다.
     *
     * @see K-IFRS 1109호 6.5.8(나)
     * @see K-IFRS 1109호 6.5.9
     */
    private static JournalEntry generateBondHedgedItemEntry(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal hedgedItemFvChange) {

        BigDecimal absAmount = hedgedItemFvChange.abs();
        int signum = hedgedItemFvChange.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum > 0) {
            // 금리 하락 → 채권 공정가치 상승 → 채권 장부 상향 조정, 이익 인식
            debit = AccountCode.HEDGED_ITEM_ADJ;
            credit = AccountCode.HEDGE_GAIN_PL;
            description = "채권 공정가치 상승 — 위험회피적용채권 장부가치 상향 조정 및 공정가치위험회피이익 인식 "
                    + "(K-IFRS 1109호 6.5.8(나), 6.5.9 상각 대상)";
        } else {
            // 금리 상승 → 채권 공정가치 하락 → 손실 인식, 채권 장부 하향 조정
            debit = AccountCode.HEDGE_LOSS_PL;
            credit = AccountCode.HEDGED_ITEM_ADJ;
            description = "채권 공정가치 하락 — 공정가치위험회피손실 인식 및 위험회피적용채권 장부가치 하향 조정 "
                    + "(K-IFRS 1109호 6.5.8(나), 6.5.9 상각 대상)";
        }

        return JournalEntry.of(
                hedgeRelationshipId,
                entryDate,
                JournalEntryType.FAIR_VALUE_HEDGE_ITEM,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REF_HEDGED_ITEM);
    }
}
