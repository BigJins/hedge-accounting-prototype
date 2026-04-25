package com.hedge.prototype.hedge.domain.model;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * 위험회피대상항목(Hedged Item) 엔티티.
 *
 * <p>K-IFRS 1109호 6.5.3(공정가치 헤지) 또는 6.5.4(현금흐름 헤지)에 따라
 * 헤지 대상으로 지정된 금융자산 또는 부채를 나타냅니다.
 *
 * <p><b>생성 방법</b>: {@link #of} 팩토리 메서드만 사용.
 * Builder, public 생성자 사용 금지.
 *
 * @see K-IFRS 1109호 6.5.3 (공정가치 헤지 대상 항목 적격성)
 * @see K-IFRS 1109호 6.5.4 (현금흐름 헤지 대상 항목 적격성)
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "hedged_items")
public class HedgedItem extends BaseAuditEntity {

    @Id
    @Column(name = "hedged_item_id", length = 50)
    private String hedgedItemId;

    /**
     * 헤지대상 항목 유형.
     *
     * @see K-IFRS 1109호 6.3.1 (위험회피대상항목 적격성)
     * @see K-IFRS 1109호 6.5.3~6.5.4 (대상항목 적격성)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private HedgedItemType itemType;

    /** 통화 (예: USD, EUR, JPY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /**
     * 명목금액 (원화 기준) — BigDecimal 필수.
     *
     * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 계산 기준)
     */
    @Column(name = "notional_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal notionalAmount;

    /**
     * 원화 환산 명목금액 — 지정일 환율 기준.
     *
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정)
     */
    @Column(name = "notional_amount_krw", precision = 20, scale = 2)
    private BigDecimal notionalAmountKrw;

    /** 만기일 */
    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /** 거래상대방명 (nullable) */
    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    /**
     * 거래상대방(헤지대상 발행자) 신용등급.
     *
     * <p>K-IFRS 1109호 B6.4.7에 따라 투자등급(BBB 이상) 여부를 판정합니다.
     *
     * @see K-IFRS 1109호 B6.4.7 (신용위험의 효과가 가치 변동보다 지배적이지 않아야 함)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "counterparty_credit_rating", nullable = false, length = 10)
    private CreditRating counterpartyCreditRating;

    /**
     * 금리 유형 (FIXED / FLOATING, nullable).
     * 이자율 위험 헤지 대상인 경우에만 적용.
     */
    @Column(name = "interest_rate_type", length = 20)
    private String interestRateType;

    /**
     * 금리 (nullable) — BigDecimal 필수.
     * 이자율 위험 헤지 대상인 경우에만 적용.
     */
    @Column(name = "interest_rate", precision = 8, scale = 6)
    private BigDecimal interestRate;

    /** 항목 설명 — K-IFRS 6.4.1(2) 문서화 요건 충족 */
    @Column(name = "description", length = 500)
    private String description;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 위험회피대상항목 생성.
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계 지정 시 위험회피대상항목을
     * 공식적으로 식별하고 문서화해야 합니다.
     *
     * @param hedgedItemId             항목 식별자 (예: HI-2026-001)
     * @param itemType                 항목 유형 (HedgedItemType enum)
     * @param currency                 통화 (예: USD)
     * @param notionalAmount           명목금액
     * @param notionalAmountKrw        원화 환산 명목금액 (nullable)
     * @param maturityDate             만기일
     * @param counterpartyName         거래상대방명 (nullable)
     * @param counterpartyCreditRating 거래상대방 신용등급
     * @param interestRateType         금리 유형 (nullable)
     * @param interestRate             금리 (nullable)
     * @param description              항목 설명
     * @return 위험회피대상항목 엔티티
     * @see K-IFRS 1109호 6.4.1(2) (위험회피관계 공식 지정·문서화)
     * @see K-IFRS 1109호 6.5.3 (공정가치 헤지 대상 항목 적격성)
     */
    public static HedgedItem of(
            String hedgedItemId,
            HedgedItemType itemType,
            String currency,
            BigDecimal notionalAmount,
            BigDecimal notionalAmountKrw,
            LocalDate maturityDate,
            String counterpartyName,
            CreditRating counterpartyCreditRating,
            String interestRateType,
            BigDecimal interestRate,
            String description) {

        requireNonNull(hedgedItemId, "항목 식별자는 필수입니다.");
        requireNonNull(itemType, "항목 유형은 필수입니다.");
        requireNonNull(currency, "통화는 필수입니다.");
        requireNonNull(notionalAmount, "명목금액은 필수입니다.");
        requireNonNull(maturityDate, "만기일은 필수입니다.");
        requireNonNull(counterpartyCreditRating, "거래상대방 신용등급은 필수입니다.");

        if (notionalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.hedge.prototype.common.exception.BusinessException(
                    "HD_010", "명목금액은 0보다 커야 합니다.");
        }

        HedgedItem item = new HedgedItem();
        item.hedgedItemId = hedgedItemId;
        item.itemType = itemType;
        item.currency = currency;
        item.notionalAmount = notionalAmount;
        item.notionalAmountKrw = notionalAmountKrw;
        item.maturityDate = maturityDate;
        item.counterpartyName = counterpartyName;
        item.counterpartyCreditRating = counterpartyCreditRating;
        item.interestRateType = interestRateType;
        item.interestRate = interestRate;
        item.description = description;

        log.info("위험회피대상항목 생성: hedgedItemId={}, itemType={}, currency={}",
                hedgedItemId, itemType, currency);
        return item;
    }
}
