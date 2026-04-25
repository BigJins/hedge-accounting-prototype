package com.hedge.prototype.valuation.domain.crs;

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
 * 통화스왑(CRS) 공정가치 평가 결과 엔티티 — Append-Only.
 *
 * <p>평가기준일 단위로 CRS 공정가치를 산출·저장합니다.
 * 이력은 영구 보존되며 수정·삭제를 허용하지 않습니다.
 *
 * <p><b>Level 2 분류 근거</b>: CRS 평가에 사용되는 환율·금리 커브가
 * 시장에서 관측가능하므로 K-IFRS 1113호 Level 2로 고정 분류합니다.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑 헤지비율 산정)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "crs_valuations")
public class CrsValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "valuation_id")
    private Long valuationId;

    /** 평가 대상 CRS 계약 ID */
    @Column(name = "contract_id", nullable = false, length = 50)
    private String contractId;

    /** 평가기준일 — K-IFRS 1113호 9항 측정일 */
    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    /**
     * 평가기준일 환율 (KRW/외화) — 외화 다리 원화 환산 기준.
     *
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 시장환율)
     */
    @Column(name = "spot_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal spotRate;

    /**
     * 원화 다리(KRW Leg) 현재가치 (KRW).
     *
     * <p>공식: Σ(krwCoupon_i × df_i) + krwNotional × df_n
     *
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 원화 금리 커브)
     */
    @Column(name = "krw_leg_pv", nullable = false, precision = 20, scale = 2)
    private BigDecimal krwLegPv;

    /**
     * 외화 다리(Foreign Leg) 현재가치 원화 환산 (KRW).
     *
     * <p>공식: Σ(foreignCoupon_i × spotRate × df_i) + foreignNotional × spotRate × df_n
     *
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 외화 금리 커브 + 환율)
     */
    @Column(name = "foreign_leg_pv", nullable = false, precision = 20, scale = 2)
    private BigDecimal foreignLegPv;

    /**
     * CRS 공정가치 (KRW).
     * = 외화다리PV(원화환산) - 원화다리PV
     *
     * @see K-IFRS 1113호 9항 (측정일 기준 공정가치)
     */
    @Column(name = "fair_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal fairValue;

    /**
     * 전기 대비 공정가치 변동액 (KRW).
     *
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — 유효부분 OCI 인식)
     */
    @Column(name = "fair_value_change", nullable = false, precision = 20, scale = 2)
    private BigDecimal fairValueChange;

    /**
     * K-IFRS 1113호 공정가치 수준.
     * CRS는 관측가능한 투입변수(환율, 금리 커브) 사용 → LEVEL_2 고정.
     *
     * @see K-IFRS 1113호 81항 (Level 2 — 관측가능한 투입변수)
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
     * CRS 공정가치 평가 결과 생성 — Append-Only INSERT.
     *
     * <p>CrsPricing 계산이 완료된 결과값을 받아 평가 이력으로 저장합니다.
     * 공정가치 수준은 CRS의 특성상 항상 LEVEL_2로 고정합니다.
     *
     * @param contractId       CRS 계약 ID
     * @param valuationDate    평가기준일
     * @param spotRate         평가기준일 환율 (KRW/외화)
     * @param krwLegPv         원화 다리 현재가치
     * @param foreignLegPv     외화 다리 현재가치 (원화 환산)
     * @param fairValue        CRS 공정가치
     * @param fairValueChange  전기 대비 공정가치 변동액
     * @return 신규 CRS 평가 결과 엔티티
     * @see K-IFRS 1113호 81항 (Level 2 고정 분류)
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 OCI 처리)
     */
    public static CrsValuation of(
            String contractId,
            LocalDate valuationDate,
            BigDecimal spotRate,
            BigDecimal krwLegPv,
            BigDecimal foreignLegPv,
            BigDecimal fairValue,
            BigDecimal fairValueChange) {

        requireNonNull(contractId, "계약 ID는 필수입니다.");
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        requireNonNull(spotRate, "환율은 필수입니다.");
        requireNonNull(krwLegPv, "원화 다리 현재가치는 필수입니다.");
        requireNonNull(foreignLegPv, "외화 다리 현재가치는 필수입니다.");
        requireNonNull(fairValue, "공정가치는 필수입니다.");
        requireNonNull(fairValueChange, "공정가치 변동액은 필수입니다.");

        CrsValuation valuation = new CrsValuation();
        valuation.contractId = contractId;
        valuation.valuationDate = valuationDate;
        valuation.spotRate = spotRate;
        valuation.krwLegPv = krwLegPv;
        valuation.foreignLegPv = foreignLegPv;
        valuation.fairValue = fairValue;
        valuation.fairValueChange = fairValueChange;
        valuation.fairValueLevel = FairValueLevel.LEVEL_2; // CRS = Level 2 고정
        valuation.createdAt = LocalDateTime.now();

        return valuation;
    }
}
