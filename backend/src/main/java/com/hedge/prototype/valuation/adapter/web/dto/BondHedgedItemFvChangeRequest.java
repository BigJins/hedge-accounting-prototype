package com.hedge.prototype.valuation.adapter.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * 채권 헤지귀속 공정가치 변동 계산 요청 DTO.
 *
 * <p>원화 고정금리채권(KRW_FIXED_BOND)의 금리위험 귀속 공정가치 변동 계산에 필요한
 * 입력값을 포함합니다. 이 API는 지속성(DB 저장) 없는 순수 계산 엔드포인트입니다.
 *
 * <h3>사용 시나리오</h3>
 * <ul>
 *   <li>FVH IRS 유효성 테스트: {@code hedgedItemPvChange} 입력값 산출</li>
 *   <li>FVH IRS 분개 생성: {@code hedgedItemAdjustment} 입력값 산출</li>
 * </ul>
 *
 * <!-- TODO(RAG 재검증): K-IFRS 1109호 B6.5.1~B6.5.5 신용위험 귀속분 분리 검증 필요 -->
 * @see <a href="#">K-IFRS 1109호 6.5.8 (공정가치 위험회피 피헤지항목 장부가치 조정)</a>
 * @see <a href="#">K-IFRS 1113호 72항 (Level 2 — 관측가능한 투입변수)</a>
 */
public record BondHedgedItemFvChangeRequest(

        /**
         * 채권 액면금액 (KRW, 원화).
         * 쿠폰 현금흐름과 원금 상환 계산의 기준이 됩니다.
         */
        @NotNull(message = "채권 액면금액은 필수입니다.")
        @DecimalMin(value = "1", message = "채권 액면금액은 0보다 커야 합니다.")
        BigDecimal notional,

        /**
         * 연 쿠폰금리 (소수 표현 — 예: 0.03 = 3.0%).
         *
         * @see <a href="#">K-IFRS 1113호 72항 (관측가능한 투입변수: 쿠폰금리)</a>
         */
        @NotNull(message = "쿠폰금리는 필수입니다.")
        @PositiveOrZero(message = "쿠폰금리는 0 이상이어야 합니다.")
        BigDecimal couponRate,

        /**
         * 잔존일수 (평가기준일 → 만기일, ACT/365 기준).
         * 예: 2026-06-30 ~ 2029-04-01 = 1004일
         */
        @Positive(message = "잔존일수는 0보다 커야 합니다.")
        int remainingDays,

        /**
         * 이자 지급 주기.
         * "QUARTERLY" (분기), "SEMI_ANNUAL" (반기), "ANNUAL" (연간)
         */
        @NotBlank(message = "결제 주기는 필수입니다.")
        String settlementFrequency,

        /**
         * 지정일 시장금리 (할인율, 소수 표현).
         * 헤지 지정 시점의 시장 금리 수준. 기준 PV 계산에 사용됩니다.
         * 지정일에 at-market IRS라면 쿠폰금리와 동일합니다.
         *
         * @see <a href="#">K-IFRS 1113호 72항 (관측가능한 투입변수: 시장금리)</a>
         */
        @NotNull(message = "지정일 시장금리는 필수입니다.")
        @PositiveOrZero(message = "지정일 시장금리는 0 이상이어야 합니다.")
        BigDecimal designationDiscountRate,

        /**
         * 현재(평가기준일) 시장금리 (할인율, 소수 표현).
         * 현재 PV 계산에 사용됩니다. 지정일 금리와의 차이가 헤지귀속 변동을 만듭니다.
         */
        @NotNull(message = "현재 시장금리는 필수입니다.")
        @PositiveOrZero(message = "현재 시장금리는 0 이상이어야 합니다.")
        BigDecimal currentDiscountRate

) {}
