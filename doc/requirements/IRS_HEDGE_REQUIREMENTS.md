# IRS 위험회피회계 요구사항 명세서 (2단계 엔진)

**버전**: v1.0
**작성일**: 2026-04-24
**작성자**: accounting-expert 에이전트
**대상 에이전트**: backend-developer
**단계**: Phase 2 — IRS 메인 엔진 편입

---

## ⚠️ RAG 검색 현황

작성 시점에 RAG API (`http://localhost:8080/api/search`, `/api/agent/context`, `/api/search/hybrid`)가
임베딩 서비스 내부 오류(500)로 전체 응답 불가 상태였습니다.
Elasticsearch 직접 조회 결과, k-ifrs-docs 인덱스에 5,929건이 적재되어 있으나
문서 콘텐츠가 인코딩 손상 상태(EUC-KR → UTF-8 오변환 추정)로 확인 불가능했습니다.

**본 문서는 K-IFRS 1109호 원문 조항 번호를 명시적으로 인용하였으며,
RAG 시스템 복구 후 반드시 해당 조항 내용과 교차 검증이 필요합니다.**

교차 검증 필요 조항:
- K-IFRS 1109호 6.5.8 (공정가치 위험회피 회계처리)
- K-IFRS 1109호 6.5.9 (장부금액 조정 상각)
- K-IFRS 1109호 6.5.10 (공정가치 위험회피 중단)
- K-IFRS 1109호 6.5.11 (현금흐름 위험회피 OCI 분리)
- K-IFRS 1109호 6.5.12 (OCI 재분류 조정)
- K-IFRS 1113호 72~90항 (Level 2 공정가치)

---

## 목차

1. [IRS 위험회피회계 유형](#1-irs-위험회피회계-유형)
2. [헤지대상·수단·위험 조합 정의](#2-헤지대상수단위험-조합-정의)
3. [IRS 공정가치 평가 로직](#3-irs-공정가치-평가-로직)
4. [유효성 테스트 방식](#4-유효성-테스트-방식)
5. [분개 처리 원칙](#5-분개-처리-원칙)
6. [예외 케이스](#6-예외-케이스)
7. [입력/출력 정의 (backend-developer용)](#7-입력출력-정의)
8. [검증 시나리오](#8-검증-시나리오)

---

## 1. IRS 위험회피회계 유형

### 1.1 공정가치 위험회피 (Fair Value Hedge, FVH)

**적용 상황**: 보유 고정금리채권의 공정가치가 시장금리 변동으로 변동되는 위험을 헤지
**헤지 방향**: 시장금리 상승 → 채권 공정가치 하락 + IRS(변동지급/고정수취) 공정가치 상승 → 상쇄

**근거**:
- K-IFRS 1109호 **6.5.8**: 공정가치 위험회피 회계처리 — 위험회피수단의 손익 및 피헤지항목의 손익(헤지위험 귀속분) 모두 당기손익으로 인식
- K-IFRS 1109호 **6.5.9**: 피헤지항목의 장부금액을 헤지위험에 귀속되는 공정가치 변동만큼 조정

**IRS 구조**: Pay Floating / Receive Fixed (`payFixedReceiveFloating = false`)
- 고정수취: 채권 쿠폰과 상쇄 → 금리 변동 위험 중립화
- 변동지급: 시장금리 상승 시 지급 증가 → 이 손실이 채권 공정가치 하락을 상쇄

---

### 1.2 현금흐름 위험회피 (Cash Flow Hedge, CFH)

**적용 상황**: 변동금리부채(또는 변동금리자산)의 미래 이자 현금흐름 불확실성을 헤지
**헤지 방향**: 시장금리 상승 → 변동금리 이자 지급 증가 + IRS(고정지급/변동수취) 평가이익 → 상쇄

**근거**:
- K-IFRS 1109호 **6.5.11**: 현금흐름 위험회피 회계처리
  - **(1)** 유효 부분: OCI (현금흐름위험회피적립금)로 인식
  - **(2)** 비효과 부분(초과분): 당기손익(P&L)으로 즉시 인식
  - Lower of Test: OCI 한도 = min(|헤지수단 누적변동|, |피헤지항목 누적변동|)
- K-IFRS 1109호 **6.5.12**: OCI → P&L 재분류 조정 — 헤지된 예상거래 또는 확정계약이 P&L에 영향을 미칠 때

**IRS 구조**: Pay Fixed / Receive Floating (`payFixedReceiveFloating = true`)
- 고정지급: 현금흐름을 고정 → 변동금리 상승 위험 제거
- 변동수취: 시장금리 상승 시 수취 증가 → IRS 평가이익을 OCI에 적립

---

## 2. 헤지대상·수단·위험 조합 정의

### 2.1 FVH — 원화 고정금리채권

| 항목 | 값 | K-IFRS 근거 |
|---|---|---|
| 헤지대상 itemType | `KRW_FIXED_BOND` | 6.3.1, 6.3.7 |
| hedgeType | `FAIR_VALUE` | 6.5.2 |
| hedgedRisk | `INTEREST_RATE` | 6.3.7 (금리 위험 구성요소) |
| 헤지수단 instrumentType | `IRS` | 6.2.1 |
| IRS 방향 | Pay Floating / Receive Fixed | 6.5.8 |
| 주요 통화 | KRW | — |

**조합 유효성**: `KRW_FIXED_BOND` → `isHedgeTypeAllowed(FAIR_VALUE) = true`, `isRiskAllowed(INTEREST_RATE) = true` ✅

---

### 2.2 CFH — 원화 변동금리채권 또는 변동금리부채

| 항목 | 값 | K-IFRS 근거 |
|---|---|---|
| 헤지대상 itemType | `KRW_FLOATING_BOND` | 6.3.1, 6.3.7 |
| hedgeType | `CASH_FLOW` | 6.5.2 |
| hedgedRisk | `INTEREST_RATE` | 6.3.7 |
| 헤지수단 instrumentType | `IRS` | 6.2.1 |
| IRS 방향 | Pay Fixed / Receive Floating | 6.5.11 |
| 주요 통화 | KRW | — |

**조합 유효성**: `KRW_FLOATING_BOND` → `isHedgeTypeAllowed(CASH_FLOW) = true`, `isRiskAllowed(INTEREST_RATE) = true` ✅

---

### 2.3 지정 불가 조합 (HD_014/HD_015 차단 대상)

| 잘못된 조합 | 에러코드 | 이유 |
|---|---|---|
| `KRW_FIXED_BOND` + `CASH_FLOW` | HD_014 | 고정금리채권은 현금흐름 불확실성이 없음 (6.5.2) |
| `KRW_FLOATING_BOND` + `FAIR_VALUE` | HD_014 | 변동금리채권은 금리변동으로 현금흐름은 변하나 공정가치 위험 헤지 대상이 아님 |
| `KRW_FIXED_BOND` + `FOREIGN_CURRENCY` | HD_015 | KRW채권은 환율 위험에 노출 없음 (6.3.7) |

---

## 3. IRS 공정가치 평가 로직

### 3.1 평가 방법론

K-IFRS **1113호 72~90항**: IRS는 관측가능한 시장 투입변수(Level 2)로 평가.
평가 기법: **현재가치법(Present Value Method)** — 시장금리 커브로 할인

K-IFRS **1113호 61~66항**: 시장참여자가 사용하는 가격결정 기법 적용.
KRW IRS 시장 표준: CD91일물 또는 KOFR(한국 무위험 지표금리) 기반 할인

---

### 3.2 PoC 1단계 기구현 현황 (`IrsPricing`)

현재 `IrsPricing` 클래스에 다음이 구현되어 있습니다:

```
할인계수:  df_i = 1 / (1 + r × t_i / 365)
고정 다리: PV_fixed  = fixedRate × notional × Σ df_i   (결제 기간별)
변동 다리: PV_float  = floatingRate × notional × df_1  (1기간 근사)
IRS FV (Receive Fixed): PV_fixed - PV_float
IRS FV (Pay Fixed):     PV_float - PV_fixed
```

**가정**: 플랫 이자율 커브 (모든 기간 동일 할인율)

---

### 3.3 2단계 엔진 목표 — 개선 요구사항

#### REQ-VAL-001: 커브 구간별 할인계수 지원
- **현재**: 플랫 커브 단일 할인율
- **목표**: 시장에서 관측된 구간별 금리(1M, 3M, 6M, 1Y, 2Y, 3Y, 5Y)로 부트스트래핑
- **근거**: K-IFRS 1113호 72항 — 관측가능한 시장 금리 사용 의무
- **입력 추가**: `Map<Integer, BigDecimal> discountCurve` (tenor_days → rate)

#### REQ-VAL-002: 변동 다리 forward rate 계산
- **현재**: 현재 변동금리 1기간 근사
- **목표**: 각 결제 기간별 선도금리(forward rate) 추정
  - `forwardRate(t1, t2) = (df(t1) / df(t2) - 1) × (365 / (t2 - t1))`
- **근거**: K-IFRS 1113호 81항 — 시장에서 관측가능한 투입변수

#### REQ-VAL-003: 실제 결제일 기반 일수 계산
- **현재**: 균등 분할 (365/4 = 91.25일 etc.)
- **목표**: 실제 결제 스케줄 `List<LocalDate> paymentDates` 기반 ACT/365 계산
- **근거**: KRW IRS 시장 관행 (ACT/365 Fixed)

#### REQ-VAL-004: CVA(신용가치조정) 필드 준비
- **현재**: 없음
- **목표**: `cvaAdjustment` 필드를 평가 결과에 포함 (nullable — PoC는 0)
- **근거**: K-IFRS 1113호 48항 — 비수행위험(nonperformance risk) 반영

---

### 3.4 피헤지항목(채권) 공정가치 변동 계산

FVH에서 헤지위험(금리위험) 귀속 피헤지항목 변동은 별도 계산이 필요합니다.

**계산 원칙** (K-IFRS 1109호 6.5.8, B6.5.1~B6.5.5):
```
헤지위험 귀속 공정가치 변동 = PV(채권 현금흐름 | 현재 시장금리) - PV(채권 현금흐름 | 지정일 시장금리)
```

- 채권 현금흐름: 쿠폰 + 원금 상환
- 할인율: 지정 시 금리 vs 현재 금리로 각각 PV 계산 → 차이가 "헤지귀속 변동"
- **주의**: 채권의 전체 공정가치 변동이 아닌, 헤지위험(금리위험)에 귀속되는 부분만 인식 (신용위험 귀속분 제외)

**PoC 단순화**: 금리위험 100% 귀속 가정 (신용위험 귀속분 분리 생략)

---

## 4. 유효성 테스트 방식

### 4.1 방법론

K-IFRS 1109호 **B6.4.12**: 매 보고기간 말 유효성 평가 의무.
K-IFRS 1109호 **B6.4.13**: Dollar-offset 방법 적용 (1단계 엔진과 동일).
K-IFRS 1109호 **BC6.234**: 80~125% 정량 기준 폐지 — "경제적 관계 존재 여부"가 핵심 판정 기준.

**1단계 `EffectivenessTestService`를 그대로 사용** — instrumentType이 IRS여도 Dollar-offset 계산은 동일.

---

### 4.2 IRS Dollar-offset 특성

| 항목 | FVH IRS | CFH IRS |
|---|---|---|
| 분자 | IRS 공정가치 당기 변동 | IRS 공정가치 당기 변동 |
| 분모 | 채권 장부금액 조정 당기 변동 | 피헤지항목(변동금리부채) 현재가치 당기 변동 |
| 정상 범위 | 반대방향(-) + |-0.80 ~ -1.25| | 반대방향(-) + |-0.80 ~ -1.25| |
| PASS 기준 | 반대방향 + 참고범위 이내 | 동일 |
| 비효과성 | 수단 + 피헤지 변동의 순합 | Lower of Test 초과분 |

---

### 4.3 0/0 입력 차단

이미 구현된 `ET_004`: `instrumentFvChange == 0 && hedgedItemPvChange == 0` 차단.
IRS는 만기 전 매 분기/반기 결제가 발생하므로 실무에서 0/0이 입력될 가능성이 낮으나,
시스템 방어 로직은 동일하게 적용됩니다.

---

## 5. 분개 처리 원칙

### 5.1 FVH IRS 분개 구조

K-IFRS 1109호 **6.5.8**: 위험회피수단과 피헤지항목의 손익 모두 당기손익 인식.

#### 분기말 평가 분개 (IRS 평가이익 시 — 예: 금리 상승)

```
(IRS 공정가치 상승 — 수단 측)
Dr. 파생상품자산 (IRS)              ×××
  Cr. 파생상품평가이익 [P&L]                    ×××

(채권 공정가치 하락 — 대상 측, 헤지위험 귀속분)
Dr. 공정가치위험회피손실 [P&L]      ×××
  Cr. 위험회피적용채권 장부가치조정             ×××
```

**순 P&L 효과** = IRS 평가이익 + 채권 평가손실 = 비효과 부분(순합)
완전헤지 시: 순합 ≒ 0

#### 이자 결제 분개 (분기/반기)

```
(IRS 고정수취)
Dr. 미수이자 / 현금                 ×××
  Cr. 이자수익 [P&L]                            ×××

(IRS 변동지급)
Dr. 이자비용 [P&L]                  ×××
  Cr. 미지급이자 / 현금                         ×××
```

---

### 5.2 CFH IRS 분개 구조

K-IFRS 1109호 **6.5.11**: 유효 부분 OCI, 비효과 부분 P&L.

#### 분기말 평가 분개 (IRS 공정가치 상승 시 — 예: 금리 상승)

```
(IRS 공정가치 상승 — 유효 부분)
Dr. 파생상품자산 (IRS)              ×××
  Cr. 현금흐름위험회피적립금 [OCI]              ×××

(비효과 부분 — 과대헤지 초과분, 있는 경우)
Dr. 파생상품자산 (IRS)              ×××
  Cr. 파생상품평가이익 [P&L]                    ×××
```

#### OCI 재분류 분개 (이자 지급 시마다)

K-IFRS 1109호 **6.5.12**: 헤지된 현금흐름이 P&L에 영향을 미칠 때 재분류

```
(이자 결제일 — OCI 적립금 → 이자비용으로 재분류)
Dr. 현금흐름위험회피적립금 [OCI]    ×××
  Cr. 이자비용 [P&L]                            ×××

(변동금리부채 실제 이자 지급)
Dr. 이자비용 [P&L]                  ×××
  Cr. 현금 / 미지급이자                        ×××
```

**실질 효과**: 변동금리 지급 + OCI 재분류 = 고정금리 지급 효과 → 현금흐름 안정화

---

### 5.3 분개 자동화 범위 (2단계 구현 목표)

| 분개 유형 | 1단계 현황 | 2단계 목표 |
|---|---|---|
| FVH IRS 평가손익 분개 | FX Forward만 구현 | IRS 평가 결과 → 자동 분개 |
| FVH 피헤지항목 장부가치 조정 | 미구현 | 채권 장부가치 조정 분개 추가 |
| CFH IRS 유효분 OCI | 현금흐름 위험회피적립금 기록 | IRS 기반으로 동일 플로우 |
| CFH OCI 재분류 (이자 결제) | 미구현 | 이자 결제 이벤트 → OCI 재분류 분개 |
| IRS 이자 결제 분개 | 미구현 | 결제 주기별 이자 수수 분개 |

---

## 6. 예외 케이스

### EX-001: IRS 중도해지

**상황**: 시장 조건 변화로 IRS를 만기 전에 해지
**처리 원칙** (K-IFRS 1109호 **6.5.6**):
- 자발적 위험회피관계 중단 불가 — 반드시 `HedgeDiscontinuationReason`에 허용된 코드로만 중단
- FVH: 중단 시점 채권 장부가치 조정액을 만기까지 상각 (K-IFRS **6.5.10**)
- CFH: 예상거래가 여전히 발생 가능하면 OCI 유지; 발생불가 확정 시 OCI 즉시 P&L 재분류 (K-IFRS **6.5.12**)

**시스템 처리**: 기존 `HedgeDesignationService.discontinue()` 로직 재사용 가능

---

### EX-002: 헤지대상 조기상환 (CFH)

**상황**: 변동금리부채가 만기 전에 조기상환
**처리 원칙** (K-IFRS 1109호 **6.5.14**):
- 예상거래가 발생하지 않을 것으로 확정 → OCI 잔액을 즉시 P&L 재분류
- IRS는 독립적으로 계속 존재하나 헤지관계에서 이탈 → 별도 평가 처리 필요

**시스템 영향**: `discontinue()` 시 `forecastTransactionExpected = false` + `currentOciBalance` 입력 → 기존 OCI 재분류 로직 적용

---

### EX-003: 비효과성 과다 발생 — 재조정(Rebalancing)

**상황**: Dollar-offset 비율이 참고범위(80~125%) 이탈 (WARNING)
**처리 원칙** (K-IFRS 1109호 **6.5.5**):
- 위험관리 목적이 동일하면 헤지비율 재조정 가능
- 명목금액 일부를 헤지수단에서 제거하거나 추가
- 재조정 전 비효과성은 즉시 인식 (K-IFRS **B6.5.8**)

**시스템 처리**: 기존 `HedgeRebalancingService` 재사용

---

### EX-004: 신용위험 지배 (Credit Risk Dominates)

**상황**: 거래상대방 신용등급 급격 하락 → IRS 공정가치 변동을 신용위험이 지배
**처리 원칙** (K-IFRS 1109호 **B6.4.7**):
- 신용위험이 공정가치 변동을 지배하면 경제적 관계 조건(6.4.1(3)(가)) 미충족
- → 위험회피관계 중단 검토 필요 (DISCONTINUE)

**차단 기준**: `CreditRating`이 투자등급(BBB 이하) 미만으로 하락 시 경고 플래그

---

### EX-005: 기준금리 전환 (IBOR → RFR)

**상황**: CD91일물 → KOFR 전환 등 변동금리 기준지수 변경
**처리 원칙** (K-IFRS 1109호 **6.8.1~6.8.24**, IBOR 개혁 관련 조항):
- 기준지수 변경이 헤지관계 중단 사유가 아님
- `floatingRateIndex` 필드 업데이트로 처리 가능
- **2단계 범위 제한**: 단순 필드 변경만 지원, 전환 조항(fallback provision) 처리는 3단계

---

### EX-006: 이자 수수 후 파생상품 공정가치 재설정

**상황**: IRS 이자 결제일에 이자 교환 후 공정가치가 재산정됨 (Clean Price 개념)
**처리 원칙**:
- 결제 후 IRS 잔존 공정가치 = 결제 전 공정가치 - 이번 기간 이자 결제액
- 평가 기준일: 결제 후 잔존 기간 기준으로 재평가
- **2단계 범위**: 이자 결제 이벤트를 별도 API로 입력받아 처리

---

## 7. 입력/출력 정의

### 7.1 IRS 헤지 지정 입력

기존 `HedgeDesignationRequest` 재사용. 추가 필드 없음.

| 필드 | 타입 | 예시 | 검증 |
|---|---|---|---|
| `hedgeType` | `HedgeType` | `FAIR_VALUE` | @NotNull |
| `hedgedRisk` | `HedgedRisk` | `INTEREST_RATE` | @NotNull |
| `instrumentType` | `InstrumentType` | `IRS` | @NotNull (nullable 시 FX_FORWARD 기본) |
| `instrumentContractId` | `String` | `"IRS-2026-001"` | HD_002: null 차단 |
| `hedgedItem.itemType` | `HedgedItemType` | `KRW_FIXED_BOND` | HD_014/015: 조합 검증 |
| `hedgeRatio` | `BigDecimal` | `1.00` | @DecimalMin("0.01") |

---

### 7.2 IRS 공정가치 평가 입력 (ValuationRequest — 2단계 신규)

```
IrsValuationRequest {
    contractId:             String          // IRS-2026-001
    valuationDate:          LocalDate       // 평가기준일
    currentFloatingRate:    BigDecimal      // 현재 CD91일물 금리 (소수, 예: 0.045)
    discountRate:           BigDecimal      // 할인율 (PoC: currentFloatingRate와 동일)
    // 2단계 확장: discountCurve Map<Integer,BigDecimal> 추가 예정
}
```

---

### 7.3 유효성 테스트 입력 (기존 `EffectivenessTestRequest` 재사용)

| 필드 | IRS FVH 의미 | IRS CFH 의미 |
|---|---|---|
| `instrumentFvChange` | IRS 공정가치 당기 변동 | IRS 공정가치 당기 변동 |
| `hedgedItemPvChange` | 채권 장부가치 조정 당기 변동 (헤지귀속분) | 피헤지 변동금리부채 현재가치 당기 변동 |
| `hedgeType` | `FAIR_VALUE` | `CASH_FLOW` |
| `instrumentType` | `IRS` | `IRS` |
| `testType` | `DOLLAR_OFFSET_PERIODIC` 또는 `DOLLAR_OFFSET_CUMULATIVE` | 동일 |

**ET_004 차단 조건**: `instrumentFvChange == 0 && hedgedItemPvChange == 0` → 저장 전 차단 (기구현)

---

### 7.4 분개 생성 입력 (JournalEntryRequest — 2단계 신규/확장)

```
IrsJournalRequest {
    hedgeRelationshipId:    String
    journalDate:            LocalDate       // 분기말 또는 이자 결제일
    journalType:            IrsJournalType  // FAIR_VALUE_GAIN / FAIR_VALUE_LOSS /
                                            // INTEREST_SETTLEMENT / OCI_RECLASSIFY
    instrumentFvChange:     BigDecimal      // IRS 평가 변동액
    hedgedItemAdjustment:   BigDecimal      // 피헤지 장부가치 조정액 (FVH만)
    effectiveAmount:        BigDecimal      // 유효 부분
    ineffectiveAmount:      BigDecimal      // 비효과 부분
    ociReserveBalance:      BigDecimal      // OCI 누적 잔액 (CFH만)
    interestReceived:       BigDecimal      // 고정수취 이자 (nullable)
    interestPaid:           BigDecimal      // 변동지급 이자 (nullable)
}
```

---

### 7.5 분개 출력 (2단계)

```
IrsJournalResult {
    journalEntries: List<JournalEntry>  // 생성된 분개 목록
    netPlEffect:    BigDecimal          // 당기손익 순영향
    ociMovement:    BigDecimal          // OCI 변동액 (CFH만)
    summary:        String              // 분개 요약 설명
}
```

---

## 8. 검증 시나리오

### 시나리오: FVH IRS — 금리 상승에 따른 채권 헤지

#### 기본 정보

| 항목 | 값 |
|---|---|
| 헤지대상 | 원화 고정금리채권 (3년 만기, 쿠폰 3.0%, 액면 10,000,000,000원) |
| 헤지수단 | IRS: Pay Floating CD91D, Receive Fixed 3.0%, 명목 10,000,000,000원, 3년 반기 결제 |
| hedgeType | `FAIR_VALUE` |
| hedgedRisk | `INTEREST_RATE` |
| itemType | `KRW_FIXED_BOND` |
| instrumentType | `IRS` |
| 지정일 | 2026-04-01 (채권 발행일, 시장금리 = 3.0%) |

#### 1단계: 헤지 지정 (2026-04-01)

- IRS 공정가치: **0원** (at-market 지정)
- 채권 공정가치: **10,000,000,000원** (액면가 = at-market)
- `hedgeRatio = 1.00` (명목금액 동일)
- 지정 시 분개: **없음** (지정만 기록)

#### 2단계: 1분기말 유효성 테스트 (2026-06-30)

**시장 변화**: CD91일물 3.0% → 4.5% (+150bps 상승)

**IRS 공정가치 변동** (수단):
- 금리 상승 → Pay Floating이 불리해지지만 Receive Fixed가 유리해짐
- 잔존 기간 2.75년, 반기 결제 5회
- 평가: `IrsPricing.calculateFairValue(notional=10B, fixedRate=0.030, floatingRate=0.045, remainingDays=1004, freq=SEMI_ANNUAL, discountRate=0.045)`
- **예상 결과**: IRS FV ≈ **+390,000,000원** (평가이익)

**채권 장부가치 조정** (대상, 헤지귀속분):
- 금리 상승 → 고정금리채권 공정가치 하락
- PV(3% 쿠폰 + 원금 | 4.5%) ≈ 9,614,000,000원
- 변동: 9,614,000,000 - 10,000,000,000 = **-386,000,000원**

**Dollar-offset 비율**:
```
ratio = instrumentFvChange / hedgedItemPvChange
      = +390,000,000 / (-386,000,000)
      = -1.0104
```
→ 반대방향(-), |ratio| = 1.01 → 참고범위(80~125%) **이내** → **PASS**

**비효과성**:
```
effectiveAmount    = min(390M, 386M) = 386,000,000원
ineffectiveAmount  = 390M + (-386M) = +4,000,000원  (수단 초과분 P&L)
```

#### 3단계: 분개 생성 (2026-06-30)

```
(IRS 평가이익 — 수단)
Dr. 파생상품자산 (IRS)          390,000,000
  Cr. 파생상품평가이익 [P&L]                  390,000,000

(채권 장부가치 조정 — 헤지귀속분)
Dr. 공정가치위험회피손실 [P&L]  386,000,000
  Cr. 위험회피적용채권 장부조정              386,000,000
```

**P&L 순효과**: +390,000,000 - 386,000,000 = **+4,000,000원** (비효과 부분)
**OCI**: 해당 없음 (FVH)

#### 4단계: 검증 포인트

| 검증 항목 | 기대값 | 실패 시 의심 원인 |
|---|---|---|
| Dollar-offset ratio | -1.0104 | IrsPricing 플랫커브 가정 오류 |
| 유효성 결과 | PASS | 비율 계산 방향 오류 |
| ineffectiveAmount 부호 | +4,000,000 (양수) | 비효과성 부호 처리 오류 (FVH는 abs() 금지) |
| P&L 순효과 | +4,000,000원 | 분개 계정 방향 오류 |
| OCI | null (FVH) | HedgeType 분기 오류 |
| 저장 후 누적 instrumentFvCumulative | +390,000,000 | computeCumulatives 오류 |

---

## 부록: 2단계 구현 우선순위

| 우선순위 | 작업 | 관련 K-IFRS |
|---|---|---|
| P1 | IRS 유효성 테스트 — 기존 엔진 연동 (instrumentType=IRS 경로 열기) | B6.4.12, B6.4.13 |
| P1 | IRS FVH 분개 자동 생성 (수단 평가 + 피헤지 장부조정) | 6.5.8 |
| P2 | 채권 헤지귀속 공정가치 변동 계산 API | 6.5.8, B6.5.1 |
| P2 | CFH IRS OCI 재분류 분개 (이자 결제 이벤트) | 6.5.11, 6.5.12 |
| P3 | IRS 만기 검증 (`validateForHedgeDesignation` IRS 버전) | 6.4.1(2) |
| P3 | 커브 구간별 할인계수 지원 (REQ-VAL-001) | 1113호 72항 |
| P4 | 기준금리 전환 (IBOR → RFR) 처리 | 6.8.1~6.8.24 |
