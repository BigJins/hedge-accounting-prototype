package com.hedge.prototype.effectiveness.adapter.web.dto;

import com.hedge.prototype.effectiveness.domain.ActionRequired;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestResult;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestType;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 유효성 테스트 결과 응답 DTO.
 *
 * <p>Dollar-offset 유효성 테스트 판정 결과와
 * 비효과적 부분(P&L) 및 유효 부분(OCI) 금액을 포함합니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 테스트)
 * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성 P&L)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L 분리)
 */
public record EffectivenessTestResponse(

        /** 유효성 테스트 레코드 ID */
        Long effectivenessTestId,

        /** 위험회피관계 ID */
        String hedgeRelationshipId,

        /** 평가 기준일 */
        LocalDate testDate,

        /** Dollar-offset 테스트 방법 */
        EffectivenessTestType testType,

        /** 위험회피 유형 */
        HedgeType hedgeType,

        /**
         * 위험회피수단 유형 (FX_FORWARD / IRS / CRS).
         * null이면 FX_FORWARD(1단계 하위호환).
         *
         * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
         */
        InstrumentType instrumentType,

        /** 위험회피수단 당기 변동액 */
        BigDecimal instrumentFvChange,

        /** 피헤지항목 당기 변동액 */
        BigDecimal hedgedItemPvChange,

        /** 위험회피수단 누적 변동액 */
        BigDecimal instrumentFvCumulative,

        /** 피헤지항목 누적 변동액 */
        BigDecimal hedgedItemPvCumulative,

        /**
         * Dollar-offset 유효성 비율 (부호 포함).
         * 음수 = 반대방향(정상), 양수 = 동방향(비정상).
         */
        BigDecimal effectivenessRatio,

        /** 판정 결과 (PASS / FAIL) */
        EffectivenessTestResult testResult,

        /**
         * 유효 부분.
         * 공정가치 헤지: 수단/대상 변동의 상계된 절대값 — <b>분석용, OCI 아님. P&L 인식.</b>
         *               K-IFRS 1109호 6.5.8에 따라 수단·대상 변동 모두 P&L에 인식됩니다.
         * 현금흐름 헤지: OCI로 인식할 금액 (Lower of Test).
         *
         * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 — P&L 인식)
         * @see K-IFRS 1109호 6.5.11⑴ (현금흐름 헤지 OCI 인식 한도)
         */
        BigDecimal effectiveAmount,

        /**
         * 비효과적 부분 — P&L 즉시 인식.
         * 공정가치 헤지: 수단 변동 + 대상 변동의 부호 있는 순합계.
         *               양수(+) = 차변 인식, 음수(-) = 대변 인식.
         * 현금흐름 헤지: 과대헤지 초과분 (항상 양수 또는 0).
         *
         * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성 P&L)
         * @see K-IFRS 1109호 6.5.11⑵ (현금흐름 헤지 비효과성 P&L)
         */
        BigDecimal ineffectiveAmount,

        /**
         * 당기 OCI 인식액 (현금흐름위험회피적립금 당기분).
         * 현금흐름 헤지에서만 유효. 공정가치 헤지 시 null.
         *
         * <p><b>주의: 이 값은 누계 잔액이 아닙니다.</b>
         * 당기 보고기간에 OCI로 인식하는 금액만 포함됩니다.
         *
         * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피적립금)
         */
        BigDecimal ociReserveBalance,

        /** 필요 조치 (NONE / REBALANCE / DISCONTINUE) */
        ActionRequired actionRequired,

        /** 실패 사유 (PASS 시 null) */
        String failureReason,

        /** 레코드 생성 시각 */
        LocalDateTime createdAt

) {

    /**
     * {@link EffectivenessTest} 엔티티로부터 응답 DTO를 생성합니다.
     *
     * @param entity 유효성 테스트 결과 엔티티
     * @return 응답 DTO
     */
    public static EffectivenessTestResponse fromEntity(EffectivenessTest entity) {
        return new EffectivenessTestResponse(
                entity.getEffectivenessTestId(),
                entity.getHedgeRelationshipId(),
                entity.getTestDate(),
                entity.getTestType(),
                entity.getHedgeType(),
                entity.getInstrumentType(),   // null이면 FX_FORWARD 하위호환
                entity.getInstrumentFvChange(),
                entity.getHedgedItemPvChange(),
                entity.getInstrumentFvCumulative(),
                entity.getHedgedItemPvCumulative(),
                entity.getEffectivenessRatio(),
                entity.getTestResult(),
                entity.getEffectiveAmount(),
                entity.getIneffectiveAmount(),
                entity.getOciReserveBalance(),
                entity.getActionRequired(),
                entity.getFailureReason(),
                entity.getCreatedAt()
        );
    }
}
