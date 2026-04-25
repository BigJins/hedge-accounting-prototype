package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.valuation.domain.common.DayCountConvention;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CRS 계약 등록 요청 DTO.
 *
 * @see K-IFRS 1109호 B6.4.9 (통화스왑의 헤지비율 산정)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 OCI 처리)
 */
public record CrsContractRequest(

        @NotBlank(message = "계약번호는 필수입니다.")
        @Size(max = 50, message = "계약번호는 50자 이하여야 합니다.")
        String contractId,

        @NotNull(message = "원화 원금은 필수입니다.")
        @DecimalMin(value = "0.01", message = "원화 원금은 0보다 커야 합니다.")
        BigDecimal notionalAmountKrw,

        @NotNull(message = "외화 원금은 필수입니다.")
        @DecimalMin(value = "0.000001", message = "외화 원금은 0보다 커야 합니다.")
        BigDecimal notionalAmountForeign,

        @NotBlank(message = "외화 통화 코드는 필수입니다.")
        @Size(max = 10, message = "외화 통화 코드는 10자 이하여야 합니다.")
        String foreignCurrency,

        @NotNull(message = "계약 환율은 필수입니다.")
        @DecimalMin(value = "0.0001", message = "계약 환율은 0보다 커야 합니다.")
        BigDecimal contractRate,

        /** 원화 고정금리 (nullable — 변동금리 원화 다리인 경우) */
        BigDecimal krwFixedRate,

        /** 원화 변동금리 기준지수 (nullable — 고정금리 원화 다리인 경우) */
        String krwFloatingIndex,

        /** 외화 고정금리 (nullable — 변동금리 외화 다리인 경우) */
        BigDecimal foreignFixedRate,

        /** 외화 변동금리 기준지수 (nullable — 고정금리 외화 다리인 경우) */
        String foreignFloatingIndex,

        @NotNull(message = "계약일은 필수입니다.")
        LocalDate contractDate,

        @NotNull(message = "만기일은 필수입니다.")
        LocalDate maturityDate,

        @NotBlank(message = "결제 주기는 필수입니다.")
        String settlementFrequency,

        @NotNull(message = "일수 계산 관행은 필수입니다.")
        DayCountConvention dayCountConvention,

        /** 거래상대방명 (nullable) */
        String counterpartyName,

        /** 거래상대방 신용등급 (nullable) */
        CreditRating counterpartyCreditRating
) {}
