package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.valuation.domain.common.FairValueLevel;

import java.math.BigDecimal;

/**
 * 채권 헤지귀속 공정가치 변동 계산 결과 응답 DTO.
 *
 * <p>입력값 에코, 중간 계산값(PV), 최종 헤지귀속 변동액을 포함합니다.
 * FVH IRS 유효성 테스트·분개 생성 모듈에서 {@code hedgeAttributedFvChange}를 재사용합니다.
 *
 * @see <a href="#">K-IFRS 1109호 6.5.8 (공정가치 위험회피 회계처리)</a>
 * @see <a href="#">K-IFRS 1109호 B6.5.1 (헤지위험 귀속 공정가치 변동 측정)</a>
 */
public record BondHedgedItemFvChangeResponse(

        // ── 입력 에코 ──────────────────────────────────────────────────────────

        /** 채권 액면금액 (KRW) */
        BigDecimal notional,

        /** 연 쿠폰금리 (소수) */
        BigDecimal couponRate,

        /** 잔존일수 */
        int remainingDays,

        /** 이자 지급 주기 */
        String settlementFrequency,

        /** 지정일 시장금리 (할인율) */
        BigDecimal designationDiscountRate,

        /** 현재 시장금리 (할인율) */
        BigDecimal currentDiscountRate,

        // ── 계산 결과 ──────────────────────────────────────────────────────────

        /**
         * 지정일 금리 기준 채권 현재가치 (KRW).
         *
         * <p>PV(designationDiscountRate) — 기준선(baseline) PV입니다.
         * 지정일 at-market IRS라면 채권 액면가와 거의 동일합니다.
         */
        BigDecimal pvAtDesignation,

        /**
         * 현재 시장금리 기준 채권 현재가치 (KRW).
         *
         * <p>PV(currentDiscountRate) — 현재 평가 시점의 PV입니다.
         * 금리 상승 시 pvAtDesignation보다 낮아집니다.
         */
        BigDecimal pvAtCurrent,

        /**
         * 헤지귀속 공정가치 변동 (KRW) — 유효성 테스트 및 분개 생성에서 재사용.
         *
         * <pre>hedgeAttributedFvChange = pvAtCurrent - pvAtDesignation</pre>
         *
         * <ul>
         *   <li>양수: 금리 하락 → 채권 가치 상승 (FVH 피헤지 평가이익 → 장부가치 조정 증가)</li>
         *   <li>음수: 금리 상승 → 채권 가치 하락 (FVH 피헤지 평가손실 → 장부가치 조정 감소)</li>
         * </ul>
         *
         * <!-- TODO(RAG 재검증): K-IFRS 1109호 B6.5.1~B6.5.5 신용위험 귀속분 분리 검증 -->
         * @see <a href="#">K-IFRS 1109호 B6.5.1 (헤지귀속 공정가치 변동 계산 방법)</a>
         */
        BigDecimal hedgeAttributedFvChange,

        /**
         * 공정가치 Level 분류.
         *
         * <p>원화 고정금리채권의 금리위험은 관측가능한 시장 금리 커브를 기반으로 평가하므로
         * K-IFRS 1113호 Level 2에 해당합니다.
         *
         * <!-- TODO(RAG 재검증): K-IFRS 1113호 73~86항 Level 2 분류 기준 재확인 필요 -->
         * @see <a href="#">K-IFRS 1113호 73~86항 (Level 2 — 관측가능한 투입변수)</a>
         */
        FairValueLevel fairValueLevel,

        /**
         * 계산 근거 설명.
         *
         * <p>감사 대응 및 문서화 지원을 위한 계산 과정 요약입니다.
         * @see <a href="#">K-IFRS 1107호 (금융상품 공시 — 헤지회계 공시)</a>
         */
        String calculationNote

) {
    /**
     * 정적 팩토리 — 계산 결과로 응답 객체 생성.
     *
     * @param request          원본 요청
     * @param pvAtDesignation  지정일 금리 PV
     * @param pvAtCurrent      현재 금리 PV
     * @param fvChange         헤지귀속 공정가치 변동
     * @return 응답 객체
     */
    public static BondHedgedItemFvChangeResponse of(
            BondHedgedItemFvChangeRequest request,
            BigDecimal pvAtDesignation,
            BigDecimal pvAtCurrent,
            BigDecimal fvChange) {

        String note = buildCalculationNote(request, pvAtDesignation, pvAtCurrent, fvChange);

        return new BondHedgedItemFvChangeResponse(
                request.notional(),
                request.couponRate(),
                request.remainingDays(),
                request.settlementFrequency(),
                request.designationDiscountRate(),
                request.currentDiscountRate(),
                pvAtDesignation,
                pvAtCurrent,
                fvChange,
                FairValueLevel.LEVEL_2,   // KRW 금리위험 — 관측가능 시장금리 커브 기반 → Level 2
                note
        );
    }

    private static String buildCalculationNote(
            BondHedgedItemFvChangeRequest req,
            BigDecimal pvAtDesig,
            BigDecimal pvAtCurr,
            BigDecimal fvChange) {

        String direction = fvChange.compareTo(BigDecimal.ZERO) < 0 ? "하락 (평가손실)" : "상승 (평가이익)";
        return String.format(
                "[K-IFRS 1109호 6.5.8 / B6.5.1] 채권 헤지귀속 FV 변동 계산 (PoC Level 2, ACT/365)%n"
                + "  잔존일수=%d일, 결제주기=%s%n"
                + "  PV(지정일 %.4f) = %,.0f원%n"
                + "  PV(현재   %.4f) = %,.0f원%n"
                + "  헤지귀속 FV 변동 = %,.0f원 (%s)%n"
                + "  [주의] PoC 단순화: 신용위험 귀속분 미분리, 플랫 커브 가정%n"
                + "  [TODO] RAG 복구 후 K-IFRS 1109호 B6.5.1~B6.5.5 교차 검증 필요",
                req.remainingDays(),
                req.settlementFrequency(),
                req.designationDiscountRate(),
                pvAtDesig,
                req.currentDiscountRate(),
                pvAtCurr,
                fvChange,
                direction
        );
    }
}
