package com.hedge.prototype.valuation.domain.common;

/**
 * K-IFRS 1113호 공정가치 수준(Level) 분류.
 *
 * <ul>
 *   <li>LEVEL_1: 활성시장에서 공시되는 가격 (상장주식, 국고채)</li>
 *   <li>LEVEL_2: 관측가능한 투입변수 사용 (장외파생: FX Forward, IRS, CRS)</li>
 *   <li>LEVEL_3: 관측불가능한 투입변수 사용 (복잡한 구조화상품)</li>
 * </ul>
 *
 * <p>통화선도(FX Forward)는 시장환율·이자율이 관측가능하므로 <b>LEVEL_2</b>로 분류합니다.
 *
 * @see K-IFRS 1113호 72~90항 (공정가치 수준별 분류)
 */
public enum FairValueLevel {
    LEVEL_1,
    LEVEL_2,
    LEVEL_3
}
