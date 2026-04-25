package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.valuation.domain.fxforward.FxForwardValuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 통화선도 공정가치 평가 응답 DTO.
 *
 * <p>스택트레이스 외부 노출 금지 — 이 DTO에만 결과값 포함.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치변동 P&L 인식)
 * @see K-IFRS 1113호 (Level 2 공시)
 */
public record FxForwardValuationResponse(
        Long valuationId,
        String contractId,
        LocalDate valuationDate,
        BigDecimal spotRate,
        BigDecimal krwInterestRate,
        BigDecimal usdInterestRate,
        Integer remainingDays,
        BigDecimal currentForwardRate,
        BigDecimal fairValue,
        BigDecimal previousFairValue,
        BigDecimal fairValueChange,
        String fairValueLevel,
        LocalDateTime createdAt
) {

    /**
     * 엔티티 → 응답 DTO 변환 팩토리 메서드.
     */
    public static FxForwardValuationResponse fromEntity(FxForwardValuation valuation) {
        return new FxForwardValuationResponse(
                valuation.getValuationId(),
                valuation.getContract().getContractId(),
                valuation.getValuationDate(),
                valuation.getSpotRate(),
                valuation.getKrwInterestRate(),
                valuation.getUsdInterestRate(),
                valuation.getRemainingDays(),
                valuation.getCurrentForwardRate(),
                valuation.getFairValue(),
                valuation.getPreviousFairValue(),
                valuation.getFairValueChange(),
                valuation.getFairValueLevel().name(),
                valuation.getCreatedAt()
        );
    }
}
