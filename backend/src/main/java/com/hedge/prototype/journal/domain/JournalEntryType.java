package com.hedge.prototype.journal.domain;

/**
 * 분개 유형.
 *
 * <p>K-IFRS 1109호에 따라 헤지회계에서 발생하는 분개의 유형을 분류합니다.
 * 공정가치 위험회피, 현금흐름 위험회피, OCI 재분류, 헤지 중단, 역분개를 포함합니다.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L 분리)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 조정)
 * @see K-IFRS 1109호 6.5.6  (위험회피 중단)
 */
public enum JournalEntryType {

    /**
     * 공정가치 위험회피 — 헤지수단 분개.
     * 헤지수단(파생상품) 공정가치 변동을 당기손익으로 인식.
     *
     * @see K-IFRS 1109호 6.5.8(가) (헤지수단 변동 P&L 인식)
     */
    FAIR_VALUE_HEDGE_INSTRUMENT("FVH-헤지수단"),

    /**
     * 공정가치 위험회피 — 피헤지항목 분개.
     * 피헤지항목의 헤지 위험분 공정가치 변동을 장부금액 조정 및 당기손익으로 인식.
     *
     * @see K-IFRS 1109호 6.5.8(나) (피헤지항목 장부금액 조정)
     */
    FAIR_VALUE_HEDGE_ITEM("FVH-피헤지항목"),

    /**
     * 현금흐름 위험회피 — 유효 부분 OCI 인식.
     * 헤지수단 공정가치 변동 중 유효 부분을 기타포괄손익으로 인식.
     *
     * @see K-IFRS 1109호 6.5.11(가) (유효 부분 OCI 인식)
     */
    CASH_FLOW_HEDGE_EFFECTIVE("CFH-유효분(OCI)"),

    /**
     * 현금흐름 위험회피 — 비유효 부분 당기손익 인식.
     * 헤지수단 공정가치 변동 중 비유효 부분을 즉시 당기손익으로 인식.
     *
     * @see K-IFRS 1109호 6.5.11(나) (비효과적 부분 즉시 P&L)
     */
    CASH_FLOW_HEDGE_INEFFECTIVE("CFH-비유효분(P&L)"),

    /**
     * OCI 재분류 — 현금흐름위험회피적립금을 당기손익으로 재분류.
     * 예상거래가 실현되거나 헤지가 중단될 때 적립금을 P&L로 재분류.
     *
     * @see K-IFRS 1109호 6.5.11(다) (재분류 조정)
     */
    OCI_RECLASSIFICATION("OCI→P&L 재분류"),

    /**
     * 헤지 중단 분개.
     * 위험회피관계 지정을 중단할 때 관련 잔액을 정리하는 분개.
     *
     * @see K-IFRS 1109호 6.5.6  (위험회피관계 중단)
     * @see K-IFRS 1109호 6.5.12 (현금흐름 헤지 중단 시 OCI 처리)
     */
    HEDGE_DISCONTINUATION("헤지 중단"),

    /**
     * IRS FVH 장부금액 조정 상각 분개.
     *
     * <p>K-IFRS 1109호 §6.5.9에 따라 공정가치 위험회피 중단 또는 만기 후
     * 잔여 HEDGED_ITEM_ADJ 누계액을 잔여 만기에 걸쳐 직선법으로 상각합니다.
     *
     * <p>상각 방향:
     * <ul>
     *   <li>HEDGED_ITEM_ADJ 차변 잔액(채권 장부 상향) 상각:
     *       차변 HEDGE_LOSS_PL / 대변 HEDGED_ITEM_ADJ</li>
     *   <li>HEDGED_ITEM_ADJ 대변 잔액(채권 장부 하향) 상각:
     *       차변 HEDGED_ITEM_ADJ / 대변 HEDGE_GAIN_PL</li>
     * </ul>
     *
     * @see K-IFRS 1109호 6.5.9 (공정가치헤지 중단 후 장부금액 조정 상각)
     */
    IRS_FVH_AMORTIZATION("IRS-FVH 장부조정상각"),

    /**
     * 역분개 (Reversing Entry).
     * 기존 분개를 취소하기 위한 역분개 (차대변 반전).
     * {@code JournalEntry#cancelsEntryId}로 원 분개를 참조.
     *
     * @see K-IFRS 1109호 (분개 불변성 원칙 — 역분개 패턴)
     */
    REVERSING_ENTRY("역분개");

    private final String koreanName;

    JournalEntryType(String koreanName) {
        this.koreanName = koreanName;
    }

    /** 보고서·화면 표시용 한글 분개유형명. */
    public String getKoreanName() {
        return koreanName;
    }
}
