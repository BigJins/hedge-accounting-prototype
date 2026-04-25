package com.hedge.prototype.hedge.domain.common;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * 위험회피대상항목 유형 열거형.
 *
 * <p>K-IFRS 1109호 6.3.1에 따라 위험회피대상항목은 인식된 자산·부채,
 * 미인식 확정계약, 예상거래, 해외사업장 순투자 등이 될 수 있습니다.
 * 본 PoC는 증권사 실무 대상 유형(외화채권, 원화채권, 외화차입금, 예상거래, 외화예금)을 지원합니다.
 *
 * @see K-IFRS 1109호 6.3.1 (위험회피대상항목 적격성 — 인식된 자산·부채, 예상거래 포함)
 * @see K-IFRS 1109호 6.3.7 (위험 구성요소의 헤지대상 지정 — 관측가능·별도 식별 가능 요건)
 */
public enum HedgedItemType {

    /**
     * 외화채권 (미국 국채, 해외 회사채 등).
     *
     * <p>USD 등 외화 표시 채권으로, 환율 위험과 금리 위험에 동시 노출됩니다.
     * 환율 위험만 분리하여 헤지 지정이 가능합니다 (K-IFRS 6.3.7).
     *
     * <ul>
     *   <li>허용 위험: FOREIGN_CURRENCY, INTEREST_RATE</li>
     *   <li>허용 헤지 유형: FAIR_VALUE (금리위험), CASH_FLOW (환율위험)</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.3.7 (외화채권의 통화위험 구성요소 단독 지정)
     */
    FOREIGN_BOND(
            "외화채권",
            new HedgedRisk[]{HedgedRisk.FOREIGN_CURRENCY, HedgedRisk.INTEREST_RATE},
            new HedgeType[]{HedgeType.FAIR_VALUE, HedgeType.CASH_FLOW}
    ),

    /**
     * 원화 고정금리채권 (국고채, 회사채 등).
     *
     * <p>원화 표시 고정금리 채권으로, 시장금리 변동에 따른 공정가치 변동 위험에 노출됩니다.
     * 공정가치 위험회피(FVH)의 대표적 헤지대상항목입니다.
     * 이자율스왑(IRS: 고정수취/변동지급)을 헤지수단으로 사용합니다.
     *
     * <ul>
     *   <li>허용 위험: INTEREST_RATE</li>
     *   <li>허용 헤지 유형: FAIR_VALUE</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 회계처리)
     * @see K-IFRS 1109호 6.3.7 (이자율 위험 구성요소 단독 지정)
     */
    KRW_FIXED_BOND(
            "원화 고정금리채권",
            new HedgedRisk[]{HedgedRisk.INTEREST_RATE},
            new HedgeType[]{HedgeType.FAIR_VALUE}
    ),

    /**
     * 원화 변동금리채권.
     *
     * <p>원화 표시 변동금리 채권으로, 기준금리 변동에 따른 현금흐름 불확실성에 노출됩니다.
     * 현금흐름 위험회피(CFH)의 대표적 헤지대상항목입니다.
     * 이자율스왑(IRS: 변동수취/고정지급)을 헤지수단으로 사용합니다.
     *
     * <ul>
     *   <li>허용 위험: INTEREST_RATE</li>
     *   <li>허용 헤지 유형: CASH_FLOW</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 OCI 처리)
     * @see K-IFRS 1109호 6.3.7 (이자율 위험 구성요소 단독 지정)
     */
    KRW_FLOATING_BOND(
            "원화 변동금리채권",
            new HedgedRisk[]{HedgedRisk.INTEREST_RATE},
            new HedgeType[]{HedgeType.CASH_FLOW}
    ),

    /**
     * 외화차입금 (달러 차입 등).
     *
     * <p>USD 등 외화로 조달된 차입금으로, 환율 위험과 금리 위험에 노출됩니다.
     * 통화스왑(CRS)을 활용하여 원화 고정금리 차입으로 전환 가능합니다.
     *
     * <ul>
     *   <li>허용 위험: FOREIGN_CURRENCY, INTEREST_RATE</li>
     *   <li>허용 헤지 유형: FAIR_VALUE, CASH_FLOW</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.3.1 (인식된 부채의 헤지대상 적격성)
     * @see K-IFRS 1109호 6.3.7 (외화 구성요소 단독 지정)
     */
    FOREIGN_BORROWING(
            "외화차입금",
            new HedgedRisk[]{HedgedRisk.FOREIGN_CURRENCY, HedgedRisk.INTEREST_RATE},
            new HedgeType[]{HedgeType.FAIR_VALUE, HedgeType.CASH_FLOW}
    ),

    /**
     * 외화 예상거래 (수출입 대금 등).
     *
     * <p>미래에 발생이 예상되는 외화 거래로, 환율 변동에 따른 현금흐름 불확실성에 노출됩니다.
     * 예상거래는 발생 가능성이 높아야(highly probable) 헤지 지정이 가능합니다.
     * 현금흐름 위험회피(CFH) 대상이며, 통화선도(FX Forward)를 헤지수단으로 사용합니다.
     *
     * <ul>
     *   <li>허용 위험: FOREIGN_CURRENCY</li>
     *   <li>허용 헤지 유형: CASH_FLOW</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.3.1 (예상거래의 헤지대상 적격성 — 발생가능성 높은 예상거래)
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 OCI 처리)
     */
    FORECAST_TRANSACTION(
            "외화 예상거래",
            new HedgedRisk[]{HedgedRisk.FOREIGN_CURRENCY},
            new HedgeType[]{HedgeType.CASH_FLOW}
    ),

    /**
     * 외화예금.
     *
     * <p>USD 등 외화 표시 예금으로, 환율 변동에 따른 원화 환산금액 변동 위험에 노출됩니다.
     * 공정가치 위험회피(FVH) 또는 현금흐름 위험회피(CFH) 모두 지정 가능합니다.
     *
     * <ul>
     *   <li>허용 위험: FOREIGN_CURRENCY</li>
     *   <li>허용 헤지 유형: FAIR_VALUE, CASH_FLOW</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.3.1 (인식된 자산의 헤지대상 적격성)
     * @see K-IFRS 1109호 6.3.7 (외화위험 구성요소 단독 지정)
     */
    FX_DEPOSIT(
            "외화예금",
            new HedgedRisk[]{HedgedRisk.FOREIGN_CURRENCY},
            new HedgeType[]{HedgeType.FAIR_VALUE, HedgeType.CASH_FLOW}
    );

    // -----------------------------------------------------------------------
    // 필드
    // -----------------------------------------------------------------------

    /** 한국어 명칭 */
    private final String koreanName;

    /**
     * 허용되는 회피 대상 위험 유형 목록.
     *
     * @see K-IFRS 1109호 6.3.7 (위험 구성요소 지정 — 관측가능·별도 식별 가능 요건)
     */
    private final HedgedRisk[] allowedRisks;

    /**
     * 허용되는 헤지 유형 목록.
     *
     * @see K-IFRS 1109호 6.5.2 (위험회피관계 3종류)
     */
    private final HedgeType[] allowedHedgeTypes;

    // -----------------------------------------------------------------------
    // 생성자
    // -----------------------------------------------------------------------

    HedgedItemType(String koreanName, HedgedRisk[] allowedRisks, HedgeType[] allowedHedgeTypes) {
        this.koreanName = koreanName;
        this.allowedRisks = allowedRisks.clone();
        this.allowedHedgeTypes = allowedHedgeTypes.clone();
    }

    // -----------------------------------------------------------------------
    // 공개 메서드
    // -----------------------------------------------------------------------

    /**
     * 한국어 명칭 반환.
     *
     * @return 한국어 명칭
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * 허용 위험 유형 목록 반환 (방어적 복사).
     *
     * @return 허용 위험 유형 배열 복사본
     * @see K-IFRS 1109호 6.3.7 (위험 구성요소 지정)
     */
    public HedgedRisk[] getAllowedRisks() {
        return allowedRisks.clone();
    }

    /**
     * 허용 헤지 유형 목록 반환 (방어적 복사).
     *
     * @return 허용 헤지 유형 배열 복사본
     * @see K-IFRS 1109호 6.5.2 (위험회피관계 3종류)
     */
    public HedgeType[] getAllowedHedgeTypes() {
        return allowedHedgeTypes.clone();
    }

    /**
     * 지정 위험 유형이 이 항목 유형에 허용되는지 검증합니다.
     *
     * @param risk 검증할 위험 유형
     * @return 허용 여부
     * @see K-IFRS 1109호 6.3.7 (위험 구성요소 적격성)
     */
    public boolean isRiskAllowed(HedgedRisk risk) {
        for (HedgedRisk allowedRisk : allowedRisks) {
            if (allowedRisk == risk) {
                return true;
            }
        }
        return false;
    }

    /**
     * 지정 헤지 유형이 이 항목 유형에 허용되는지 검증합니다.
     *
     * @param hedgeType 검증할 헤지 유형
     * @return 허용 여부
     * @see K-IFRS 1109호 6.5.2 (위험회피관계 3종류)
     */
    public boolean isHedgeTypeAllowed(HedgeType hedgeType) {
        for (HedgeType allowed : allowedHedgeTypes) {
            if (allowed == hedgeType) {
                return true;
            }
        }
        return false;
    }

    @JsonCreator
    public static HedgedItemType fromJson(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty() || "OTHER".equalsIgnoreCase(normalized)) {
            return null;
        }

        try {
            return HedgedItemType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
