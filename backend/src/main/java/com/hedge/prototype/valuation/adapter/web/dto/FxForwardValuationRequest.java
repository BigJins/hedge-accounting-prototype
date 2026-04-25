package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 통화선도 공정가치 평가 요청 DTO.
 *
 * <p>계약이 없으면 신규 등록 후 평가를 수행합니다.
 * 계약이 이미 존재하면 market data만 사용하여 평가를 수행합니다.
 */
public record FxForwardValuationRequest(

        // ── 계약 정보 ──────────────────────────────────────────────────────

        @NotBlank(message = "계약번호는 필수입니다.")
        @Size(max = 50, message = "계약번호는 50자 이하여야 합니다.")
        String contractId,

        @NotNull(message = "명목원금은 필수입니다.")
        @DecimalMin(value = "0.01", message = "명목원금은 0보다 커야 합니다.")
        BigDecimal notionalAmountUsd,

        @NotNull(message = "계약 선물환율은 필수입니다.")
        @DecimalMin(value = "0.0001", message = "계약 선물환율은 0보다 커야 합니다.")
        BigDecimal contractForwardRate,

        @NotNull(message = "계약일은 필수입니다.")
        LocalDate contractDate,

        @NotNull(message = "만기일은 필수입니다.")
        LocalDate maturityDate,

        @NotNull(message = "헤지 지정일은 필수입니다.")
        LocalDate hedgeDesignationDate,

        /** 거래상대방 신용등급 — K-IFRS 1109호 B6.4.7 신용위험 지배 판단 */
        @NotNull(message = "거래상대방 신용등급은 필수입니다.")
        CreditRating counterpartyCreditRating,

        // ── 평가 시장 데이터 ────────────────────────────────────────────────

        @NotNull(message = "평가기준일은 필수입니다.")
        LocalDate valuationDate,

        @NotNull(message = "현물환율은 필수입니다.")
        @DecimalMin(value = "0.0001", message = "현물환율은 0보다 커야 합니다.")
        BigDecimal spotRate,

        @NotNull(message = "원화이자율은 필수입니다.")
        @DecimalMin(value = "0.0", message = "원화이자율은 0 이상이어야 합니다.")
        BigDecimal krwInterestRate,

        @NotNull(message = "달러이자율은 필수입니다.")
        @DecimalMin(value = "0.0", message = "달러이자율은 0 이상이어야 합니다.")
        BigDecimal usdInterestRate
) {}
