package com.hedge.prototype.valuation.adapter.web;

import com.hedge.prototype.valuation.adapter.web.dto.BondHedgedItemFvChangeRequest;
import com.hedge.prototype.valuation.adapter.web.dto.BondHedgedItemFvChangeResponse;
import com.hedge.prototype.valuation.domain.bond.BondHedgedItemPricing;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 채권 헤지귀속 공정가치 변동 계산 REST API 컨트롤러.
 *
 * <p>원화 고정금리채권(KRW_FIXED_BOND)의 금리위험 귀속 공정가치 변동을 계산합니다.
 * 이 엔드포인트는 <b>순수 계산 API</b>입니다 — DB 저장 없이 입력값을 받아 결과를 반환합니다.
 *
 * <h3>엔드포인트</h3>
 * <pre>POST /api/bond-hedged-item/calculate-fv-change</pre>
 *
 * <h3>재사용 경로</h3>
 * <ol>
 *   <li>FVH IRS 유효성 테스트: 응답의 {@code hedgeAttributedFvChange}를
 *       {@code EffectivenessTestRequest.hedgedItemPvChange}로 입력</li>
 *   <li>FVH IRS 분개 생성: 응답의 {@code hedgeAttributedFvChange}를
 *       {@code JournalEntryRequest.hedgedItemAdjustment}로 입력</li>
 * </ol>
 *
 * <!-- TODO(RAG 재검증): K-IFRS 1109호 6.5.8, B6.5.1~B6.5.5 교차 검증 필요 -->
 * @see <a href="#">K-IFRS 1109호 6.5.8 (공정가치 위험회피 — 피헤지항목 장부가치 조정)</a>
 * @see <a href="#">K-IFRS 1109호 B6.5.1 (헤지위험 귀속 공정가치 변동 측정)</a>
 * @see <a href="#">K-IFRS 1113호 72~90항 (Level 2 공정가치 측정)</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/bond-hedged-item")
public class BondHedgedItemController {

    /**
     * 채권 헤지귀속 공정가치 변동 계산.
     *
     * <p>K-IFRS 1109호 6.5.8 및 B6.5.1 기준으로 원화 고정금리채권의
     * 금리위험 귀속 공정가치 변동을 계산합니다.
     *
     * <p>계산 공식:
     * <pre>
     * 헤지귀속 FV 변동 = PV(현재 시장금리) - PV(지정일 시장금리)
     * 채권 PV(r) = Σ[coupon × df(r, t_i)] + notional × df(r, t_n)
     * df(r, t) = 1 / (1 + r × t / 365)  — KRW ACT/365
     * </pre>
     *
     * <p><b>PoC 단순화</b>: 플랫 커브, 신용위험 귀속분 미분리, 균등 기간 분할.
     *
     * @param request 계산 요청 (채권 정보 + 지정일/현재 시장금리)
     * @return 헤지귀속 공정가치 변동 계산 결과
     *
     * @see <a href="#">K-IFRS 1109호 6.5.8 (피헤지항목 공정가치 변동 — 당기손익 인식)</a>
     * @see <a href="#">K-IFRS 1109호 B6.5.1 (헤지귀속 공정가치 변동 측정 방법)</a>
     */
    @PostMapping("/calculate-fv-change")
    public BondHedgedItemFvChangeResponse calculateFvChange(
            @Valid @RequestBody BondHedgedItemFvChangeRequest request) {

        log.info("채권 헤지귀속 FV 변동 계산 요청: notional={}, couponRate={}, remainingDays={}, "
                + "designationRate={}, currentRate={}",
                request.notional(), request.couponRate(), request.remainingDays(),
                request.designationDiscountRate(), request.currentDiscountRate());

        // 지정일 시장금리 기준 PV
        BigDecimal pvAtDesignation = BondHedgedItemPricing.calculateBondPv(
                request.notional(),
                request.couponRate(),
                request.remainingDays(),
                request.settlementFrequency(),
                request.designationDiscountRate()
        );

        // 현재 시장금리 기준 PV
        BigDecimal pvAtCurrent = BondHedgedItemPricing.calculateBondPv(
                request.notional(),
                request.couponRate(),
                request.remainingDays(),
                request.settlementFrequency(),
                request.currentDiscountRate()
        );

        // 헤지귀속 FV 변동 = PV(현재) - PV(지정일)
        BigDecimal fvChange = BondHedgedItemPricing.calculateHedgeAttributedFvChange(
                request.notional(),
                request.couponRate(),
                request.remainingDays(),
                request.settlementFrequency(),
                request.designationDiscountRate(),
                request.currentDiscountRate()
        );

        log.info("채권 헤지귀속 FV 변동 계산 완료: pvAtDesignation={}, pvAtCurrent={}, fvChange={}",
                pvAtDesignation, pvAtCurrent, fvChange);

        return BondHedgedItemFvChangeResponse.of(request, pvAtDesignation, pvAtCurrent, fvChange);
    }
}
