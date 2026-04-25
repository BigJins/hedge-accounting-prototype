package com.hedge.prototype.valuation.domain.irs;

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
 * 이자율스왑(IRS, Interest Rate Swap) 계약 엔티티.
 *
 * <p>고정금리와 변동금리를 교환하는 파생상품 계약을 나타냅니다.
 * 공정가치 위험회피(FVH)에서는 고정수취/변동지급(payFixed=false) 구조로,
 * 현금흐름 위험회피(CFH)에서는 고정지급/변동수취(payFixed=true) 구조로 사용됩니다.
 *
 * <p><b>생성 방법</b>: {@link #of} 팩토리 메서드만 사용.
 * Builder, public 생성자 사용 금지.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 헤지수단)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — IRS 헤지수단)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "irs_contracts")
public class IrsContract extends BaseAuditEntity {

    @Id
    @Column(name = "contract_id", length = 50)
    private String contractId;

    /**
     * 명목금액 (원화 KRW) — BigDecimal 필수.
     * IRS 금리 계산의 기준금액입니다.
     *
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 — 명목금액 기준)
     */
    @Column(name = "notional_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal notionalAmount;

    /**
     * 고정금리 (연 %, 소수 표현 — 예: 0.035 = 3.5%).
     *
     * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수)
     */
    @Column(name = "fixed_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal fixedRate;

    /**
     * 변동금리 기준지수.
     * 예: "CD_91D" (91일 CD금리), "SOFR" (미 달러 SOFR), "LIBOR_3M"
     */
    @Column(name = "floating_rate_index", nullable = false, length = 30)
    private String floatingRateIndex;

    /**
     * 변동금리 스프레드 (nullable — 없으면 기준지수 그대로 적용).
     * 소수 표현 (예: 0.005 = 50bps)
     */
    @Column(name = "floating_spread", precision = 8, scale = 6)
    private BigDecimal floatingSpread;

    /** 계약일 */
    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    /** 만기일 (계약일 이후) */
    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /**
     * 고정금리 지급 방향.
     * true  = 고정지급/변동수취 (Pay Fixed, Receive Floating) — 현금흐름 위험회피(CFH)
     * false = 변동지급/고정수취 (Pay Floating, Receive Fixed) — 공정가치 위험회피(FVH)
     *
     * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — Receive Fixed)
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — Pay Fixed)
     */
    @Column(name = "pay_fixed_receive_floating", nullable = false)
    private boolean payFixedReceiveFloating;

    /**
     * 이자 결제 주기.
     * "QUARTERLY" (분기), "SEMI_ANNUAL" (반기), "ANNUAL" (연간)
     */
    @Column(name = "settlement_frequency", nullable = false, length = 20)
    private String settlementFrequency;

    /**
     * 일수 계산 관행.
     * 원화 IRS: ACT_365, USD IRS: ACT_360
     *
     * @see K-IFRS 1113호 81항 (Level 2 — 시장 표준 day count 준수)
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
     * @see K-IFRS 1109호 6.5.10 (공정가치 위험회피 중단)
     * @see K-IFRS 1109호 6.5.14 (현금흐름 위험회피 중단)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    /**
     * 연계된 위험회피관계 ID (nullable — 미지정 계약 허용).
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별·문서화)
     */
    @Column(name = "hedge_relationship_id", length = 50)
    private String hedgeRelationshipId;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 이자율스왑 계약 등록.
     *
     * <p>K-IFRS 1109호 6.2.1: 파생상품(IRS)을 위험회피수단으로 적격하게 지정합니다.
     * K-IFRS 1109호 6.4.1(2): 위험회피관계 지정 시 위험회피수단을 공식 식별·문서화합니다.
     *
     * @param contractId               계약번호
     * @param notionalAmount           명목금액 (KRW, 양수)
     * @param fixedRate                고정금리 (소수, 양수)
     * @param floatingRateIndex        변동금리 기준지수 (예: "CD_91D")
     * @param floatingSpread           변동금리 스프레드 (nullable)
     * @param contractDate             계약일
     * @param maturityDate             만기일 (계약일 이후)
     * @param payFixedReceiveFloating  true=고정지급/변동수취, false=변동지급/고정수취
     * @param settlementFrequency      결제 주기 ("QUARTERLY", "SEMI_ANNUAL", "ANNUAL")
     * @param dayCountConvention       일수 계산 관행
     * @param counterpartyName         거래상대방명 (nullable)
     * @param counterpartyCreditRating 거래상대방 신용등급 (nullable)
     * @return 신규 IRS 계약 엔티티
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — 파생상품)
     * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
     */
    public static IrsContract of(
            String contractId,
            BigDecimal notionalAmount,
            BigDecimal fixedRate,
            String floatingRateIndex,
            BigDecimal floatingSpread,
            LocalDate contractDate,
            LocalDate maturityDate,
            boolean payFixedReceiveFloating,
            String settlementFrequency,
            DayCountConvention dayCountConvention,
            String counterpartyName,
            CreditRating counterpartyCreditRating) {

        requireNonNull(contractId, "계약번호는 필수입니다.");
        requireNonNull(notionalAmount, "명목금액은 필수입니다.");
        requireNonNull(fixedRate, "고정금리는 필수입니다.");
        requireNonNull(floatingRateIndex, "변동금리 기준지수는 필수입니다.");
        requireNonNull(contractDate, "계약일은 필수입니다.");
        requireNonNull(maturityDate, "만기일은 필수입니다.");
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        requireNonNull(dayCountConvention, "일수 계산 관행은 필수입니다.");

        if (notionalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("IRS_001", "명목금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (fixedRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("IRS_002", "고정금리는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!maturityDate.isAfter(contractDate)) {
            throw new BusinessException("IRS_003", "만기일은 계약일 이후여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        IrsContract contract = new IrsContract();
        contract.contractId = contractId;
        contract.notionalAmount = notionalAmount;
        contract.fixedRate = fixedRate;
        contract.floatingRateIndex = floatingRateIndex;
        contract.floatingSpread = floatingSpread;
        contract.contractDate = contractDate;
        contract.maturityDate = maturityDate;
        contract.payFixedReceiveFloating = payFixedReceiveFloating;
        contract.settlementFrequency = settlementFrequency;
        contract.dayCountConvention = dayCountConvention;
        contract.counterpartyName = counterpartyName;
        contract.counterpartyCreditRating = counterpartyCreditRating;
        contract.status = ContractStatus.ACTIVE;

        log.info("IRS 계약 등록: contractId={}, payFixed={}, settlementFrequency={}",
                contractId, payFixedReceiveFloating, settlementFrequency);
        return contract;
    }

    // -----------------------------------------------------------------------
    // 비즈니스 메서드
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 연계.
     *
     * @param hedgeRelationshipId 연계할 위험회피관계 ID
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별·문서화)
     */
    public void linkHedgeRelationship(String hedgeRelationshipId) {
        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        this.hedgeRelationshipId = hedgeRelationshipId;
        log.info("IRS 위험회피관계 연계: contractId={}, hedgeRelationshipId={}", contractId, hedgeRelationshipId);
    }

    /**
     * 헤지 지정 가능 여부 검증 (ACTIVE 상태, 만기 이전).
     *
     * @param designationDate 헤지 지정일
     * @throws BusinessException IRS_006 — ACTIVE 상태가 아닌 경우
     * @throws BusinessException IRS_006 — 만기가 지정일 이전인 경우
     * @see K-IFRS 1109호 6.4.1(2) (헤지 지정 요건)
     */
    public void validateForHedgeDesignation(LocalDate designationDate) {
        requireNonNull(designationDate, "헤지 지정일은 필수입니다.");
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("IRS_006",
                    String.format("ACTIVE 상태의 계약만 위험회피수단으로 지정할 수 있습니다. contractId=%s, status=%s",
                            contractId, status), HttpStatus.BAD_REQUEST);
        }
        if (!this.maturityDate.isAfter(designationDate)) {
            throw new BusinessException("IRS_006",
                    String.format("만기 도래 계약은 헤지 지정이 불가합니다. 계약만기=%s, 지정일=%s",
                            maturityDate, designationDate));
        }
    }

    /**
     * 평가기준일 유효성 검증 — 만기일 이후 평가 불가.
     *
     * @param valuationDate 평가기준일
     * @throws BusinessException IRS_004 — 평가기준일이 만기일 이후인 경우
     * @see K-IFRS 1109호 6.5.10 (공정가치 위험회피 중단)
     */
    public void validateValuationDate(LocalDate valuationDate) {
        requireNonNull(valuationDate, "평가기준일은 필수입니다.");
        if (!valuationDate.isBefore(maturityDate)) {
            throw new BusinessException("IRS_004",
                    String.format("평가기준일(%s)이 만기일(%s) 이후입니다.", valuationDate, maturityDate));
        }
    }

    /**
     * 잔존일수 계산 — 평가기준일부터 만기일까지.
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
     *
     * @see K-IFRS 1109호 6.5.6 (위험회피수단 재지정)
     */
    public void update(
            BigDecimal notionalAmount,
            BigDecimal fixedRate,
            String floatingRateIndex,
            BigDecimal floatingSpread,
            LocalDate maturityDate,
            CreditRating counterpartyCreditRating) {
        this.notionalAmount = requireNonNull(notionalAmount, "명목금액은 필수입니다.");
        this.fixedRate = requireNonNull(fixedRate, "고정금리는 필수입니다.");
        this.floatingRateIndex = requireNonNull(floatingRateIndex, "변동금리 기준지수는 필수입니다.");
        this.floatingSpread = floatingSpread;
        this.maturityDate = requireNonNull(maturityDate, "만기일은 필수입니다.");
        this.counterpartyCreditRating = counterpartyCreditRating;
        log.info("IRS 계약 정보 갱신: contractId={}", contractId);
    }

    /**
     * 계약 조기 중단.
     *
     * @see K-IFRS 1109호 6.5.10 (공정가치 위험회피 중단)
     * @see K-IFRS 1109호 6.5.14 (현금흐름 위험회피 중단)
     */
    public void terminate() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new BusinessException("IRS_004", "활성 상태의 계약만 중단할 수 있습니다.");
        }
        this.status = ContractStatus.TERMINATED;
        log.info("IRS 계약 중단: contractId={}", contractId);
    }
}
