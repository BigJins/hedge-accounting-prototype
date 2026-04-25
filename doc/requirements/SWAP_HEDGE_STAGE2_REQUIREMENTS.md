# SWAP 위험회피회계 2단계 요구사항 명세서 — IRS + CRS

**기능명**: `swap-hedge-stage2`
**작성자**: accounting-expert 에이전트
**작성일**: 2026-04-25
**대상 에이전트**: backend-developer / frontend-developer (planning 단계)
**상위 문서**: [`IRS_HEDGE_REQUIREMENTS.md`](./IRS_HEDGE_REQUIREMENTS.md)

---

## ⚠️ RAG 검색 현황

작성 시점(2026-04-25)에 RAG API(`http://localhost:8080/api/agent/context`, `/api/search/hybrid`) 가 **응답하지 않음**(HTTP 000). 따라서 본 문서는 다음 **로컬 근거**에 의존했습니다.

- `doc/requirements/IRS_HEDGE_REQUIREMENTS.md` — 1차 IRS 사양(2026-04-24, accounting-expert 작성)
- `doc/knowledge/kifrs_1039_hedge_accounting.md` — 1039 vs 1109 비교
- `doc/knowledge/code_review_1039_vs_1109.md` — BC6.234 폐지 등 핵심 차이
- `doc/accounting/공정가치평가_처리근거.md` — Level 1/2/3 분류 실무 근거(IRS·CRS 모두 Level 2 명시)

**RAG 복구 후 반드시 교차 검증 필요한 조항**:
- K-IFRS 1109호 6.5.8, 6.5.10, 6.5.11, 6.5.12, 6.5.13, 6.5.14, 6.6.1~6.6.4(그룹 헤지), 6.8.1~6.8.24(IBOR 개혁)
- K-IFRS 1109호 B6.4.7~B6.4.13, B6.5.1~B6.5.21
- K-IFRS 1109호 BC6.234 (80~125% 정량기준 폐지)
- K-IFRS 1113호 72~90항 (Level 2), 1113호 48항(CVA·DVA), 61~66항(가격결정 기법)

---

## 1. 기능 개요

PoC 1단계(FX Forward 단일 엔진) 위에 **IRS / CRS 2단계 엔진**을 올린다. 본 문서는 회계사 관점에서 다음을 확정한다:
1. IRS·CRS 가 어떤 헤지 유형 조합으로 적격한가.
2. 평가는 K-IFRS 1113호 어느 Level 에 속하는가.
3. Dollar-offset 방식(Periodic / Cumulative) 중 PoC 가 허용·시연할 범위.
4. 각 항목이 **이번에 구현 / 문서로만 선언 / 시연 제외** 중 어디에 속하는가.

> **80~125% 표현 원칙**: K-IFRS 1109호 BC6.234에 따라 80~125% 비율은 **합격선이 아니라 참고 범위**다. 본 문서 어디에서도 "PASS 자동 인정 기준" 으로 쓰지 않고, "재조정 검토 신호" 로만 표현한다. FAIL 판정은 동방향(경제적 관계 훼손) 때만 적용한다.

---

## 2. K-IFRS 근거 매핑 (요약)

| 조항 | 내용 | 본 문서 적용 |
|---|---|---|
| 1109호 6.4.1 | 헤지 지정 3 적격요건 | IRS·CRS 공통 — 1단계 엔진(`HedgeRelationship.checkHedgeRatio`) 그대로 |
| 1109호 6.5.2 | 위험회피관계 3종 (FVH/CFH/NIH) | IRS·CRS 모두 FVH/CFH 만 지원, NIH(해외사업장순투자) 제외 |
| 1109호 6.5.8 | FVH 회계처리 — 수단·대상 P&L | IRS FVH 분개 |
| 1109호 6.5.9 | 피헤지항목 장부가치 조정 | IRS FVH 채권 장부조정 |
| 1109호 6.5.10 | FVH 중단 시 장부가치 조정 상각 | 검토 필요 — 본 PoC 미구현 |
| 1109호 6.5.11 | CFH OCI/P&L 분리 (Lower of Test) | IRS CFH · CRS CFH 공통 |
| 1109호 6.5.12 | OCI → P&L 재분류 | CFH 이자 결제 시점 재분류 (검토 필요) |
| 1109호 6.5.13 | 예상거래 미발생 시 OCI 처리 | 1단계 코드 재사용 |
| 1109호 6.5.14 | CFH 헤지대상 조기상환 | 1단계 `discontinue` + `forecastTransactionExpected=false` 재사용 |
| 1109호 6.6.1~6.6.4 | 그룹·항목 일부 헤지 | **시연 제외** — 본 PoC 범위 외 |
| 1109호 6.8.1~6.8.24 | IBOR 개혁 | **문서 선언만** — 단순 필드 변경 수준만 허용 |
| 1109호 B6.4.12 | Dollar-offset 매 보고기간 말 평가 | Periodic / Cumulative 모두 코드 존재 |
| 1109호 B6.4.13 | Dollar-offset 방법 허용 | 본 문서 §5 |
| 1109호 BC6.234 | 80~125% 정량기준 폐지 | 본 문서 어디에서도 합격선 아님 |
| 1109호 B6.5.1~B6.5.5 | 헤지귀속 공정가치 변동 분리 | IRS FVH 채권 헤지귀속분 (검토 필요) |
| 1109호 B6.5.7~B6.5.21 | 재조정 절차 | 1단계 `HedgeRebalancingService` 재사용 |
| 1113호 48항 | CVA·DVA 비수행위험 | 필드만 자리잡기 (PoC 0) |
| 1113호 61~66항 | 시장참여자 가격결정 기법 | IRS·CRS 평가 근거 |
| 1113호 72~90항 | 공정가치 Level 분류 | IRS·CRS = **Level 2** |
| 1107호 22A~24G | 헤지회계 공시 | **시연 제외** — 본 PoC 범위 외 |

---

## 3. IRS — Fair Value Hedge (FVH)

### 3.1 적격 조합

| 항목 | 값 | 근거 |
|---|---|---|
| `itemType` | `KRW_FIXED_BOND` | K-IFRS 1109호 6.3.1, 6.3.7 |
| `hedgedRisk` | `INTEREST_RATE` | 6.3.7 (위험 구성요소 단독 지정) |
| `hedgeType` | `FAIR_VALUE` | 6.5.2 |
| `instrumentType` | `IRS` | 6.2.1 |
| IRS 방향 | **Pay Floating / Receive Fixed** (`payFixedReceiveFloating = false`) | 6.5.8 |
| 통화 | KRW 단일 | — |

**적용 시나리오**: 시장금리 상승 → 보유 고정금리채권 공정가치 하락 + IRS 공정가치 상승 → 상쇄.

### 3.2 평가 Level

**K-IFRS 1113호 Level 2**. 근거:
- IRS 는 OTC 파생, 활성시장 공시가격 없음 → Level 1 불가.
- 투입변수(시장 지표금리 커브, 할인계수) 가 모두 관측가능 → Level 3 아님.
- `doc/accounting/공정가치평가_처리근거.md:215` 에 IRS·CRS 모두 Level 2 명시.

### 3.3 분개 (6.5.8 / 6.5.9)

평가일 분개 — 금리 상승, IRS 평가이익 시:
```
Dr. 파생상품자산(IRS)               ×××
  Cr. 파생상품평가이익 [P&L]                    ×××

Dr. 공정가치위험회피손실 [P&L]      ×××
  Cr. 위험회피적용채권 장부조정              ×××
```

이자 결제 분개(반기/분기) — 별도. **본 PoC 에서는 이자 결제 분개를 자동화하지 않음**(검토 필요).

### 3.4 유효성 평가

- Dollar-offset 비율 = `instrumentFvChange / hedgedItemFvChange`(헤지귀속분).
- 비효과성 = 수단 변동 + 대상 변동(헤지귀속분) 의 **순합** — 1단계 `EffectivenessTestService.calculateFairValueIneffectiveness` 그대로 재사용.
- BC6.234 — 80~125% 는 합격선 아님, 참고 범위.

---

## 4. IRS — Cash Flow Hedge (CFH)

### 4.1 적격 조합

| 항목 | 값 | 근거 |
|---|---|---|
| `itemType` | `KRW_FLOATING_BOND` | 6.3.1, 6.3.7 |
| `hedgedRisk` | `INTEREST_RATE` | 6.3.7 |
| `hedgeType` | `CASH_FLOW` | 6.5.2 |
| `instrumentType` | `IRS` | 6.2.1 |
| IRS 방향 | **Pay Fixed / Receive Floating** (`payFixedReceiveFloating = true`) | 6.5.11 |
| 통화 | KRW 단일 | — |

**적용 시나리오**: 변동금리부채(또는 변동금리자산) 의 미래 이자 현금흐름 변동성 → 고정 IRS 로 현금흐름 고정.

### 4.2 분개 (6.5.11 / 6.5.12)

평가일 분개:
```
(유효 부분 — Lower of Test 한도)
Dr. 파생상품자산(IRS)               ×××
  Cr. 현금흐름위험회피적립금 [OCI]              ×××

(비효과 부분, 있는 경우)
Dr. 파생상품자산(IRS)               ×××
  Cr. 파생상품평가이익 [P&L]                    ×××
```

이자 결제일 OCI 재분류 — K-IFRS 1109호 **6.5.12** ("헤지된 현금흐름이 P&L 에 영향을 미칠 때"):
```
Dr. 현금흐름위험회피적립금 [OCI]    ×××
  Cr. 이자비용 [P&L]                            ×××
```

> **검토 필요**: 본 PoC 의 1단계 엔진은 OCI 재분류 트리거를 "헤지 중단 + `forecastTransactionExpected=false`" 단일 경로로만 처리한다. **이자 결제 이벤트 기반 자동 재분류**는 미구현이며 2단계 작업 항목이다(§7 표 참조).

### 4.3 유효성 평가 — Lower of Test 부호 양방향(AVM-007 적용)

1단계에서 이미 해결된 `LowerOfTestCalculator.calculateSignedEffectivePortion` / `calculateSignedIneffectivePortion` 가 IRS CFH 에도 그대로 적용된다. 손실 누적 시 OCI 가 음수로도 형성될 수 있음(K-IFRS 1109호 6.5.11(4)(다) — "현금흐름위험회피적립금이 차손" 명시 조항).

---

## 5. CRS — FVH/CFH 가능 범위

### 5.1 일반 원칙

CRS(Cross-Currency Swap) 는 **두 다리가 서로 다른 통화** 인 스왑이며, 환위험 + 금리위험이 결합되어 있다. K-IFRS 1109호 **6.3.7** 에 따라 위험 구성요소 단독 지정이 가능하지만, CRS 는 실무상 **결합 위험 통합 헤지** 가 일반적이다.

본 PoC 범위에서 정의하는 CRS 적격 조합:

| # | itemType | hedgedRisk | hedgeType | CRS 방향 | 분류 |
|---|---|---|---|---|---|
| C1 | `FOREIGN_BOND` (USD 고정금리채권) | `FOREIGN_CURRENCY` (단독 또는 결합) | `FAIR_VALUE` | Pay USD Fixed / Receive KRW Fixed | **이번에 구현** (단순화 가정) |
| C2 | `FOREIGN_BOND` 변동금리(USD) | `FOREIGN_CURRENCY` | `CASH_FLOW` | Pay USD Floating / Receive KRW Fixed | **이번에 구현** |
| C3 | 외화 차입금(USD 변동/고정) | `FOREIGN_CURRENCY` | `CASH_FLOW` | Pay KRW Fixed / Receive USD Floating | **문서 선언만** — 차입금 도메인 미구현 |
| C4 | USD 예상거래(미래 USD 수취·지급) | `FOREIGN_CURRENCY` | `CASH_FLOW` | 통화·금리 결합 | **시연 제외** — 1단계 FX Forward 와 시연 충돌 |

**CRS FVH 단독(금리만)** 지정은 환위험을 분리할 수 없는 결합 구조 때문에 **검토 필요**(엄밀한 헤지 귀속분 분리 산식 미확정).

### 5.2 평가 Level

**K-IFRS 1113호 Level 2**. 근거 동일 — 환율·양 통화 커브 모두 관측 가능, OTC 이므로 Level 1 아님, 표준 시장 데이터로 산정 가능하므로 Level 3 아님.

CRS 평가는 IRS 와 비교해 다음이 추가됨:
- **이중 커브 할인** — KRW 다리는 KRW 커브, USD 다리는 USD 커브로 각각 PV 산출.
- **현물환율** — USD 다리 PV 를 KRW 로 환산.
- **CSA(Credit Support Annex) 담보 조건** — 시장 표준은 OIS 할인이지만 PoC 는 단순 spot rate + flat curve 가정(검토 필요).

### 5.3 분개

CRS FVH 분개는 IRS FVH 와 동일 구조 + 환산 단계. CRS CFH 분개는 IRS CFH + OCI 재분류 시 환율 변동 부분이 추가로 OCI 에 잔존하는 사례가 있어 **검토 필요**(K-IFRS 1109호 6.5.13 적용 여부).

---

## 6. Dollar-offset Periodic vs Cumulative — PoC 허용 범위

### 6.1 K-IFRS 근거

K-IFRS 1109호 **B6.4.12**: 매 보고기간 말 유효성 평가 의무.
K-IFRS 1109호 **B6.4.13**: Dollar-offset 방법 허용. **Periodic 과 Cumulative 모두 허용**되며, 기업이 **일관 적용**하면 된다.

### 6.2 차이

| 방식 | 분자/분모 | 특성 | 실무 빈도 |
|---|---|---|---|
| **Periodic** (`DOLLAR_OFFSET_PERIODIC`) | 당기 변동 | 기간별 변동성 큼, 단기 잡음에 민감 | 보고기간이 짧을 때 |
| **Cumulative** (`DOLLAR_OFFSET_CUMULATIVE`) | 지정일 이후 누적 변동 | 안정적, 누적 평가에 적합 | **실무 표준** |

### 6.3 본 PoC 결정

- **양쪽 모두 허용** — 1단계 `EffectivenessTestType` enum 에 두 값이 이미 정의되어 있고, `EffectivenessTestService.resolveReferenceValue` 가 이미 분기 처리 중. 추가 코드 변경 없이 IRS·CRS 에 동일 적용 가능.
- **시연 기본값**: `DOLLAR_OFFSET_PERIODIC` (현재 데모 시나리오에 고정됨). 질문 시 "Cumulative 로 바꾸면 누적 기준 비율이 산출된다"는 설명 제공.
- **일관성 규칙**: 같은 헤지관계에 대해 Periodic / Cumulative 를 섞지 않을 것을 권고(B6.4.13). 단, 본 PoC 에서는 시스템 차원 강제 차단은 **검토 필요** — 현재는 사용자 책임.

---

## 7. 구현 범위 분리 — 이번 구현 / 문서 선언 / 시연 제외

| 항목 | K-IFRS 근거 | 분류 | 메모 |
|---|---|---|---|
| **IRS FVH** 헤지 지정 + 적격 조합 검증 (KRW_FIXED_BOND + INTEREST_RATE + FAIR_VALUE) | 6.4.1 / 6.5.2 / 6.3.7 | **이번에 구현** | 1단계 `HedgeRelationship.checkHedgeRatio` + 조합표(`HedgedItemType.isHedgeTypeAllowed`) 로 충분 |
| **IRS FVH** 평가 (`IrsPricing` Pay Fix·Receive Fix 분기) | 1113호 72~90항, 6.5.8 | **이번에 구현** | 1단계 `IrsPricing` 클래스 이미 존재(플랫커브). 컨트롤러·DTO 노출만 필요 |
| **IRS FVH** 채권 장부가치 조정 분개 자동화 | 6.5.8 / 6.5.9 | **이번에 구현** | `FairValueHedgeJournalGenerator` 에 IRS 분기 추가 (수단 + 대상 2건) |
| **IRS FVH** 채권 헤지귀속 FV 변동 별도 산정 API | 6.5.8 / B6.5.1~B6.5.5 | **검토 필요** | 채권 PV 모듈 미구현. 신용위험 분리 산식 미확정. PoC 는 "프런트가 직접 입력" 단순화 |
| **IRS CFH** 헤지 지정 + 적격 조합 (KRW_FLOATING_BOND + INTEREST_RATE + CASH_FLOW) | 6.4.1 / 6.5.2 / 6.3.7 | **이번에 구현** | 조합표만 추가 |
| **IRS CFH** 평가 + 유효분 OCI 분개 | 1113호 72~90항, 6.5.11 | **이번에 구현** | 1단계 `CashFlowHedgeJournalGenerator` + `LowerOfTestCalculator` 그대로 재사용 |
| **IRS CFH** 이자 결제 OCI 재분류 자동화 | 6.5.12 | **검토 필요** | 결제 이벤트 입력 API + 재분류 트리거 미구현. 1단계는 중단 경로 OCI 재분류만 자동화 |
| **IRS** 이자 결제 분개(고정 수취 / 변동 지급) 자동 생성 | 6.5.8 (이자수익·이자비용) | **검토 필요** | 결제일 이벤트 모델 미정. 본 PoC 는 평가 분개만 자동화 |
| **IRS** 만기 검증(`validateForHedgeDesignation` IRS 버전) | 6.4.1(2) | **이번에 구현** | 1단계 FX Forward 검증 메서드 패턴 복제 |
| **IRS** 커브 구간별 할인계수 (REQ-VAL-001) | 1113호 72항 | **문서 선언만** | 플랫커브 가정 유지. 본 PoC 시연 제외 |
| **IRS** 변동 다리 forward rate 계산 (REQ-VAL-002) | 1113호 81항 | **문서 선언만** | 1기간 근사 유지 |
| **IRS** 실제 결제일 기반 일수 계산 (REQ-VAL-003) | KRW IRS 시장 관행 | **문서 선언만** | 균등 분할 유지 |
| **IRS** CVA 필드 (REQ-VAL-004) | 1113호 48항 | **문서 선언만** | 응답 필드 자리는 만들어두고 PoC 값 0 |
| **CRS C1·C2** 적격 조합 등록 | 6.4.1 / 6.5.2 / 6.3.7 | **이번에 구현** | 조합표·헤지 지정 폼만 확장. 평가는 §하단 분리 |
| **CRS** 평가(이중 커브 할인 + 환산) | 1113호 72~90항 | **검토 필요** | 1단계 `CrsPricing` 코드 존재, OIS·CSA 담보 조건 미반영. PoC 는 spot + flat curve |
| **CRS C3** 외화 차입금 헤지 | 6.5.11 | **문서 선언만** | 차입금 엔티티·도메인 미구현 |
| **CRS C4** 외화 예상거래 헤지 | 6.5.11 / 6.5.13 | **시연 제외** | 1단계 FX Forward 데모와 충돌 — 별도 트랙 |
| **CRS CFH** 환산 차이 OCI 잔존 처리 | 6.5.11 / 6.5.13 | **검토 필요** | 환위험 vs 금리위험 분리 산식 미확정 |
| **Dollar-offset Periodic** | B6.4.12 / B6.4.13 | **이번에 구현** (시연 기본값) | 1단계 enum 그대로 사용 |
| **Dollar-offset Cumulative** | B6.4.12 / B6.4.13 | **이번에 구현** (질문 시 전환 시연) | 1단계 `resolveReferenceValue` 그대로 사용 |
| Dollar-offset 방식 일관성 시스템 강제 | B6.4.13 | **검토 필요** | 현재 사용자 책임. UI 안내문 정도만 |
| **80~125% 참고 범위 메시지** (FAIL 자동 인정 아님) | BC6.234 | **이미 구현** | 1단계 PASS/WARNING/FAIL 3단계 체계 그대로 유지 |
| **유효성 0/0 입력 차단** | (시스템 방어) | **이미 구현** | 1단계 `superRefine` 으로 프런트에서 차단 |
| **헤지 중단** (FVH 장부조정 상각) | 6.5.10 | **검토 필요** | IRS FVH 중단 시 채권 장부조정 만기까지 상각 절차 미구현 |
| **헤지 중단** (CFH OCI 처리) | 6.5.13 / 6.5.14 | **이미 구현** (1단계) | `forecastTransactionExpected` 분기 IRS·CRS 동일 적용 가능 |
| **재조정 (Rebalancing)** | 6.5.5 / B6.5.7~B6.5.21 | **이미 구현** (1단계) | `HedgeRebalancingService` IRS·CRS 동일 적용 가능 |
| **자발적 중단 차단** | 6.5.6 | **이미 구현** | `HedgeDiscontinuationReason.isAllowed` |
| **그룹 헤지 / 항목 일부 헤지** | 6.6.1~6.6.4 | **시연 제외** | 본 PoC 범위 외 |
| **IBOR 개혁(IBOR → RFR 전환)** | 6.8.1~6.8.24 | **문서 선언만** | `floatingRateIndex` 필드 단순 변경 가능. 전환 조항(fallback) 처리는 **검토 필요** |
| **K-IFRS 1107호 공식 공시 양식 자동화** | 1107호 22A~24G | **시연 제외** | 본 PoC 범위 외 (전사 공시 모듈) |

---

## 8. 평가 Level — 한눈에

| 인스트루먼트 | Level | 근거 |
|---|---|---|
| FX Forward (1단계) | **Level 2** | OTC 파생, 환율·금리 관측가능 (1113호 72~90항) |
| IRS FVH | **Level 2** | OTC, 시장 지표금리 커브 관측가능 |
| IRS CFH | **Level 2** | 동일 |
| CRS FVH | **Level 2** | OTC, 양 통화 커브 + 현물환율 관측가능 |
| CRS CFH | **Level 2** | 동일 |
| Level 1 | — | 본 PoC 범위 내에서 해당 없음(거래소 표준화 파생만 가능) |
| Level 3 | — | 비상장 특수 옵션·관측 불가 변동성 사용 시. 본 PoC **검토 필요** 처리 |

---

## 9. 입력/출력 — 회계 관점 합의

### 9.1 IRS 평가 입력(2단계 신규)
프런트가 직접 입력하는 최소 필드:
- `contractId`, `valuationDate`, `currentFloatingRate`, `discountRate`(PoC 플랫).
- (검토 필요) `discountCurve: Map<tenorDays, rate>` — 본 PoC 미사용.

### 9.2 IRS·CRS 유효성 테스트 입력(1단계 재사용)
| 필드 | IRS FVH | IRS CFH | CRS FVH | CRS CFH |
|---|---|---|---|---|
| `instrumentFvChange` | IRS FV 변동 | IRS FV 변동 | CRS FV 변동(KRW 환산) | 동일 |
| `hedgedItemPvChange` | 채권 장부조정 변동(헤지귀속분) | 변동금리부채 PV 변동 | 외화채권 PV 변동(KRW 환산) | 외화 변동금리부채 PV 변동(KRW 환산) |
| `testType` | Periodic / Cumulative | 동일 | 동일 | 동일 |
| `hedgeType` | `FAIR_VALUE` | `CASH_FLOW` | `FAIR_VALUE` | `CASH_FLOW` |

### 9.3 회계 분개 결과(2단계 신규/확장)
- **FVH 공통**: 수단 평가 분개 + 피헤지 장부조정 분개 = 2건/평가일.
- **CFH 공통**: 유효분 OCI 분개 + (있는 경우) 비효과분 P&L 분개 = 1~2건/평가일.
- **CFH 이자 결제 재분류**: **검토 필요** — 자동화 대상 외.
- **이자 수수 분개**(고정/변동): **검토 필요** — 본 PoC 미구현.

---

## 10. 예외·경계 처리

| 상황 | 처리 | 근거 | 분류 |
|---|---|---|---|
| 거래상대방 신용등급 BBB 미만 → IRS/CRS 신용위험 지배 | 경고 + 재조정·중단 검토 안내 | B6.4.7 | **검토 필요** (자동 차단 X) |
| IRS 중도해지(자발적) | `BusinessException HD_012` | 6.5.6 | **이미 구현** |
| IRS 만기 도래 | `discontinue(HEDGE_INSTRUMENT_EXPIRED)` | 6.5.6 | **이미 구현** |
| CFH 헤지대상 조기상환 | `discontinue(HEDGE_ITEM_NO_LONGER_EXISTS)` + `forecastTransactionExpected=false` | 6.5.14 | **이미 구현** |
| Dollar-offset 비율이 동방향(양수) | FAIL — 경제적 관계 훼손 | BC6.234 / 6.5.6 | **이미 구현** |
| Dollar-offset 비율이 80~125% 이탈(반대방향) | WARNING — 재조정 검토 신호. **자동 FAIL 아님** | BC6.234 / 6.5.5 | **이미 구현** |
| `instrumentFvChange == 0 && hedgedItemPvChange == 0` | 프런트 차단 | 시스템 방어 | **이미 구현** |
| IRS 이자 결제 후 공정가치 재산정 | "결제 후 잔존 기간" 기반 재평가 | — | **검토 필요** — 본 PoC 미구현 |

---

## 11. 다음 단계

- **백엔드 에이전트**: 본 표의 "이번에 구현" 항목만 작업 — 우선순위는 [`IRS_HEDGE_REQUIREMENTS.md`](./IRS_HEDGE_REQUIREMENTS.md) 부록의 P1~P4 를 따른다.
- **프런트 에이전트**: 본 문서와 짝을 이루는 화면 설계는 별도 IRS 프런트 플래닝 문서(이전 turn 산출물)에서 정리됨. CRS 화면은 IRS 패밀리 정착 후 추가.
- **검증 에이전트**: "검토 필요" 라벨 항목은 시연 전 회계 리뷰 필수. 자동 구현 금지.
- **RAG 복구 후**: §1 의 "교차 검증 필요 조항" 목록을 1차 검증 대상으로 사용.

---

## 12. 산출 구분 요약 (한눈에)

| 분류 | 개수 | 의미 |
|---|---|---|
| 이번에 구현 | 9 | 1단계 코드 재사용·확장으로 본 2단계 작업 범위 안에서 완성 |
| 문서 선언만 | 6 | 인터페이스·필드만 자리 잡고 PoC 값은 placeholder |
| 시연 제외 | 4 | 코드 작업 자체를 보류, 본 PoC 시연 흐름에 포함 안 함 |
| 검토 필요 | 8 | K-IFRS 해석·외부 데이터·도메인 모델 추가 검토 후 결정 |
| 이미 구현 | 7 | 1단계에서 완료, IRS·CRS 에도 자동 적용 |

> "검토 필요" 항목은 절대 추정으로 구현하지 말 것. 회계 리뷰 또는 RAG 복구 후 K-IFRS 원문 재확인이 선행되어야 한다.

---

*K-IFRS 근거: 1109호 6.3.1 / 6.3.7 / 6.4.1 / 6.5.2 / 6.5.5 / 6.5.6 / 6.5.8 / 6.5.9 / 6.5.10 / 6.5.11 / 6.5.12 / 6.5.13 / 6.5.14 / 6.6.1~6.6.4 / 6.8.1~6.8.24 / B6.4.7~B6.4.13 / B6.5.1~B6.5.5 / B6.5.7~B6.5.21 / BC6.234 — 1113호 48 / 61~66 / 72~90 — 1107호 22A~24G*
*작성 기반: RAG 미동작(2026-04-25 확인), 로컬 근거 4종 + 1차 IRS 사양 문서*
