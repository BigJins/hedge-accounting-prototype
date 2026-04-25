package com.hedge.prototype.journal.domain;

/**
 * 헤지회계 분개에 사용되는 계정과목 코드.
 *
 * <p>K-IFRS 1109호 및 K-IFRS 1113호에 따라 공정가치 위험회피와
 * 현금흐름 위험회피에서 사용되는 16개 계정과목을 정의합니다.
 *
 * <p>각 계정은 코드명(영문), 한글명, 영문명, 계정유형({@link AccountType})을 갖습니다.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리 — 계정과목)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI 계정)
 * @see K-IFRS 1109호 6.5.12 (OCI 재분류 조정 계정)
 */
public enum AccountCode {

    /**
     * 파생상품자산.
     * 헤지수단(통화선도 등) 공정가치가 자산 위치일 때 사용.
     *
     * @see K-IFRS 1109호 6.5.8(가) (헤지수단 공정가치 변동 인식)
     * @see K-IFRS 1113호 (공정가치 측정 — Level 2)
     */
    DRV_ASSET("파생상품자산", "Derivative Asset", AccountType.ASSET),

    /**
     * 파생상품부채.
     * 헤지수단(통화선도 등) 공정가치가 부채 위치일 때 사용.
     *
     * @see K-IFRS 1109호 6.5.8(가) (헤지수단 공정가치 변동 인식)
     */
    DRV_LIAB("파생상품부채", "Derivative Liability", AccountType.LIABILITY),

    /**
     * 피헤지항목 장부가액 조정.
     * 공정가치 위험회피에서 피헤지항목 장부금액을 헤지 위험분만큼 조정.
     *
     * @see K-IFRS 1109호 6.5.8(나) (피헤지항목 장부금액 조정)
     */
    HEDGED_ITEM_ADJ("피헤지항목장부조정", "Hedged Item Book Value Adjustment", AccountType.ASSET_CONTRA),

    /**
     * 현금흐름위험회피적립금 (기타포괄손익).
     * 현금흐름 위험회피의 유효 부분을 OCI로 누적.
     *
     * @see K-IFRS 1109호 6.5.11(가) (유효 부분 OCI 인식)
     */
    CFHR_OCI("현금흐름위험회피적립금", "Cash Flow Hedge Reserve", AccountType.OCI),

    /**
     * 파생상품평가이익 (당기손익).
     * 헤지수단 공정가치 상승 시 인식하는 평가이익.
     *
     * @see K-IFRS 1109호 6.5.8(가) (헤지수단 변동 P&L 인식)
     */
    DRV_GAIN_PL("파생상품평가이익", "Gain on Derivative", AccountType.REVENUE),

    /**
     * 파생상품평가손실 (당기손익).
     * 헤지수단 공정가치 하락 시 인식하는 평가손실.
     *
     * @see K-IFRS 1109호 6.5.8(가) (헤지수단 변동 P&L 인식)
     */
    DRV_LOSS_PL("파생상품평가손실", "Loss on Derivative", AccountType.EXPENSE),

    /**
     * 위험회피이익 (당기손익).
     * 공정가치 위험회피에서 피헤지항목 공정가치 상승 시 인식.
     *
     * @see K-IFRS 1109호 6.5.8(나) (피헤지항목 변동 P&L 인식)
     */
    HEDGE_GAIN_PL("위험회피이익", "Hedging Gain", AccountType.REVENUE),

    /**
     * 위험회피손실 (당기손익).
     * 공정가치 위험회피에서 피헤지항목 공정가치 하락 시 인식.
     *
     * @see K-IFRS 1109호 6.5.8(나) (피헤지항목 변동 P&L 인식)
     */
    HEDGE_LOSS_PL("위험회피손실", "Hedging Loss", AccountType.EXPENSE),

    /**
     * 헤지비효과이익 (당기손익).
     * 현금흐름 위험회피의 비효과적 부분 이익.
     *
     * @see K-IFRS 1109호 6.5.11(나) (비효과적 부분 즉시 P&L)
     */
    INEFF_GAIN_PL("헤지비효과이익", "Hedge Ineffectiveness Gain", AccountType.REVENUE),

    /**
     * 헤지비효과손실 (당기손익).
     * 현금흐름 위험회피의 비효과적 부분 손실.
     *
     * @see K-IFRS 1109호 6.5.11(나) (비효과적 부분 즉시 P&L)
     */
    INEFF_LOSS_PL("헤지비효과손실", "Hedge Ineffectiveness Loss", AccountType.EXPENSE),

    /**
     * OCI 재분류손익 (당기손익).
     * 현금흐름위험회피적립금에서 당기손익으로 재분류 시 사용.
     *
     * @see K-IFRS 1109호 6.5.11(다) (OCI → P&L 재분류 조정)
     */
    RECLASSIFY_PL("OCI재분류손익", "OCI Reclassification Adjustment", AccountType.PL),

    /**
     * 이자수익.
     * 헤지 대상 금융상품의 이자 수익 인식.
     *
     * @see K-IFRS 1109호 (이자수익 — 유효이자율법)
     */
    INTEREST_INCOME("이자수익", "Interest Income", AccountType.REVENUE),

    /**
     * 이자비용.
     * 헤지 대상 금융부채의 이자 비용 인식.
     *
     * @see K-IFRS 1109호 (이자비용 — 유효이자율법)
     */
    INTEREST_EXPENSE("이자비용", "Interest Expense", AccountType.EXPENSE),

    /**
     * 외환이익 (당기손익).
     * 외화 환산 또는 결제 시 발생하는 외환 이익.
     *
     * @see K-IFRS 1109호 (통화위험 위험회피 — 외환손익)
     */
    FX_GAIN_PL("외환이익", "Foreign Exchange Gain", AccountType.REVENUE),

    /**
     * 외환손실 (당기손익).
     * 외화 환산 또는 결제 시 발생하는 외환 손실.
     *
     * @see K-IFRS 1109호 (통화위험 위험회피 — 외환손익)
     */
    FX_LOSS_PL("외환손실", "Foreign Exchange Loss", AccountType.EXPENSE),

    /**
     * 현금및현금성자산.
     * 헤지 결제 시 현금 수수 계정.
     */
    CASH("현금및현금성자산", "Cash and Cash Equivalents", AccountType.ASSET);

    // -----------------------------------------------------------------------
    // 속성
    // -----------------------------------------------------------------------

    /** 한글 계정명 */
    private final String koreanName;

    /** 영문 계정명 */
    private final String englishName;

    /** 계정 유형 */
    private final AccountType accountType;

    AccountCode(String koreanName, String englishName, AccountType accountType) {
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.accountType = accountType;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public AccountType getAccountType() {
        return accountType;
    }
}
