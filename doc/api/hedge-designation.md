# 헤지 지정 및 K-IFRS 적격요건 자동 검증 API 명세서

**버전**: v1.1
**최초 작성일**: 2026-04-19
**최종 수정일**: 2026-04-20
**작성자**: documentation-writer 에이전트
**적용 기능**: hedge-designation
**검증 기준**: requirements/hedge-designation.md (accounting-expert 에이전트 v1.0)

**변경 이력**

| 버전 | 날짜 | 내용 |
|---|---|---|
| v1.0 | 2026-04-19 | 최초 작성 |
| v1.1 | 2026-04-20 | Append-Only 원칙 추가 (헤지 재지정 시 항상 새 레코드 생성), 목록 API 페이징 파라미터 명세 보강, 상세보기/재제출 패턴 추가 |

---

## 목차

1. [개요](#1-개요)
2. [Base URL 및 공통 사항](#2-base-url-및-공통-사항)
3. [엔드포인트 상세](#3-엔드포인트-상세)
   - [3.1 헤지 지정 및 적격요건 자동 검증 (POST)](#31-헤지-지정-및-적격요건-자동-검증)
   - [3.2 헤지관계 단건 조회 (GET)](#32-헤지관계-단건-조회)
   - [3.3 헤지관계 목록 조회 (GET)](#33-헤지관계-목록-조회)
4. [요청 필드 정의](#4-요청-필드-정의)
5. [응답 필드 정의](#5-응답-필드-정의)
   - [5.1 헤지 지정 응답 전체 구조](#51-헤지-지정-응답-전체-구조)
   - [5.2 적격요건 검증 결과 상세 (3조건)](#52-적격요건-검증-결과-상세-3조건)
   - [5.3 목록 조회 응답 구조](#53-목록-조회-응답-구조)
6. [에러 코드 목록](#6-에러-코드-목록)
7. [데모 시나리오 JSON 예시](#7-데모-시나리오-json-예시)
8. [BigDecimal 직렬화 주의사항](#8-bigdecimal-직렬화-주의사항)

---

## 1. 개요

통화선도 계약을 위험회피수단으로 지정하고, K-IFRS 1109호 6.4.1이 규정하는 적격요건 3가지를 자동으로 검증하는 API입니다.

- **기능 범위**: 헤지 지정 + 적격요건 자동 검증 + 헤지 문서화 자동 생성
- **지원 헤지 유형**: 공정가치 위험회피(FAIR_VALUE), 현금흐름 위험회피(CASH_FLOW)
- **미지원 헤지 유형**: 해외사업장순투자 위험회피 (본 개발 시 추가 예정)
- **적격요건 3조건**: ① 경제적 관계 존재, ② 신용위험 지배적 아님, ③ 헤지비율 적절
- **K-IFRS 근거**: 1109호 6.4.1, 6.4.1(3)(가)~(다), B6.4.1, B6.4.7~B6.4.8, B6.4.9~B6.4.11

---

## 2. Base URL 및 공통 사항

```
Base URL:  http://localhost:8090/api/v1
Content-Type: application/json
Accept: application/json
```

### 공통 응답 코드

| HTTP 상태 코드 | 의미 |
|---|---|
| 201 Created | 헤지 지정 성공 (적격요건 PASS) |
| 200 OK | 단건/목록 조회 성공 |
| 400 Bad Request | 입력값 유효성 오류 |
| 404 Not Found | 리소스 없음 (HD_009) |
| 422 Unprocessable Entity | 적격요건 미충족 또는 비즈니스 규칙 위반 (HD_001~HD_008) |
| 500 Internal Server Error | 서버 내부 오류 |

> **설계 원칙**: 적격요건 미충족(HD_002~HD_004)은 예외를 즉시 던지지 않고 검증 결과 객체를 완전히 채워 HTTP 422로 반환합니다. 프론트엔드가 "어떤 조건이 왜 실패했는지"를 상세히 표시할 수 있도록 설계되었습니다.

---

## 3. 엔드포인트 상세

### 3.1 헤지 지정 및 적격요건 자동 검증

```
POST /api/v1/hedge-relationships
```

위험회피수단(통화선도 계약)과 위험회피대상항목(외화예금 등)을 연결하여 헤지관계를 수립합니다. 저장과 동시에 K-IFRS 1109호 6.4.1 적격요건 3가지를 자동으로 검증합니다.

**처리 흐름**

1. FxForwardContract 유효성 사전 확인 (존재 여부, ACTIVE 상태, 중복 지정 여부)
2. 통화 매칭 사전 차단 (헤지대상 통화 ≠ 헤지수단 기초통화 시 즉시 거부)
3. 적격요건 3조건 자동 검증 (중간 실패해도 3조건 모두 검증 후 종합 결과 반환)
4. 검증 통과 시 HedgeRelationship 저장
5. FxForwardContract 헤지 지정일(hedgeDesignationDate) 자동 업데이트
6. 헤지 문서화 정보 자동 생성 (documentationSummary)
7. 결과 반환 (검증 실패 시에도 결과 상세 반환)

**HTTP 응답 코드**

| 조건 | HTTP 코드 |
|---|---|
| 적격요건 PASS, 헤지 지정 완료 | 201 Created |
| 적격요건 FAIL (1개 이상 조건 불충족) | 422 Unprocessable Entity |
| 사전 유효성 오류 (HD_001, HD_005~HD_008) | 422 Unprocessable Entity |

---

### 3.2 헤지관계 단건 조회

```
GET /api/v1/hedge-relationships/{id}
```

헤지관계 ID로 특정 헤지관계의 전체 정보를 조회합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| id | String | 필수 | 헤지관계 ID (예: HR-2026-001) |

**응답**: HedgeRelationship 전체 필드 + 연계된 HedgedItem + FxForwardContract 요약 + eligibilityCheckResult

**에러 응답 예시 (존재하지 않는 ID)**

```json
{
  "errorCode": "HD_009",
  "message": "존재하지 않는 위험회피관계 ID입니다: HR-9999-999"
}
```

---

### 3.3 헤지관계 목록 조회

```
GET /api/v1/hedge-relationships
```

등록된 모든 헤지관계를 조건에 따라 필터링하여 페이지네이션 목록으로 조회합니다. **createdAt 내림차순** (최신 레코드 우선)으로 반환합니다.

**Append-Only**: 헤지 재지정(재제출)은 기존 레코드를 수정하지 않고 항상 새 헤지관계 레코드를 생성합니다. 이력 전체가 목록에 누적되므로 재지정 이전 내역도 조회할 수 있습니다.

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 | 예시 |
|---|---|---|---|---|---|
| hedgeType | String | 선택 | - | FAIR_VALUE / CASH_FLOW 필터 | `CASH_FLOW` |
| status | String | 선택 | - | DESIGNATED / DISCONTINUED / REBALANCED / MATURED 필터 | `DESIGNATED` |
| eligibilityStatus | String | 선택 | - | ELIGIBLE / INELIGIBLE / PENDING 필터 | `ELIGIBLE` |
| page | Integer | 선택 | 0 | 페이지 번호 (0-based) | `0` |
| size | Integer | 선택 | 20 | 페이지 크기 | `20` |

**요청 예시**

```
GET /api/v1/hedge-relationships?hedgeType=CASH_FLOW&status=DESIGNATED&page=0&size=20
```

**응답 구조** (PageResponse)

| 필드명 | 타입 | 설명 |
|---|---|---|
| content | Array | 헤지관계 목록 (요약 정보) |
| totalElements | Long | 전체 건수 |
| totalPages | Integer | 전체 페이지 수 |
| size | Integer | 페이지 크기 |
| number | Integer | 현재 페이지 번호 (0-based) |

### 3.4 헤지 이력 상세보기 및 재제출

목록에서 특정 헤지관계 레코드를 클릭하면 상세 패널이 표시됩니다. 상세 패널에는 **"이 값으로 재지정"** 버튼이 제공되며, 클릭 시 해당 레코드의 입력값(헤지 유형, 헤지비율, 위험관리 목적 등)이 지정 폼에 자동으로 채워집니다. 값을 수정하거나 그대로 **[저장]** 버튼을 클릭하면 새 헤지관계 레코드가 INSERT됩니다 (기존 레코드 변경 없음).

---

## 4. 요청 필드 정의

`POST /api/v1/hedge-relationships` 요청 본문 필드.

### 헤지관계 기본 정보 필드

| 필드명 | 타입 | 필수 | 설명 | DEMO 예시 |
|---|---|---|---|---|
| hedgeType | String | 필수 | 위험회피 유형: `FAIR_VALUE` 또는 `CASH_FLOW` | `"CASH_FLOW"` |
| hedgedRisk | String | 필수 | 회피대상 위험: `FOREIGN_CURRENCY` / `INTEREST_RATE` / `COMMODITY` | `"FOREIGN_CURRENCY"` |
| designationDate | String (ISO 날짜) | 필수 | 헤지 공식 지정일 (yyyy-MM-dd) | `"2026-04-01"` |
| hedgePeriodEnd | String (ISO 날짜) | 필수 | 위험회피기간 종료일 — 지정일 이후여야 함 (yyyy-MM-dd) | `"2026-07-01"` |
| hedgeRatio | BigDecimal | 필수 | 헤지비율 (예: 1.00 = 100%, 소수 표현, 0 초과) | `1.00` |
| riskManagementObjective | String | 필수 | 위험관리 목적 (K-IFRS 1109호 6.4.1(2) 문서화 요건, 최대 1000자) | `"USD 외화예금의 환율 변동 위험을 통화선도로 관리"` |
| hedgeStrategy | String | 필수 | 위험회피 전략 설명 (K-IFRS 1109호 6.4.1(2) 문서화 요건, 최대 1000자) | `"만기 일치 통화선도 매도를 통한 USD/KRW 현금흐름 고정"` |
| fxForwardContractId | String | 필수 | 위험회피수단으로 지정할 기존 통화선도 계약 ID (예: INS-2026-001) | `"INS-2026-001"` |

### 위험회피대상항목 (hedgedItem) 필드

`hedgedItem` 객체는 요청 본문에 중첩 객체로 포함됩니다.

| 필드명 | 타입 | 필수 | 설명 | DEMO 예시 |
|---|---|---|---|---|
| itemType | String | 필수 | 항목 유형: `FX_DEPOSIT` / `FIXED_RATE_BOND` / `FLOATING_RATE_BOND` | `"FX_DEPOSIT"` |
| currency | String | 필수 | 외화 통화 코드 (ISO 4217, 3자리) | `"USD"` |
| notionalAmount | BigDecimal | 필수 | 명목금액 (외화 기준, 0 초과) | `10000000` |
| maturityDate | String (ISO 날짜) | 필수 | 만기일 (yyyy-MM-dd) | `"2026-07-01"` |
| counterpartyCreditRating | String | 필수 | 거래상대방 신용등급 (AAA/AA/A/BBB/BB/B/CCC/D) | `"A"` |
| interestRateType | String | 선택 | 금리 유형: `FIXED` / `FLOATING` (채권류 항목에 해당) | `"FLOATING"` |
| interestRate | BigDecimal | 선택 | 금리 (소수 표현, interestRateType 입력 시 필수) | `0.045` |
| description | String | 선택 | 항목 설명 (최대 500자) | `"미국 달러 정기예금 만기 2026-07-01"` |

---

## 5. 응답 필드 정의

### 5.1 헤지 지정 응답 전체 구조

`POST /api/v1/hedge-relationships` 성공(201) 및 실패(422) 응답의 최상위 필드.

| 필드명 | 타입 | 설명 | DEMO 예시 |
|---|---|---|---|
| hedgeRelationshipId | String | 헤지관계 ID (지정 성공 시 생성, 실패 시 null) | `"HR-2026-001"` |
| designationDate | String (ISO 날짜) | 헤지 지정일 | `"2026-04-01"` |
| hedgeType | String | 위험회피 유형 | `"CASH_FLOW"` |
| hedgedRisk | String | 회피대상 위험 | `"FOREIGN_CURRENCY"` |
| status | String | 헤지관계 상태 (지정 성공 시 `DESIGNATED`, 실패 시 null) | `"DESIGNATED"` |
| eligibilityStatus | String | 적격요건 검증 결과: `ELIGIBLE` / `INELIGIBLE` | `"ELIGIBLE"` |
| eligibilityCheckResult | Object | 적격요건 3조건 검증 상세 결과 (하단 5.2 참조) | (객체) |
| documentationGenerated | Boolean | 헤지 문서화 자동 생성 여부 | `true` |
| documentationSummary | Object | 자동 생성된 헤지 문서화 요약 | (객체) |
| hedgedItem | Object | 위험회피대상항목 정보 | (객체) |
| hedgingInstrument | Object | 위험회피수단(통화선도) 요약 정보 | (객체) |
| errors | Array | 오류 목록 (검증 실패 시 채워짐, 성공 시 빈 배열) | `[]` |

### 5.2 적격요건 검증 결과 상세 (3조건)

`eligibilityCheckResult` 객체 구조.

#### 최상위 필드

| 필드명 | 타입 | 설명 |
|---|---|---|
| overallResult | String | 종합 결과: `PASS` / `FAIL` |
| checkedAt | String (ISO 날짜시간) | 검증 수행 일시 |
| kifrsReference | String | 적용 K-IFRS 조항 |

#### condition1EconomicRelationship (조건 1: 경제적 관계)

| 필드명 | 타입 | 설명 |
|---|---|---|
| result | String | `PASS` / `FAIL` |
| underlyingMatch | Boolean | 기초변수(통화쌍 등) 동일 여부 |
| notionalCoverageRatio | BigDecimal | 명목금액 커버율 (헤지수단 명목금액 / 헤지대상 노출금액) |
| maturityMatch | Boolean | 만기 일치 여부 (헤지수단 만기 >= 헤지대상 만기) |
| oppositeDirection | Boolean | 헤지수단과 헤지대상의 가치 변동 반대 방향 여부 |
| details | String | 검증 결과 상세 설명 |
| kifrsReference | String | `"K-IFRS 1109호 6.4.1(3)(가), B6.4.1"` |

#### condition2CreditRisk (조건 2: 신용위험)

| 필드명 | 타입 | 설명 |
|---|---|---|
| result | String | `PASS` / `FAIL` |
| hedgedItemCreditRating | String | 위험회피대상항목 거래상대방 신용등급 |
| hedgingInstrumentCreditRating | String | 위험회피수단 거래상대방 신용등급 |
| creditRiskDominant | Boolean | 신용위험이 경제적 관계 변동보다 지배적인지 여부 (true이면 FAIL) |
| details | String | 검증 결과 상세 설명 |
| kifrsReference | String | `"K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8"` |

#### condition3HedgeRatio (조건 3: 헤지비율)

| 필드명 | 타입 | 설명 |
|---|---|---|
| result | String | `PASS` / `FAIL` |
| hedgeRatio | BigDecimal | 헤지비율 (소수, 예: 1.00) |
| hedgeRatioPercent | BigDecimal | 헤지비율 (%, 예: 100.0) |
| withinAcceptableRange | Boolean | 수용 가능 범위(80%~125%) 이내 여부 |
| lowerBound | BigDecimal | 하한 (0.80) |
| upperBound | BigDecimal | 상한 (1.25) |
| details | String | 검증 결과 상세 설명 |
| kifrsReference | String | `"K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"` |

### 5.3 목록 조회 응답 구조

`GET /api/v1/hedge-relationships` 응답은 PageResponse 래퍼로 감쌉니다. 페이징 파라미터 및 응답 구조 상세는 [3.3 헤지관계 목록 조회](#33-헤지관계-목록-조회) 섹션을 참조하십시오.

content 배열의 각 항목 주요 필드:

| 필드명 | 타입 | 설명 |
|---|---|---|
| hedgeRelationshipId | String | 헤지관계 ID |
| hedgeType | String | 위험회피 유형 |
| hedgedRisk | String | 회피대상 위험 |
| designationDate | String | 헤지 지정일 |
| hedgePeriodEnd | String | 위험회피기간 종료일 |
| hedgeRatio | BigDecimal | 헤지비율 |
| status | String | 헤지관계 상태 |
| eligibilityStatus | String | 적격요건 검증 결과 |
| fxForwardContractId | String | 연계된 통화선도 계약 ID |
| createdAt | String | 생성 일시 |

---

## 6. 에러 코드 목록

| 에러코드 | HTTP 상태 | 발생 조건 | 메시지 예시 |
|---|---|---|---|
| HD_001 | 422 | 존재하지 않는 FxForwardContract ID 지정 | `"존재하지 않는 통화선도 계약입니다: INS-9999-999"` |
| HD_002 | 422 | 조건 2 실패: 신용위험이 지배적 (투자등급 미만 신용등급) | `"신용위험 적격요건 미충족: 거래상대방 신용등급이 투자등급(BBB) 미만입니다."` |
| HD_003 | 422 | 조건 3 실패: 헤지비율이 80%~125% 범위 이탈 | `"헤지비율 적격요건 미충족: 헤지비율 60.0%는 허용범위(80%~125%) 이탈입니다."` |
| HD_004 | 422 | 조건 1 실패: 경제적 관계 미존재 (기초변수 불일치 등) | `"경제적 관계 적격요건 미충족: 헤지수단 만기(2026-06-01)가 헤지대상 만기(2026-07-01)보다 이전입니다."` |
| HD_005 | 422 | 이미 다른 헤지관계에 지정된 계약 (중복 지정 금지) | `"이미 헤지 지정된 통화선도 계약입니다: INS-2026-001 (HR-2026-001)"` |
| HD_006 | 422 | 만기 초과 계약(status ≠ ACTIVE) 지정 시도 | `"만기 초과 또는 비활성 상태의 계약은 헤지 지정할 수 없습니다: INS-2026-001"` |
| HD_007 | 422 | 위험회피기간 종료일(hedgePeriodEnd)이 지정일(designationDate)보다 이전 | `"위험회피기간 종료일(2026-03-01)이 지정일(2026-04-01)보다 이전입니다."` |
| HD_008 | 422 | 헤지대상 통화와 헤지수단 기초통화 불일치 (경제적 관계 사전 차단) | `"헤지대상 통화(EUR)와 헤지수단 기초통화(USD)가 일치하지 않습니다."` |
| HD_009 | 404 | 존재하지 않는 헤지관계 ID 조회 | `"존재하지 않는 위험회피관계 ID입니다: HR-9999-999"` |
| HD_010 | 400 | 입력값 유효성 오류 (필수 필드 누락, 타입 오류 등) | `"입력값 유효성 오류"` (details 배열 포함) |

**에러 응답 형식 (단일 에러)**

```json
{
  "errorCode": "HD_005",
  "message": "이미 헤지 지정된 통화선도 계약입니다: INS-2026-001 (HR-2026-001)"
}
```

**에러 응답 형식 (유효성 오류 — 400)**

```json
{
  "errorCode": "HD_010",
  "message": "입력값 유효성 오류",
  "details": [
    { "field": "hedgeType", "message": "위험회피 유형은 필수입니다." },
    { "field": "hedgedItem.currency", "message": "통화 코드는 필수입니다." }
  ]
}
```

---

## 7. 데모 시나리오 JSON 예시

**가나금융투자 박지영 과장 케이스 — 화면 2 기준 (DEMO_SCENARIO.md)**

### 7.1 요청 예시 (적격요건 PASS 케이스)

```json
POST /api/v1/hedge-relationships

{
  "hedgeType": "CASH_FLOW",
  "hedgedRisk": "FOREIGN_CURRENCY",
  "designationDate": "2026-04-01",
  "hedgePeriodEnd": "2026-07-01",
  "hedgeRatio": 1.00,
  "riskManagementObjective": "USD 정기예금 만기 시 수령할 달러 현금흐름을 통화선도로 고정하여 환율 변동 위험을 제거",
  "hedgeStrategy": "만기 일치 USD/KRW 통화선도 매도 계약을 통한 현금흐름 위험회피",
  "fxForwardContractId": "INS-2026-001",
  "hedgedItem": {
    "itemType": "FX_DEPOSIT",
    "currency": "USD",
    "notionalAmount": 10000000,
    "maturityDate": "2026-07-01",
    "counterpartyCreditRating": "A",
    "interestRateType": "FLOATING",
    "interestRate": 0.045,
    "description": "미국 달러 정기예금 (가나은행, 만기 2026-07-01)"
  }
}
```

### 7.2 응답 예시 (적격요건 PASS — 201 Created)

```json
{
  "hedgeRelationshipId": "HR-2026-001",
  "designationDate": "2026-04-01",
  "hedgeType": "CASH_FLOW",
  "hedgedRisk": "FOREIGN_CURRENCY",
  "status": "DESIGNATED",
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
      "details": "USD/KRW 동일 기초변수 / 명목금액 1:1 매칭 (100%) / 만기 일치 (2026-07-01)",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(가), B6.4.1"
    },

    "condition2CreditRisk": {
      "result": "PASS",
      "hedgedItemCreditRating": "A",
      "hedgingInstrumentCreditRating": "AA",
      "creditRiskDominant": false,
      "details": "양측 모두 투자등급 (BBB 이상) — 헤지대상 A등급, 헤지수단 거래상대방 AA등급",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8"
    },

    "condition3HedgeRatio": {
      "result": "PASS",
      "hedgeRatio": 1.00,
      "hedgeRatioPercent": 100.0,
      "withinAcceptableRange": true,
      "lowerBound": 0.80,
      "upperBound": 1.25,
      "details": "헤지비율 100.0% — 허용범위(80%~125%) 내 충족",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
    }
  },

  "documentationGenerated": true,
  "documentationSummary": {
    "hedgedItem": "USD 정기예금 $10,000,000 (만기 2026-07-01)",
    "hedgingInstrument": "USD/KRW 통화선도 매도 (계약환율: 1,350원, 만기 2026-07-01)",
    "hedgedRisk": "외화위험 (USD/KRW 환율 변동)",
    "riskManagementObjective": "USD 정기예금 만기 시 수령할 달러 현금흐름을 통화선도로 고정하여 환율 변동 위험을 제거",
    "hedgeStrategy": "만기 일치 USD/KRW 통화선도 매도 계약을 통한 현금흐름 위험회피",
    "effectivenessAssessmentMethod": "Dollar-offset (K-IFRS 1109호 B6.4.12)"
  },

  "hedgedItem": {
    "hedgedItemId": "HI-2026-001",
    "itemType": "FX_DEPOSIT",
    "currency": "USD",
    "notionalAmount": 10000000,
    "maturityDate": "2026-07-01",
    "counterpartyCreditRating": "A"
  },

  "hedgingInstrument": {
    "contractId": "INS-2026-001",
    "contractForwardRate": 1350.00,
    "maturityDate": "2026-07-01",
    "notionalAmountUsd": 10000000
  },

  "errors": []
}
```

### 7.3 응답 예시 (적격요건 FAIL — 422 Unprocessable Entity)

신용등급 BB(비투자등급) 거래상대방으로 조건 2 실패한 경우.

```json
{
  "hedgeRelationshipId": null,
  "designationDate": "2026-04-01",
  "hedgeType": "CASH_FLOW",
  "hedgedRisk": "FOREIGN_CURRENCY",
  "status": null,
  "eligibilityStatus": "INELIGIBLE",

  "eligibilityCheckResult": {
    "overallResult": "FAIL",
    "checkedAt": "2026-04-01T09:05:00",
    "kifrsReference": "K-IFRS 1109호 6.4.1",

    "condition1EconomicRelationship": {
      "result": "PASS",
      "underlyingMatch": true,
      "notionalCoverageRatio": 1.00,
      "maturityMatch": true,
      "oppositeDirection": true,
      "details": "경제적 관계 충족",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(가), B6.4.1"
    },

    "condition2CreditRisk": {
      "result": "FAIL",
      "hedgedItemCreditRating": "A",
      "hedgingInstrumentCreditRating": "BB",
      "creditRiskDominant": true,
      "details": "헤지수단 거래상대방 신용등급 BB — 비투자등급으로 신용위험이 지배적일 가능성",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(나), B6.4.7"
    },

    "condition3HedgeRatio": {
      "result": "PASS",
      "hedgeRatio": 1.00,
      "hedgeRatioPercent": 100.0,
      "withinAcceptableRange": true,
      "lowerBound": 0.80,
      "upperBound": 1.25,
      "details": "헤지비율 100.0% — 허용범위 내 충족",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
    }
  },

  "documentationGenerated": false,
  "errors": [
    {
      "errorCode": "HD_002",
      "message": "신용위험 적격요건 미충족: 거래상대방 신용등급이 투자등급(BBB) 미만입니다.",
      "kifrsReference": "K-IFRS 1109호 6.4.1(3)(나)"
    }
  ]
}
```

---

## 8. BigDecimal 직렬화 주의사항

이 API의 금액·비율·헤지비율 필드는 모두 `BigDecimal` 타입입니다. JSON 직렬화 시 다음 사항에 유의하십시오.

### 8.1 숫자 형식 (Number, 문자열 아님)

```json
// 올바른 형식 (숫자)
"hedgeRatio": 1.00
"notionalAmount": 10000000

// 잘못된 형식 (문자열) — 파싱 오류 발생
"hedgeRatio": "1.00"
"notionalAmount": "10000000"
```

### 8.2 소수점 자리수 (Scale)

| 필드 분류 | Scale | 예시 |
|---|---|---|
| 헤지비율 (hedgeRatio) | 2자리 | `1.00`, `0.85` |
| 명목금액 커버율 (notionalCoverageRatio) | 4자리 | `1.0000`, `0.8500` |
| 명목금액 (notionalAmount) | 2자리 | `10000000.00` |
| 금리 (interestRate) | 6자리 | `0.045000` |

### 8.3 JavaScript/TypeScript 클라이언트 주의사항

명목금액(USD 10M = 10,000,000)처럼 큰 숫자는 JavaScript `Number` 타입 정밀도 한계로 오차가 발생할 수 있습니다. BigDecimal 필드는 문자열로 수신한 후 전용 라이브러리로 처리하는 것을 권장합니다.

---

*K-IFRS 근거: 1109호 6.4.1, 6.4.1(2), 6.4.1(3)(가)~(다), 6.5.2, B6.4.1, B6.4.7~B6.4.8, B6.4.9~B6.4.11, B6.4.12*
*요구사항 기준: requirements/hedge-designation.md (accounting-expert 에이전트, 2026-04-19)*
*작성일: 2026-04-19 / 최종 수정: 2026-04-20 / 버전: v1.1*
