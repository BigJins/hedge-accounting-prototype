package com.hedge.prototype.effectiveness.adapter.web.dto;

import com.hedge.prototype.effectiveness.domain.EffectivenessTestType;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 유효성 테스트 실행 요청 DTO.
 *
 * <p>위험회피수단 및 피헤지항목의 당기 변동액을 입력받아
 * Dollar-offset 유효성 테스트를 수행합니다.
 *
 * <p>누적값은 서비스 계층에서 이전 이력을 조회하여 자동으로 계산됩니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 방법)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법)
 */
public record EffectivenessTestRequest(

        /**
         * 위험회피관계 ID (예: HR-2026-001).
         */
        @NotBlank(message = "위험회피관계 ID는 필수입니다.")
        String hedgeRelationshipId,

        /**
         * 유효성 평가 기준일 — 매 보고기간 말.
         *
         * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 평가)
         */
        @NotNull(message = "평가 기준일은 필수입니다.")
        LocalDate testDate,

        /**
         * Dollar-offset 테스트 방법 (기간별 / 누적).
         *
         * @see K-IFRS 1109호 B6.4.12
         */
        @NotNull(message = "테스트 방법은 필수입니다.")
        EffectivenessTestType testType,

        /**
         * 위험회피 유형 (공정가치 / 현금흐름).
         *
         * <p>비효과성 계산 방법을 결정합니다:
         * FAIR_VALUE — 수단·대상 변동 모두 P&L 인식 (K-IFRS 1109호 6.5.8)
         * CASH_FLOW  — Lower of Test로 OCI/P&L 분리 (K-IFRS 1109호 6.5.11)
         *
         * <p>프론트엔드가 명시적으로 전달하며, 자동 분개(Journal Entry) 등
         * 후속 처리에서도 동일하게 사용됩니다.
         *
         * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
         * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L 분리)
         */
        @NotNull(message = "위험회피 유형은 필수입니다.")
        HedgeType hedgeType,

        /**
         * 위험회피수단 공정가치 당기 변동액.
         * 음수 허용 (손실), 부호가 반대방향 헤지 효과를 나타냅니다.
         *
         * @see K-IFRS 1109호 6.5.8 (위험회피수단 공정가치 변동)
         */
        @NotNull(message = "위험회피수단 당기 변동은 필수입니다.")
        BigDecimal instrumentFvChange,

        /**
         * 피헤지항목 현재가치 당기 변동액.
         * 헤지 위험에 귀속되는 변동만 포함합니다.
         *
         * @see K-IFRS 1109호 6.5.8 (피헤지항목 공정가치 변동 — 헤지위험 귀속분)
         */
        @NotNull(message = "피헤지항목 당기 변동은 필수입니다.")
        BigDecimal hedgedItemPvChange,

        /**
         * 위험회피수단 유형 (선택).
         *
         * <p>FX_FORWARD: 통화선도 기반 유효성 테스트 (환율 변동 기준)
         * IRS: 금리스왑 기반 유효성 테스트 (금리 변동 기준)
         * CRS: 통화스왑 기반 유효성 테스트 (환율+금리 복합 기준)
         *
         * <p>null이면 FX_FORWARD 기본값으로 처리합니다.
         *
         * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법 — 모든 헤지수단 공통 적용)
         */
        InstrumentType instrumentType

) {}
