package com.hedge.prototype.effectiveness.domain;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * 위험회피 유효성 테스트 결과 엔티티.
 *
 * <p>매 보고기간 말 Dollar-offset 방법으로 위험회피 유효성을 평가하고
 * 비효과적 부분을 계산합니다. 이 엔티티는 Append-Only로 관리됩니다.
 *
 * <p>공정가치 헤지 비효과성 = 위험회피수단 변동 + 피헤지항목 변동 (잔액이 P&L)
 * 현금흐름 헤지 비효과성 = Lower of Test에 따라 OCI / P&L 분리
 *
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 — 매 보고기간 말)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법 허용)
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 비효과성 P&L 인식)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L 분리)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "effectiveness_tests")
public class EffectivenessTest extends BaseAuditEntity {

    // -----------------------------------------------------------------------
    // 식별자
    // -----------------------------------------------------------------------

    /**
     * 유효성 테스트 ID (자동 생성).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "effectiveness_test_id")
    private Long effectivenessTestId;

    // -----------------------------------------------------------------------
    // 연관 관계
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 ID (FK → HedgeRelationship.hedgeRelationshipId).
     *
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
     */
    @Column(name = "hedge_relationship_id", nullable = false, length = 50)
    private String hedgeRelationshipId;

    // -----------------------------------------------------------------------
    // 평가 기준 정보
    // -----------------------------------------------------------------------

    /**
     * 유효성 평가 기준일 — 매 보고기간 말.
     *
     * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 평가 의무)
     */
    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    /**
     * 유효성 테스트 방법 (기간별 / 누적 Dollar-offset).
     *
     * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 방법)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 30)
    private EffectivenessTestType testType;

    /**
     * 위험회피 유형 (FAIR_VALUE / CASH_FLOW).
     * 비효과성 계산 방법 결정에 사용됩니다.
     *
     * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성)
     * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 비효과성)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "hedge_type", nullable = false, length = 20)
    private HedgeType hedgeType;

    /**
     * 위험회피수단 유형 (FX_FORWARD / IRS / CRS).
     *
     * <p>null이면 FX_FORWARD 기본값으로 간주합니다 (1단계 하위호환).
     * 2단계부터 instrumentType=IRS 경로가 활성화됩니다.
     *
     * <p>Dollar-offset 계산 로직 자체는 수단 유형에 무관하게 동일합니다.
     * 이 필드는 분개 생성 및 리포팅 단계에서 수단별 처리를 분기하는 데 사용됩니다.
     *
     * <p>TODO: RAG 교차검증 필요 — K-IFRS 1109호 6.2.1 (파생상품 전체를 수단으로 지정 가능)
     *
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
     * @see K-IFRS 1109호 B6.4.13 (Dollar-offset — 수단 유형 무관 적용)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 20)
    private InstrumentType instrumentType;

    // -----------------------------------------------------------------------
    // 입력값 — BigDecimal 필수 (금융권 표준)
    // -----------------------------------------------------------------------

    /**
     * 위험회피수단 공정가치 변동 (당기).
     * 통화선도 등 파생상품의 당기 공정가치 변동액.
     *
     * @see K-IFRS 1109호 6.5.8 (위험회피수단 공정가치 변동 P&L 인식)
     */
    @Column(name = "instrument_fv_change", precision = 20, scale = 2)
    private BigDecimal instrumentFvChange;

    /**
     * 피헤지항목 현재가치 변동 (당기).
     * 헤지 위험에 귀속되는 피헤지항목의 당기 공정가치(현재가치) 변동액.
     *
     * @see K-IFRS 1109호 6.5.8 (피헤지항목 공정가치 변동 장부금액 조정)
     */
    @Column(name = "hedged_item_pv_change", precision = 20, scale = 2)
    private BigDecimal hedgedItemPvChange;

    /**
     * 위험회피수단 공정가치 변동 누계 (헤지 지정 이후).
     *
     * @see K-IFRS 1109호 B6.4.12 (누적 Dollar-offset 계산 기준)
     */
    @Column(name = "instrument_fv_cumulative", precision = 20, scale = 2)
    private BigDecimal instrumentFvCumulative;

    /**
     * 피헤지항목 현재가치 변동 누계 (헤지 지정 이후).
     *
     * @see K-IFRS 1109호 B6.4.12 (누적 Dollar-offset 계산 기준)
     */
    @Column(name = "hedged_item_pv_cumulative", precision = 20, scale = 2)
    private BigDecimal hedgedItemPvCumulative;

    // -----------------------------------------------------------------------
    // 결과값
    // -----------------------------------------------------------------------

    /**
     * Dollar-offset 유효성 비율 (음수 포함).
     * ratio = 위험회피수단 변동 / 피헤지항목 변동
     * 음수 = 반대방향(정상), 양수 = 동방향(비정상).
     *
     * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 비율 80%~125%)
     */
    @Column(name = "effectiveness_ratio", precision = 10, scale = 6)
    private BigDecimal effectivenessRatio;

    /**
     * 유효성 테스트 최종 판정 결과 (PASS / FAIL).
     *
     * @see K-IFRS 1109호 6.5.6 (FAIL 시 재조정 또는 중단)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "test_result", nullable = false, length = 10)
    private EffectivenessTestResult testResult;

    // -----------------------------------------------------------------------
    // 비효과성 분리
    // -----------------------------------------------------------------------

    /**
     * 유효(effective) 부분 금액.
     * 공정가치 헤지: 수단/대상 변동 중 상계된 절대값 — <b>분석용, OCI 아님. P&L 인식.</b>
     *               K-IFRS 1109호 6.5.8에 따라 수단·대상 변동 모두 당기손익으로 인식되므로
     *               이 값은 헤지 효율성 분석 목적으로만 사용합니다.
     * 현금흐름 헤지: OCI로 인식할 금액 (Lower of Test 결과).
     *
     * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 — 수단·대상 변동 모두 P&L)
     * @see K-IFRS 1109호 6.5.11⑴ (현금흐름 헤지 OCI 인식 한도)
     */
    @Column(name = "effective_amount", precision = 20, scale = 2)
    private BigDecimal effectiveAmount;

    /**
     * 비효과적(ineffective) 부분 금액 — 즉시 P&L 인식.
     * 공정가치 헤지: 수단 변동 + 대상 변동 잔액 (순효과).
     * 현금흐름 헤지: 과대헤지 초과분만 (Lower of Test).
     *
     * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성 P&L)
     * @see K-IFRS 1109호 6.5.11⑵ (현금흐름 헤지 비효과성 P&L)
     */
    @Column(name = "ineffective_amount", precision = 20, scale = 2)
    private BigDecimal ineffectiveAmount;

    /**
     * 당기 OCI 인식액 (현금흐름위험회피적립금 당기분).
     * 현금흐름 헤지에서만 사용. 공정가치 헤지 시 null.
     *
     * <p><b>주의: 이 값은 누계 잔액이 아닙니다.</b>
     * 당기 보고기간에 OCI로 인식하는 금액(= effectiveAmount)을 저장합니다.
     * 헤지 지정 이후 OCI 누계 잔액(현금흐름위험회피적립금 전체)이 필요한 경우
     * 과거 모든 테스트 레코드의 유효 부분을 합산해야 합니다.
     * (PoC 범위: 유효성 테스트 결과 산출만, 누계 적립금 관리는 별도 모듈)
     *
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피적립금)
     * @see K-IFRS 1109호 6.5.12 (적립금 재분류 조정)
     */
    @Column(name = "oci_reserve_balance", precision = 20, scale = 2)
    private BigDecimal ociReserveBalance;

    // -----------------------------------------------------------------------
    // 상태 및 감사
    // -----------------------------------------------------------------------

    /**
     * 필요 조치 (NONE / REBALANCE / DISCONTINUE).
     *
     * @see K-IFRS 1109호 6.5.5 (재조정)
     * @see K-IFRS 1109호 6.5.6 (중단)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_required", nullable = false, length = 20)
    private ActionRequired actionRequired;

    /**
     * FAIL 사유 기술 (PASS 시 null).
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 유효성 테스트 결과 엔티티 생성.
     *
     * <p>Append-Only 정책에 따라 새 레코드를 INSERT합니다.
     * 기존 결과를 덮어쓰지 않습니다.
     *
     * @param hedgeRelationshipId    위험회피관계 ID
     * @param testDate               평가 기준일
     * @param testType               테스트 방법 (기간별/누적)
     * @param hedgeType              위험회피 유형
     * @param instrumentFvChange     위험회피수단 당기 변동
     * @param hedgedItemPvChange     피헤지항목 당기 변동
     * @param instrumentFvCumulative 위험회피수단 누적 변동
     * @param hedgedItemPvCumulative 피헤지항목 누적 변동
     * @param effectivenessRatio     유효성 비율
     * @param testResult             판정 결과
     * @param effectiveAmount        유효 부분
     * @param ineffectiveAmount      비효과적 부분
     * @param ociReserveBalance      당기 OCI 인식액 — 누계 잔액이 아님 (CFH만, 나머지 null)
     * @param actionRequired         필요 조치
     * @param failureReason          실패 사유 (PASS 시 null)
     * @param instrumentType         위험회피수단 유형 (null이면 FX_FORWARD 하위호환)
     * @return 유효성 테스트 결과 엔티티
     * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 유효성 평가)
     * @see K-IFRS 1109호 6.2.1  (위험회피수단 적격성 — 수단 유형 무관 Dollar-offset 적용)
     */
    public static EffectivenessTest of(
            String hedgeRelationshipId,
            LocalDate testDate,
            EffectivenessTestType testType,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange,
            BigDecimal instrumentFvCumulative,
            BigDecimal hedgedItemPvCumulative,
            BigDecimal effectivenessRatio,
            EffectivenessTestResult testResult,
            BigDecimal effectiveAmount,
            BigDecimal ineffectiveAmount,
            BigDecimal ociReserveBalance,
            ActionRequired actionRequired,
            String failureReason,
            InstrumentType instrumentType) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 ID는 필수입니다.");
        requireNonNull(testDate, "평가 기준일은 필수입니다.");
        requireNonNull(testType, "테스트 방법은 필수입니다.");
        requireNonNull(hedgeType, "위험회피 유형은 필수입니다.");
        requireNonNull(instrumentFvChange, "위험회피수단 당기 변동은 필수입니다.");
        requireNonNull(hedgedItemPvChange, "피헤지항목 당기 변동은 필수입니다.");
        requireNonNull(instrumentFvCumulative, "위험회피수단 누적 변동은 필수입니다.");
        requireNonNull(hedgedItemPvCumulative, "피헤지항목 누적 변동은 필수입니다.");
        requireNonNull(testResult, "판정 결과는 필수입니다.");
        requireNonNull(actionRequired, "필요 조치는 필수입니다.");

        EffectivenessTest test = new EffectivenessTest();
        test.hedgeRelationshipId = hedgeRelationshipId;
        test.testDate = testDate;
        test.testType = testType;
        test.hedgeType = hedgeType;
        test.instrumentType = instrumentType;  // null 허용 (FX_FORWARD 하위호환)
        test.instrumentFvChange = instrumentFvChange;
        test.hedgedItemPvChange = hedgedItemPvChange;
        test.instrumentFvCumulative = instrumentFvCumulative;
        test.hedgedItemPvCumulative = hedgedItemPvCumulative;
        test.effectivenessRatio = effectivenessRatio;
        test.testResult = testResult;
        test.effectiveAmount = effectiveAmount;
        test.ineffectiveAmount = ineffectiveAmount;
        test.ociReserveBalance = ociReserveBalance;
        test.actionRequired = actionRequired;
        test.failureReason = failureReason;

        log.info("유효성 테스트 생성: hedgeRelationshipId={}, testDate={}, testType={}, instrumentType={}, result={}",
                hedgeRelationshipId, testDate, testType, instrumentType, testResult);

        return test;
    }
}
