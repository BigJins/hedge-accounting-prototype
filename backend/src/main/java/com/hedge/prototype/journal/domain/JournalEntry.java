package com.hedge.prototype.journal.domain;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * 헤지회계 분개 엔티티.
 *
 * <p>K-IFRS 1109호에 따라 공정가치 위험회피 및 현금흐름 위험회피에서
 * 발생하는 모든 분개를 기록합니다. Append-Only 정책으로 관리되며,
 * 수정이 필요한 경우 역분개({@link JournalEntryType#REVERSING_ENTRY}) 패턴을 사용합니다.
 *
 * <p><b>무결성 규칙</b>:
 * <ul>
 *   <li>차변계정 ≠ 대변계정</li>
 *   <li>금액(amount)은 항상 양수 (절대값 저장)</li>
 *   <li>단일 분개 내 차변 = 대변 (이중분개 원칙)</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 분개)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 분개)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 분개)
 * @see K-IFRS 1107호 (금융상품 공시 — 분개 감사 추적)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "journal_entries", indexes = {
        @Index(name = "idx_journal_hedge_relationship", columnList = "hedge_relationship_id"),
        @Index(name = "idx_journal_entry_date", columnList = "entry_date")
})
public class JournalEntry extends BaseAuditEntity {

    // -----------------------------------------------------------------------
    // 식별자
    // -----------------------------------------------------------------------

    /**
     * 분개 ID (자동 생성).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    // -----------------------------------------------------------------------
    // 연관 관계
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 ID (FK → HedgeRelationship.hedgeRelationshipId).
     *
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정)
     */
    @Column(name = "hedge_relationship_id", nullable = false, length = 50)
    private String hedgeRelationshipId;

    // -----------------------------------------------------------------------
    // 분개 기본 정보
    // -----------------------------------------------------------------------

    /**
     * 분개 기준일.
     * 보고기간 말 또는 거래 발생일.
     *
     * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 평가)
     */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /**
     * 분개 유형.
     *
     * @see JournalEntryType
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private JournalEntryType entryType;

    // -----------------------------------------------------------------------
    // 계정과목 및 금액
    // -----------------------------------------------------------------------

    /**
     * 차변 계정과목.
     *
     * @see AccountCode
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "debit_account", nullable = false, length = 30)
    private AccountCode debitAccount;

    /**
     * 대변 계정과목.
     *
     * @see AccountCode
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "credit_account", nullable = false, length = 30)
    private AccountCode creditAccount;

    /**
     * 분개 금액 (항상 양수, 절대값 저장).
     * 차변/대변 방향은 {@link #debitAccount}/{@link #creditAccount}로 결정됩니다.
     *
     * @see K-IFRS 1109호 (이중분개 원칙 — 차변 = 대변 = amount)
     */
    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    // -----------------------------------------------------------------------
    // 설명 및 근거
    // -----------------------------------------------------------------------

    /**
     * 적요 — 분개 내용 설명.
     */
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /**
     * K-IFRS 근거 조항.
     * 예: "K-IFRS 1109호 6.5.8(가)"
     *
     * @see K-IFRS 1107호 (헤지회계 공시 — 회계 정책 근거)
     */
    @Column(name = "ifrs_reference", nullable = false, length = 100)
    private String ifrsReference;

    // -----------------------------------------------------------------------
    // OCI 재분류 관련 (선택적)
    // -----------------------------------------------------------------------

    /**
     * OCI 재분류 사유.
     * {@link JournalEntryType#OCI_RECLASSIFICATION} 분개에만 사용.
     *
     * @see K-IFRS 1109호 6.5.11(다) (재분류 사유)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reclassification_reason", length = 40)
    private ReclassificationReason reclassificationReason;

    /**
     * 최초 OCI 인식일.
     * 재분류 분개에서 원 OCI 인식 시점 참조용.
     *
     * @see K-IFRS 1109호 6.5.11(다) (최초 인식일 추적)
     */
    @Column(name = "original_oci_entry_date")
    private LocalDate originalOciEntryDate;

    // -----------------------------------------------------------------------
    // 역분개 연결
    // -----------------------------------------------------------------------

    /**
     * 이 분개를 역분개한 분개의 ID.
     * 이 분개가 역분개되어 취소된 경우 설정됩니다.
     */
    @Column(name = "cancelled_by_entry_id")
    private Long cancelledByEntryId;

    /**
     * 이 분개가 역분개하는 원 분개의 ID.
     * 이 분개가 역분개인 경우({@link JournalEntryType#REVERSING_ENTRY}) 설정됩니다.
     */
    @Column(name = "cancels_entry_id")
    private Long cancelsEntryId;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 일반 분개 생성 (역분개/재분류 아님).
     *
     * <p>Append-Only 정책에 따라 새 레코드를 INSERT합니다.
     * 차변계정 ≠ 대변계정, amount > 0 검증은 서비스 레이어에서 수행합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param entryDate           분개 기준일
     * @param entryType           분개 유형
     * @param debitAccount        차변 계정과목
     * @param creditAccount       대변 계정과목
     * @param amount              분개 금액 (양수)
     * @param description         적요
     * @param ifrsReference       K-IFRS 근거 조항
     * @return 분개 엔티티
     * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 분개)
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 분개)
     */
    public static JournalEntry of(
            String hedgeRelationshipId,
            LocalDate entryDate,
            JournalEntryType entryType,
            AccountCode debitAccount,
            AccountCode creditAccount,
            BigDecimal amount,
            String description,
            String ifrsReference) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(entryDate, "분개 기준일은 필수입니다.");
        requireNonNull(entryType, "분개 유형은 필수입니다.");
        requireNonNull(debitAccount, "차변 계정과목은 필수입니다.");
        requireNonNull(creditAccount, "대변 계정과목은 필수입니다.");
        requireNonNull(amount, "분개 금액은 필수입니다.");
        requireNonNull(description, "적요는 필수입니다.");
        requireNonNull(ifrsReference, "K-IFRS 근거 조항은 필수입니다.");

        JournalEntry entry = new JournalEntry();
        entry.hedgeRelationshipId = hedgeRelationshipId;
        entry.entryDate = entryDate;
        entry.entryType = entryType;
        entry.debitAccount = debitAccount;
        entry.creditAccount = creditAccount;
        entry.amount = amount;
        entry.description = description;
        entry.ifrsReference = ifrsReference;

        log.info("분개 생성: hedgeRelationshipId={}, entryDate={}, entryType={}, debit={}, credit={}",
                hedgeRelationshipId, entryDate, entryType, debitAccount, creditAccount);

        return entry;
    }

    /**
     * OCI 재분류 분개 생성.
     *
     * @param hedgeRelationshipId   위험회피관계 ID
     * @param entryDate             재분류 기준일
     * @param debitAccount          차변 계정과목
     * @param creditAccount         대변 계정과목
     * @param amount                재분류 금액 (양수)
     * @param description           적요
     * @param ifrsReference         K-IFRS 근거 조항
     * @param reclassificationReason 재분류 사유
     * @param originalOciEntryDate  최초 OCI 인식일
     * @return OCI 재분류 분개 엔티티
     * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 조정)
     */
    public static JournalEntry ofReclassification(
            String hedgeRelationshipId,
            LocalDate entryDate,
            AccountCode debitAccount,
            AccountCode creditAccount,
            BigDecimal amount,
            String description,
            String ifrsReference,
            ReclassificationReason reclassificationReason,
            LocalDate originalOciEntryDate) {

        JournalEntry entry = of(hedgeRelationshipId, entryDate,
                JournalEntryType.OCI_RECLASSIFICATION,
                debitAccount, creditAccount, amount, description, ifrsReference);
        entry.reclassificationReason = requireNonNull(reclassificationReason, "재분류 사유는 필수입니다.");
        entry.originalOciEntryDate = originalOciEntryDate;

        return entry;
    }

    /**
     * 역분개 생성.
     * 원 분개의 차대변을 반전하여 취소합니다.
     *
     * @param original    취소할 원 분개
     * @param reverseDate 역분개 기준일
     * @return 역분개 엔티티
     */
    public static JournalEntry ofReversal(JournalEntry original, LocalDate reverseDate) {
        requireNonNull(original, "원 분개는 필수입니다.");
        requireNonNull(reverseDate, "역분개 기준일은 필수입니다.");

        JournalEntry entry = new JournalEntry();
        entry.hedgeRelationshipId = original.hedgeRelationshipId;
        entry.entryDate = reverseDate;
        entry.entryType = JournalEntryType.REVERSING_ENTRY;
        // 차대변 반전
        entry.debitAccount = original.creditAccount;
        entry.creditAccount = original.debitAccount;
        entry.amount = original.amount;
        entry.description = "역분개: " + original.description;
        entry.ifrsReference = original.ifrsReference;
        entry.cancelsEntryId = original.journalEntryId;

        log.info("역분개 생성: originalEntryId={}, reverseDate={}", original.journalEntryId, reverseDate);

        return entry;
    }

    /**
     * 이 분개가 역분개됨을 표시합니다.
     * 역분개 엔티티 저장 후 원 분개에 cancelledByEntryId를 설정합니다.
     *
     * @param reversingEntryId 역분개 분개 ID
     */
    public void markAsCancelled(Long reversingEntryId) {
        this.cancelledByEntryId = requireNonNull(reversingEntryId, "역분개 ID는 필수입니다.");
    }
}
