package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 위험회피수단(통화선도) 요약 응답 DTO.
 *
 * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별·문서화)
 */
public record HedgingInstrumentResponse(

        String contractId,
        BigDecimal contractForwardRate,
        LocalDate maturityDate,
        BigDecimal notionalAmountUsd,
        String counterpartyName,
        CreditRating counterpartyCreditRating
) {
    public static HedgingInstrumentResponse from(FxForwardContract contract) {
        return new HedgingInstrumentResponse(
                contract.getContractId(),
                contract.getContractForwardRate(),
                contract.getMaturityDate(),
                contract.getNotionalAmountUsd(),
                contract.getCounterpartyName(),
                contract.getCounterpartyCreditRating()
        );
    }
}
