package com.hedge.prototype.journal.adapter.web.dto;

import com.hedge.prototype.journal.domain.JournalEntry;
import com.hedge.prototype.journal.domain.JournalEntryType;
import com.hedge.prototype.journal.domain.ReclassificationReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 헤지회계 분개 응답 DTO.
 *
 * <p>JournalEntry 엔티티의 모든 필드와 함께
 * 계정과목 한글명 및 포맷된 금액을 포함합니다.
 *
 * @see K-IFRS 1107호 (헤지회계 공시 — 분개 정보)
 */
public record JournalEntryResponse(

        /** 분개 ID */
        Long journalEntryId,

        /** 위험회피관계 ID */
        String hedgeRelationshipId,

        /** 분개 기준일 */
        LocalDate entryDate,

        /** 분개 유형 */
        JournalEntryType entryType,

        /** 차변 계정 코드 */
        String debitAccount,

        /** 차변 계정 한글명 */
        String debitAccountName,

        /** 차변 금액 (포맷: #,##0.00) */
        String formattedDebitAmount,

        /** 대변 계정 코드 */
        String creditAccount,

        /** 대변 계정 한글명 */
        String creditAccountName,

        /** 대변 금액 (포맷: #,##0.00) */
        String formattedCreditAmount,

        /** 분개 금액 (원본 BigDecimal) */
        BigDecimal amount,

        /** 적요 */
        String description,

        /** K-IFRS 근거 조항 */
        String ifrsReference,

        /** OCI 재분류 사유 (재분류 분개에만 값 있음) */
        ReclassificationReason reclassificationReason,

        /** 최초 OCI 인식일 (재분류 분개에만 값 있음) */
        LocalDate originalOciEntryDate,

        /** 이 분개를 역분개한 분개 ID */
        Long cancelledByEntryId,

        /** 이 역분개가 취소하는 원 분개 ID */
        Long cancelsEntryId,

        /** 생성일시 */
        LocalDateTime createdAt,

        /** 수정일시 */
        LocalDateTime updatedAt
) {

    /**
     * JournalEntry 엔티티로부터 응답 DTO 생성.
     *
     * @param entity JournalEntry 엔티티
     * @return JournalEntryResponse
     */
    public static JournalEntryResponse fromEntity(JournalEntry entity) {
        String formatted = formatAmount(entity.getAmount());

        return new JournalEntryResponse(
                entity.getJournalEntryId(),
                entity.getHedgeRelationshipId(),
                entity.getEntryDate(),
                entity.getEntryType(),
                entity.getDebitAccount().name(),
                entity.getDebitAccount().getKoreanName(),
                formatted,
                entity.getCreditAccount().name(),
                entity.getCreditAccount().getKoreanName(),
                formatted,
                entity.getAmount(),
                entity.getDescription(),
                entity.getIfrsReference(),
                entity.getReclassificationReason(),
                entity.getOriginalOciEntryDate(),
                entity.getCancelledByEntryId(),
                entity.getCancelsEntryId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * BigDecimal 금액을 #,##0.00 포맷 문자열로 변환.
     *
     * @param amount 금액 (양수, BigDecimal)
     * @return 포맷된 금액 문자열 (예: "1,234,567.89")
     */
    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        // java.text.NumberFormat 사용 (double 변환 없이 BigDecimal 직접 처리)
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        return df.format(amount);
    }
}
