package com.hedge.prototype.journal.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * K-IFRS 1109호 6.5.11(다) OCI 재분류 분개 생성기.
 *
 * <p>현금흐름위험회피적립금(OCI)을 당기손익(P&amp;L)으로 재분류하는 분개를 생성합니다.
 * 예상거래가 실현되거나 헤지가 중단될 때 발생합니다.
 *
 * <p><b>분개 패턴</b>:
 * <pre>
 * [OCI 잔액이 이익(양수)인 경우 재분류]
 *   차변: 현금흐름위험회피적립금(OCI) / 대변: 대응 P&L 계정 (예: 외환이익)
 *
 * [OCI 잔액이 손실(음수)인 경우 재분류]
 *   차변: 대응 P&L 계정 (예: 외환손실) / 대변: 현금흐름위험회피적립금(OCI)
 * </pre>
 *
 * @see K-IFRS 1109호 6.5.11(다) (재분류 조정 — 예상거래 실현 시)
 * @see K-IFRS 1109호 6.5.12    (현금흐름 헤지 중단 시 OCI 잔액 처리)
 */
@Slf4j
public final class OciReclassificationJournalGenerator {

    private static final String IFRS_REFERENCE = "K-IFRS 1109호 6.5.11(다)";

    private OciReclassificationJournalGenerator() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * OCI 재분류 분개 생성.
     *
     * <p>reclassificationAmount > 0: OCI 잔액(이익)을 P&L로 재분류
     *   차변=CFHR_OCI, 대변=plAccount
     *
     * <p>reclassificationAmount < 0: OCI 잔액(손실)을 P&L로 재분류
     *   차변=plAccount, 대변=CFHR_OCI
     *
     * @param hedgeRelationshipId    위험회피관계 ID
     * @param reclassificationDate   재분류 기준일
     * @param reclassificationAmount 재분류 금액 (양수 = OCI 이익→P&L, 음수 = OCI 손실→P&L)
     * @param plAccount              대응 P&L 계정 (FX_GAIN_PL, FX_LOSS_PL, INTEREST_INCOME 등)
     * @param reason                 재분류 사유
     * @param originalOciEntryDate   최초 OCI 인식일 (추적 목적)
     * @return OCI 재분류 분개 엔티티
     * @see K-IFRS 1109호 6.5.11(다) (재분류 조정)
     */
    public static JournalEntry generate(
            String hedgeRelationshipId,
            LocalDate reclassificationDate,
            BigDecimal reclassificationAmount,
            AccountCode plAccount,
            ReclassificationReason reason,
            LocalDate originalOciEntryDate) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(reclassificationDate, "재분류 기준일은 필수입니다.");
        requireNonNull(reclassificationAmount, "재분류 금액은 필수입니다.");
        requireNonNull(plAccount, "대응 P&L 계정은 필수입니다.");
        requireNonNull(reason, "재분류 사유는 필수입니다.");

        BigDecimal absAmount = reclassificationAmount.abs();
        int signum = reclassificationAmount.signum();

        AccountCode debit;
        AccountCode credit;
        String description;

        if (signum >= 0) {
            // OCI 이익(양수) → P&L로 재분류: OCI 감소, P&L 수익 인식
            debit  = AccountCode.CFHR_OCI;
            credit = plAccount;
            description = buildDescription(reason, "OCI 이익 재분류 — 현금흐름위험회피적립금 → " + plAccount.getKoreanName());
        } else {
            // OCI 손실(음수) → P&L로 재분류: P&L 비용 인식, OCI 증가(차감 취소)
            debit  = plAccount;
            credit = AccountCode.CFHR_OCI;
            description = buildDescription(reason, "OCI 손실 재분류 — " + plAccount.getKoreanName() + " → 현금흐름위험회피적립금");
        }

        JournalEntry entry = JournalEntry.ofReclassification(
                hedgeRelationshipId,
                reclassificationDate,
                debit,
                credit,
                absAmount,
                description,
                IFRS_REFERENCE,
                reason,
                originalOciEntryDate);

        log.info("OCI 재분류 분개 생성: hedgeRelationshipId={}, reclassificationDate={}, reason={}",
                hedgeRelationshipId, reclassificationDate, reason);

        return entry;
    }

    private static String buildDescription(ReclassificationReason reason, String baseDescription) {
        String reasonKorean = switch (reason) {
            case TRANSACTION_REALIZED           -> "예상거래 실현";
            case HEDGE_DISCONTINUED             -> "헤지 중단";
            case TRANSACTION_NO_LONGER_EXPECTED -> "예상거래 미발생";
        };
        return baseDescription + " [사유: " + reasonKorean + "]";
    }
}
