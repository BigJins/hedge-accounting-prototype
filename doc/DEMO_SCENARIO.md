# 데모 시나리오 — CFH 메인 고정 (1개)

> **이 문서는 PoC 수주 시연을 위한 단일 CFH 시나리오입니다.**
> 모든 수치는 현재 `FxForwardPricing`·`LowerOfTestCalculator` 구현 기준의 실계산값입니다.
> 코드에 없는 기능은 이 문서에 포함하지 않습니다.
> IRS·CRS 평가는 시연 제외 분류로, 본 플로우에 섞지 않습니다 (질문 시 API 경로만 안내).

---

## 1분 발표용 요약 (5줄)

1. **문제** — 헤지회계는 월말마다 엑셀로 수일이 걸리고, 80~125% 자동 탈락·OCI 부호 오류 같은 감사 리스크가 크다.
2. **솔루션** — 헤지 지정 → 공정가치 평가 → 유효성 테스트 → 자동 분개까지 한 흐름으로 처리. 모든 분개에 K-IFRS 조항(`ifrsReference`)을 자동 인용.
3. **메인 시연** — USD 10,000,000 CFH 1개. 지정 즉시 6.4.1 적격요건 3조건 자동 검증 → IRP로 공정가치 `437,984,708.98원` → Dollar-offset `|−0.9820| ≈ 98.2% PASS` → CFH 유효분 분개 1건 자동 생성.
4. **차별점** — BC6.234(80~125% 자동 탈락 폐지) 준수, Lower of Test 부호 양방향(AVM-007) 처리, Append-Only 감사 추적, 자발적 중단 차단(6.5.6).
5. **다음 단계** — 본 개발에서 시장데이터 실시간 연동(Bloomberg/Refinitiv), 1107호 공식 공시 양식, ERP 원장 연계를 추가.

---

## 시나리오 개요

**제목**: USD/KRW 통화선도 현금흐름 위험회피(CFH) End-to-End 처리 — 메인 1종 고정

**주인공**: 가나금융투자(가상) 회계팀 박지영 과장

**배경**: 3개월 뒤 USD 10,000,000 예상거래(수출 대금 수취)에 대한 환율 변동 위험을 통화선도로 헤지.

**시연 범위 경계** (섞지 않음):
- FVH 분개·IRS/CRS 평가 — **시연 제외**. 질문 시 API 경로만 소개.
- 재조정(Rebalancing) — 직접 호출 API는 없음. WARNING + REBALANCE 판정 시 `HedgeRebalancingService`가 **자동 위임**되는 구조만 구두 설명.
- 예상거래 독립 취소 — 별도 API 없음(백로그). 헤지 중단 동반 경로로만 시연 가능.

---

## 시연 흐름 (4단계 + 후속 2절, 약 8분)

| 순서 | 단계 | API | 예상 소요 |
|---|---|---|---|
| 1 | 헤지 지정 (적격요건 자동 검증) | `POST /api/v1/hedge-relationships` | 1분 30초 |
| 2 | FX Forward 공정가치 평가 (IRP) | `POST /api/v1/valuations/fx-forward` | 1분 30초 |
| 3 | 유효성 테스트 (Dollar-offset PASS) | `POST /api/v1/effectiveness-tests` | 2분 |
| 4 | 분개 조회 + Excel 수출 | `GET /api/v1/journal-entries` · `/export/excel` | 1분 30초 |
| 5 | (구두) 중단·재조정 경로 설명 | `PATCH .../discontinue` / 자동 위임 | 1분 30초 |

---

## 고정 입력값

| 항목 | 값 | 근거 / 메모 |
|---|---|---|
| 헤지 유형 | `CASH_FLOW` | K-IFRS 6.5.2 |
| 회피 대상 위험 | `FOREIGN_CURRENCY` | `HedgedRisk` enum |
| 위험회피수단 유형 | `FX_FORWARD` | `InstrumentType` |
| 위험회피수단 계약 ID | `INS-2026-001` | 데모용 |
| 통화선도 방향 | `SELL_USD_BUY_KRW` | `FxForwardContract.position` 기본값 |
| 헤지대상 항목 유형 | `FORECAST_TRANSACTION` | `HedgedItemType` |
| 명목원금 | USD 10,000,000 | `notionalAmountUsd` |
| 계약 선물환율 | 1,300.00 KRW/USD | `contractForwardRate` |
| 평가시점 현물환율 | 1,350.00 KRW/USD | `spotRate` |
| KRW 이자율 | 3.5% (Act/365 Fixed) | `krwInterestRate` |
| USD 이자율 | 5.2% (Act/360) | `usdInterestRate` |
| 잔존일수 | 90일 | `valuationDate`와 `maturityDate`로 계산 |
| 헤지 지정일 = 평가기준일 | 2026-01-02 | `designationDate` = `valuationDate` |
| 만기일 | 2026-04-02 | `maturityDate` (지정일 + 90일) |
| 거래상대방 | Korea Development Bank | `counterpartyName` |
| 신용등급 | `AA` (투자등급) | `CreditRating.AA` |
| 헤지비율 | 1.00 (100%) | `hedgeRatio` — 참고 범위 80~125% 이내 |
| 위험관리 목적 | USD 수취 예상거래의 환율 변동 위험 회피 | `riskManagementObjective` |
| 위험회피 전략 | 통화선도를 통해 수취 시점 원화 환산액 안정화 | `hedgeStrategy` |

---

## 기대 계산 결과 (실계산 기반)

### IRP 이론 선물환율

```
F = S × (1 + r_KRW × T/365) / (1 + r_USD × T/360)
T/365 = 90/365 ≈ 0.246575 (scale=6 HALF_UP)
T/360 = 90/360 = 0.250000
F = 1,350 × (1 + 0.035 × 0.246575) / (1 + 0.052 × 0.250000)
  = 1,350 × 1.008630125 / 1.013000
  ≈ 1,344.1764 KRW/USD  (scale=4 HALF_UP)
```

### 현가계수

```
DF = 1 / (1 + 0.035 × 0.246575) = 1 / 1.008630125 ≈ 0.991445 (scale=6)
```

### 공정가치 (FxForwardPricing.calculateFairValue)

```
FV = (F − K) × 명목원금 × DF
   = (1,344.1764 − 1,300) × 10,000,000 × 0.991445
   = 44.1764 × 10,000,000 × 0.991445
   ≈ +437,984,708.98 원  (scale=2 HALF_UP)
```

- 공정가치 서열: **Level 2** (관측가능 투입변수 — 환율·금리). K-IFRS 1113호 문단 81.
- FX Forward의 코드 단위 테스트는 `contract=1380·spot=1350·days=92` 시나리오로 `-359,728,422원(±300,000)` 범위를 검증한다. 본 시연 수치(`contract=1300·spot=1350·days=90`)는 같은 공식에 다른 파라미터를 대입한 결과다.

### 유효성 테스트 (Dollar-offset, K-IFRS B6.4.12 / BC6.234)

```
instrumentFvChange       = +437,984,708.98
hedgedItemPvChange(기대) = -446,000,000     ← 피헤지 예상거래 PV 변동(반대방향)
effectivenessRatio       = 437,984,708.98 / (-446,000,000) ≈ -0.982028
|ratio|                  ≈ 98.20%  →  PASS (반대방향 + 참고범위 80~125% 이내)
testResult               = PASS
actionRequired           = NONE
```

### Lower of Test (K-IFRS 6.5.11(1)(2), BC6.280, AVM-007)

```
|instrument| = 437,984,708.98
|hedgedItem| =  446,000,000.00
effectiveMagnitude = min(|inst|, |hedged|) = 437,984,708.98
instrument sign    = +                     (AVM-007: 부호는 수단 방향을 따름)
effectiveAmount    = +437,984,708.98       → OCI 인식
ineffectiveAmount  = |inst| − magnitude = 0 → 즉시 P&L 인식 없음
```

### 자동 생성 분개 (CFH 유효분 단건)

| 구분 | 차변 계정 | 차변 금액 | 대변 계정 | 대변 금액 | K-IFRS |
|---|---|---|---|---|---|
| CFH 유효분 | 파생상품자산 (`DRV_ASSET`) | 437,984,708.98 | 현금흐름위험회피적립금 (`CFHR_OCI`) | 437,984,708.98 | 6.5.11(가) |
| CFH 비유효분 | — | 0 | — | 0 | 6.5.11(나) (해당 없음) |

차변 합계 = 대변 합계 = 437,984,708.98원.

---

## 단계별 시연 (입력 · 기대 · 분개 · 멘트 한 묶음)

각 단계는 `입력 → API 응답 기대값 → 분개 결과 → 설명 멘트` 순서로 한 블록에 묶었다.

### 단계 1 — 헤지 지정 (1분 30초)

**API**: `POST /api/v1/hedge-relationships`

**입력**
```json
{
  "hedgeType": "CASH_FLOW",
  "hedgedRisk": "FOREIGN_CURRENCY",
  "designationDate": "2026-01-02",
  "hedgePeriodEnd": "2026-04-02",
  "hedgeRatio": 1.00,
  "riskManagementObjective": "USD 수취 예상거래의 환율 변동 위험 회피",
  "hedgeStrategy": "통화선도를 통해 수취 시점 원화 환산액 안정화",
  "hedgedItem": {
    "itemType": "FORECAST_TRANSACTION",
    "currency": "USD",
    "notionalAmount": 10000000,
    "maturityDate": "2026-04-02",
    "counterpartyName": "해외 수입처",
    "counterpartyCreditRating": "AA"
  },
  "instrumentType": "FX_FORWARD",
  "instrumentContractId": "INS-2026-001"
}
```

**기대 응답 (HTTP 201)**
```json
{
  "hedgeRelationshipId": "HR-2026-001",
  "status": "DESIGNATED",
  "eligibilityStatus": "ELIGIBLE",
  "eligibilityCheckResult": {
    "condition1EconomicRelationship": { "result": true },
    "condition2CreditRisk":           { "result": true },
    "condition3HedgeRatio":            { "result": true }
  }
}
```

**분개**: 없음 — 지정 단계는 분개를 생성하지 않는다.

**설명 멘트**
- "지정 즉시 K-IFRS 6.4.1 세 가지 적격요건이 자동 점검됩니다. 수작업 리뷰 없이 ELIGIBLE 여부가 결정됩니다."
- "헤지비율 100%는 참고범위 80~125% 이내라 PASS — 범위 이탈도 자동 FAIL이 아니라 WARNING(재조정 신호)으로 처리됩니다. BC6.234 기준."
- "자발적 중단(`VOLUNTARY_DISCONTINUATION`)은 도메인에서 차단되고, 허용 사유 4종만 통과합니다 (뒤 단계 5에서 설명)."

---

### 단계 2 — FX Forward 공정가치 평가 (1분 30초)

**API**: `POST /api/v1/valuations/fx-forward`

**입력**
```json
{
  "contractId": "INS-2026-001",
  "notionalAmountUsd": 10000000,
  "contractForwardRate": 1300.00,
  "contractDate": "2026-01-02",
  "maturityDate": "2026-04-02",
  "hedgeDesignationDate": "2026-01-02",
  "counterpartyCreditRating": "AA",
  "valuationDate": "2026-01-02",
  "spotRate": 1350.00,
  "krwInterestRate": 0.035,
  "usdInterestRate": 0.052
}
```

**기대 응답 (HTTP 201)**
```json
{
  "contractId": "INS-2026-001",
  "valuationDate": "2026-01-02",
  "remainingDays": 90,
  "theoreticalForwardRate": "1344.1764",
  "discountFactor": "0.991445",
  "fairValue": "437984708.98",
  "fvLevel": "LEVEL_2",
  "ifrsReference": "K-IFRS 1113호 문단 72~90"
}
```

**분개**: 없음 — 평가 자체는 분개를 만들지 않는다. 평가 이력은 Append-Only로 저장(AVM-016).

**설명 멘트**
- "IRP 공식 하나로 이론 선물환율(1,344.1764)·현가계수(0.991445)·공정가치(≈ 4.38억원)가 모두 산출됩니다."
- "관측 가능한 투입변수(환율·금리)만 사용하므로 K-IFRS 1113호 Level 2로 자동 분류됩니다."
- "같은 계약을 다시 평가해도 이전 레코드를 덮어쓰지 않고 새 이력을 추가합니다 — 감사 추적을 위한 Append-Only 정책(AVM-016)."

---

### 단계 3 — 유효성 테스트 (2분)

**API**: `POST /api/v1/effectiveness-tests`

**입력**
```json
{
  "hedgeRelationshipId": "HR-2026-001",
  "testDate": "2026-01-02",
  "testType": "DOLLAR_OFFSET_PERIODIC",
  "hedgeType": "CASH_FLOW",
  "instrumentFvChange": 437984708.98,
  "hedgedItemPvChange": -446000000,
  "instrumentFvCumulative": 437984708.98,
  "hedgedItemPvCumulative": -446000000
}
```

**기대 응답 (HTTP 201)**
```json
{
  "effectivenessTestId": 1,
  "hedgeRelationshipId": "HR-2026-001",
  "testDate": "2026-01-02",
  "effectivenessRatio": "-0.982028",
  "testResult": "PASS",
  "effectiveAmount": "437984708.98",
  "ineffectiveAmount": "0.00",
  "ociReserveBalance": "437984708.98",
  "actionRequired": "NONE"
}
```

**비율 해석**
- `effectivenessRatio`가 음수인 이유: 헤지수단(+)과 헤지대상(−) 변동의 **부호가 반대** → 정상적인 상쇄 관계.
- 절대값 약 98.20% → 참고 범위 80~125% 이내 → **PASS**. `actionRequired = NONE`이므로 재조정·중단 불필요.

**분개**: 유효성 테스트가 PASS를 리턴하면 `EffectivenessTestCompletedEventHandler`가 CFH 분개 1건을 자동 생성한다 (단계 4에서 조회).

**설명 멘트**
- "BC6.234에 따라 80~125%는 자동 탈락 기준이 아닙니다. 이 범위 이탈은 WARNING(재조정 신호), 동방향(비율 양수)일 때만 FAIL입니다."
- "Lower of Test는 MIN(|수단|, |대상|)으로 OCI 한도를 정하고, **부호는 헤지수단 방향을 따릅니다** — 손실 케이스에서도 OCI가 반대방향으로 쌓이지 않도록 수정한 것이 AVM-007."

**경계 케이스 구두 설명** (시연에서 실행은 하지 않음)
- `|ratio|`가 80% 미만 또는 125% 초과 → `WARNING`+`actionRequired=REBALANCE` → **자동**으로 `HedgeRebalancingService`가 호출되어 (1) B6.5.8에 따른 비효과성 분개 선행 생성 → (2) 목표 헤지비율 재조정 → (3) 관계 상태 `REBALANCED`로 갱신. 직접 호출하는 API는 없다.
- 수단·대상이 동방향(비율 양수) → `FAIL`+`actionRequired=DISCONTINUE` → 헤지관계 중단 안내만 발생. 중단 자체는 단계 5의 `PATCH /discontinue`를 통해 수행한다.

---

### 단계 4 — 분개 조회 + Excel 수출 (1분 30초)

**API 1 (자동 생성된 분개 조회)**
```
GET /api/v1/journal-entries?hedgeRelationshipId=HR-2026-001
```

**기대 응답 (HTTP 200, 요약)**
```json
[
  {
    "journalEntryId": 1,
    "hedgeRelationshipId": "HR-2026-001",
    "entryDate": "2026-01-02",
    "hedgeType": "CASH_FLOW",
    "debitAccount":  "DRV_ASSET",
    "debitAmount":   437984708.98,
    "creditAccount": "CFHR_OCI",
    "creditAmount":  437984708.98,
    "ifrsReference": "K-IFRS 1109호 6.5.11(가)"
  }
]
```

**API 2 (Excel 수출)**
```
GET /api/v1/journal-entries/export/excel?hedgeRelationshipId=HR-2026-001
→ .xlsx 3시트: 분개장 / OCI 변동표 / 재분류 이력
```

**설명 멘트**
- "PASS이므로 EventHandler가 CFH 유효분 분개 1건을 단독으로 생성했습니다 — WARNING+REBALANCE 경로에서는 RebalancingService가 단독 책임을 집니다(AVM-015, 이중 분개 방지)."
- "각 분개에는 K-IFRS 조항이 `ifrsReference`로 자동 첨부되어 감사자가 근거를 즉시 확인할 수 있습니다."
- "Excel·PDF 수출은 `hedgeRelationshipId` 기준으로 지금 바로 다운로드 가능합니다 (AVM-017)."

---

### 단계 5 — (구두) 중단·재조정 경로 (1분 30초)

실제 호출은 시연 주 흐름에서 하지 않는다. 질문이 들어올 때 아래 내용을 **구두로 설명**한다.

#### 5-1. 재조정 (Rebalancing) — 자동 위임

- **트리거 조건**: 유효성 테스트 결과가 `WARNING` + `actionRequired=REBALANCE`.
- **직접 호출 API 없음**. 이벤트 `EffectivenessTestCompletedEvent` → `EffectivenessTestCompletedEventHandler`가 `HedgeRebalancingService.processRebalancing()`에 위임.
- 처리 순서 (K-IFRS 6.5.5 / B6.5.8):
  1. 재조정 전 비효과성 금액이 0이 아니면 분개 선행 생성 (B6.5.8).
  2. `calculateTargetHedgeRatio()`로 ±20% 클램프 + 절대 [10%, 300%] 범위 내 목표 비율 산출.
  3. `HedgeRelationship.rebalance()` 호출 후 `save()` — 상태 `REBALANCED`.
- "이중 분개 방지를 위해 EventHandler는 WARNING+REBALANCE 분기에서 분개를 만들지 않는다" — AVM-015의 핵심 약속.

#### 5-2. 중단 (Discontinuation)

**API**: `PATCH /api/v1/hedge-relationships/HR-2026-001/discontinue`

**CFH 중단 — 예상거래 여전히 발생 가능 (OCI 유보)**
```json
{
  "discontinuationDate": "2026-02-01",
  "reason": "HEDGE_INSTRUMENT_EXPIRED",
  "forecastTransactionExpected": true,
  "details": "수단 조기 소멸, 예상거래는 지속"
}
```
→ OCI 잔액 유지, 재분류 분개 **생성하지 않음**.

**CFH 중단 — 예상거래 발생 불가 확정 (OCI 즉시 P&L)**
```json
{
  "discontinuationDate": "2026-02-01",
  "reason": "HEDGE_ITEM_NO_LONGER_EXISTS",
  "forecastTransactionExpected": false,
  "currentOciBalance": 437984708.98,
  "plAccount": "FX_GAIN_PL",
  "details": "예상거래 취소 확정"
}
```
→ `차: CFHR_OCI 437,984,708.98 / 대: FX_GAIN_PL 437,984,708.98` 재분류 분개 자동 생성 (K-IFRS 6.5.12(2), AVM-014).

**자발적 중단 시도 차단**
```json
{ "reason": "VOLUNTARY_DISCONTINUATION", "forecastTransactionExpected": true }
```
→ `BusinessException HD_012` (K-IFRS 6.5.6 위반). 허용 사유 4종만 통과:
`RISK_MANAGEMENT_OBJECTIVE_CHANGED`, `HEDGE_INSTRUMENT_EXPIRED`,
`HEDGE_ITEM_NO_LONGER_EXISTS`, `ELIGIBILITY_CRITERIA_NOT_MET`.

**설명 멘트**
- "재조정은 이벤트로 자동 위임되므로 시연 중 수동 호출은 하지 않습니다. 요청이 있으면 유효성 테스트 입력을 WARNING 범위로 조정해 보여드릴 수 있습니다."
- "중단 API는 `reason`과 (CFH일 때) `forecastTransactionExpected` 2개 필드로 OCI 후속 처리 4가지 분기를 전부 커버합니다."
- "자발적 중단이 코드 레벨에서 거부되는 것은 감사 측면에서 가장 설득력 있는 포인트입니다."

---

## 시연 기준 수치 요약표

| 항목 | 고정값 | 산출 기준 |
|---|---|---|
| 명목금액 | USD 10,000,000 | 입력 |
| 계약 선물환율 | 1,300.00 | 입력 |
| 평가시점 현물환율 | 1,350.00 | 입력 |
| KRW 금리 | 3.5% (Act/365 Fixed) | 입력 |
| USD 금리 | 5.2% (Act/360) | 입력 |
| 잔존일수 | 90일 | `valuationDate` − `maturityDate` |
| 이론 선물환율 | 1,344.1764 | `FxForwardPricing.calculateForwardRate` |
| 현가계수 | 0.991445 | `FxForwardPricing.calculateDiscountFactor` |
| 공정가치 | 437,984,708.98원 | `FxForwardPricing.calculateFairValue` |
| 공정가치 서열 | Level 2 | K-IFRS 1113호 문단 72~90 |
| 유효성 비율 | ≈ −0.9820 (\|98.20%\|) | Dollar-offset |
| 유효성 판정 | PASS | K-IFRS B6.4.12 / BC6.234 |
| `effectiveAmount` | +437,984,708.98원 | Lower of Test × sign(수단) |
| `ineffectiveAmount` | 0원 | \|수단\| ≤ \|대상\| |
| OCI 적립 | +437,984,708.98원 | 6.5.11(가) |
| P&L 비유효분 | 0원 | 6.5.11(나) |
| 자동 분개 건수 | 1건 (CFH 유효분) | `EffectivenessTestCompletedEventHandler` |
| 헤지관계 ID | HR-2026-001 | 데모 픽스처 |
| 계약 ID | INS-2026-001 | 데모 픽스처 |

---

## 예상 질문 & 답변

### Q1 — 공정가치 437,984,708.98원이 정확히 어떻게 나오나요?
`(F − K) × 명목원금 × DF = (1344.1764 − 1300) × 10,000,000 × 0.991445 ≈ 437,984,708.98`. 할인계수를 곱하지 않으면 441,764,000 수준이지만, 코드는 IRP 현가까지 반영해 약 4백만원 낮게 산출합니다.

### Q2 — 98.2%는 어떻게 계산되나요?
Dollar-offset: `instrumentFvChange / hedgedItemPvChange = 437,984,708.98 / −446,000,000 ≈ −0.982028`. 절대값이 0.8~1.25 사이이고 부호가 반대 → PASS. 80~125% 이탈만으로 FAIL하지 않는 것이 BC6.234의 핵심입니다.

### Q3 — OCI와 P&L 분리가 어떻게 자동화되나요?
`LowerOfTestCalculator`가 `min(|수단|, |대상|) × sign(수단)`으로 유효분 부호를 만들고, 초과분을 비유효분(P&L)으로 분리합니다. 이번 시나리오는 \|수단\| < \|대상\| 이므로 전액 유효분, 비유효분 0원입니다.

### Q4 — 헤지 중단 시 OCI는요?
`PATCH /discontinue`에서 CFH이면 `forecastTransactionExpected` 필드가 필수입니다. `true`면 OCI 유보, `false`면 OCI 잔액이 즉시 P&L로 재분류됩니다. `VOLUNTARY_DISCONTINUATION`은 enum 레벨에서 차단됩니다.

### Q5 — 시장 데이터는요?
PoC에서는 요청 시 직접 입력합니다. Bloomberg/Refinitiv/한국자산평가 연동은 백로그입니다 (README 백로그 행 참조).

### Q6 — IRS·CRS도 보여주실 수 있나요?
코드·단위 테스트는 존재하지만 본 시연 범위 밖(시연 제외)입니다. API 경로(`POST /valuations/irs`, `/valuations/crs`)만 안내드립니다. 고객 요청 시 별도 세션으로 준비합니다.

### Q7 — 재조정은 어떻게 실행하나요?
직접 호출하는 API는 없습니다. 유효성 테스트가 WARNING+REBALANCE로 끝나면 이벤트 기반으로 `HedgeRebalancingService`가 자동 위임돼 비효과성 분개 선행 → 비율 재조정 → 저장까지 처리합니다.

### Q8 — 분개가 이중으로 생성되진 않나요?
AVM-015에서 해결된 이슈입니다. EventHandler는 PASS 분기에서만 분개를 만들고, WARNING+REBALANCE 분기에서는 RebalancingService가 단독으로 분개를 생성합니다.

---

## 시연 준비 체크리스트

### 기술 준비
- [ ] 백엔드 기동 (localhost:8090)
- [ ] PostgreSQL 연결 (localhost:5432), `ddl-auto: update`로 스키마 자동 생성 확인
- [ ] 4단계 API 응답을 사전에 1회 실행해 수치 확인 (`HR-2026-001`, `INS-2026-001` 초기화 후)
- [ ] Excel 수출 다운로드 정상 여부 (3시트)

### 리허설
- [ ] 리허설 1회 — 수치 검증 (공정가치 `437,984,708.98`, 유효성 `|0.982|`)
- [ ] 리허설 2회 — 나레이션 흐름 (7~8분 내 완주)
- [ ] 단계 5 구두 스크립트 암기 — 중단·재조정 플로우

### 백업
- [ ] 시스템 장애 대비 각 단계 응답 스크린샷 준비
- [ ] Q&A 8문항 숙지, "시연 제외" 질문이 들어와도 답변 통일

---

## 샘플 데이터 (개발·리허설용)

```json
{
  "scenario": "USD/KRW CFH — 메인 시연 고정값",
  "hedgeRelationship": {
    "hedgeRelationshipId": "HR-2026-001",
    "hedgeType": "CASH_FLOW",
    "hedgedRisk": "FOREIGN_CURRENCY",
    "designationDate": "2026-01-02",
    "hedgePeriodEnd": "2026-04-02",
    "hedgeRatio": 1.00,
    "counterpartyCreditRating": "AA"
  },
  "hedgingInstrument": {
    "contractId": "INS-2026-001",
    "instrumentType": "FX_FORWARD",
    "position": "SELL_USD_BUY_KRW",
    "notionalAmountUsd": 10000000,
    "contractForwardRate": 1300.00,
    "contractDate": "2026-01-02",
    "maturityDate": "2026-04-02"
  },
  "marketData": {
    "valuationDate": "2026-01-02",
    "spotRate": 1350.00,
    "krwInterestRate": 0.035,
    "usdInterestRate": 0.052,
    "remainingDays": 90
  },
  "expectedResults": {
    "theoreticalForwardRate": 1344.1764,
    "discountFactor": 0.991445,
    "fairValue": 437984708.98,
    "fvLevel": "LEVEL_2",
    "effectivenessRatio": -0.982028,
    "testResult": "PASS",
    "effectiveAmount": 437984708.98,
    "ineffectiveAmount": 0,
    "ociReserveBalance": 437984708.98,
    "actionRequired": "NONE"
  },
  "expectedJournalEntries": [
    {
      "type": "CFH_EFFECTIVE",
      "debitAccount": "DRV_ASSET",
      "debitAmount": 437984708.98,
      "creditAccount": "CFHR_OCI",
      "creditAmount": 437984708.98,
      "ifrsReference": "K-IFRS 1109호 6.5.11(가)"
    }
  ]
}
```

---

## 1분 발표용 요약 (5줄, 재인용)

1. **문제** — 헤지회계는 월말마다 엑셀로 수일이 걸리고, 80~125% 자동 탈락·OCI 부호 오류 같은 감사 리스크가 크다.
2. **솔루션** — 헤지 지정 → 공정가치 평가 → 유효성 테스트 → 자동 분개까지 한 흐름. 모든 분개에 K-IFRS 조항을 자동 인용.
3. **메인 시연** — USD 10M CFH 1개. 지정·평가·테스트·분개가 약 7분에 끝나고, 공정가치 437,984,708.98원 · 유효성 \|98.20%\| PASS · CFH 유효분 분개 1건 자동 생성.
4. **차별점** — BC6.234 준수(WARNING/FAIL 3단계), Lower of Test 부호 양방향(AVM-007), Append-Only 감사, 자발적 중단 차단(6.5.6).
5. **다음 단계** — 본 개발에서 시장데이터 실시간 연동, 1107호 공식 공시 양식, ERP 원장 연계. IRS·CRS와 예상거래 독립 취소는 별도 스프린트에서 시연.

---

## 참조 문서

- [프로젝트 개요](PROJECT_BRIEF.md)
- [회계 검증표 (ACCOUNTING_VALIDATION_MATRIX.md)](ACCOUNTING_VALIDATION_MATRIX.md) — AVM-001·005·007·010·013·014·015 근거
- [아키텍처 메모 (ARCHITECTURE_MEMO.md)](ARCHITECTURE_MEMO.md) — BC 경계·책임 분산 설명
- [요구사항 명세서](REQUIREMENTS.md)
