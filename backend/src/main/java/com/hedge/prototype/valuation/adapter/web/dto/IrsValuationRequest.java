package com.hedge.prototype.valuation.adapter.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * IRS 공정가치 평가 요청 DTO.
 *
 * <p>K-IFRS 1113호 Level 2 평가에 필요한 시장 관측 데이터를 포함합니다.
 * 입력 검증(Bean Validation)은 DTO에서 수행합니다.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수)
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가)
 */
public record IrsValuationRequest(

        @NotBlank(message = "계약 ID는 필수입니다.")
        String contractId,

        @NotNull(message = "평가기준일은 필수입니다.")
        LocalDate valuationDate,

        /**
         * 현재 변동금리 (기준금리 + 스프레드, 소수 표현 — 예: 0.035 = 3.5%).
         *
         * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 시장변동금리)
         */
        @NotNull(message = "현재 변동금리는 필수입니다.")
        @PositiveOrZero(message = "현재 변동금리는 0 이상이어야 합니다.")
        BigDecimal currentFloatingRate,

        /**
         * 할인율 (무위험이자율, 소수 표현 — 예: 0.035).
         *
         * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 무위험이자율)
         */
        @NotNull(message = "할인율은 필수입니다.")
        @PositiveOrZero(message = "할인율은 0 이상이어야 합니다.")
        BigDecimal discountRate,

        /**
         * 명목금액 오버라이드 (nullable — null이면 계약에서 조회).
         * PoC에서 계약 정보 없이 평가를 테스트하는 경우 사용.
         */
        @DecimalMin(value = "0.01", message = "명목금액은 0보다 커야 합니다.")
        BigDecimal notionalAmount
) {}
