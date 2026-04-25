package com.hedge.prototype.valuation.application;

import com.hedge.prototype.valuation.domain.fxforward.FxForwardValuation;

/**
 * 공정가치 평가 실행 결과 래퍼.
 *
 * <p>{@code isNew}로 신규 평가(201)와 중복 평가(200)를 구분하여
 * API 클라이언트가 상태를 정확히 인지할 수 있도록 합니다.
 *
 * @param valuation 평가 결과 엔티티
 * @param isNew     신규 생성 여부 (true: 201, false: 200)
 */
public record ValuationResult(FxForwardValuation valuation, boolean isNew) {

    public static ValuationResult created(FxForwardValuation valuation) {
        return new ValuationResult(valuation, true);
    }

    public static ValuationResult existing(FxForwardValuation valuation) {
        return new ValuationResult(valuation, false);
    }
}
