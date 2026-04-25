package com.hedge.prototype.valuation.domain.fxforward;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import com.hedge.prototype.valuation.domain.common.FairValueLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import static java.util.Objects.requireNonNull;

/**
 * 통화선도 공정가치 평가 결과 엔티티.
 *
 * <p>평가기준일 단위로 IRP 기반 공정가치를 산출·저장합니다.
 * 이력은 영구 보존되며 수정·삭제를 허용하지 않습니다.
 *
 * <p><b>Level 2 분류 근거</b>: 평가 투입변수(환율, 이자율)가
 * 시장에서 관측가능하므로 K-IFRS 1113호 수준 2로 고정 분류합니다.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리 — 평가손익 P&L 인식)
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수)
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 주기 — 매 보고기간 말)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fx_forward_valuations")
public class FxForwardValuation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "valuation_id")
    private Long valuationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private FxForwardContract contract;

    /** 평가기준일 — K-IFRS 1113호 9항 측정일 */
    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    /** 평가기준일 현물환율 (KRW/USD) */
    @Column(name = "spot_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal spotRate;

    /** 원화 무위험이자율 (국고채 기준) */
    @Column(name = "krw_interest_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal krwInterestRate;

    /** 달러 무위험이자율 (SOFR 기준) */
    @Column(name = "usd_interest_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal usdInterestRate;

    /** 잔존일수 (평가기준일 ~ 만기일) */
    @Column(name = "remaining_days", nullable = false)
    private Integer remainingDays;

    /**
     * IRP 산출 현재 선물환율 (KRW/USD).
     * 공식: S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)
     */
    @Column(name = "current_forward_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal currentForwardRate;

    /** 공정가치 (KRW) — (현재선물환율 - 계약선물환율) × 명목원금 × 현가계수 */
    @Column(name = "fair_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal fairValue;

    /** 전기 공정가치 (KRW) — 최초 평가 시 0 */
    @Column(name = "previous_fair_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal previousFairValue;

    /** 공정가치 변동액 (KRW) — 당기 평가손익, P&L 인식 근거 */
    @Column(name = "fair_value_change", nullable = false, precision = 20, scale = 2)
    private BigDecimal fairValueChange;

    /**
     * K-IFRS 1113호 공정가치 수준.
     * 통화선도는 관측가능한 투입변수 사용 → LEVEL_2 고정.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "fair_value_level", nullable = false, length = 10)
    private FairValueLevel fairValueLevel;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 공정가치 평가 결과 생성.
     *
     * <p>IRP 계산이 완료된 결과값을 받아 평가 이력으로 저장합니다.
     * 공정가치 수준은 FX Forward의 특성상 항상 LEVEL_2로 고정합니다.
     *
     * @see K-IFRS 1113호 72~90항 (Level 2 분류)
     * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 — 변동액 P&L 인식)
     */
    public static FxForwardValuation of(
            FxForwardContract contract,
            LocalDate valuationDate,
            BigDecimal spotRate,
            BigDecimal krwInterestRate,
            BigDecimal usdInterestRate,
            Integer remainingDays,
            BigDecimal currentForwardRate,
            BigDecimal fairValue,
            BigDecimal previousFairValue) {

        requireNonNull(contract, "계약은 필수입니다.");
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        requireNonNull(spotRate, "현물환율은 필수입니다.");
        requireNonNull(krwInterestRate, "원화이자율은 필수입니다.");
        requireNonNull(usdInterestRate, "달러이자율은 필수입니다.");
        requireNonNull(remainingDays, "잔존일수는 필수입니다.");
        requireNonNull(currentForwardRate, "현재 선물환율은 필수입니다.");
        requireNonNull(fairValue, "공정가치는 필수입니다.");
        requireNonNull(previousFairValue, "전기 공정가치는 필수입니다.");

        FxForwardValuation valuation = new FxForwardValuation();
        valuation.contract = contract;
        valuation.valuationDate = valuationDate;
        valuation.spotRate = spotRate;
        valuation.krwInterestRate = krwInterestRate;
        valuation.usdInterestRate = usdInterestRate;
        valuation.remainingDays = remainingDays;
        valuation.currentForwardRate = currentForwardRate;
        valuation.fairValue = fairValue;
        valuation.previousFairValue = previousFairValue;
        valuation.fairValueChange = fairValue.subtract(previousFairValue);
        valuation.fairValueLevel = FairValueLevel.LEVEL_2; // FX Forward = Level 2 고정

        return valuation;
    }

    // -----------------------------------------------------------------------
    // 비즈니스 메서드
    // -----------------------------------------------------------------------

    /**
     * 시장 데이터 변경 시 공정가치 재계산 — PoC 시연용.
     *
     * <p>동일 평가기준일에 현물환율·이자율이 변경될 때 기존 레코드를 갱신합니다.
     * unique constraint(contract_id, valuation_date)를 유지하면서 재계산을 허용합니다.
     *
     * @see K-IFRS 1113호 9항 (측정일 기준 공정가치)
     */
    public void recalculate(
            BigDecimal spotRate,
            BigDecimal krwInterestRate,
            BigDecimal usdInterestRate,
            Integer remainingDays,
            BigDecimal currentForwardRate,
            BigDecimal fairValue,
            BigDecimal previousFairValue) {

        this.spotRate = requireNonNull(spotRate, "현물환율은 필수입니다.");
        this.krwInterestRate = requireNonNull(krwInterestRate, "원화이자율은 필수입니다.");
        this.usdInterestRate = requireNonNull(usdInterestRate, "달러이자율은 필수입니다.");
        this.remainingDays = requireNonNull(remainingDays, "잔존일수는 필수입니다.");
        this.currentForwardRate = requireNonNull(currentForwardRate, "현재 선물환율은 필수입니다.");
        this.fairValue = requireNonNull(fairValue, "공정가치는 필수입니다.");
        this.previousFairValue = requireNonNull(previousFairValue, "전기 공정가치는 필수입니다.");
        this.fairValueChange = fairValue.subtract(previousFairValue);
    }

}
