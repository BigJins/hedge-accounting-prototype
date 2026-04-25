package com.hedge.prototype.hedge.domain.common;

/**
 * 신용등급 열거형.
 *
 * <p>K-IFRS 1109호 B6.4.7~B6.4.8에 따라 신용위험이 경제적 관계로 인한
 * 가치 변동보다 지배적인지 여부를 판단하는 데 사용됩니다.
 * 투자등급(BBB 이상)인 경우 신용위험이 지배적이지 않은 것으로 간주합니다.
 *
 * <p>PoC 구현: 신용등급을 ENUM으로 입력받아 투자등급 여부를 자동 판정합니다.
 * 실제 시스템에서는 외부 신용평가사 API(S&P, Moody's, Fitch)와 연동합니다.
 *
 * @see K-IFRS 1109호 6.4.1(3)(나) (신용위험 지배적 아님 조건)
 * @see K-IFRS 1109호 B6.4.7~B6.4.8 (신용위험 지배 판단 지침)
 */
public enum CreditRating {

    /** 최우량 신용등급 (S&P/Fitch 기준) */
    AAA,

    /** 우량 신용등급 */
    AA,

    /** 양호한 신용등급 */
    A,

    /** 투자등급 최하위 — BBB 이상이 투자등급(Investment Grade) */
    BBB,

    /** 투기등급 시작 — BB부터 비투자등급(Non-Investment Grade, Speculative) */
    BB,

    /** 투기등급 */
    B,

    /** 부실 우려 등급 */
    CCC,

    /** 부도/디폴트 */
    D;

    /**
     * 투자등급(Investment Grade) 여부 판정.
     *
     * <p>BBB 이상(AAA, AA, A, BBB)은 투자등급으로 분류되며,
     * K-IFRS 1109호 B6.4.7에 따라 이 경우 신용위험이 지배적이지 않다고 간주합니다.
     *
     * @return {@code true} — BBB 이상 투자등급인 경우
     * @see K-IFRS 1109호 B6.4.7 (신용위험의 효과가 가치 변동보다 지배적이지 않아야 함)
     */
    public boolean isInvestmentGrade() {
        return this == AAA || this == AA || this == A || this == BBB;
    }
}
