package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 위험회피대상항목 요청 DTO.
 *
 * @see K-IFRS 1109호 6.3.1 (위험회피대상항목 적격성)
 * @see K-IFRS 1109호 6.5.3 (공정가치 헤지 대상 항목 적격성)
 * @see K-IFRS 1109호 6.5.4 (현금흐름 헤지 대상 항목 적격성)
 */
public record HedgedItemRequest(

        @NotNull(message = "항목 유형은 필수입니다.")
        HedgedItemType itemType,

        @NotBlank(message = "통화는 필수입니다.")
        @Size(max = 10, message = "통화는 10자 이하여야 합니다.")
        String currency,

        @NotNull(message = "명목금액은 필수입니다.")
        @DecimalMin(value = "0.01", message = "명목금액은 0보다 커야 합니다.")
        BigDecimal notionalAmount,

        /** 원화 환산 명목금액 (nullable — 지정일 환율 기준, 시스템이 계산할 수 있음) */
        BigDecimal notionalAmountKrw,

        @NotNull(message = "만기일은 필수입니다.")
        LocalDate maturityDate,

        /** 거래상대방명 (nullable) */
        String counterpartyName,

        @NotNull(message = "거래상대방 신용등급은 필수입니다.")
        CreditRating counterpartyCreditRating,

        /** 금리 유형 (FIXED/FLOATING, nullable) */
        String interestRateType,

        /** 금리 (nullable) */
        BigDecimal interestRate,

        /** 항목 설명 */
        @Size(max = 500, message = "항목 설명은 500자 이하여야 합니다.")
        String description
) {}
