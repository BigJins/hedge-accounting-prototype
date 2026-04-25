package com.hedge.prototype.effectiveness.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dollar-offset 방법 유효성 비율 계산기.
 *
 * <p>위험회피수단 변동액을 피헤지항목 변동액으로 나누어 유효성 비율을 계산합니다.
 * K-IFRS 1109호 BC6.234에 따라 80~125% 정량 기준은 공식 폐지되었습니다.
 * Dollar-offset 비율은 PASS/FAIL 단독 판정 기준이 아닌 "참고 지표"로만 사용합니다.
 *
 * <p><b>계산식</b>:
 * <pre>
 *   ratio = instrumentChange / hedgedItemChange
 *   반대방향 조건: ratio &lt; 0 (경제적 관계 성립의 기본 조건)
 *   참고 범위: |ratio| ∈ [0.80, 1.25] (K-IFRS 1039호 기준 — 참고용만)
 * </pre>
 *
 * <p><b>K-IFRS 1109호 변경 사항</b>:
 * <ul>
 *   <li>BC6.234: 80~125% 정량 기준 폐지 — 단독 합격/불합격 기준 사용 금지</li>
 *   <li>B6.4.12: 유효성 평가는 경제적 관계 존재 여부를 정성적/정량적으로 평가</li>
 *   <li>B6.4.13: Dollar-offset은 선택 가능한 방법의 하나 (유일한 기준 아님)</li>
 * </ul>
 *
 * <p><b>반환 체계</b>:
 * <ul>
 *   <li>{@link #evaluateReferenceGrade}: 비율 수치 + 참고 등급 반환 (정보 제공용)</li>
 *   <li>{@link #isOppositeDirection}: 동방향 여부 확인 (FAIL 판단의 유일한 근거)</li>
 *   <li>80~125% 이탈은 FAIL이 아닌 WARNING 처리 (재조정 검토 신호)</li>
 * </ul>
 *
 * <p><b>분모 근사 0 처리</b>:
 * 피헤지항목 변동이 임계값(0.0001) 이하인 경우 피헤지항목의 변동이 없는 것으로 판단하여 PASS 처리합니다.
 * 이 경우 수단에도 유의미한 변동이 없어 유효성을 훼손하지 않기 때문입니다.
 *
 * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 방법 — 정성적/정량적 병용)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법은 선택 가능 방법의 하나)
 */
public class DollarOffsetCalculator {

    /**
     * Dollar-offset 참고 범위 하한 (80%).
     *
     * <p><b>주의</b>: K-IFRS 1109호 BC6.234에 따라 이 값은 합격/불합격 기준이 아닌
     * 재조정 검토 신호(WARNING)를 위한 참고 범위 하한입니다.
     *
     * @see K-IFRS 1109호 BC6.234 (정량 기준 폐지)
     */
    static final BigDecimal REFERENCE_LOWER_BOUND = new BigDecimal("0.80");

    /**
     * Dollar-offset 참고 범위 상한 (125%).
     *
     * <p><b>주의</b>: K-IFRS 1109호 BC6.234에 따라 이 값은 합격/불합격 기준이 아닌
     * 재조정 검토 신호(WARNING)를 위한 참고 범위 상한입니다.
     *
     * @see K-IFRS 1109호 BC6.234 (정량 기준 폐지)
     */
    static final BigDecimal REFERENCE_UPPER_BOUND = new BigDecimal("1.25");

    /**
     * 피헤지항목 변동 임계값 — 이하인 경우 "변동 없음"으로 판단 (0.0001).
     *
     * <p>분모가 극소값일 때 나누기 연산의 폭발적 결과를 방지합니다.
     */
    private static final BigDecimal THRESHOLD = new BigDecimal("0.0001");

    /**
     * 유효성 비율 계산.
     *
     * <p>ratio = instrumentChange / hedgedItemChange
     * 비율이 음수이면 반대방향(정상적인 헤지), 양수이면 동방향(비효과적).
     *
     * @param instrumentChange  위험회피수단 공정가치 변동 (당기 또는 누적)
     * @param hedgedItemChange  피헤지항목 현재가치 변동 (당기 또는 누적)
     * @return 유효성 비율 (부호 포함, 소수점 6자리)
     * @throws ArithmeticException 분모가 임계값 이하인 경우 (호출 전 {@link #isHedgedItemChangeNegligible} 확인 필요)
     * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 비율 계산)
     */
    public static BigDecimal calculateRatio(BigDecimal instrumentChange, BigDecimal hedgedItemChange) {
        return instrumentChange.divide(hedgedItemChange, 6, RoundingMode.HALF_UP);
    }

    /**
     * 비율이 반대방향(음수)인지 확인.
     *
     * <p>경제적 관계의 기본 조건: 위험회피수단과 피헤지항목이 반대 방향으로 움직여야 합니다.
     * 비율이 양수 또는 0이면 동방향 — 경제적 관계 자체가 훼손된 상태입니다.
     * 이 경우에만 FAIL로 판정해야 합니다 (BC6.234, B6.4.12).
     *
     * @param ratio 유효성 비율
     * @return true이면 반대방향 (정상), false이면 동방향 (FAIL 신호)
     * @see K-IFRS 1109호 B6.4.12 (경제적 관계 존재 여부 판단)
     */
    public static boolean isOppositeDirection(BigDecimal ratio) {
        return ratio.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Dollar-offset 비율에 대한 참고 등급 평가.
     *
     * <p><b>K-IFRS 1109호 BC6.234에 따라 이 반환값은 단독 PASS/FAIL 판정에 사용하지 않습니다.</b>
     * 참고 등급은 정보 제공 목적으로만 사용하며, 실제 유효성 판정은
     * 정성적 평가(경제적 관계 존재 여부)와 병용해야 합니다.
     *
     * <p>참고 등급 분류:
     * <ul>
     *   <li>{@link EffectivenessTestResult#PASS}: 반대방향 + 참고 범위(80~125%) 이내</li>
     *   <li>{@link EffectivenessTestResult#WARNING}: 반대방향이나 참고 범위 이탈 → 재조정 검토</li>
     *   <li>{@link EffectivenessTestResult#FAIL}: 동방향(비율 양수) → 경제적 관계 훼손</li>
     * </ul>
     *
     * @param ratio 유효성 비율 ({@link #calculateRatio}의 결과)
     * @return 참고 등급 (정보 제공용 — 단독 판정 금지)
     * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
     * @see K-IFRS 1109호 B6.4.12 (유효성 평가 방법)
     */
    public static EffectivenessTestResult evaluateReferenceGrade(BigDecimal ratio) {
        // 동방향(비율이 양수 또는 0): 경제적 관계 훼손 → FAIL
        // K-IFRS 1109호 B6.4.12: 경제적 관계가 없으면 유효성 인정 불가
        if (!isOppositeDirection(ratio)) {
            return EffectivenessTestResult.FAIL;
        }

        // 반대방향이나 참고 범위(80~125%) 이탈: 재조정 신호 → WARNING
        // K-IFRS 1109호 BC6.234: 범위 이탈 자체는 FAIL 사유가 되지 않음
        // K-IFRS 1109호 6.5.5: 위험관리 목적이 유지되는 한 재조정 우선 검토
        BigDecimal absRatio = ratio.abs();
        boolean withinReferenceRange = absRatio.compareTo(REFERENCE_LOWER_BOUND) >= 0
                && absRatio.compareTo(REFERENCE_UPPER_BOUND) <= 0;

        return withinReferenceRange ? EffectivenessTestResult.PASS : EffectivenessTestResult.WARNING;
    }

    /**
     * 피헤지항목 변동이 임계값 이하인지 여부 확인.
     *
     * <p>분모가 근사 0이면 나누기가 불가능하므로, 이 경우
     * 피헤지항목의 변동이 없는 것으로 보아 PASS 처리합니다.
     * 이는 헤지 비효과성이 발생하지 않은 상황으로 해석됩니다.
     *
     * @param hedgedItemChange 피헤지항목 변동액
     * @return true이면 변동 없음으로 판단 (PASS 처리)
     */
    public static boolean isHedgedItemChangeNegligible(BigDecimal hedgedItemChange) {
        return hedgedItemChange.abs().compareTo(THRESHOLD) <= 0;
    }

    /**
     * 참고 등급 관련 메시지 생성.
     *
     * <p>정보 제공 목적의 메시지로, FAIL·WARNING·PASS 이유를 설명합니다.
     * K-IFRS 1109호 BC6.234에 따라 80~125% 이탈은 FAIL 사유가 아닌 재조정 검토 신호임을 명시합니다.
     *
     * @param ratio 계산된 유효성 비율
     * @return 참고 등급 설명 문자열
     * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
     */
    public static String buildReferenceGradeMessage(BigDecimal ratio) {
        if (!isOppositeDirection(ratio)) {
            return String.format(
                    "[FAIL] 동방향 움직임 — 비율 %.4f (양수). 위험회피수단과 피헤지항목이 같은 방향으로 변동하여 "
                    + "경제적 관계 훼손. K-IFRS 1109호 B6.4.12",
                    ratio);
        }
        BigDecimal absRatio = ratio.abs();
        BigDecimal absRatioPercent = absRatio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        if (absRatio.compareTo(REFERENCE_LOWER_BOUND) < 0) {
            return String.format(
                    "[WARNING] Dollar-offset 참고 비율 %.2f%% — 참고 하한(80%%) 미달. "
                    + "재조정(Rebalancing) 검토 권고. BC6.234: 이 범위 이탈은 FAIL 사유가 아님. K-IFRS 1109호 6.5.5",
                    absRatioPercent);
        }
        if (absRatio.compareTo(REFERENCE_UPPER_BOUND) > 0) {
            return String.format(
                    "[WARNING] Dollar-offset 참고 비율 %.2f%% — 참고 상한(125%%) 초과. "
                    + "재조정(Rebalancing) 검토 권고. BC6.234: 이 범위 이탈은 FAIL 사유가 아님. K-IFRS 1109호 6.5.5",
                    absRatioPercent);
        }
        return String.format(
                "[PASS] Dollar-offset 참고 비율 %.2f%% — 참고 범위(80%%~125%%) 이내. "
                + "K-IFRS 1109호 B6.4.13",
                absRatioPercent);
    }

    // 유틸리티 클래스 — 인스턴스화 금지
    private DollarOffsetCalculator() {}
}
