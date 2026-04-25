package com.hedge.prototype.journal.adapter.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * IRS FVH 장부금액 조정 상각 요청 DTO.
 *
 * <p>K-IFRS 1109호 §6.5.9: 공정가치 위험회피 관계가 중단되거나 만기도래한 후,
 * HEDGED_ITEM_ADJ 누계 잔액을 잔여 만기에 걸쳐 당기손익으로 인식합니다.
 *
 * <p><b>사용 시점</b>:
 * <ul>
 *   <li>위험회피관계 중단 후 매 기간 말 — 잔여 잔액을 remainingPeriods로 나눠 단계적 상각</li>
 *   <li>위험회피수단(IRS) 만기 후 — 남은 채권 장부조정 잔액 일괄 또는 분할 상각</li>
 * </ul>
 *
 * <p><b>cumulativeAdjBalance 부호 규칙</b>:
 * <ul>
 *   <li>양수: HEDGED_ITEM_ADJ 차변 잔액 — 금리 하락 시 채권 상향조정 누계
 *       → 상각 분개: 차변 HEDGE_LOSS_PL / 대변 HEDGED_ITEM_ADJ</li>
 *   <li>음수: HEDGED_ITEM_ADJ 대변 잔액 — 금리 상승 시 채권 하향조정 누계
 *       → 상각 분개: 차변 HEDGED_ITEM_ADJ / 대변 HEDGE_GAIN_PL</li>
 * </ul>
 *
 * @see K-IFRS 1109호 §6.5.9 (공정가치헤지 중단 후 장부금액 조정 상각)
 * @see com.hedge.prototype.journal.domain.IrsFvhAmortizationJournalGenerator
 */
public record IrsFvhAmortizationRequest(

        /**
         * 위험회피관계 ID.
         *
         * @see K-IFRS 1109호 §6.4.1 (위험회피관계 지정)
         */
        @NotBlank(message = "위험회피관계 ID는 필수입니다.")
        String hedgeRelationshipId,

        /**
         * 상각 기준일 (기간 말).
         * 매 보고기간 말 또는 상각 이벤트 발생일.
         *
         * @see K-IFRS 1109호 §B6.4.12 (매 보고기간 말 평가)
         */
        @NotNull(message = "상각 기준일은 필수입니다.")
        LocalDate amortizationDate,

        /**
         * HEDGED_ITEM_ADJ 잔여 누계 잔액 (부호 포함, 0 불가).
         *
         * <p>양수 = HEDGED_ITEM_ADJ 차변 잔액 (채권 장부 상향조정 누계, 금리 하락 시 발생).
         * 음수 = HEDGED_ITEM_ADJ 대변 잔액 (채권 장부 하향조정 누계, 금리 상승 시 발생).
         *
         * <p>이 값은 직전 기간까지의 상각 후 남은 순 잔액이어야 합니다.
         * 전체 누계에서 이미 상각된 금액을 차감하여 계산합니다.
         *
         * @see K-IFRS 1109호 §6.5.8(나) (최초 인식 시 HEDGED_ITEM_ADJ 발생)
         * @see K-IFRS 1109호 §6.5.9     (상각 의무)
         */
        @NotNull(message = "HEDGED_ITEM_ADJ 누계 잔액은 필수입니다.")
        BigDecimal cumulativeAdjBalance,

        /**
         * 이번 기간 포함 잔여 상각 기간 수 (1 이상).
         *
         * <p>직선법 상각 계산:
         * {@code 기간 상각액 = |cumulativeAdjBalance| / remainingPeriods}
         *
         * <p>예시 — 12개월 잔여, 월별 상각 시 remainingPeriods=12.
         * 다음 달 요청 시에는 remainingPeriods=11과 갱신된 잔액으로 재호출합니다.
         *
         * @see K-IFRS 1109호 §6.5.9 (잔여 만기에 걸쳐 상각)
         */
        @NotNull(message = "잔여 상각 기간 수는 필수입니다.")
        @Min(value = 1, message = "잔여 상각 기간 수는 1 이상이어야 합니다.")
        Integer remainingPeriods

) {
}
