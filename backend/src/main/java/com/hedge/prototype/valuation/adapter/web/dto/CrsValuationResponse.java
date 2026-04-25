package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.valuation.domain.crs.CrsValuation;
import com.hedge.prototype.valuation.domain.common.FairValueLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CRS 공정가치 평가 결과 응답 DTO.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1113호 (공정가치 측정 Level 2)
 */
public record CrsValuationResponse(
        Long valuationId,
        String contractId,
        LocalDate valuationDate,
        BigDecimal spotRate,
        BigDecimal krwLegPv,
        BigDecimal foreignLegPv,
        BigDecimal fairValue,
        BigDecimal fairValueChange,
        FairValueLevel fairValueLevel,
        LocalDateTime createdAt
) {
    public static CrsValuationResponse from(CrsValuation valuation) {
        return new CrsValuationResponse(
                valuation.getValuationId(),
                valuation.getContractId(),
                valuation.getValuationDate(),
                valuation.getSpotRate(),
                valuation.getKrwLegPv(),
                valuation.getForeignLegPv(),
                valuation.getFairValue(),
                valuation.getFairValueChange(),
                valuation.getFairValueLevel(),
                valuation.getCreatedAt()
        );
    }
}
