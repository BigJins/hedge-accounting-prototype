package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 위험회피관계 목록 조회 요약 응답 DTO.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 */
public record HedgeRelationshipSummaryResponse(

        String hedgeRelationshipId,
        HedgeType hedgeType,
        HedgedRisk hedgedRisk,
        LocalDate designationDate,
        LocalDate hedgePeriodEnd,
        BigDecimal hedgeRatio,
        HedgeStatus status,
        EligibilityStatus eligibilityStatus,
        String fxForwardContractId
) {}
