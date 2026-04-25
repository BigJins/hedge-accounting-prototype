package com.hedge.prototype.valuation.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CRS 공정가치 평가 요청 DTO.
 *
 * <p>K-IFRS 1113호 Level 2 평가에 필요한 시장 관측 데이터를 포함합니다.
 * 원화 다리와 외화 다리를 각각 할인하기 위해 별도 할인율을 입력받습니다.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑 공정가치 측정)
 */
public record CrsValuationRequest(

        @NotBlank(message = "계약 ID는 필수입니다.")
        String contractId,

        @NotNull(message = "평가기준일은 필수입니다.")
        LocalDate valuationDate,

        /**
         * 평가기준일 환율 (KRW/외화 — 예: KRW/USD = 1350.0).
         *
         * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 시장환율)
         */
        @NotNull(message = "환율은 필수입니다.")
        @PositiveOrZero(message = "환율은 0 이상이어야 합니다.")
        BigDecimal spotRate,

        /**
         * 원화 할인율 (무위험이자율, 소수 표현 — 예: 0.035 = 3.5%).
         *
         * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 원화 무위험이자율)
         */
        @NotNull(message = "원화 할인율은 필수입니다.")
        @PositiveOrZero(message = "원화 할인율은 0 이상이어야 합니다.")
        BigDecimal krwDiscountRate,

        /**
         * 외화 할인율 (소수 표현 — 예: 0.05 = 5.0%).
         *
         * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 외화 무위험이자율)
         */
        @NotNull(message = "외화 할인율은 필수입니다.")
        @PositiveOrZero(message = "외화 할인율은 0 이상이어야 합니다.")
        BigDecimal foreignDiscountRate
) {}
