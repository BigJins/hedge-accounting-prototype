# 통화선도 공정가치 평가 API 명세서

**버전**: v1.2
**최초 작성일**: 2026-04-19
**최종 수정일**: 2026-04-20
**작성자**: documentation-writer 에이전트
**검증 기준**: fx_forward_valuation_backend_v4_20260419.md (PASS)

**변경 이력**

| 버전 | 날짜 | 내용 |
|---|---|---|
| v1.0 | 2026-04-19 | 최초 작성 |
| v1.1 | 2026-04-19 | idempotent 캐싱 제거 (항상 재계산), 계약 업데이트 동작 추가, CRUD API 4개 추가 |
| v1.2 | 2026-04-20 | Append-Only 방식 전환 (평가 이력 덮어쓰기 제거), 목록 API 페이징 적용, 최신 레코드 우선 정렬, 상세보기/재제출 패턴 추가, unique constraint 제거 반영 |

---

## 목차

1. [개요](#1-개요)
2. [Base URL 및 공통 사항](#2-base-url-및-공통-사항)
3. [엔드포인트 상세](#3-엔드포인트-상세)
   - [3.1 공정가치 평가 실행 (POST)](#31-공정가치-평가-실행)
   - [3.2 평가 결과 단건 조회 (GET)](#32-평가-결과-단건-조회)
   - [3.3 전체 평가 이력 조회 (GET)](#33-전체-평가-이력-조회)
   - [3.4 계약별 평가 이력 조회 (GET)](#34-계약별-평가-이력-조회)
   - [3.5 전체 계약 목록 조회 (GET)](#35-전체-계약-목록-조회)
   - [3.6 계약 단건 조회 (GET)](#36-계약-단건-조회)
   - [3.7 계약 삭제 (DELETE)](#37-계약-삭제)
   - [3.8 평가 삭제 (DELETE)](#38-평가-삭제)
4. [요청 필드 정의](#4-요청-필드-정의)
5. [응답 필드 정의](#5-응답-필드-정의)
6. [에러 코드 목록](#6-에러-코드-목록)
7. [BigDecimal 직렬화 주의사항](#7-bigdecimal-직렬화-주의사항)
8. [Append-Only 설계 원칙](#8-append-only-설계-원칙)
9. [페이징 공통 응답 구조](#9-페이징-공통-응답-구조)

---

## 1. 개요

통화선도(FX Forward) 계약의 공정가치를 K-IFRS 1113호 기준(이자율 평형 이론, IRP)으로 산출하는 API입니다.

- **평가 방식**: IRP(Interest Rate Parity) 기반 현재가치 할인
- **공정가치 수준**: K-IFRS 1113호 Level 2 (관측가능한 시장 투입변수)
- **Day Count Convention**: KRW Actual/365 Fixed, USD Actual/360
- **K-IFRS 근거**: 1109호 6.5.8 / 1113호 61~66항, 72~90항

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
| 200 OK | 정상 처리 (중복 평가 요청 포함) |
| 201 Created | 신규 평가 생성 성공 |
| 400 Bad Request | 입력값 유효성 오류 |
| 404 Not Found | 리소스 없음 (FX_004) |
| 422 Unprocessable Entity | 비즈니스 규칙 위반 (FX_001~FX_003) |
| 500 Internal Server Error | 서버 내부 오류 |

---

## 3. 엔드포인트 상세

### 3.1 공정가치 평가 실행

```
POST /api/v1/valuations/fx-forward
```

IRP 공식으로 통화선도 공정가치를 계산하고 평가 이력을 저장합니다.

- 계약이 없으면 신규 등록 후 평가를 수행합니다.
- **동일 contractId가 이미 존재하면 계약 정보(명목원금, 선물환율, 만기일 등)를 요청값으로 갱신합니다.**
- **Append-Only**: 동일 contractId + 동일 valuationDate 조합이라도 기존 레코드를 덮어쓰지 않고 항상 새로운 평가 레코드를 INSERT합니다. `fx_forward_valuations` 테이블에는 `uk_valuation_contract_date` unique constraint가 없으므로 중복 날짜 INSERT가 허용됩니다.
- PoC 단계에서는 멱등성(idempotent) 캐싱을 적용하지 않습니다. 매 요청마다 실제 계산이 수행됩니다.

**HTTP 응답 코드**

| 조건 | HTTP 코드 |
|---|---|
| 신규 평가 생성 (항상) | 201 Created |

**요청 예시 (DEMO 수치)**

```json
{
  "contractId": "FX-2024-001",
  "notionalAmountUsd": 10000000.00,
  "contractForwardRate": 1380.0000,
  "contractDate": "2024-01-15",
  "maturityDate": "2024-07-15",
  "hedgeDesignationDate": "2024-01-15",
  "valuationDate": "2024-03-31",
  "spotRate": 1350.0000,
  "krwInterestRate": 0.035000,
  "usdInterestRate": 0.053000
}
```

**응답 예시 (DEMO 수치 기준)**

```json
{
  "valuationId": 1,
  "contractId": "FX-2024-001",
  "valuationDate": "2024-03-31",
  "spotRate": 1350.0000,
  "krwInterestRate": 0.035000,
  "usdInterestRate": 0.053000,
  "remainingDays": 92,
  "currentForwardRate": 1343.7098,
  "fairValue": -359728422.00,
  "previousFairValue": 0.00,
  "fairValueChange": -359728422.00,
  "fairValueLevel": "LEVEL_2",
  "createdAt": "2026-04-19T09:00:00"
}
```

---

### 3.2 평가 결과 단건 조회

```
GET /api/v1/valuations/fx-forward/{id}
```

평가 ID로 특정 평가 결과를 조회합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| id | Long | 필수 | 평가 ID (DB 자동 생성 숫자) |

**요청 예시**

```
GET /api/v1/valuations/fx-forward/1
```

**응답 예시**

```json
{
  "valuationId": 1,
  "contractId": "FX-2024-001",
  "valuationDate": "2024-03-31",
  "spotRate": 1350.0000,
  "krwInterestRate": 0.035000,
  "usdInterestRate": 0.053000,
  "remainingDays": 92,
  "currentForwardRate": 1343.7098,
  "fairValue": -359728422.00,
  "previousFairValue": 0.00,
  "fairValueChange": -359728422.00,
  "fairValueLevel": "LEVEL_2",
  "createdAt": "2026-04-19T09:00:00"
}
```

**에러 응답 예시 (존재하지 않는 ID)**

```json
{
  "errorCode": "FX_004",
  "message": "존재하지 않는 평가 ID입니다: 999"
}
```

---

### 3.3 전체 평가 이력 조회

```
GET /api/v1/valuations/fx-forward
```

저장된 모든 계약의 전체 평가 이력을 페이지네이션으로 조회합니다. **createdAt 내림차순** (최신 레코드 우선)으로 반환합니다.

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| page | Integer | 선택 | 0 | 페이지 번호 (0-based) |
| size | Integer | 선택 | 20 | 페이지 크기 |

**요청 예시**

```
GET /api/v1/valuations/fx-forward?page=0&size=20
```

**응답 예시** (PageResponse 구조)

```json
{
  "content": [
    {
      "valuationId": 3,
      "contractId": "FX-2024-001",
      "valuationDate": "2024-03-31",
      "spotRate": 1350.0000,
      "krwInterestRate": 0.035000,
      "usdInterestRate": 0.053000,
      "remainingDays": 92,
      "currentForwardRate": 1343.7098,
      "fairValue": -359728422.00,
      "previousFairValue": 0.00,
      "fairValueChange": -359728422.00,
      "fairValueLevel": "LEVEL_2",
      "createdAt": "2026-04-20T11:00:00"
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

- 평가 이력이 없는 경우 `content: []`, `totalElements: 0`을 반환합니다.

---

### 3.4 계약별 평가 이력 조회

```
GET /api/v1/valuations/fx-forward/contract/{contractId}
```

특정 계약번호에 대한 모든 평가 이력을 페이지네이션으로 조회합니다. **createdAt 내림차순** (최신 레코드 우선)으로 반환합니다.

Append-Only 방식이므로 동일 valuationDate에 여러 레코드가 존재할 수 있습니다. 화면에서는 가장 최근에 생성된 레코드(ID가 가장 큰 것)가 목록 상단에 표시됩니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| contractId | String | 필수 | 계약번호 (예: FX-2024-001) |

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| page | Integer | 선택 | 0 | 페이지 번호 (0-based) |
| size | Integer | 선택 | 20 | 페이지 크기 |

**요청 예시**

```
GET /api/v1/valuations/fx-forward/contract/FX-2024-001?page=0&size=20
```

**응답 예시 (동일 날짜 재평가 포함, 최신순)** (PageResponse 구조)

```json
{
  "content": [
    {
      "valuationId": 3,
      "contractId": "FX-2024-001",
      "valuationDate": "2024-03-31",
      "spotRate": 1352.0000,
      "krwInterestRate": 0.035000,
      "usdInterestRate": 0.053000,
      "remainingDays": 92,
      "currentForwardRate": 1345.7098,
      "fairValue": -340000000.00,
      "previousFairValue": 0.00,
      "fairValueChange": -340000000.00,
      "fairValueLevel": "LEVEL_2",
      "createdAt": "2026-04-20T11:00:00"
    },
    {
      "valuationId": 2,
      "contractId": "FX-2024-001",
      "valuationDate": "2024-03-31",
      "spotRate": 1350.0000,
      "krwInterestRate": 0.035000,
      "usdInterestRate": 0.053000,
      "remainingDays": 92,
      "currentForwardRate": 1343.7098,
      "fairValue": -359728422.00,
      "previousFairValue": 0.00,
      "fairValueChange": -359728422.00,
      "fairValueLevel": "LEVEL_2",
      "createdAt": "2026-04-20T09:00:00"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

**에러 응답 예시 (존재하지 않는 계약)**

```json
{
  "errorCode": "FX_004",
  "message": "존재하지 않는 계약번호입니다: FX-9999-999"
}
```

---

### 3.5 전체 계약 목록 조회

```
GET /api/v1/valuations/fx-forward/contracts
```

등록된 모든 통화선도 계약의 목록을 페이지네이션으로 조회합니다. 계약 정보(명목원금, 선물환율, 만기일 등)를 포함하며, 평가 이력은 포함되지 않습니다. **createdAt 내림차순** (최신 등록 계약 우선)으로 반환합니다.

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| page | Integer | 선택 | 0 | 페이지 번호 (0-based) |
| size | Integer | 선택 | 20 | 페이지 크기 |

**요청 예시**

```
GET /api/v1/valuations/fx-forward/contracts?page=0&size=20
```

**응답 예시** (PageResponse 구조)

```json
{
  "content": [
    {
      "contractId": "FX-2024-001",
      "notionalAmountUsd": 10000000.00,
      "contractForwardRate": 1380.0000,
      "contractDate": "2024-01-15",
      "maturityDate": "2024-07-15",
      "hedgeDesignationDate": "2024-01-15",
      "status": "ACTIVE",
      "createdAt": "2026-04-19T09:00:00",
      "updatedAt": "2026-04-19T09:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

- 등록된 계약이 없는 경우 `content: []`, `totalElements: 0`을 반환합니다.

---

### 3.6 계약 단건 조회

```
GET /api/v1/valuations/fx-forward/contracts/{contractId}
```

특정 계약번호의 계약 정보를 조회합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| contractId | String | 필수 | 계약번호 (예: FX-2024-001) |

**요청 예시**

```
GET /api/v1/valuations/fx-forward/contracts/FX-2024-001
```

**응답 예시**

```json
{
  "contractId": "FX-2024-001",
  "notionalAmountUsd": 10000000.00,
  "contractForwardRate": 1380.0000,
  "contractDate": "2024-01-15",
  "maturityDate": "2024-07-15",
  "hedgeDesignationDate": "2024-01-15",
  "status": "ACTIVE",
  "createdAt": "2026-04-19T09:00:00",
  "updatedAt": "2026-04-19T09:00:00"
}
```

**에러 응답 예시 (존재하지 않는 계약)**

```json
{
  "errorCode": "FX_004",
  "message": "존재하지 않는 계약번호입니다: FX-9999-999"
}
```

---

### 3.7 계약 삭제

```
DELETE /api/v1/valuations/fx-forward/contracts/{contractId}
```

특정 계약번호의 계약을 삭제합니다. **연관된 모든 평가 이력도 함께 삭제됩니다.**

> 주의: 이 작업은 복구 불가능합니다. 감사 대응이 필요한 계약은 삭제 전 반드시 평가 이력을 백업하십시오.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| contractId | String | 필수 | 삭제할 계약번호 (예: FX-2024-001) |

**요청 예시**

```
DELETE /api/v1/valuations/fx-forward/contracts/FX-2024-001
```

**응답 코드**

| 조건 | HTTP 코드 |
|---|---|
| 삭제 성공 | 204 No Content |
| 존재하지 않는 계약 | 404 Not Found (FX_004) |

---

### 3.8 평가 삭제

```
DELETE /api/v1/valuations/fx-forward/{id}
```

특정 평가 ID의 평가 결과를 삭제합니다. 계약 정보는 삭제되지 않습니다.

> 주의: 이 작업은 복구 불가능합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| id | Long | 필수 | 평가 ID (DB 자동 생성 숫자) |

**요청 예시**

```
DELETE /api/v1/valuations/fx-forward/1
```

**응답 코드**

| 조건 | HTTP 코드 |
|---|---|
| 삭제 성공 | 204 No Content |
| 존재하지 않는 평가 | 404 Not Found (FX_004) |

---

## 4. 요청 필드 정의

`POST /api/v1/valuations/fx-forward` 요청 본문 필드.

### 계약 정보 필드

| 필드명 | 타입 | 필수 | 최대 길이 | 설명 | DEMO 예시 |
|---|---|---|---|---|---|
| contractId | String | 필수 | 50자 | 통화선도 계약번호 (업무 식별자) | `"FX-2024-001"` |
| notionalAmountUsd | BigDecimal | 필수 | - | 명목원금 (USD, 0 초과) | `10000000.00` |
| contractForwardRate | BigDecimal | 필수 | - | 계약 선물환율 (KRW/USD, 소수점 4자리, 0 초과) | `1380.0000` |
| contractDate | String (ISO 날짜) | 필수 | - | 계약 체결일 (yyyy-MM-dd) | `"2024-01-15"` |
| maturityDate | String (ISO 날짜) | 필수 | - | 만기일 (계약일 이후, yyyy-MM-dd) | `"2024-07-15"` |
| hedgeDesignationDate | String (ISO 날짜) | 필수 | - | 헤지 공식 지정일 (K-IFRS 1109호 6.4.1) | `"2024-01-15"` |

### 평가 시장 데이터 필드

| 필드명 | 타입 | 필수 | 설명 | DEMO 예시 |
|---|---|---|---|---|
| valuationDate | String (ISO 날짜) | 필수 | 평가기준일 (만기일 이전, yyyy-MM-dd) | `"2024-03-31"` |
| spotRate | BigDecimal | 필수 | 평가기준일 현물환율 (KRW/USD, 0 초과) | `1350.0000` |
| krwInterestRate | BigDecimal | 필수 | 원화 무위험이자율 (국고채 기준, 소수 표현, 0 이상) | `0.035000` (= 3.5%) |
| usdInterestRate | BigDecimal | 필수 | 달러 무위험이자율 (SOFR 기준, 소수 표현, 0 이상) | `0.053000` (= 5.3%) |

> **이자율 입력 단위**: 퍼센트(%)가 아닌 소수(decimal)로 입력합니다.
> 3.5%는 `0.035000`, 5.3%는 `0.053000`으로 전송해야 합니다.

---

## 5. 응답 필드 정의

| 필드명 | 타입 | 설명 | DEMO 예시 |
|---|---|---|---|
| valuationId | Long | 평가 고유 ID (DB 자동 생성) | `1` |
| contractId | String | 계약번호 | `"FX-2024-001"` |
| valuationDate | String (ISO 날짜) | 평가기준일 | `"2024-03-31"` |
| spotRate | BigDecimal | 평가기준일 현물환율 (KRW/USD, scale=4) | `1350.0000` |
| krwInterestRate | BigDecimal | 원화이자율 입력값 (scale=6) | `0.035000` |
| usdInterestRate | BigDecimal | 달러이자율 입력값 (scale=6) | `0.053000` |
| remainingDays | Integer | 잔존일수 (평가기준일~만기일) | `92` |
| currentForwardRate | BigDecimal | IRP 산출 현재 선물환율 (KRW/USD, scale=4) | `1343.7098` |
| fairValue | BigDecimal | 공정가치 (KRW, scale=2) | `-359728422.00` |
| previousFairValue | BigDecimal | 전기 공정가치 (KRW, scale=2, 최초 평가 시 0) | `0.00` |
| fairValueChange | BigDecimal | 공정가치 변동액 = fairValue - previousFairValue (KRW) | `-359728422.00` |
| fairValueLevel | String | K-IFRS 1113호 공정가치 수준 (고정값) | `"LEVEL_2"` |
| createdAt | String (ISO 날짜시간) | 평가 생성일시 | `"2026-04-19T09:00:00"` |

---

## 6. 에러 코드 목록

| 에러코드 | HTTP 상태 | 발생 조건 | 메시지 예시 |
|---|---|---|---|
| FX_001 | 422 | 평가기준일이 만기일 이후인 경우 (만기 초과 계약) | `"평가기준일(2024-08-01)이 만기일(2024-07-15) 이후입니다."` |
| FX_002 | 422 | 현물환율 또는 명목원금이 0 이하인 경우 | `"현물환율은 0보다 커야 합니다."` |
| FX_003 | 422 | 이자율(원화 또는 달러)이 음수인 경우 | `"원화이자율은 0 이상이어야 합니다."` |
| FX_004 | 404 | 존재하지 않는 평가 ID 또는 계약번호 조회 | `"존재하지 않는 계약번호입니다: FX-9999-999"` |

**에러 응답 형식**

```json
{
  "errorCode": "FX_001",
  "message": "평가기준일(2024-08-01)이 만기일(2024-07-15) 이후입니다."
}
```

**유효성 오류 응답 형식 (400 Bad Request)**

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "입력값 유효성 오류",
  "details": [
    { "field": "spotRate", "message": "현물환율은 0보다 커야 합니다." },
    { "field": "contractId", "message": "계약번호는 필수입니다." }
  ]
}
```

---

## 7. BigDecimal 직렬화 주의사항

이 API의 금액·환율·이자율 필드는 모두 `BigDecimal` 타입입니다. JSON 직렬화 시 다음 사항에 유의하십시오.

### 7.1 숫자 형식 (Number, 문자열 아님)

```json
// 올바른 형식 (숫자)
"fairValue": -359728422.00
"spotRate": 1350.0000

// 잘못된 형식 (문자열) — 파싱 오류 발생
"fairValue": "-359728422.00"
"spotRate": "1350.0000"
```

### 7.2 소수점 자리수 (Scale)

| 필드 분류 | Scale | 예시 |
|---|---|---|
| 환율 (spot, forward, contract) | 4자리 | `1350.0000`, `1343.7098` |
| 이자율 (krw, usd) | 6자리 | `0.035000`, `0.053000` |
| 금액 (fairValue 등, KRW) | 2자리 | `-359728422.00` |

### 7.3 음수 공정가치

공정가치가 음수(`-`)이면 위험회피수단(통화선도)이 평가손실 포지션임을 의미합니다.

```json
"fairValue": -359728422.00   // 평가손 (달러 약세로 USD 선도매도 포지션 불리)
"fairValue": 120000000.00    // 평가익 (달러 강세로 USD 선도매도 포지션 유리)
```

### 7.4 JavaScript/TypeScript 클라이언트 주의사항

JavaScript의 `Number` 타입은 53비트 정밀도 한계로 대형 금액에서 오차가 발생할 수 있습니다.
10억 원 이상 규모의 금액 필드는 문자열로 수신한 후 전용 BigDecimal 라이브러리로 처리하는 것을 권장합니다.

```javascript
// 권장: 문자열 수신 후 처리
const fairValue = response.data.fairValue.toString();

// 위험: 대형 숫자 직접 사용 시 정밀도 손실 가능
const fairValue = response.data.fairValue; // 주의 필요
```

---

---

## 8. Append-Only 설계 원칙

### 8.1 원칙 설명

`POST /api/v1/valuations/fx-forward`는 **항상 새로운 평가 레코드를 INSERT**합니다. 기존 레코드를 UPDATE하거나 DELETE하지 않습니다.

- **동일 contractId + 동일 valuationDate** 조합으로 재요청해도 기존 레코드는 그대로 보존되고, 새 레코드가 추가됩니다.
- `fx_forward_valuations` 테이블에는 `(contract_id, valuation_date)` unique constraint가 없습니다.
- 헤지 재지정도 마찬가지로 항상 새 레코드를 생성합니다.

### 8.2 최신 레코드 우선 정렬

목록 API는 모두 `createdAt DESC` 기준으로 정렬합니다. 동일 valuationDate에 여러 레코드가 있을 경우, 가장 최근에 생성된 레코드가 목록 상단에 표시됩니다.

### 8.3 상세보기와 재제출

목록에서 특정 이력 레코드를 클릭하면 상세 패널이 열립니다. 상세 패널에는 **"이 값으로 재평가"** 버튼이 제공되며, 클릭 시 해당 레코드의 시장 데이터(spotRate, krwInterestRate, usdInterestRate, valuationDate 등)가 평가 입력 폼에 자동으로 채워집니다. 폼에서 값을 수정하거나 그대로 **[공정가치 평가 실행]** 버튼을 클릭하면 새 INSERT가 수행됩니다.

> 이 패턴은 과거 평가를 기준으로 감사용 재현(replay)이 필요할 때 유용합니다.

---

## 9. 페이징 공통 응답 구조

목록 API(`3.3`, `3.4`, `3.5`)는 모두 다음 PageResponse 구조로 응답합니다.

| 필드명 | 타입 | 설명 |
|---|---|---|
| content | Array | 실제 데이터 목록 |
| totalElements | Long | 전체 건수 (필터 적용 후) |
| totalPages | Integer | 전체 페이지 수 |
| size | Integer | 요청한 페이지 크기 |
| number | Integer | 현재 페이지 번호 (0-based) |

**쿼리 파라미터 공통**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| page | 0 | 첫 번째 페이지는 0 |
| size | 20 | 한 페이지에 반환할 건수 |

---

*K-IFRS 근거: 1109호 6.4.1, 6.5.8, B6.4.12, 예시 주석 37 / 1113호 문단 61~66, 72~90, 89항*
*백엔드 검증: fx_forward_valuation_backend_v4_20260419.md — 종합 판정 PASS*
