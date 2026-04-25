package com.hedge.prototype.journal.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * K-IFRS 1109호 6.5.8 공정가치 위험회피 분개 생성기.
 *
 * <p>공정가치 위험회피에서는 헤지수단과 피헤지항목의 공정가치 변동을
 * 모두 당기손익(P&amp;L)으로 인식합니다. 두 변동의 차이가 비효과성입니다.
 *
 * <p><b>분개 패턴</b>:
 * <pre>
 * [헤지수단 이익 시]
 *   차변: 파생상품자산     / 대변: 파생상품평가이익
 *
 * [헤지수단 손실 시]
 *   차변: 파생상품평가손실  / 대변: 파생상품부채
 *
 * [피헤지항목 공정가치 상승 시]
 *   차변: 피헤지항목장부조정 / 대변: 위험회피이익
 *
 * [피헤지항목 공정가치 하락 시]
 *   차변: 위험회피손실     / 대변: 피헤지항목장부조정
 * </pre>
 *
 * @see K-IFRS 1109호 6.5.8(가) (헤지수단 공정가치 변동 당기손익 인식)
 * @see K-IFRS 1109호 6.5.8(나) (피헤지항목 장부금액 조정 및 당기손익 인식)
 * @see K-IFRS 1109호 6.5.10   (공정가치 헤지 중단 처리)
 */
@Slf4j
public final class FairValueHedgeJournalGenerator {

    private static final String IFRS_REFERENCE_INSTRUMENT = "K-IFRS 1109호 6.5.8(가)";
    private static final String IFRS_REFERENCE_ITEM       = "K-IFRS 1109호 6.5.8(나)";

    private FairValueHedgeJournalGenerator() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * 공정가치 위험회피 분개 목록 생성.
     *
     * <p>헤지수단 분개와 피헤지항목 분개를 각각 생성하여 반환합니다.
     * 두 분개를 합산하면 비효과성(instrumentFvChange + hedgedItemFvChange)이
     * 자동으로 당기손익에 반영됩니다.
     *
     * @param hedgeRelationshipId  위험회피관계 ID
     * @param entryDate            분개 기준일 (보고기간 말)
     * @param instrumentFvChange   헤지수단 공정가치 변동 (양수 = 이익, 음수 = 손실)
     * @param hedgedItemFvChange   피헤지항목 공정가치 변동 (양수 = 상승, 음수 = 하락)
     * @return 분개 목록 (최소 2건)
     * @throws IllegalArgumentException instrumentFvChange 또는 hedgedItemFvChange가 0인 경우
     * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 회계처리)
     */
    public static List<JournalEntry> generate(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemFvChange) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(entryDate, "분개 기준일은 필수입니다.");
        requireNonNull(instrumentFvChange, "헤지수단 공정가치 변동은 필수입니다.");
        requireNonNull(hedgedItemFvChange, "피헤지항목 공정가치 변동은 필수입니다.");

        List<JournalEntry> entries = new ArrayList<>();

        // 1. 헤지수단(파생상품) 분개 생성
        entries.add(generateInstrumentEntry(hedgeRelationshipId, entryDate, instrumentFvChange));

        // 2. 피헤지항목 분개 생성
        entries.add(generateHedgedItemEntry(hedgeRelationshipId, entryDate, hedgedItemFvChange));

        log.info("공정가치 위험회피 분개 생성 완료: hedgeRelationshipId={}, entryDate={}, count={}",
                hedgeRelationshipId, entryDate, entries.size());

        return entries;
    }

    /**
     * 헤지수단(파생상품) 분개 생성.
     *
     * <p>instrumentFvChange > 0: 차변=파생상품자산, 대변=파생상품평가이익
     * <p>instrumentFvChange < 0: 차변=파생상품평가손실, 대변=파생상품부채
     *
     * @see K-IFRS 1109호 6.5.8(가)
     */
    private static JournalEntry generateInstrumentEntry(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal instrumentFvChange) {

        BigDecimal absAmount = instrumentFvChange.abs();
        int signum = instrumentFvChange.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum > 0) {
            // 헤지수단 공정가치 상승 → 자산 증가, 이익 인식
            debit = AccountCode.DRV_ASSET;
            credit = AccountCode.DRV_GAIN_PL;
            description = "공정가치 위험회피 — 헤지수단(파생상품) 공정가치 상승 이익 인식";
        } else {
            // 헤지수단 공정가치 하락 → 손실 인식, 부채 증가
            debit = AccountCode.DRV_LOSS_PL;
            credit = AccountCode.DRV_LIAB;
            description = "공정가치 위험회피 — 헤지수단(파생상품) 공정가치 하락 손실 인식";
        }

        return JournalEntry.of(
                hedgeRelationshipId,
                entryDate,
                JournalEntryType.FAIR_VALUE_HEDGE_INSTRUMENT,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REFERENCE_INSTRUMENT);
    }

    /**
     * 피헤지항목 분개 생성.
     *
     * <p>hedgedItemFvChange > 0: 차변=피헤지항목장부조정, 대변=위험회피이익
     * <p>hedgedItemFvChange < 0: 차변=위험회피손실, 대변=피헤지항목장부조정
     *
     * @see K-IFRS 1109호 6.5.8(나)
     */
    private static JournalEntry generateHedgedItemEntry(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal hedgedItemFvChange) {

        BigDecimal absAmount = hedgedItemFvChange.abs();
        int signum = hedgedItemFvChange.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum > 0) {
            // 피헤지항목 공정가치 상승 → 장부조정 증가, 이익 인식
            debit = AccountCode.HEDGED_ITEM_ADJ;
            credit = AccountCode.HEDGE_GAIN_PL;
            description = "공정가치 위험회피 — 피헤지항목 공정가치 상승에 따른 장부금액 조정 및 이익 인식";
        } else {
            // 피헤지항목 공정가치 하락 → 손실 인식, 장부조정 감소
            debit = AccountCode.HEDGE_LOSS_PL;
            credit = AccountCode.HEDGED_ITEM_ADJ;
            description = "공정가치 위험회피 — 피헤지항목 공정가치 하락에 따른 손실 인식 및 장부금액 조정";
        }

        return JournalEntry.of(
                hedgeRelationshipId,
                entryDate,
                JournalEntryType.FAIR_VALUE_HEDGE_ITEM,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REFERENCE_ITEM);
    }
}
