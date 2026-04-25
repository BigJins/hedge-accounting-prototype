package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.valuation.domain.common.FairValueLevel;
import com.hedge.prototype.valuation.domain.irs.IrsValuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IRS 공정가치 평가 결과 응답 DTO.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가손익)
 * @see K-IFRS 1113호 (공정가치 측정 Level 2)
 */
public record IrsValuationResponse(
        Long valuationId,
        String contractId,
        LocalDate valuationDate,
        BigDecimal fixedLegPv,
        BigDecimal floatingLegPv,
        BigDecimal fairValue,
        BigDecimal fairValueChange,
        BigDecimal discountRate,
        int remainingTermDays,
        FairValueLevel fairValueLevel,
        LocalDateTime createdAt
) {
    public static IrsValuationResponse from(IrsValuation valuation) {
        return new IrsValuationResponse(
                valuation.getValuationId(),
                valuation.getContractId(),
                valuation.getValuationDate(),
                valuation.getFixedLegPv(),
                valuation.getFloatingLegPv(),
                valuation.getFairValue(),
                valuation.getFairValueChange(),
                valuation.getDiscountRate(),
                valuation.getRemainingTermDays(),
                valuation.getFairValueLevel(),
                valuation.getCreatedAt()
        );
    }
}
