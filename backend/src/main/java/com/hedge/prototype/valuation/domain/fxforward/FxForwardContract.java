package com.hedge.prototype.valuation.domain.fxforward;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.valuation.domain.common.ContractStatus;
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
 * 통화선도(FX Forward) 계약 엔티티.
 *
 * <p>USD/KRW 통화선도 계약을 나타냅니다.
 * 위험회피수단으로 지정되어 공정가치 위험회피회계를 적용합니다.
 *
 * <p><b>생성 방법</b>: {@link #designate} 팩토리 메서드만 사용.
 * Builder, public 생성자 사용 금지.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fx_forward_contracts")
public class FxForwardContract extends BaseAuditEntity {

    @Id
    @Column(name = "contract_id", length = 50)
    private String contractId;

    /** 명목원금 (USD) — BigDecimal 필수 */
    @Column(name = "notional_amount_usd", nullable = false, precision = 20, scale = 2)
    private BigDecimal notionalAmountUsd;

    /**
     * 계약 선물환율 (KRW/USD) — 체결일 확정 환율.
     * IRP 공식의 기준점이 됩니다.
     */
    @Column(name = "contract_forward_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal contractForwardRate;

    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /** 헤지 지정일 — K-IFRS 6.4.1(2) 공식 지정·문서화 시점 */
    @Column(name = "hedge_designation_date", nullable = false)
    private LocalDate hedgeDesignationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 30)
    private FxForwardPosition position = FxForwardPosition.SELL_USD_BUY_KRW;

    /**
     * 거래상대방(은행 등) 신용등급.
     *
     * <p>K-IFRS 1109호 B6.4.7에 따라 신용위험 지배 여부 판단에 사용됩니다.
     * 투자등급(A- 이상 권고) 확인.
     *
     * @see K-IFRS 1109호 B6.4.7~B6.4.8 (신용위험 지배 판단)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "counterparty_credit_rating", length = 10)
    private CreditRating counterpartyCreditRating;

    /**
     * 거래상대방명 (nullable).
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 문서화)
     */
    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    /**
     * 헤지 지정된 위험회피관계 ID (nullable — 미지정 계약도 존재 가능).
     *
     * <p>헤지 지정(HedgeRelationship.designate) 시 채워집니다.
     * 한 계약은 하나의 위험회피관계에만 지정 가능합니다 (중복 지정 금지).
     *
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
     */
    @Column(name = "hedge_relationship_id", length = 50)
    private String hedgeRelationshipId;

    public static FxForwardContract designate(
            String contractId,
            BigDecimal notionalAmountUsd,
            BigDecimal contractForwardRate,
            LocalDate contractDate,
            LocalDate maturityDate,
            LocalDate hedgeDesignationDate,
            String counterpartyName,
            CreditRating counterpartyCreditRating,
            FxForwardPosition position) {

        FxForwardContract contract = designate(
                contractId,
                notionalAmountUsd,
                contractForwardRate,
                contractDate,
                maturityDate,
                hedgeDesignationDate,
                counterpartyName,
                counterpartyCreditRating
        );
        contract.position = requireNonNull(position, "FX Forward position is required.");
        return contract;
    }

    public String getBaseCurrency() {
        return "USD";
    }

    public boolean offsetsForeignCurrencyAsset() {
        return this.position == FxForwardPosition.SELL_USD_BUY_KRW;
    }

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 통화선도 헤지 지정 — 신규 계약을 등록하고 위험회피수단으로 지정합니다.
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계의 지정과 위험관리 목적 및
     * 위험회피전략에 대한 공식적인 지정·문서화가 필요합니다.
     *
     * @param contractId           계약번호 (업무 식별자)
     * @param notionalAmountUsd    명목원금 (USD, 양수)
     * @param contractForwardRate  계약 선물환율 (KRW/USD, 양수)
     * @param contractDate         계약일
     * @param maturityDate         만기일 (계약일 이후)
     * @param hedgeDesignationDate 헤지 지정일
     * @return 신규 통화선도 계약 엔티티
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
     */
    public static FxForwardContract designate(
            String contractId,
            BigDecimal notionalAmountUsd,
            BigDecimal contractForwardRate,
            LocalDate contractDate,
            LocalDate maturityDate,
            LocalDate hedgeDesignationDate) {

        return designate(contractId, notionalAmountUsd, contractForwardRate,
                contractDate, maturityDate, hedgeDesignationDate, null, null);
    }

    /**
     * 통화선도 헤지 지정 (거래상대방 정보 포함) — 신규 계약을 등록하고 위험회피수단으로 지정합니다.
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계의 지정과 위험관리 목적 및
     * 위험회피전략에 대한 공식적인 지정·문서화가 필요합니다.
     *
     * @param contractId             계약번호 (업무 식별자)
     * @param notionalAmountUsd      명목원금 (USD, 양수)
     * @param contractForwardRate    계약 선물환율 (KRW/USD, 양수)
     * @param contractDate           계약일
     * @param maturityDate           만기일 (계약일 이후)
     * @param hedgeDesignationDate   헤지 지정일
     * @param counterpartyName       거래상대방명 (nullable)
     * @param counterpartyCreditRating 거래상대방 신용등급 (nullable)
     * @return 신규 통화선도 계약 엔티티
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
     * @see K-IFRS 1109호 B6.4.7 (신용위험 지배 판단 — 거래상대방 신용등급)
     */
    public static FxForwardContract designate(
            String contractId,
            BigDecimal notionalAmountUsd,
            BigDecimal contractForwardRate,
            LocalDate contractDate,
            LocalDate maturityDate,
            LocalDate hedgeDesignationDate,
            String counterpartyName,
            CreditRating counterpartyCreditRating) {

        requireNonNull(contractId, "계약번호는 필수입니다.");
        requireNonNull(notionalAmountUsd, "명목원금은 필수입니다.");
        requireNonNull(contractForwardRate, "계약 선물환율은 필수입니다.");
        requireNonNull(contractDate, "계약일은 필수입니다.");
        requireNonNull(maturityDate, "만기일은 필수입니다.");
        requireNonNull(hedgeDesignationDate, "헤지 지정일은 필수입니다.");

        if (notionalAmountUsd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("FX_002", "명목원금은 0보다 커야 합니다.");
        }
        if (contractForwardRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("FX_002", "계약 선물환율은 0보다 커야 합니다.");
        }
        if (!maturityDate.isAfter(contractDate)) {
            throw new BusinessException("FX_001", "만기일은 계약일 이후여야 합니다.");
        }

        FxForwardContract contract = new FxForwardContract();
        contract.contractId = contractId;
        contract.notionalAmountUsd = notionalAmountUsd;
        contract.contractForwardRate = contractForwardRate;
        contract.contractDate = contractDate;
        contract.maturityDate = maturityDate;
        contract.hedgeDesignationDate = hedgeDesignationDate;
        contract.status = ContractStatus.ACTIVE;
        contract.counterpartyName = counterpartyName;
        contract.counterpartyCreditRating = counterpartyCreditRating;

        log.info("통화선도 헤지 지정 완료: contractId={}", contractId);
        return contract;
    }

    // -----------------------------------------------------------------------
    // 상태 변경 비즈니스 메서드
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 연계 — 헤지 지정 시 호출.
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계 지정과 동시에 위험회피수단에
     * 연계 정보를 기록합니다.
     *
     * @param hedgeRelationshipId 연계할 위험회피관계 ID
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별·문서화)
     */
    public void linkHedgeRelationship(String hedgeRelationshipId) {
        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        this.hedgeRelationshipId = hedgeRelationshipId;
        log.info("통화선도 위험회피관계 연계: contractId={}, hedgeRelationshipId={}",
                this.contractId, hedgeRelationshipId);
    }

    /**
     * 헤지 지정 가능 여부 검증 — ACTIVE 상태 확인 및 만기 도래 여부 확인.
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계 지정은 공식 지정일에 이루어져야 하며,
     * 이미 만기 도래하거나 비활성화된 계약은 위험회피수단으로 지정할 수 없습니다.
     *
     * @param designationDate 헤지 지정일
     * @throws BusinessException HD_006 — ACTIVE 상태가 아닌 경우
     * @throws BusinessException HD_006 — 만기가 지정일 이전이거나 동일한 경우
     * @see K-IFRS 1109호 6.4.1(2) (헤지 지정 요건)
     */
    public void validateForHedgeDesignation(LocalDate designationDate) {
        requireNonNull(designationDate, "헤지 지정일은 필수입니다.");
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("HD_006",
                    String.format("ACTIVE 상태의 계약만 위험회피수단으로 지정할 수 있습니다. contractId=%s, status=%s",
                            this.contractId, this.status),
                    HttpStatus.BAD_REQUEST);
        }
        if (!this.maturityDate.isAfter(designationDate)) {
            throw new BusinessException("HD_006",
                    String.format("만기 도래 계약은 헤지 지정이 불가합니다. 계약만기=%s, 지정일=%s",
                            this.maturityDate, designationDate));
        }
    }

    /**
     * 평가기준일 유효성 검증 — 만기일 이후 평가 불가.
     *
     * <p>K-IFRS 1109호 6.5.10: 위험회피관계가 종료된 이후에는
     * 위험회피회계를 적용할 수 없습니다.
     *
     * @param valuationDate 평가기준일
     * @throws BusinessException FX_001 — 평가기준일이 만기일 이후인 경우
     * @see K-IFRS 1109호 6.5.10 (공정가치위험회피 중단 — 만기 도래)
     */
    public void validateValuationDate(LocalDate valuationDate) {
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        if (!valuationDate.isBefore(maturityDate)) {
            throw new BusinessException("FX_001",
                    String.format("평가기준일(%s)이 만기일(%s) 이후입니다.", valuationDate, maturityDate));
        }
    }

    /**
     * 잔존일수 계산 — 평가기준일부터 만기일까지의 일수.
     *
     * @param valuationDate 평가기준일
     * @return 잔존일수 (양수)
     * @see K-IFRS 1113호 (공정가치 측정 — IRP 투입변수)
     */
    public int calculateRemainingDays(LocalDate valuationDate) {
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        return (int) ChronoUnit.DAYS.between(valuationDate, maturityDate);
    }

    /**
     * 계약 정보 갱신 — PoC 재계산 허용.
     *
     * <p>동일 계약 ID로 재제출 시 계약 조건이 변경된 경우 갱신합니다.
     *
     * @param notionalAmountUsd    갱신할 명목원금 (USD)
     * @param contractForwardRate  갱신할 계약 선물환율 (KRW/USD)
     * @param contractDate         갱신할 계약일
     * @param maturityDate         갱신할 만기일
     * @param hedgeDesignationDate 갱신할 헤지 지정일
     * @see K-IFRS 1109호 6.5.6 (위험회피수단 재지정)
     */
    public void update(
            BigDecimal notionalAmountUsd,
            BigDecimal contractForwardRate,
            LocalDate contractDate,
            LocalDate maturityDate,
            LocalDate hedgeDesignationDate,
            CreditRating counterpartyCreditRating) {
        this.notionalAmountUsd = requireNonNull(notionalAmountUsd, "명목원금은 필수입니다.");
        this.contractForwardRate = requireNonNull(contractForwardRate, "계약 선물환율은 필수입니다.");
        this.contractDate = requireNonNull(contractDate, "계약일은 필수입니다.");
        this.maturityDate = requireNonNull(maturityDate, "만기일은 필수입니다.");
        this.hedgeDesignationDate = requireNonNull(hedgeDesignationDate, "헤지 지정일은 필수입니다.");
        this.counterpartyCreditRating = requireNonNull(counterpartyCreditRating, "거래상대방 신용등급은 필수입니다.");
        log.info("통화선도 계약 정보 갱신: contractId={}", this.contractId);
    }

    /**
     * 헤지 관계 조기 중단.
     *
     * @see K-IFRS 1109호 6.5.10 (공정가치위험회피 중단)
     */
    public void terminate() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("FX_001", "활성 상태의 계약만 중단할 수 있습니다.");
        }
        this.status = ContractStatus.TERMINATED;
        log.info("통화선도 헤지 중단: contractId={}", contractId);
    }

    /**
     * 만기 도래 처리 — 결제 완료 시 호출.
     */
    public void markAsMatured() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("FX_001", "활성 상태의 계약만 만기 처리할 수 있습니다.");
        }
        this.status = ContractStatus.MATURED;
        log.info("통화선도 만기 처리: contractId={}", contractId);
    }

}
