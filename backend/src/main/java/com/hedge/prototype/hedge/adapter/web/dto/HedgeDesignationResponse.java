package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.domain.model.HedgedItem;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 헤지 지정 응답 DTO.
 *
 * <p>K-IFRS 1109호 6.4.1 적격요건 검증 결과와 지정 결과를 함께 반환합니다.
 * 검증 실패 시에도 상세 결과를 반환하여 프론트엔드가 원인을 표시할 수 있습니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 */
public record HedgeDesignationResponse(

        /** 위험회피관계 ID (검증 실패 시 null) */
        String hedgeRelationshipId,

        /** 헤지 지정일 */
        LocalDate designationDate,

        /** 위험회피 유형 */
        HedgeType hedgeType,

        /** 회피 대상 위험 */
        HedgedRisk hedgedRisk,

        /** 헤지비율 */
        BigDecimal hedgeRatio,

        /** 적격요건 검증 상태 (ELIGIBLE / INELIGIBLE) */
        String eligibilityStatus,

        /** K-IFRS 1109호 6.4.1 적격요건 상세 검증 결과 */
        EligibilityCheckResultResponse eligibilityCheckResult,

        /** 문서화 자동 생성 여부 */
        boolean documentationGenerated,

        /** 자동 생성 문서화 요약 */
        DocumentationSummary documentationSummary,

        /** 위험회피대상항목 정보 */
        HedgedItemResponse hedgedItem,

        /** 위험회피수단 정보 */
        HedgingInstrumentResponse hedgingInstrument,

        /** 에러 목록 (적격요건 미충족 시) */
        List<ErrorDetail> errors
) {
    /**
     * 에러 상세 정보.
     */
    public record ErrorDetail(
            String errorCode,
            String message,
            String kifrsReference
    ) {}
}
