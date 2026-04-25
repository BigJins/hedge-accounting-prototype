package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 헤지 지정 요청 DTO.
 *
 * <p>K-IFRS 1109호 6.4.1(2)에 따라 위험회피관계 지정 시
 * 위험관리 목적, 전략, 위험회피대상·수단 정보를 포함해야 합니다.
 *
 * <p>instrumentType 필드를 통해 FX Forward / IRS / CRS 다형성을 지원합니다.
 * instrumentType이 null이면 하위 호환을 위해 FX_FORWARD로 간주합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — FX Forward / IRS / CRS)
 */
public record HedgeDesignationRequest(

        @NotNull(message = "위험회피 유형은 필수입니다.")
        HedgeType hedgeType,

        @NotNull(message = "회피 대상 위험은 필수입니다.")
        HedgedRisk hedgedRisk,

        @NotNull(message = "헤지 지정일은 필수입니다.")
        LocalDate designationDate,

        @NotNull(message = "위험회피기간 종료일은 필수입니다.")
        LocalDate hedgePeriodEnd,

        @NotNull(message = "헤지비율은 필수입니다.")
        @DecimalMin(value = "0.01", message = "헤지비율은 0보다 커야 합니다.")
        BigDecimal hedgeRatio,

        @NotBlank(message = "위험관리 목적은 필수입니다.")
        @Size(max = 1000, message = "위험관리 목적은 1000자 이하여야 합니다.")
        String riskManagementObjective,

        @NotBlank(message = "위험회피 전략은 필수입니다.")
        @Size(max = 1000, message = "위험회피 전략은 1000자 이하여야 합니다.")
        String hedgeStrategy,

        @NotNull(message = "헤지대상항목 정보는 필수입니다.")
        @Valid
        HedgedItemRequest hedgedItem,

        /**
         * 위험회피수단 유형 (FX_FORWARD / IRS / CRS).
         *
         * <p>null이면 FX_FORWARD로 간주합니다 (하위 호환).
         * instrumentContractId와 함께 사용합니다.
         *
         * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — 파생상품)
         */
        InstrumentType instrumentType,

        /**
         * 위험회피수단 계약 ID.
         *
         * <p>instrumentType에 따라 FxForwardContract / IrsContract / CrsContract의 ID.
         * instrumentType이 null인 경우 fxForwardContractId 필드와 동일하게 처리됩니다.
         *
         * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화)
         */
        @Size(max = 50, message = "계약 ID는 50자 이하여야 합니다.")
        String instrumentContractId,

        /**
         * FX Forward 계약 ID (하위 호환용).
         *
         * <p><b>deprecated</b>: instrumentContractId 사용을 권장합니다.
         * FX Forward 기존 클라이언트 하위 호환을 위해 유지됩니다.
         */
        @Size(max = 50, message = "계약 ID는 50자 이하여야 합니다.")
        String fxForwardContractId
) {}
