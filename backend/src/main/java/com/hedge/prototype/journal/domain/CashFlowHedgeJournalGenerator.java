package com.hedge.prototype.journal.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * K-IFRS 1109호 6.5.11 현금흐름 위험회피 분개 생성기.
 *
 * <p>현금흐름 위험회피에서는 헤지수단 공정가치 변동 중 유효 부분은
 * 기타포괄손익(OCI — 현금흐름위험회피적립금)으로, 비유효 부분은
 * 즉시 당기손익(P&amp;L)으로 인식합니다.
 *
 * <p><b>분개 패턴</b>:
 * <pre>
 * [유효 부분 이익 시]
 *   차변: 파생상품자산              / 대변: 현금흐름위험회피적립금(OCI)
 *
 * [유효 부분 손실 시]
 *   차변: 현금흐름위험회피적립금(OCI) / 대변: 파생상품부채
 *
 * [비유효 부분 이익 시 — 추가 분개]
 *   차변: 파생상품자산              / 대변: 헤지비효과이익
 *
 * [비유효 부분 손실 시 — 추가 분개]
 *   차변: 헤지비효과손실            / 대변: 파생상품부채
 * </pre>
 *
 * @see K-IFRS 1109호 6.5.11(가) (현금흐름 헤지 유효 부분 OCI 인식)
 * @see K-IFRS 1109호 6.5.11(나) (현금흐름 헤지 비유효 부분 즉시 P&L)
 */
@Slf4j
public final class CashFlowHedgeJournalGenerator {

    private static final String IFRS_REFERENCE_EFFECTIVE   = "K-IFRS 1109호 6.5.11(가)";
    private static final String IFRS_REFERENCE_INEFFECTIVE = "K-IFRS 1109호 6.5.11(나)";

    private CashFlowHedgeJournalGenerator() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * 현금흐름 위험회피 분개 목록 생성.
     *
     * <p>유효 부분 분개는 항상 생성되며, 비유효 부분이 0이 아닌 경우에만
     * 비유효 부분 분개를 추가합니다.
     *
     * <p>effectiveAmount의 부호:
     * <ul>
     *   <li>양수(이익): 차변=파생상품자산, 대변=현금흐름위험회피적립금(OCI 증가)</li>
     *   <li>음수(손실): 차변=현금흐름위험회피적립금(OCI 감소), 대변=파생상품부채</li>
     * </ul>
     * 부호 있는 effectiveAmount를 반드시 공급해야 올바른 분개가 생성됩니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param entryDate           분개 기준일 (보고기간 말)
     * @param effectiveAmount     유효 부분 (양수 = 이익, 음수 = 손실)
     * @param ineffectiveAmount   비유효 부분 (양수 = 이익, 음수 = 손실, 0 = 없음)
     * @return 분개 목록 (1~2건)
     * @see K-IFRS 1109호 6.5.11⑴ (유효 부분 OCI 인식 방향 — 수단 손익 방향 동일)
     * @see K-IFRS 1109호 6.5.11⑷㈐ (OCI 적립금 차손 규정)
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 분개)
     */
    public static List<JournalEntry> generate(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal effectiveAmount,
            BigDecimal ineffectiveAmount) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(entryDate, "분개 기준일은 필수입니다.");
        requireNonNull(effectiveAmount, "유효 부분 금액은 필수입니다.");
        requireNonNull(ineffectiveAmount, "비유효 부분 금액은 필수입니다.");

        List<JournalEntry> entries = new ArrayList<>();

        // 1. 유효 부분 분개 생성 (OCI 인식)
        entries.add(generateEffectiveEntry(hedgeRelationshipId, entryDate, effectiveAmount));

        // 2. 비유효 부분 분개 생성 (0이 아닌 경우에만)
        if (ineffectiveAmount.signum() != 0) {
            entries.add(generateIneffectiveEntry(hedgeRelationshipId, entryDate, ineffectiveAmount));
        }

        log.info("현금흐름 위험회피 분개 생성 완료: hedgeRelationshipId={}, entryDate={}, count={}",
                hedgeRelationshipId, entryDate, entries.size());

        return entries;
    }

    /**
     * 유효 부분 분개 생성 (OCI 인식).
     *
     * <p>effectiveAmount > 0: 차변=파생상품자산, 대변=현금흐름위험회피적립금
     * <p>effectiveAmount < 0: 차변=현금흐름위험회피적립금, 대변=파생상품부채
     *
     * @see K-IFRS 1109호 6.5.11(가)
     */
    private static JournalEntry generateEffectiveEntry(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal effectiveAmount) {

        BigDecimal absAmount = effectiveAmount.abs();
        int signum = effectiveAmount.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum >= 0) {
            // 유효 부분 이익 → 파생상품자산 증가, OCI 적립금 증가
            debit = AccountCode.DRV_ASSET;
            credit = AccountCode.CFHR_OCI;
            description = "현금흐름 위험회피 — 헤지수단 유효 부분 이익 OCI 인식 (현금흐름위험회피적립금)";
        } else {
            // 유효 부분 손실 → OCI 적립금 감소, 파생상품부채 증가
            debit = AccountCode.CFHR_OCI;
            credit = AccountCode.DRV_LIAB;
            description = "현금흐름 위험회피 — 헤지수단 유효 부분 손실 OCI 인식 (현금흐름위험회피적립금)";
        }

        return JournalEntry.of(
                hedgeRelationshipId,
                entryDate,
                JournalEntryType.CASH_FLOW_HEDGE_EFFECTIVE,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REFERENCE_EFFECTIVE);
    }

    /**
     * 비유효 부분 분개 생성 (즉시 P&L 인식).
     *
     * <p>ineffectiveAmount > 0: 차변=파생상품자산, 대변=헤지비효과이익
     * <p>ineffectiveAmount < 0: 차변=헤지비효과손실, 대변=파생상품부채
     *
     * @see K-IFRS 1109호 6.5.11(나)
     */
    private static JournalEntry generateIneffectiveEntry(
            String hedgeRelationshipId,
            LocalDate entryDate,
            BigDecimal ineffectiveAmount) {

        BigDecimal absAmount = ineffectiveAmount.abs();
        int signum = ineffectiveAmount.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum > 0) {
            // 비유효 부분 이익 → 파생상품자산 증가, 이익 인식
            debit = AccountCode.DRV_ASSET;
            credit = AccountCode.INEFF_GAIN_PL;
            description = "현금흐름 위험회피 — 헤지수단 비유효 부분 이익 즉시 당기손익 인식";
        } else {
            // 비유효 부분 손실 → 손실 인식, 파생상품부채 증가
            debit = AccountCode.INEFF_LOSS_PL;
            credit = AccountCode.DRV_LIAB;
            description = "현금흐름 위험회피 — 헤지수단 비유효 부분 손실 즉시 당기손익 인식";
        }

        return JournalEntry.of(
                hedgeRelationshipId,
                entryDate,
                JournalEntryType.CASH_FLOW_HEDGE_INEFFECTIVE,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REFERENCE_INEFFECTIVE);
    }
}
