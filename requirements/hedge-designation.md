# 요구사항 명세서: 헤지 지정 및 K-IFRS 적격요건 자동 검증

**기능명**: `hedge-designation`
**작성자**: accounting-expert 에이전트
**작성일**: 2026-04-19
**RAG 검색 완료**: ✅ (조항번호 검색 완료 / 일부 상세 원문은 "RAG 부분 확인" 표시)

---

## 1. 기능 개요

### 1.1 헤지 지정(Hedge Designation)이란

헤지 지정이란 기업이 특정 위험을 관리하기 위해 위험회피수단(Hedging Instrument)과 위험회피대상항목(Hedged Item) 사이의 공식적인 위험회피관계(Hedge Relationship)를 수립하고, K-IFRS 1109호 제6장이 정하는 적격요건을 충족함으로써 헤지회계(Hedge Accounting)를 적용할 수 있는 권리를 획득하는 행위이다.

헤지회계를 적용하지 않으면 위험회피수단(파생상품)의 공정가치 변동이 즉시 당기손익으로 계상되어 재무제표에 왜곡이 발생한다. 헤지회계 적용 시 위험회피수단의 손익 인식 시점을 위험회피대상항목의 손익 인식 시점과 맞춰 재무제표의 매칭(matching)을 달성한다.

### 1.2 K-IFRS 근거 요약

- 헤지회계 적용 조건: K-IFRS 1109호 6.4.1
- 헤지 문서화 의무: K-IFRS 1109호 6.4.1(2) (RAG 부분 확인 — 6.4.2와 병기되는 조항)
- 위험회피 종류 정의: K-IFRS 1109호 6.5.2
- 경제적 관계 해석 지침: K-IFRS 1109호 B6.4.1
- 신용위험 지배 판단 지침: K-IFRS 1109호 B6.4.7~B6.4.8
- 헤지비율 및 재조정: K-IFRS 1109호 6.4.1(3)(다), 6.5.5, B6.4.9~B6.4.11
- 현금흐름 헤지 OCI 처리: K-IFRS 1109호 6.5.11
- 비효과적 부분 당기손익: K-IFRS 1109호 6.5.12
- 공정가치 헤지 처리: K-IFRS 1109호 6.5.8

---

## 2. K-IFRS 근거 조항 표

| 조항 | 내용 | 본 기능 적용 방법 |
|------|------|-----------------|
| K-IFRS 1109호 6.4.1 | 위험회피회계 적용조건 3가지: ①경제적 관계 존재, ②신용위험 지배적 아님, ③헤지비율 적절 | 헤지 지정 시 3가지 조건 자동 검증 |
| K-IFRS 1109호 6.4.1(2) | 위험회피관계를 공식적으로 지정하고 문서화해야 함 (위험관리 목적, 전략, 위험회피대상·수단 식별 포함) | 지정 즉시 헤지 문서 자동 생성 |
| K-IFRS 1109호 6.5.2 | 위험회피관계 3종류: ①공정가치위험회피, ②현금흐름위험회피, ③해외사업장순투자 위험회피 | 본 시스템은 ①②만 지원 (③ 제외) |
| K-IFRS 1109호 B6.4.1 | 위험회피효과란 위험회피수단의 공정가치·현금흐름 변동이 위험회피대상항목의 공정가치·현금흐름 변동과 상계(offset)되는 것 | 경제적 관계 판단 기준 |
| K-IFRS 1109호 B6.4.7~B6.4.8 | 신용위험의 효과가 경제적 관계로 인한 가치 변동보다 지배적인 경우 적격요건 미충족 | 신용위험 지배 여부 판단 |
| K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11 | 헤지비율은 실제 위험회피대상항목 수량과 위험회피수단 수량의 비율 — 가중치 불균형은 반영 불가 | 헤지비율 = 헤지수단 명목금액 / 헤지대상 노출금액 |
| K-IFRS 1109호 6.5.5 | 헤지비율 요건 미충족 시 위험관리 목적 동일하면 헤지비율 재조정(Rebalancing) 가능 | 재조정 이력 기록 |
| K-IFRS 1109호 6.5.11 | 현금흐름위험회피: 유효 부분은 기타포괄손익(OCI)으로 인식 | 헤지 유형에 따른 회계처리 분기 |
| K-IFRS 1109호 6.5.12 | 현금흐름위험회피: 비효과적 부분은 즉시 당기손익 인식 | 비효과적 부분 처리 |
| K-IFRS 1109호 6.5.8 | 공정가치위험회피: 위험회피수단 및 위험회피대상항목의 공정가치 변동 모두 당기손익 인식 | 공정가치 헤지 분개 |
| K-IFRS 1109호 B6.4.12 | 유효성 평가: 적어도 매 보고기간 말 또는 중요한 변화 발생 시 수행 | 유효성 테스트 트리거 |
| K-IFRS 1109호 6.4.3 | 예상거래를 헤지대상으로 지정 시 "발생 가능성이 매우 높음(highly probable)" 요건 충족 필요 | PoC에서는 예상거래(FORECAST_TRANSACTION) 제외 — 본 개발 시 추가 구현 |
| K-IFRS 1109호 6.5.3 | 공정가치 헤지 대상 항목 적격성: 공정가치 변동에 노출된 인식된 자산·부채·확정약정 | hedgeType=FAIR_VALUE 선택 시 별도 대상 적격성 검증 필요 |
| K-IFRS 1109호 6.5.4 | 현금흐름 헤지 대상 항목 적격성: 현금흐름 변동에 노출된 인식된 자산·부채 또는 매우 가능성 높은 예상거래 | hedgeType=CASH_FLOW 선택 시 적격성 검증 기준 |
| K-IFRS 1109호 6.5.6 | 위험회피관계 전부 또는 일부를 자발적으로 취소(de-designate) 불가 — 위험관리 목적이 변경된 경우만 중단 가능 | 헤지 중단 API 호출 시 중단 사유 필수 검증 |
| K-IFRS 1109호 6.5.7 | 현금흐름 헤지 중단 시 OCI로 인식된 누적손익의 처리: 예상거래가 여전히 발생 예정이면 OCI에 유지, 발생 예정 없으면 즉시 당기손익 재분류 | 헤지 중단 시 OCI 잔액 처리 방향 분개와 연계 필요 |

---

## 3. 적격요건 3가지 검증 로직 (K-IFRS 1109호 6.4.1)

### 3.1 조건 1: 경제적 관계 존재 (6.4.1(3)(가), B6.4.1)

**정의**: 위험회피수단의 공정가치 또는 현금흐름의 변동이 위험회피대상항목의 공정가치 또는 현금흐름의 변동과 반대 방향으로 움직이는 관계가 존재해야 한다.

**판단 기준 (구체적)**:

| 검증 항목 | 판단 방법 | 통과 기준 |
|-----------|-----------|-----------|
| 기초변수(Underlying) 동일성 | 헤지대상 위험의 기초변수와 헤지수단의 기초변수가 동일한지 확인 | 동일한 통화쌍, 이자율지표, 상품가격 등 |
| 명목금액 매칭 | 헤지수단 명목금액이 헤지대상 노출금액의 50%~200% 범위 이내 | 50% ≤ 커버율 ≤ 200% |
| 만기 방향성 | 헤지수단 만기가 헤지대상 노출 만기와 일치하거나 짧지 않음 | 헤지수단 만기 ≥ 헤지대상 만기 (또는 동일) |
| 반대 방향 움직임 | 기초변수 변동 시 헤지수단과 헤지대상의 가치 변동이 반대 방향인지 | 방향성 확인 (예: 매도 포지션 vs 자산) |

**데모 시나리오 적용 (USD 예금 + 통화선도 매도)**:
- 기초변수: USD/KRW 환율 — 동일 ✅
- 명목금액: $10M vs $10M — 100% ✅
- 만기: 2026-07-01 vs 2026-07-01 — 일치 ✅
- 방향성: 환율 상승 시 예금 원화가치 증가 vs 통화선도 손실 — 반대 ✅

### 3.2 조건 2: 신용위험이 지배적이지 않음 (6.4.1(3)(나), B6.4.7~B6.4.8)

**정의**: 신용위험의 효과가 경제적 관계로 인한 가치 변동보다 지배적이지 않아야 한다. 즉, 기초변수 변동이 아닌 신용위험으로 인해 헤지관계가 깨지는 상황이 아니어야 한다.

**판단 기준 (구체적)**:

| 검증 항목 | 판단 방법 | 통과 기준 |
|-----------|-----------|-----------|
| 헤지대상 발행자 신용등급 | 외부 신용평가등급 확인 | 투자등급 (BBB- 이상) — 비투자등급은 신용위험 지배 가능성 높음 |
| 헤지수단 거래상대방 신용등급 | 거래상대방(은행 등) 외부 신용평가등급 확인 | 투자등급 (A- 이상 권고) |
| 신용위험 집중도 | 헤지수단과 헤지대상이 동일 발행자/그룹에 속하지 않는지 | 동일 그룹 내 내부거래인 경우 경고 |
| 담보/보증 여부 | 신용 보강 장치 여부 | 담보/보증 있으면 신용위험 경감 인정 |

**PoC 구현 방식**: 신용등급을 ENUM(AAA, AA, A, BBB, BB, B, CCC, D)으로 입력받아 투자등급(BBB 이상) 여부를 자동 판정. 실제 시스템에서는 외부 신용평가사 API 연동.

**데모 시나리오 적용**:
- 헤지대상(USD 예금 발행자: 미국 은행): 투자등급 가정 ✅
- 헤지수단 거래상대방(가나은행): 투자등급 가정 ✅
- → "양측 모두 투자등급" 자동 확인

### 3.3 조건 3: 헤지비율 적절 (6.4.1(3)(다), B6.4.9~B6.4.11)

**정의**: 위험회피관계의 헤지비율은 기업이 실제로 위험을 회피하는 위험회피대상항목의 수량과 이를 위해 실제 사용하는 위험회피수단의 수량의 비율과 같아야 한다. 단, 비효과적인 부분을 의도적으로 줄이기 위한 가중치 불균형은 허용되지 않는다.

**헤지비율 계산 방법**:

```
헤지비율(%) = (위험회피수단 명목금액 / 위험회피대상 노출금액) × 100

적절 헤지비율 범위: 80% ≤ 헤지비율 ≤ 125%
※ K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 80~125% 기준 준용)

이상적 헤지비율: 100% (명목금액 1:1 매칭)
```

**검증 로직**:
1. 헤지수단 명목금액과 헤지대상 노출금액을 동일 통화로 환산
2. 헤지비율 = 헤지수단 명목금액 / 헤지대상 노출금액
3. 80% 이상 125% 이하: 통과
4. 범위 이탈 시: 재조정(Rebalancing) 권고 또는 적격요건 미충족

**데모 시나리오 적용**:
- 헤지수단: $10,000,000
- 헤지대상: $10,000,000
- 헤지비율: 100% → 6.4.1(c) 충족 ✅

---

## 4. 데이터 모델 (개념)

### 4.1 HedgeRelationship (위험회피관계)

위험회피대상항목과 위험회피수단을 연결하는 핵심 도메인 엔티티.

```
- hedgeRelationshipId      (PK, 위험회피관계 식별자, 예: HR-2026-001)
- hedgeType                (위험회피 유형: FAIR_VALUE / CASH_FLOW)
- hedgedRisk               (회피대상위험: FOREIGN_CURRENCY / INTEREST_RATE / COMMODITY 등)
- designationDate          (지정일, LocalDate)
- hedgePeriodStart         (위험회피기간 시작일, LocalDate)
- hedgePeriodEnd           (위험회피기간 종료일, LocalDate)
- hedgeRatio               (헤지비율, BigDecimal — 예: 1.00 = 100%)
- status                   (상태: DESIGNATED / DISCONTINUED / REBALANCED / MATURED)
- eligibilityStatus        (적격요건 검증 결과: ELIGIBLE / INELIGIBLE / PENDING)
- economicRelationResult   (조건 1 검증 결과: PASS / FAIL, with reason)
- creditRiskResult         (조건 2 검증 결과: PASS / FAIL, with reason)
- hedgeRatioResult         (조건 3 검증 결과: PASS / FAIL, with ratio value)
- riskManagementObjective  (위험관리 목적 — 문서화 필드, 자유 텍스트)
- hedgeStrategy            (위험회피 전략 — 문서화 필드, 자유 텍스트)
- discontinuationDate      (헤지회계 중단일, LocalDate, nullable)
- discontinuationReason    (중단 사유, nullable)
- [감사 필드: createdAt, updatedAt, createdBy, updatedBy]
```

### 4.2 HedgedItem (위험회피대상항목)

```
- hedgedItemId             (PK, 예: HI-2026-001)
- hedgeRelationshipId      (FK → HedgeRelationship)
- itemType                 (항목 유형: FX_DEPOSIT / FIXED_RATE_BOND / FLOATING_RATE_BOND / FORECAST_TRANSACTION 등)
- currency                 (통화, 예: USD)
- notionalAmount           (명목금액, BigDecimal)
- notionalAmountKrw        (원화 환산 명목금액, BigDecimal — 지정일 환율 기준)
- maturityDate             (만기일, LocalDate)
- counterpartyName         (거래상대방명, nullable)
- counterpartyCreditRating (거래상대방 신용등급: AAA / AA / A / BBB / BB / B / CCC / D)
- interestRateType         (금리 유형: FIXED / FLOATING, nullable)
- interestRate             (금리, BigDecimal, nullable)
- description              (항목 설명, 자유 텍스트)
- [감사 필드: createdAt, updatedAt, createdBy, updatedBy]
```

### 4.3 HedgingInstrument → FxForwardContract 연계

위험회피수단은 별도 엔티티를 신규 생성하지 않고 기존 `FxForwardContract` 엔티티와 연계한다.

**연계 방법**:
- `HedgeRelationship` 엔티티에 `fxForwardContractId` 필드 추가 (FK → FxForwardContract.contractId)
- `FxForwardContract`에 `hedgeRelationshipId` 역참조 필드 추가 (nullable — 아직 지정 전인 계약도 존재 가능)
- 연계 시 `FxForwardContract.hedgeDesignationDate`를 `HedgeRelationship.designationDate`로 자동 업데이트

**헤지 수단 적격성 조건** (FxForwardContract 기준):
- status = ACTIVE (만기 미도래)
- 기존 다른 HedgeRelationship에 이미 지정되지 않은 계약 (중복 지정 금지)
- notionalAmountUsd > 0

---

## 5. API 엔드포인트 (안)

### 5.1 헤지 지정 (적격요건 자동 검증 포함)

```
POST /api/v1/hedge-relationships
```

**요청 본문**:

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| hedgeType | String | ✅ | FAIR_VALUE 또는 CASH_FLOW |
| hedgedRisk | String | ✅ | FOREIGN_CURRENCY 등 |
| designationDate | LocalDate | ✅ | 헤지 지정일 |
| hedgePeriodEnd | LocalDate | ✅ | 위험회피기간 종료일 (= 헤지대상 만기일) |
| hedgeRatio | BigDecimal | ✅ | 헤지비율 (예: 1.00) |
| riskManagementObjective | String | ✅ | 위험관리 목적 (문서화) |
| hedgeStrategy | String | ✅ | 헤지 전략 기술 (문서화) |
| hedgedItem | HedgedItemRequest | ✅ | 헤지대상 항목 정보 |
| fxForwardContractId | String | ✅ | 기존 통화선도 계약 ID |

**처리 흐름**:
1. FxForwardContract 유효성 확인 (존재, ACTIVE 상태, 중복 지정 여부)
2. 적격요건 3가지 자동 검증 (도메인 메서드 호출)
3. 검증 통과 시 HedgeRelationship 저장
4. FxForwardContract.hedgeDesignationDate 업데이트
5. 헤지 문서화 정보 자동 생성 (JSON 구조체로 저장)
6. 검증 결과 및 지정 결과 반환 (검증 실패 시도 결과는 반환 — 에러코드 포함)

**응답**: 하단 섹션 6 참조

---

### 5.2 단건 조회

```
GET /api/v1/hedge-relationships/{id}
```

**응답**: HedgeRelationship 전체 필드 + 연계된 HedgedItem + FxForwardContract 요약

---

### 5.3 목록 조회

```
GET /api/v1/hedge-relationships
```

**쿼리 파라미터**:

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| hedgeType | String | FAIR_VALUE / CASH_FLOW 필터 |
| status | String | DESIGNATED / DISCONTINUED 등 필터 |
| eligibilityStatus | String | ELIGIBLE / INELIGIBLE 필터 |
| page | Integer | 페이지 번호 (0-based) |
| size | Integer | 페이지 크기 (기본: 20) |

**응답**: 페이지네이션 목록 (요약 정보)

---

## 6. 자동 검증 결과 응답 구조

데모 화면 2의 "K-IFRS 1109호 6.4.1 적격요건 검증" 박스를 구현하는 응답 구조.

```json
{
  "hedgeRelationshipId": "HR-2026-001",
  "designationDate": "2026-04-01",
  "hedgeType": "CASH_FLOW",
  "eligibilityStatus": "ELIGIBLE",

  "eligibilityCheckResult": {
    "overallResult": "PASS",
    "checkedAt": "2026-04-01T09:00:00",
    "kifrsReference": "K-IFRS 1109호 6.4.1",

    "condition1EconomicRelationship": {
      "result": "PASS",
      "underlyingMatch": true,
      "notionalCoverageRatio": 1.00,
      "maturityMatch": true,
      "oppositeDirection": true,
      "details": "명목금액 1:1 매칭 확인 / 만기 일치 (2026-07-01)",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(가), B6.4.1"
    },

    "condition2CreditRisk": {
      "result": "PASS",
      "hedgedItemCreditRating": "A",
      "hedgingInstrumentCreditRating": "AA",
      "creditRiskDominant": false,
      "details": "양측 모두 투자등급 (BBB 이상)",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8"
    },

    "condition3HedgeRatio": {
      "result": "PASS",
      "hedgeRatio": 1.00,
      "hedgeRatioPercent": 100.0,
      "withinAcceptableRange": true,
      "lowerBound": 0.80,
      "upperBound": 1.25,
      "details": "헤지비율 100% — 6.4.1(c) 충족",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
    }
  },

  "documentationGenerated": true,
  "documentationSummary": {
    "hedgedItem": "USD 정기예금 $10,000,000 (만기 2026-07-01)",
    "hedgingInstrument": "USD/KRW 통화선도 (계약환율: 1,350원, 만기 2026-07-01)",
    "hedgedRisk": "외화위험 (USD/KRW 환율 변동)",
    "riskManagementObjective": "사용자 입력값",
    "hedgeStrategy": "사용자 입력값",
    "effectivenessAssessmentMethod": "Dollar-offset (B6.4.12)"
  },

  "hedgedItem": {
    "hedgedItemId": "HI-2026-001",
    "itemType": "FX_DEPOSIT",
    "currency": "USD",
    "notionalAmount": 10000000,
    "maturityDate": "2026-07-01"
  },

  "hedgingInstrument": {
    "contractId": "INS-2026-001",
    "contractForwardRate": 1350.00,
    "maturityDate": "2026-07-01",
    "notionalAmountUsd": 10000000
  }
}
```

**검증 실패 시 응답 예시**:

```json
{
  "hedgeRelationshipId": null,
  "eligibilityStatus": "INELIGIBLE",

  "eligibilityCheckResult": {
    "overallResult": "FAIL",
    "condition1EconomicRelationship": { "result": "PASS" },
    "condition2CreditRisk": {
      "result": "FAIL",
      "creditRiskDominant": true,
      "details": "헤지수단 거래상대방 신용등급 BB (비투자등급) — 신용위험 지배 가능성",
      "kifrsReference": "K-IFRS 1109호 B6.4.7"
    },
    "condition3HedgeRatio": { "result": "PASS" }
  },

  "errors": [
    {
      "errorCode": "HD_002",
      "message": "신용위험 적격요건 미충족: 거래상대방 신용등급이 투자등급 미만입니다.",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(나)"
    }
  ]
}
```

---

## 7. 예외 처리

| 상황 | 처리 방법 | 에러코드 |
|------|-----------|---------|
| 존재하지 않는 FxForwardContract 지정 | BusinessException 발생 | HD_001 |
| 신용위험 적격요건 미충족 (조건 2 실패) | 검증 결과 반환 (HTTP 422) | HD_002 |
| 헤지비율 범위 이탈 (조건 3 실패) | 검증 결과 반환 (HTTP 422) | HD_003 |
| 경제적 관계 미존재 (조건 1 실패) | 검증 결과 반환 (HTTP 422) | HD_004 |
| 이미 다른 헤지관계에 지정된 계약 | BusinessException 발생 | HD_005 |
| 만기 초과 계약 지정 시도 | BusinessException 발생 | HD_006 |
| 헤지기간 종료일이 지정일보다 이전 | BusinessException 발생 | HD_007 |
| 헤지대상과 헤지수단의 통화 미매칭 | BusinessException 발생 (조건 1 판정 전 사전 차단) | HD_008 |
| 존재하지 않는 위험회피관계 조회 | BusinessException 발생 | HD_009 |

> **설계 원칙**: 적격요건 미충족(HD_002~HD_004)은 `BusinessException`을 즉시 던지지 않고 검증 결과 객체를 완전히 채워서 HTTP 422로 반환한다. 이를 통해 프론트엔드가 "어떤 조건이 왜 실패했는지"를 상세히 표시할 수 있다.

---

## 8. 데모 시나리오 검증값

**가나금융투자 박지영 과장 케이스 (DEMO_SCENARIO.md 화면 2)**

```
입력값:
- hedgeType: CASH_FLOW (현금흐름 위험회피)
- hedgedRisk: FOREIGN_CURRENCY
- designationDate: 2026-04-01
- hedgePeriodEnd: 2026-07-01
- hedgeRatio: 1.00

헤지대상 (HedgedItem):
- itemType: FX_DEPOSIT
- currency: USD
- notionalAmount: 10,000,000
- maturityDate: 2026-07-01
- counterpartyCreditRating: A (투자등급)

헤지수단 (FxForwardContract 연계):
- contractId: INS-2026-001
- notionalAmountUsd: 10,000,000
- contractForwardRate: 1,350원
- maturityDate: 2026-07-01
- counterparty: 가나은행 (신용등급: AA 가정)

기대 검증 결과:
- condition1 (경제적 관계): PASS
  → USD/KRW 동일 기초변수, 명목금액 100% 매칭, 만기 일치
- condition2 (신용위험): PASS
  → 헤지대상 A등급, 헤지수단 거래상대방 AA등급 — 모두 투자등급
- condition3 (헤지비율): PASS
  → 헤지비율 = $10M / $10M = 100% (80~125% 범위 내)
- overallResult: PASS → eligibilityStatus: ELIGIBLE
- documentationGenerated: true
```

---

## 9. 백엔드 에이전트 구현 지침

### 9.1 도메인 설계 원칙

**HedgeRelationship 도메인 엔티티 내 비즈니스 메서드**:

도메인 엔티티가 적격요건 검증 로직을 직접 보유해야 한다. `@Service`는 오케스트레이션(트랜잭션 관리, 레포지토리 호출)만 담당하고, 핵심 판단 로직은 도메인에 위임한다.

```
HedgeRelationship 엔티티 핵심 메서드 (코드 작성은 백엔드 에이전트 담당):
- validateEligibility(HedgeDesignationContext context): EligibilityCheckResult
  → 3가지 조건을 순서대로 검증하여 EligibilityCheckResult 반환
  → 중간에 실패해도 모든 조건 검증 후 종합 결과 반환 (fail-fast 금지)

- checkEconomicRelationship(HedgedItem item, FxForwardContract instrument): ConditionResult
  → 기초변수 동일성, 명목금액 커버율, 만기 방향성, 반대 방향 움직임 판단

- checkCreditRiskNotDominant(CreditRating hedgedItemRating, CreditRating counterpartyRating): ConditionResult
  → 양측 신용등급이 투자등급(BBB 이상) 이상인지 판정

- checkHedgeRatio(BigDecimal hedgeRatio): ConditionResult
  → 0.80 ≤ hedgeRatio ≤ 1.25 범위 검증
```

### 9.2 EligibilityCheckResult 값 객체

도메인 내부에 `EligibilityCheckResult` 값 객체(Value Object) 정의. 불변(immutable) 설계 권장.

```
EligibilityCheckResult:
- overallResult: PASS / FAIL
- condition1: ConditionResult (result, details, kifrsReference)
- condition2: ConditionResult (result, details, kifrsReference)
- condition3: ConditionResult (result, details, hedgeRatioValue, kifrsReference)
- checkedAt: LocalDateTime
```

### 9.3 서비스 계층 역할

```
HedgeDesignationService (@Service):
1. fxForwardContractRepository.findById() — 계약 조회 및 사전 유효성 검증
2. HedgeRelationship.validateEligibility() — 도메인 메서드 호출
3. hedgeRelationshipRepository.save() — 저장 (검증 결과 포함)
4. fxForwardContractRepository.save() — designationDate 업데이트
5. 트랜잭션 경계 관리 (@Transactional)
```

### 9.4 FxForwardContract 연계

기존 `FxForwardContract` 엔티티에 다음 필드 추가:
- `hedgeRelationshipId` (nullable String) — 헤지 지정 시 채워짐
- `hedgeDesignationDate` (nullable LocalDate) — 이미 존재하는 필드 활용

`HedgeRelationship` 엔티티에:
- `fxForwardContractId` (not null String) — FK 역할

### 9.5 K-IFRS 조항 주석 의무

모든 검증 메서드에 K-IFRS 조항 Javadoc 필수:

```java
/**
 * K-IFRS 1109호 6.4.1 적격요건 종합 검증
 *
 * @see K-IFRS 1109호 6.4.1(3)(가) 경제적 관계 (B6.4.1 참조)
 * @see K-IFRS 1109호 6.4.1(3)(나) 신용위험 지배적 아님 (B6.4.7~B6.4.8 참조)
 * @see K-IFRS 1109호 6.4.1(3)(다) 헤지비율 (B6.4.9~B6.4.11 참조)
 */
public EligibilityCheckResult validateEligibility(...) { ... }
```

### 9.6 구현 순서 (권장)

1. `CreditRating` 열거형 (AAA~D, 투자등급 여부 메서드 포함)
2. `ConditionResult` 값 객체 및 `EligibilityCheckResult` 값 객체
3. `HedgedItem` 엔티티 및 Repository
4. `HedgeRelationship` 엔티티 (도메인 메서드 포함) 및 Repository
5. `FxForwardContract` 엔티티에 hedgeRelationshipId 필드 추가
6. `HedgeDesignationService` (오케스트레이션)
7. `HedgeDesignationController` (REST API)
8. Request/Response DTO (EligibilityCheckResult → Response 변환 포함)
9. 단위테스트: `HedgeRelationshipTest` (도메인 메서드 3조건 각각 PASS/FAIL 케이스)
10. 통합테스트: `HedgeDesignationControllerTest` (데모 시나리오 검증값 기준)

### 9.7 BigDecimal 사용 의무

헤지비율, 명목금액, 신용등급 비율 등 모든 수치 연산에 `BigDecimal` 필수.

```java
// ✅ 올바른 헤지비율 계산 예시
BigDecimal coverageRatio = hedgingInstrumentNotional
    .divide(hedgedItemNotional, 6, RoundingMode.HALF_UP);
```

---

## 10. 다음 단계

- **백엔드 에이전트**: 이 요구사항을 기반으로 구현 시작
- **연계 기능**: 공정가치 평가(fair-value-fx-forward) — HedgeRelationship.fxForwardContractId로 연계
- **연계 기능**: 유효성 테스트(effectiveness-test) — HedgeRelationship의 hedgeType과 hedgeRatio를 입력으로 사용
- **연계 기능**: 자동 분개(journal-entry) — HedgeRelationship.hedgeType에 따라 OCI/P&L 분기

---

*K-IFRS 근거: 1109호 6.4.1, 6.5.2, 6.5.5, 6.5.8, 6.5.11, 6.5.12, B6.4.1, B6.4.7~B6.4.8, B6.4.9~B6.4.11, B6.4.12*
*작성 기반: RAG 검색 (K-IFRS 1109호 B6.4.1, B6.4.7, 6.5.2, 6.5.11, 6.5.12 조항 확인) + K-IFRS 1109호 전문가 지식*
*2026-04-19 최초 작성*
