package com.hedge.prototype.valuation.adapter.web.dto;

import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 통화선도 계약 응답 DTO.
 *
 * <p>스택트레이스 외부 노출 금지 — 이 DTO에만 결과값 포함.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건 — 계약 정보 공시)
 * @see K-IFRS 1107호 (금융상품 공시)
 */
public record FxForwardContractResponse(
        String contractId,
        BigDecimal notionalAmountUsd,
        BigDecimal contractForwardRate,
        LocalDate contractDate,
        LocalDate maturityDate,
        LocalDate hedgeDesignationDate,
        String status,
        LocalDateTime createdAt
) {

    /**
     * 엔티티 → 응답 DTO 변환 팩토리 메서드.
     *
     * @param contract 통화선도 계약 엔티티
     * @return 계약 응답 DTO
     */
    public static FxForwardContractResponse fromEntity(FxForwardContract contract) {
        return new FxForwardContractResponse(
                contract.getContractId(),
                contract.getNotionalAmountUsd(),
                contract.getContractForwardRate(),
                contract.getContractDate(),
                contract.getMaturityDate(),
                contract.getHedgeDesignationDate(),
                contract.getStatus().name(),
                contract.getCreatedAt()
        );
    }
}
