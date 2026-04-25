# CRS(통화스왑) 2단계 Gap 분석

**작성일**: 2026-04-25  
**작성자**: 백엔드 에이전트  
**기준 커밋**: 현재 main (IRS FVH 구현 직후)  
**분석 대상**:
- `valuation/domain/crs/`, `valuation/application/`, `valuation/adapter/web/`
- `hedge/application/HedgeDesignationService.java`
- `effectiveness/application/EffectivenessTestService.java`
- `journal/application/JournalEntryService.java`, `journal/domain/`

---

## 1. 결론 요약 (TL;DR)

| 경로 | 현재 상태 | 2단계 추가 필요 |
|------|-----------|----------------|
| CRS 공정가치 계산 (`CrsPricing`) | ✅ 완전 구현 | 없음 |
| CRS 계약 관리 API (`/api/crs/*`) | ✅ 완전 구현 | 없음 |
| CRS 평가 API (`/api/crs/valuate`) | ✅ 완전 구현 | 없음 |
| 헤지 지정 (`HedgeDesignationService`) | ✅ CRS 경로 구현 완료 | 없음 |
| 유효성 테스트 (`EffectivenessTestService`) | ⚠️ IRS와 동일 경로로 통과 — CRS 전용 검증 없음 | CRS 복합위험 입력값 검증 분기 |
| 분개 생성 (`JournalEntryService`) | ❌ CRS 라우팅 없음 — FX_FORWARD 경로 낙오 | `CrsCashFlowHedgeJournalGenerator` 또는 기존 CFH 라우팅 |
| 단위 테스트 | ⚠️ `CrsPricingTest` 1개만 존재 | Service·Controller 계층 통합 테스트 |
| 프런트엔드 | ❌ CRS 화면 없음 | 2단계 UI 전체 |

**결론**: CRS는 평가 엔진과 헤지 지정까지는 바로 동작하나,  
유효성 테스트 후 **분개 자동 생성 경로에서 CRS가 FX_FORWARD 경로로 낙오**한다.  
이것이 현재의 가장 큰 단일 Gap이다.

---

## 2. 현재 구현된 것 — 상세

### 2.1 CRS 평가 엔진 (완전 구현)

**파일**:
```
valuation/domain/crs/
├── CrsContract.java      — 계약 엔티티, 검증 포함
├── CrsValuation.java     — 평가 결과 Append-Only 엔티티
└── CrsPricing.java       — 순수 도메인 계산 (Spring 의존 없음)

valuation/application/
├── CrsValuationService.java     — 오케스트레이션
├── CrsValuationUseCase.java     — 인터페이스
└── port/
    ├── CrsContractRepository.java
    └── CrsValuationRepository.java

valuation/adapter/web/
├── CrsValuationController.java  — REST API 7개 엔드포인트
└── dto/
    ├── CrsContractRequest.java
    ├── CrsValuationRequest.java
    └── CrsValuationResponse.java
```

**`CrsPricing` 핵심 로직**:
```
FV = 외화다리PV(원화환산) - 원화다리PV

외화다리PV = Σ(foreignCoupon_i × spotRate × df_i) + foreignNotional × spotRate × df_n
원화다리PV  = Σ(krwCoupon_i × df_i) + krwNotional × df_n
df(r,t)    = 1 / (1 + r × t/365)   — ACT/365 Fixed
```

- PoC 단순화: 플랫 커브, 균등 기간 분할, 신용위험 귀속분 미분리
- `CrsPricingTest.java` 단위 테스트 존재
- IRS의 `IrsPricing`과 완전히 대칭적 구조

**활성 REST 엔드포인트**:

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/crs/contracts` | 계약 등록(upsert) |
| GET  | `/api/crs/contracts` | 계약 목록(페이징) |
| GET  | `/api/crs/contracts/{id}` | 계약 단건 조회 |
| DELETE | `/api/crs/contracts/{id}` | 계약 삭제 |
| POST | `/api/crs/valuate` | 공정가치 평가 |
| GET  | `/api/crs/valuations/{id}` | 평가 결과 조회 |
| GET  | `/api/crs/contracts/{id}/valuations` | 평가 이력 조회 |

---

### 2.2 헤지 지정 (완전 구현)

**파일**: `hedge/application/HedgeDesignationService.java`

CRS를 `instrumentType=CRS`로 지정하면 아래 경로가 활성화된다:

```java
// validateIrsOrCrsContract() — 라인 611~615
} else if (instrumentType == InstrumentType.CRS) {
    crsContractRepository.findById(contractId)
            .orElseThrow(() -> new BusinessException("HD_001",
                    "CRS 계약을 찾을 수 없습니다. contractId=" + contractId));
}

// designateIrsOrCrs() — 라인 414~490
// IRS/CRS 공통 경로: USD 통화 검증 없음, 원화채권·외화차입금 대상 허용
```

**지원되는 피헤지항목 조합**:

| `HedgedItemType` | `HedgeType` | CRS 사용 가능 여부 |
|------|------|------|
| `FOREIGN_BORROWING` | `CASH_FLOW` | ✅ (K-IFRS 1109호 주요 CRS 사용 사례) |
| `FOREIGN_BOND` | `CASH_FLOW` | ✅ |
| `FOREIGN_BOND` | `FAIR_VALUE` | ✅ |
| `KRW_FIXED_BOND` | `FAIR_VALUE` | ✅ (IRS와 동일 경로) |

`InstrumentType.CRS`는 `InstrumentType` enum에 이미 정의되어 있고,  
`HedgeRelationship.instrumentType` 컬럼에 저장된다.

---

### 2.3 유효성 테스트 (부분 구현)

**파일**: `effectiveness/application/EffectivenessTestService.java`

```java
// 라인 89~92
InstrumentType instrumentType = resolveInstrumentType(request.instrumentType());
if (instrumentType == InstrumentType.IRS) {
    validateIrsHedgeTypeCombination(hedgeType, request.hedgeRelationshipId());
}
// ← CRS 분기 없음. instrumentType=CRS로 요청하면 IRS 검증을 건너뛰고
//   Dollar-offset 계산으로 직행한다.
```

**동작 가능한 부분**: CRS 요청 시 Dollar-offset 비율 계산과 PASS/WARNING/FAIL 판정은 정상 동작.  
**누락된 부분**: CRS 전용 검증 없음 (통화쌍 매칭, 피헤지항목 FOREIGN_BORROWING 확인 등).

`EffectivenessTest` 엔티티에 `instrumentType` 컬럼이 있으므로 CRS로 저장 자체는 가능.

---

## 3. 막히는 지점 — Gap 상세

### Gap 1: 분개 생성 CRS 라우팅 없음 (가장 큰 Gap)

**파일**: `journal/application/JournalEntryService.java` 라인 91~118

```java
if (request.hedgeType() == HedgeType.FAIR_VALUE) {
    if (InstrumentType.IRS == request.instrumentType()) {
        // ← IRS FVH 전용 분개 생성기 라우팅
        entries.addAll(IrsFairValueHedgeJournalGenerator.generate(...));
    } else {
        // ← FX_FORWARD (null 포함) 낙오 — CRS도 여기로 빠짐
        entries.addAll(FairValueHedgeJournalGenerator.generate(...));
    }
} else if (request.hedgeType() == HedgeType.CASH_FLOW) {
    // ← CFH는 수단 구분 없이 단일 경로 — CRS CFH는 여기서 처리됨
    entries.addAll(CashFlowHedgeJournalGenerator.generate(...));
}
```

**영향**:
- CRS + FAIR_VALUE 헤지: `FairValueHedgeJournalGenerator`로 낙오. 계정과목 구조는 유사하나 적요·참조조항이 IRS/CRS에 맞지 않음.
- CRS + CASH_FLOW 헤지: `CashFlowHedgeJournalGenerator`로 정상 처리. **실질적으로 동작한다.**
- `JournalEntryRequest`의 `instrumentType` 필드 주석은 "IRS 전용"으로만 문서화되어 있음.

**`journal/domain/` 현황**:
```
FairValueHedgeJournalGenerator.java      — FX_FORWARD FVH (1단계)
IrsFairValueHedgeJournalGenerator.java   — IRS FVH (2단계)
IrsFvhAmortizationJournalGenerator.java  — IRS FVH 장부가액 상각 (K-IFRS 6.5.9)
CashFlowHedgeJournalGenerator.java       — CFH 공통 (수단 무관)
OciReclassificationJournalGenerator.java — OCI 재분류 공통
CrsCashFlowHedgeJournalGenerator.java    ← 존재하지 않음
CrsFairValueHedgeJournalGenerator.java   ← 존재하지 않음
```

---

### Gap 2: 유효성 테스트 CRS 전용 검증 없음

**현황**: `instrumentType=CRS`로 요청 시 IRS 검증(`validateIrsHedgeTypeCombination`)을 건너뛰고  
Dollar-offset 계산으로 직행한다. 기능적으로는 동작하나 CRS 특유의 조건을 검증하지 않는다.

**CRS에서 추가로 검증해야 할 항목**:
- 피헤지항목이 `FOREIGN_BORROWING` 또는 `FOREIGN_BOND`인지 확인  
- 통화쌍 매칭: CRS 계약의 `foreignCurrency` ↔ 피헤지항목의 `currency` 일치 여부  
- 원금 커버율: CRS 명목원금이 피헤지항목 원금 대비 합리적 범위인지

---

### Gap 3: 피헤지항목 CRS 귀속 FV 변동 계산 API 없음

**IRS와의 비교**:

| 항목 | IRS | CRS |
|------|-----|-----|
| 헤지수단 평가 | `CrsValuationController` ✅ | `IrsValuationController` ✅ |
| 피헤지항목 귀속 FV 변동 | `BondHedgedItemController` ✅ | ❌ 없음 |

IRS FVH 시나리오에서는 `POST /api/bond-hedged-item/calculate-fv-change`로  
채권의 헤지귀속 FV 변동을 계산해 유효성 테스트 입력값으로 사용한다.  
CRS CFH에서는 외화차입금의 환율·금리 귀속 현금흐름 변동 계산 API가 없다.

**단, CFH 시나리오에서는 수치를 수동 입력하는 방식으로 우회 가능** (PoC 수준).

---

### Gap 4: 단위·통합 테스트 부족

**현황**:
- `CrsPricingTest.java` — 도메인 계층 단위 테스트 존재
- `CrsValuationService`, `CrsValuationController` — 통합 테스트 없음
- IRS와 달리 `CrsValuationServiceTest`, `CrsValuationControllerTest` 없음

---

### Gap 5: 프런트엔드 전무

CRS 계약 등록·평가·헤지 지정·유효성 테스트·분개 조회 화면이 없다.  
백엔드 API는 Postman/curl로만 접근 가능하다.

---

## 4. 구현 순서 — IRS 이후 CRS 로드맵

### Phase 0: 지금 당장 가능 (추가 개발 없이)

CRS를 현금흐름 헤지(CFH) 수단으로 사용하는 전체 흐름이 **수동 입력** 방식으로 동작한다:

```
① CRS 계약 등록       POST /api/crs/contracts
② CRS 공정가치 평가   POST /api/crs/valuate
③ CRS 헤지 지정       POST /api/hedge/designations  (instrumentType=CRS)
④ 유효성 테스트       POST /api/effectiveness/tests (instrumentType=CRS, 수치 수동 입력)
⑤ 분개 생성           POST /api/journal/entries     (hedgeType=CASH_FLOW)
```

**주의**: ④에서 `hedgedItemPvChange`를 수동으로 입력해야 하며,  
⑤는 `CashFlowHedgeJournalGenerator`를 통해 정상 처리된다.

---

### Phase 1: 안전하게 구현 가능한 최소 범위

**목표**: CRS CFH 전체 흐름 자동화 (수동 입력 제거)

#### 1-A. 유효성 테스트 CRS 검증 분기 추가

```java
// EffectivenessTestService.java 라인 89 이후
if (instrumentType == InstrumentType.IRS) {
    validateIrsHedgeTypeCombination(hedgeType, request.hedgeRelationshipId());
} else if (instrumentType == InstrumentType.CRS) {
    validateCrsHedgeTypeCombination(hedgeType, request.hedgeRelationshipId()); // 신규
}
```

`validateCrsHedgeTypeCombination()`은 CRS는 원칙적으로 CFH 대상임을 검증한다.  
(FVH로 CRS를 사용하는 경우는 드물고 PoC 범위 외이므로 일단 경고 처리)

**위험도**: 낮음. 기존 경로에 `else if` 분기 추가만으로 완결.

#### 1-B. 외화차입금 귀속 현금흐름 변동 계산 API

```
POST /api/crs-hedged-item/calculate-cfh-change
```

IRS의 `BondHedgedItemPricing`에 대응하는  
`ForeignBorrowingHedgedItemPricing` 도메인 클래스 + Controller 추가.  
CRS가 헤지하는 현금흐름(이자·원금 환산액) 변동을 계산한다.

**위험도**: 낮음. Spring 의존 없는 순수 계산 클래스로 시작 가능.

---

### Phase 2: CRS FVH 분개 라우팅

**목표**: CRS + FAIR_VALUE 분개가 올바른 생성기로 라우팅

```java
// JournalEntryService.java
if (request.hedgeType() == HedgeType.FAIR_VALUE) {
    if (InstrumentType.IRS == request.instrumentType()) {
        entries.addAll(IrsFairValueHedgeJournalGenerator.generate(...));
    } else if (InstrumentType.CRS == request.instrumentType()) {  // 신규
        entries.addAll(CrsFairValueHedgeJournalGenerator.generate(...));
    } else {
        entries.addAll(FairValueHedgeJournalGenerator.generate(...));
    }
}
```

`CrsFairValueHedgeJournalGenerator`는 `IrsFairValueHedgeJournalGenerator`와  
계정과목 구조가 동일하지만 적요(description)와 K-IFRS 참조조항이 다르다.  
(CRS FVH 사용 사례는 드물므로 Phase 2에 배치)

**위험도**: 낮음. 기존 CFH 경로(`CashFlowHedgeJournalGenerator`)는 변경 없음.

---

### Phase 3: 통합 테스트 및 시나리오 검증

- `CrsValuationServiceTest` — 평가 서비스 계층 테스트
- CRS CFH 전체 흐름 E2E 테스트
  - CRS 계약 등록 → 평가 → 헤지 지정 → 유효성 테스트 → 분개 생성
- Dollar-offset 비율 검증: FOREIGN_BORROWING $100M × KRW/USD 환율 변동 시나리오

---

### Phase 4: 프런트엔드 (별도 에이전트)

CRS 계약 등록·평가 화면, 헤지 지정 시 CRS 선택 UI 등.  
백엔드 API가 이미 존재하므로 프런트 구현만 남음.

---

## 5. 지금 하면 위험한 범위

| 항목 | 이유 |
|------|------|
| CRS FVH 전체 구현 | 실무에서 CRS는 거의 CFH 수단으로 사용. FVH 분개 계정과목·조항이 PoC 문서에 없어 RAG 재검증 필요. 잘못 구현 시 IRS FVH 경로와 혼동 위험. |
| 피헤지항목 귀속 FV 변동 공식 변경 | `CrsPricing` 할인계수 공식은 현재 ACT/365 단일 규칙. CRS의 외화 다리는 ACT/360(USD) 적용 필요성 검토 전에 수정하면 기존 CrsPricingTest 파괴. RAG 교차검증(K-IFRS 1113호 72항) 후 진행. |
| HedgeDesignationService 피헤지항목 검증 수정 | `FOREIGN_BORROWING` 조합 검증 로직은 IRS/CRS 공통 경로에 있음. 잘못 수정 시 IRS 지정 흐름에 영향. |
| DB 스키마 변경 | `ddl-auto: update`로 운영 중. `crs_contracts`, `crs_valuations` 테이블은 이미 JPA가 자동 생성. 수동 ALTER는 충돌 위험. |

---

## 6. 참조 K-IFRS 조항

| 조항 | 내용 | CRS 관련성 |
|------|------|-----------|
| 1109호 6.2.1 | 위험회피수단 적격성 — 파생상품(CRS 포함) | CRS 적격 수단 확인 |
| 1109호 6.4.1 | 헤지 지정 요건 3조건 | CRS 헤지 지정 시 적용 |
| 1109호 6.5.11 | 현금흐름 위험회피 OCI/P&L | CRS CFH의 핵심 조항 |
| 1109호 B6.4.9 | 헤지비율 결정 방법 | CRS 원금 커버율 판단 |
| 1109호 B6.5.1~B6.5.5 | 헤지위험 귀속 FV 변동 | CRS FVH 사용 시 적용 |
| 1113호 81항 | Level 2 공정가치 — 관측가능 투입변수 | CRS 공정가치 Level 분류 |

> **TODO(RAG 재검증)**: 위 조항들은 현재 코드 주석에 참조만 있으며  
> RAG 시스템(localhost:8080) 재기동 후 원문 교차검증 필요.

---

## 7. 파일별 현황 요약

```
✅ 완전 구현
  valuation/domain/crs/CrsContract.java
  valuation/domain/crs/CrsValuation.java
  valuation/domain/crs/CrsPricing.java
  valuation/application/CrsValuationService.java
  valuation/application/CrsValuationUseCase.java
  valuation/application/port/CrsContractRepository.java
  valuation/application/port/CrsValuationRepository.java
  valuation/adapter/web/CrsValuationController.java
  valuation/adapter/web/dto/CrsContractRequest.java
  valuation/adapter/web/dto/CrsValuationRequest.java
  valuation/adapter/web/dto/CrsValuationResponse.java
  hedge/domain/common/InstrumentType.java          (CRS enum 값 포함)
  hedge/domain/model/HedgeRelationship.java        (instrumentType=CRS 저장 가능)
  hedge/application/HedgeDesignationService.java   (CRS 지정 경로 완성)
  test/valuation/domain/crs/CrsPricingTest.java

⚠️ 부분 구현 (동작하나 CRS 전용 검증 없음)
  effectiveness/application/EffectivenessTestService.java
  effectiveness/adapter/web/dto/EffectivenessTestRequest.java  (CRS 주석만 있음)

❌ 미구현
  journal/domain/CrsCashFlowHedgeJournalGenerator.java  (CFH는 기존 CFH 생성기로 우회 가능)
  journal/domain/CrsFairValueHedgeJournalGenerator.java  (FVH 사용 사례 드묾)
  valuation/*/ForeignBorrowingHedgedItemPricing.java     (피헤지항목 귀속 변동 계산)
  test/valuation/application/CrsValuationServiceTest.java
  프런트엔드 CRS 화면 전체
```
