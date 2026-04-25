package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.model.HedgedItem;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 위험회피대상항목 응답 DTO.
 *
 * @see K-IFRS 1109호 6.5.3~6.5.4 (대상항목 적격성)
 */
public record HedgedItemResponse(

        String hedgedItemId,
        HedgedItemType itemType,
        String currency,
        BigDecimal notionalAmount,
        BigDecimal notionalAmountKrw,
        LocalDate maturityDate,
        String counterpartyName,
        CreditRating counterpartyCreditRating,
        String description
) {
    public static HedgedItemResponse from(HedgedItem item) {
        return new HedgedItemResponse(
                item.getHedgedItemId(),
                item.getItemType(),
                item.getCurrency(),
                item.getNotionalAmount(),
                item.getNotionalAmountKrw(),
                item.getMaturityDate(),
                item.getCounterpartyName(),
                item.getCounterpartyCreditRating(),
                item.getDescription()
        );
    }
}
