package com.hedge.prototype.valuation.domain.irs;

import com.hedge.prototype.valuation.domain.common.FairValueLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

/**
 * 이자율스왑(IRS) 공정가치 평가 결과 엔티티 — Append-Only.
 *
 * <p>평가기준일 단위로 IRS 공정가치를 산출·저장합니다.
 * 이력은 영구 보존되며 수정·삭제를 허용하지 않습니다.
 *
 * <p><b>Level 2 분류 근거</b>: IRS 평가에 사용되는 금리 커브(할인율, 변동금리 등)가
 * 시장에서 관측가능하므로 K-IFRS 1113호 Level 2로 고정 분류합니다.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가손익 P&L 인식)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — IRS 유효부분 OCI 처리)
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "irs_valuations")
public class IrsValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "valuation_id")
    private Long valuationId;

    /** 평가 대상 IRS 계약 ID */
    @Column(name = "contract_id", nullable = false, length = 50)
    private String contractId;

    /** 평가기준일 — K-IFRS 1113호 9항 측정일 */
    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    /**
     * 고정 다리(Fixed Leg) 현재가치 (KRW).
     *
     * <p>공식: fixedRate × notional × Σ df_i
     * 여기서 df_i = 1/(1+r)^(t_i/365)
     *
     * @see K-IFRS 1113호 72~90항 (관측가능한 금리 커브 사용)
     */
    @Column(name = "fixed_leg_pv", nullable = false, precision = 20, scale = 2)
    private BigDecimal fixedLegPv;

    /**
     * 변동 다리(Floating Leg) 현재가치 (KRW).
     *
     * <p>공식: currentFloatingRate × notional × df_1기간
     *
     * @see K-IFRS 1113호 72~90항 (시장관측 변동금리 사용)
     */
    @Column(name = "floating_leg_pv", nullable = false, precision = 20, scale = 2)
    private BigDecimal floatingLegPv;

    /**
     * IRS 공정가치 (KRW).
     * Pay Fixed: 변동다리PV - 고정다리PV
     * Receive Fixed: 고정다리PV - 변동다리PV
     *
     * @see K-IFRS 1113호 9항 (측정일 기준 공정가치)
     */
    @Column(name = "fair_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal fairValue;

    /**
     * 전기 대비 공정가치 변동액 (KRW).
     * 최초 평가 시 fairValue 전체가 변동액.
     *
     * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — 변동액 P&L 인식)
     */
    @Column(name = "fair_value_change", nullable = false, precision = 20, scale = 2)
    private BigDecimal fairValueChange;

    /**
     * 평가에 사용된 할인율 (소수 표현 — 예: 0.035 = 3.5%).
     *
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수 — 무위험이자율)
     */
    @Column(name = "discount_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal discountRate;

    /** 잔존일수 (평가기준일 ~ 만기일) */
    @Column(name = "remaining_term_days", nullable = false)
    private int remainingTermDays;

    /**
     * K-IFRS 1113호 공정가치 수준.
     * IRS는 관측가능한 금리 커브 사용 → LEVEL_2 고정.
     *
     * @see K-IFRS 1113호 81항 (Level 2 — 관측가능한 투입변수: 금리 커브)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "fair_value_level", nullable = false, length = 10)
    private FairValueLevel fairValueLevel;

    /** 평가 생성 일시 (Append-Only 이력 관리) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * IRS 공정가치 평가 결과 생성 — Append-Only INSERT.
     *
     * <p>IrsPricing 계산이 완료된 결과값을 받아 평가 이력으로 저장합니다.
     * 공정가치 수준은 IRS의 특성상 항상 LEVEL_2로 고정합니다.
     *
     * @param contractId       IRS 계약 ID
     * @param valuationDate    평가기준일
     * @param fixedLegPv       고정 다리 현재가치
     * @param floatingLegPv    변동 다리 현재가치
     * @param fairValue        IRS 공정가치
     * @param fairValueChange  전기 대비 공정가치 변동액
     * @param discountRate     사용된 할인율
     * @param remainingTermDays 잔존일수
     * @return 신규 IRS 평가 결과 엔티티
     * @see K-IFRS 1113호 81항 (Level 2 고정 분류)
     * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가손익)
     */
    public static IrsValuation of(
            String contractId,
            LocalDate valuationDate,
            BigDecimal fixedLegPv,
            BigDecimal floatingLegPv,
            BigDecimal fairValue,
            BigDecimal fairValueChange,
            BigDecimal discountRate,
            int remainingTermDays) {

        requireNonNull(contractId, "계약 ID는 필수입니다.");
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        requireNonNull(fixedLegPv, "고정 다리 현재가치는 필수입니다.");
        requireNonNull(floatingLegPv, "변동 다리 현재가치는 필수입니다.");
        requireNonNull(fairValue, "공정가치는 필수입니다.");
        requireNonNull(fairValueChange, "공정가치 변동액은 필수입니다.");
        requireNonNull(discountRate, "할인율은 필수입니다.");

        IrsValuation valuation = new IrsValuation();
        valuation.contractId = contractId;
        valuation.valuationDate = valuationDate;
        valuation.fixedLegPv = fixedLegPv;
        valuation.floatingLegPv = floatingLegPv;
        valuation.fairValue = fairValue;
        valuation.fairValueChange = fairValueChange;
        valuation.discountRate = discountRate;
        valuation.remainingTermDays = remainingTermDays;
        valuation.fairValueLevel = FairValueLevel.LEVEL_2; // IRS = Level 2 고정
        valuation.createdAt = LocalDateTime.now();

        return valuation;
    }
}
