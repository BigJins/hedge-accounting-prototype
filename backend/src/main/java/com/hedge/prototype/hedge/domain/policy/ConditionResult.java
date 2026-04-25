package com.hedge.prototype.hedge.domain.policy;

import static java.util.Objects.requireNonNull;

/**
 * 개별 적격요건 조건 검증 결과 값 객체 (Value Object).
 *
 * <p>K-IFRS 1109호 6.4.1의 3가지 조건(경제적 관계, 신용위험, 헤지비율) 각각에 대한
 * 검증 결과를 담는 불변(Immutable) 값 객체입니다.
 *
 * <p>팩토리 메서드 {@link #pass} 또는 {@link #fail}로만 생성합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 */
public final class ConditionResult {

    /** 검증 통과 여부 */
    private final boolean result;

    /** 검증 상세 내역 — 통과/실패 이유 및 계산 근거 */
    private final String details;

    /** 관련 K-IFRS 조항 참조 */
    private final String kifrsReference;

    private ConditionResult(boolean result, String details, String kifrsReference) {
        this.result = requireNonNull(result ? Boolean.TRUE : Boolean.FALSE, "result는 필수입니다.");
        this.details = requireNonNull(details, "details는 필수입니다.");
        this.kifrsReference = requireNonNull(kifrsReference, "kifrsReference는 필수입니다.");
    }

    /**
     * 조건 통과 결과 생성.
     *
     * @param details       통과 근거 상세 내역
     * @param kifrsReference 관련 K-IFRS 조항
     * @return PASS 조건 결과
     */
    public static ConditionResult pass(String details, String kifrsReference) {
        requireNonNull(details, "details는 필수입니다.");
        requireNonNull(kifrsReference, "kifrsReference는 필수입니다.");
        return new ConditionResult(true, details, kifrsReference);
    }

    /**
     * 조건 실패 결과 생성.
     *
     * @param details       실패 이유 상세 내역
     * @param kifrsReference 관련 K-IFRS 조항
     * @return FAIL 조건 결과
     */
    public static ConditionResult fail(String details, String kifrsReference) {
        requireNonNull(details, "details는 필수입니다.");
        requireNonNull(kifrsReference, "kifrsReference는 필수입니다.");
        return new ConditionResult(false, details, kifrsReference);
    }

    /** @return 조건 통과 여부 */
    public boolean isResult() {
        return result;
    }

    /** @return 검증 상세 내역 */
    public String getDetails() {
        return details;
    }

    /** @return 관련 K-IFRS 조항 참조 */
    public String getKifrsReference() {
        return kifrsReference;
    }
}
