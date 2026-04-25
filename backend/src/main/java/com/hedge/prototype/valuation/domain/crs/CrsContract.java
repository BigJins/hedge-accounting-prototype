package com.hedge.prototype.valuation.domain.crs;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.valuation.domain.common.ContractStatus;
import com.hedge.prototype.valuation.domain.common.DayCountConvention;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static java.util.Objects.requireNonNull;

/**
 * 통화스왑(CRS, Cross Currency Swap) 계약 엔티티.
 *
 * <p>서로 다른 통화의 원금과 이자를 교환하는 파생상품 계약을 나타냅니다.
 * 주로 외화차입금의 환율·금리 위험을 동시에 헤지하는 현금흐름 위험회피(CFH)에 활용됩니다.
 *
 * <p><b>생성 방법</b>: {@link #of} 팩토리 메서드만 사용.
 * Builder, public 생성자 사용 금지.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑의 헤지비율 산정 지침)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "crs_contracts")
public class CrsContract extends BaseAuditEntity {

    @Id
    @Column(name = "contract_id", length = 50)
    private String contractId;

    /**
     * 원화(KRW) 원금 — BigDecimal 필수.
     *
     * @see K-IFRS 1109호 B6.4.9 (헤지비율 산정 — 원금 기준)
     */
    @Column(name = "notional_amount_krw", nullable = false, precision = 20, scale = 2)
    private BigDecimal notionalAmountKrw;

    /**
     * 외화 원금 — BigDecimal 필수.
     *
     * @see K-IFRS 1109호 B6.4.9 (헤지비율 산정)
     */
    @Column(name = "notional_amount_foreign", nullable = false, precision = 20, scale = 6)
    private BigDecimal notionalAmountForeign;

    /**
     * 외화 통화 코드 (예: "USD", "EUR", "JPY").
     */
    @Column(name = "foreign_currency", nullable = false, length = 10)
    private String foreignCurrency;

    /**
     * 계약 환율 (원금 교환 기준 환율, KRW/외화).
     *
     * @see K-IFRS 1109호 B6.4.9 (통화스왑 헤지비율)
     */
    @Column(name = "contract_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal contractRate;

    /**
     * 원화 고정금리 (nullable — 변동금리 원화 다리인 경우 null).
     * 소수 표현 (예: 0.035 = 3.5%)
     */
    @Column(name = "krw_fixed_rate", precision = 8, scale = 6)
    private BigDecimal krwFixedRate;

    /**
     * 원화 변동금리 기준지수 (nullable — 고정금리 원화 다리인 경우 null).
     * 예: "CD_91D"
     */
    @Column(name = "krw_floating_index", length = 30)
    private String krwFloatingIndex;

    /**
     * 외화 고정금리 (nullable — 변동금리 외화 다리인 경우 null).
     * 소수 표현 (예: 0.05 = 5.0%)
     */
    @Column(name = "foreign_fixed_rate", precision = 8, scale = 6)
    private BigDecimal foreignFixedRate;

    /**
     * 외화 변동금리 기준지수 (nullable — 고정금리 외화 다리인 경우 null).
     * 예: "SOFR"
     */
    @Column(name = "foreign_floating_index", length = 30)
    private String foreignFloatingIndex;

    /** 계약일 */
    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    /** 만기일 (계약일 이후) */
    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /**
     * 이자 결제 주기.
     * "QUARTERLY" (분기), "SEMI_ANNUAL" (반기), "ANNUAL" (연간)
     */
    @Column(name = "settlement_frequency", nullable = false, length = 20)
    private String settlementFrequency;

    /**
     * 일수 계산 관행.
     *
     * @see K-IFRS 1113호 81항 (Level 2 — 시장 표준 day count)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_count_convention", nullable = false, length = 10)
    private DayCountConvention dayCountConvention;

    /** 거래상대방명 */
    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    /**
     * 거래상대방 신용등급.
     *
     * @see K-IFRS 1109호 B6.4.7 (신용위험 지배 판단)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "counterparty_credit_rating", length = 10)
    private CreditRating counterpartyCreditRating;

    /**
     * 계약 상태.
     *
     * @see K-IFRS 1109호 6.5.14 (현금흐름 위험회피 중단)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    /**
     * 연계된 위험회피관계 ID (nullable).
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별·문서화)
     */
    @Column(name = "hedge_relationship_id", length = 50)
    private String hedgeRelationshipId;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 통화스왑 계약 등록.
     *
     * <p>K-IFRS 1109호 B6.4.9: 통화스왑을 위험회피수단으로 지정 시
     * 원금 교환 환율과 이자 조건을 공식 식별·문서화합니다.
     *
     * @param contractId               계약번호
     * @param notionalAmountKrw        원화 원금 (KRW, 양수)
     * @param notionalAmountForeign    외화 원금 (양수)
     * @param foreignCurrency          외화 통화 코드
     * @param contractRate             계약 환율 (KRW/외화)
     * @param krwFixedRate             원화 고정금리 (nullable)
     * @param krwFloatingIndex         원화 변동금리 기준지수 (nullable)
     * @param foreignFixedRate         외화 고정금리 (nullable)
     * @param foreignFloatingIndex     외화 변동금리 기준지수 (nullable)
     * @param contractDate             계약일
     * @param maturityDate             만기일 (계약일 이후)
     * @param settlementFrequency      결제 주기
     * @param dayCountConvention       일수 계산 관행
     * @param counterpartyName         거래상대방명 (nullable)
     * @param counterpartyCreditRating 거래상대방 신용등급 (nullable)
     * @return 신규 CRS 계약 엔티티
     * @see K-IFRS 1109호 B6.4.9 (통화스왑의 헤지비율 산정)
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 OCI 처리)
     */
    public static CrsContract of(
            String contractId,
            BigDecimal notionalAmountKrw,
            BigDecimal notionalAmountForeign,
            String foreignCurrency,
            BigDecimal contractRate,
            BigDecimal krwFixedRate,
            String krwFloatingIndex,
            BigDecimal foreignFixedRate,
            String foreignFloatingIndex,
            LocalDate contractDate,
            LocalDate maturityDate,
            String settlementFrequency,
            DayCountConvention dayCountConvention,
            String counterpartyName,
            CreditRating counterpartyCreditRating) {

        requireNonNull(contractId, "계약번호는 필수입니다.");
        requireNonNull(notionalAmountKrw, "원화 원금은 필수입니다.");
        requireNonNull(notionalAmountForeign, "외화 원금은 필수입니다.");
        requireNonNull(foreignCurrency, "외화 통화 코드는 필수입니다.");
        requireNonNull(contractRate, "계약 환율은 필수입니다.");
        requireNonNull(contractDate, "계약일은 필수입니다.");
        requireNonNull(maturityDate, "만기일은 필수입니다.");
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        requireNonNull(dayCountConvention, "일수 계산 관행은 필수입니다.");

        if (notionalAmountKrw.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("CRS_001", "원화 원금은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (notionalAmountForeign.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("CRS_001", "외화 원금은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (contractRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("CRS_002", "계약 환율은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!maturityDate.isAfter(contractDate)) {
            throw new BusinessException("CRS_003", "만기일은 계약일 이후여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        CrsContract contract = new CrsContract();
        contract.contractId = contractId;
        contract.notionalAmountKrw = notionalAmountKrw;
        contract.notionalAmountForeign = notionalAmountForeign;
        contract.foreignCurrency = foreignCurrency;
        contract.contractRate = contractRate;
        contract.krwFixedRate = krwFixedRate;
        contract.krwFloatingIndex = krwFloatingIndex;
        contract.foreignFixedRate = foreignFixedRate;
        contract.foreignFloatingIndex = foreignFloatingIndex;
        contract.contractDate = contractDate;
        contract.maturityDate = maturityDate;
        contract.settlementFrequency = settlementFrequency;
        contract.dayCountConvention = dayCountConvention;
        contract.counterpartyName = counterpartyName;
        contract.counterpartyCreditRating = counterpartyCreditRating;
        contract.status = ContractStatus.ACTIVE;

        log.info("CRS 계약 등록: contractId={}, foreignCurrency={}, settlementFrequency={}",
                contractId, foreignCurrency, settlementFrequency);
        return contract;
    }

    // -----------------------------------------------------------------------
    // 비즈니스 메서드
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 연계.
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별·문서화)
     */
    public void linkHedgeRelationship(String hedgeRelationshipId) {
        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        this.hedgeRelationshipId = hedgeRelationshipId;
        log.info("CRS 위험회피관계 연계: contractId={}, hedgeRelationshipId={}", contractId, hedgeRelationshipId);
    }

    /**
     * 헤지 지정 가능 여부 검증.
     *
     * @throws BusinessException CRS_006 — ACTIVE 상태가 아닌 경우 또는 만기 초과
     * @see K-IFRS 1109호 6.4.1(2) (헤지 지정 요건)
     */
    public void validateForHedgeDesignation(LocalDate designationDate) {
        requireNonNull(designationDate, "헤지 지정일은 필수입니다.");
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("CRS_006",
                    String.format("ACTIVE 상태의 계약만 위험회피수단으로 지정할 수 있습니다. contractId=%s, status=%s",
                            contractId, status), HttpStatus.BAD_REQUEST);
        }
        if (!this.maturityDate.isAfter(designationDate)) {
            throw new BusinessException("CRS_006",
                    String.format("만기 도래 계약은 헤지 지정이 불가합니다. 계약만기=%s, 지정일=%s",
                            maturityDate, designationDate));
        }
    }

    /**
     * 평가기준일 유효성 검증.
     *
     * @throws BusinessException CRS_004 — 평가기준일이 만기일 이후인 경우
     * @see K-IFRS 1109호 6.5.14 (현금흐름 위험회피 중단)
     */
    public void validateValuationDate(LocalDate valuationDate) {
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        if (!valuationDate.isBefore(maturityDate)) {
            throw new BusinessException("CRS_004",
                    String.format("평가기준일(%s)이 만기일(%s) 이후입니다.", valuationDate, maturityDate));
        }
    }

    /**
     * 잔존일수 계산.
     *
     * @param valuationDate 평가기준일
     * @return 잔존일수 (양수)
     */
    public int calculateRemainingDays(LocalDate valuationDate) {
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        return (int) ChronoUnit.DAYS.between(valuationDate, maturityDate);
    }

    /**
     * 계약 정보 갱신 (PoC 재제출 허용).
     */
    public void update(
            BigDecimal notionalAmountKrw,
            BigDecimal notionalAmountForeign,
            BigDecimal contractRate,
            BigDecimal krwFixedRate,
            String krwFloatingIndex,
            BigDecimal foreignFixedRate,
            String foreignFloatingIndex,
            LocalDate maturityDate,
            CreditRating counterpartyCreditRating) {
        this.notionalAmountKrw = requireNonNull(notionalAmountKrw, "원화 원금은 필수입니다.");
        this.notionalAmountForeign = requireNonNull(notionalAmountForeign, "외화 원금은 필수입니다.");
        this.contractRate = requireNonNull(contractRate, "계약 환율은 필수입니다.");
        this.krwFixedRate = krwFixedRate;
        this.krwFloatingIndex = krwFloatingIndex;
        this.foreignFixedRate = foreignFixedRate;
        this.foreignFloatingIndex = foreignFloatingIndex;
        this.maturityDate = requireNonNull(maturityDate, "만기일은 필수입니다.");
        this.counterpartyCreditRating = counterpartyCreditRating;
        log.info("CRS 계약 정보 갱신: contractId={}", contractId);
    }

    /**
     * 계약 조기 중단.
     *
     * @see K-IFRS 1109호 6.5.14 (현금흐름 위험회피 중단)
     */
    public void terminate() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("CRS_004", "활성 상태의 계약만 중단할 수 있습니다.");
        }
        this.status = ContractStatus.TERMINATED;
        log.info("CRS 계약 중단: contractId={}", contractId);
    }
}
