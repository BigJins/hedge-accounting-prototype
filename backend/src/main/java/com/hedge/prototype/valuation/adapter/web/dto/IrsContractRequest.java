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
 * IRS 계약 등록 요청 DTO.
 *
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 — IRS 계약 등록)
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 */
public record IrsContractRequest(

        @NotBlank(message = "계약번호는 필수입니다.")
        @Size(max = 50, message = "계약번호는 50자 이하여야 합니다.")
        String contractId,

        @NotNull(message = "명목금액은 필수입니다.")
        @DecimalMin(value = "0.01", message = "명목금액은 0보다 커야 합니다.")
        BigDecimal notionalAmount,

        @NotNull(message = "고정금리는 필수입니다.")
        @DecimalMin(value = "0.0", message = "고정금리는 0 이상이어야 합니다.")
        BigDecimal fixedRate,

        @NotBlank(message = "변동금리 기준지수는 필수입니다.")
        @Size(max = 30, message = "변동금리 기준지수는 30자 이하여야 합니다.")
        String floatingRateIndex,

        /** 변동금리 스프레드 (nullable — 없으면 기준지수 그대로 적용) */
        BigDecimal floatingSpread,

        @NotNull(message = "계약일은 필수입니다.")
        LocalDate contractDate,

        @NotNull(message = "만기일은 필수입니다.")
        LocalDate maturityDate,

        /** true=고정지급/변동수취(CFH), false=변동지급/고정수취(FVH) */
        @NotNull(message = "고정금리 지급 방향은 필수입니다.")
        Boolean payFixedReceiveFloating,

        @NotBlank(message = "결제 주기는 필수입니다.")
        String settlementFrequency,

        @NotNull(message = "일수 계산 관행은 필수입니다.")
        DayCountConvention dayCountConvention,

        /** 거래상대방명 (nullable) */
        String counterpartyName,

        /** 거래상대방 신용등급 (nullable) */
        CreditRating counterpartyCreditRating
) {}
