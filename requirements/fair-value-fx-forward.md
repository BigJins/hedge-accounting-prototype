# 요구사항 명세서: 통화선도 공정가치 평가

**기능명**: `fair-value-fx-forward`
**작성자**: accounting-expert 에이전트
**작성일**: 2026-04-19
**RAG 검색 완료**: ✅

---

## 1. 기능 개요

통화선도(FX Forward) 계약의 공정가치를 K-IFRS 1113호 기준에 따라 평가하는 기능.
헤지회계에서 위험회피수단(통화선도)의 공정가치 변동을 정확히 산출하여 유효성 테스트 및 회계분개에 활용한다.

---

## 2. K-IFRS 근거

### 핵심 조항

| 조항 | 내용 | 적용 |
|------|------|------|
| K-IFRS 1113호 9항 | 공정가치는 측정일에 시장참여자 사이의 정상거래에서 자산을 매도하거나 부채를 이전할 때 받거나 지급하는 가격 | 통화선도 평가 기준일 설정 |
| K-IFRS 1113호 72~90항 (Level 2) | 관측가능한 시장 투입변수 사용 시 수준 2 분류 — 대부분의 장외파생상품(FX Forward, IRS, CRS) 해당 | 통화선도 = Level 2 분류 |
| K-IFRS 1109호 6.5.8 | 공정가치위험회피 적용 시 위험회피수단을 공정가치로 측정하고 그 변동을 당기손익으로 인식 | 위험회피수단 평가손익 처리 |
| K-IFRS 1109호 6.4.1 | 위험회피관계 지정 요건: 경제적 관계, 신용위험 지배 금지, 헤지비율 1:1 원칙 | 지정 시 검증 |
| K-IFRS 1109호 B6.4.12 | 위험회피 효과는 개시시점부터 지속적으로 평가 — 적어도 매 보고기간 말 또는 중요한 변화 발생 시 | 평가 주기 |

### 공정가치 평가 산식 (이자율 평형 이론, IRP)

```
선물환율(Fair Forward Rate) = S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)

통화선도 공정가치 = (현재 선물환율 - 계약 선물환율) × 명목원금(USD) × 현가계수
현가계수 = 1 / (1 + r_KRW × T/365)
```

- `S₀`: 평가기준일 현물환율 (KRW/USD)
- `T`: 잔존일수 (평가기준일 ~ 만기일)
- `r_KRW`: 원화 무위험이자율 (국고채 기준)
- `r_USD`: 달러 무위험이자율 (SOFR 기준)
- 계약 선물환율: 계약 체결일에 확정된 환율

### Day Count Convention (일수 계산 관행)

| 통화 | 관행 | 분모 | 근거 |
|---|---|---|---|
| KRW | Actual/365 Fixed | 365 (윤년 무관 고정) | 한국 CD금리·국고채 자금시장 표준 |
| USD | Actual/360 | 360 (윤년 무관 고정) | USD SOFR·구LIBOR Money Market 국제 표준 |

**K-IFRS 근거**:
- K-IFRS 1113호 문단 61~66: 시장참여자가 사용하는 가격결정기법 및 투입변수 적용 원칙 (Level 2)
- K-IFRS 1113호 문단 89: 시장참여자의 가정 고려 (관측가능 투입변수 우선)
- K-IFRS 1109호 예시 주석 37: "일수 계산방법의 차이"를 헤지 비효과성 원인으로 명시적 인정

> 윤년(366일) 발생 시에도 분모는 통화별 고정 기준을 유지함. Actual/Actual(윤년 적용)은 국고채 장기물 쿠폰 계산에만 해당.

---

## 3. 비즈니스 요건

### 3.1 평가 대상
- **위험회피수단**: USD/KRW 통화선도 매도 계약 (USD 수취, KRW 지급)
- **위험회피대상항목**: USD 예금 (환율 변동 위험 노출)
- **데모 시나리오**: 가나금융투자 박지영 과장 — USD 10,000,000 예금을 통화선도로 헤지

### 3.2 입력 데이터

| 항목 | 설명 | 예시 | 필수 |
|------|------|------|------|
| 계약번호 | 통화선도 식별자 | FX-2024-001 | ✅ |
| 명목원금(USD) | 계약 USD 금액 | 10,000,000 | ✅ |
| 계약 선물환율 | 체결일 확정 환율 (KRW/USD) | 1,380.00 | ✅ |
| 계약일 | 헤지 지정일 | 2024-01-15 | ✅ |
| 만기일 | 결제 예정일 | 2024-07-15 | ✅ |
| 평가기준일 | 공정가치 산출 기준일 | 2024-03-31 | ✅ |
| 현물환율 (S₀) | 평가기준일 시장 현물환율 | 1,350.00 | ✅ |
| 원화이자율 (r_KRW) | 잔존기간 해당 무위험이자율 | 0.0350 (3.5%) | ✅ |
| 달러이자율 (r_USD) | 잔존기간 해당 무위험이자율 | 0.0530 (5.3%) | ✅ |

### 3.3 출력 데이터

| 항목 | 설명 | 단위 |
|------|------|------|
| 현재 선물환율 | 평가기준일 기준 IRP 산출 환율 | KRW/USD |
| 공정가치 (KRW) | 통화선도 현재 공정가치 | 원 |
| 전기 공정가치 (KRW) | 직전 평가기준일 공정가치 | 원 |
| 공정가치 변동액 (KRW) | 당기 평가손익 | 원 |
| Level 분류 | K-IFRS 1113호 공정가치 수준 | Level 2 |
| 잔존일수 | 평가기준일 ~ 만기일 | 일 |

---

## 4. 기능 요건

### 4.1 핵심 기능

**FR-001**: 선물환율 계산
- IRP 공식 적용: `S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)`
- KRW: Actual/365 Fixed, USD: Actual/360 (통화별 Day Count Convention 적용)
- 단순이자 방식 적용 (1년 이하 계약 표준)
- 소수점 이하 4자리 반올림

**FR-002**: 공정가치 산출
- 공정가치 = (현재 선물환율 - 계약 선물환율) × 명목원금 × 현가계수
- 현가계수 = `1 / (1 + r_KRW × T/365)`
- BigDecimal 연산 필수 (RoundingMode.HALF_UP, scale=2)

**FR-003**: 공정가치 변동액 계산
- 당기 공정가치 - 전기 공정가치
- 최초 평가 시 전기 공정가치 = 0 (계약 체결일 공정가치는 일반적으로 0)

**FR-004**: K-IFRS 1113호 Level 분류
- 통화선도는 관측가능한 시장 투입변수(환율, 이자율) 사용 → **Level 2 고정**
- 공시 목적으로 Level 정보 저장

**FR-005**: 이력 관리
- 평가 이력 전체 보존 (평가기준일 단위)
- 동일 계약의 재평가 시 이전 평가 보존, 최신 평가 추가

### 4.2 API 엔드포인트

```
POST /api/v1/valuations/fx-forward          # 신규 평가 실행
GET  /api/v1/valuations/fx-forward/{id}     # 평가 결과 단건 조회
GET  /api/v1/valuations/fx-forward          # 평가 이력 목록 조회
GET  /api/v1/valuations/fx-forward/contract/{contractId}  # 계약별 이력
```

---

## 5. 데이터 모델 (개념)

### FxForwardContract (통화선도 계약)
```
- contractId (계약번호, PK)
- notionalAmountUsd (명목원금, BigDecimal)
- contractForwardRate (계약 선물환율, BigDecimal)
- contractDate (계약일, LocalDate)
- maturityDate (만기일, LocalDate)
- hedgeDesignationDate (헤지 지정일, LocalDate)
- status (ACTIVE / TERMINATED / MATURED)
- [감사 필드: createdAt, updatedAt, createdBy, updatedBy]
```

### FxForwardValuation (통화선도 평가)
```
- valuationId (PK)
- contractId (FK → FxForwardContract)
- valuationDate (평가기준일, LocalDate)
- spotRate (현물환율, BigDecimal)
- krwInterestRate (원화이자율, BigDecimal)
- usdInterestRate (달러이자율, BigDecimal)
- remainingDays (잔존일수, Integer)
- currentForwardRate (현재 선물환율, BigDecimal)
- fairValue (공정가치, BigDecimal)
- previousFairValue (전기 공정가치, BigDecimal)
- fairValueChange (공정가치 변동액, BigDecimal)
- fairValueLevel (공정가치 수준: LEVEL_2)
- [감사 필드: createdAt, updatedAt, createdBy, updatedBy]
```

---

## 6. 비기능 요건

| 항목 | 요건 |
|------|------|
| 정밀도 | BigDecimal, scale=2 (원화), scale=4 (환율), RoundingMode.HALF_UP |
| 응답시간 | 단건 평가 500ms 이내 |
| 감사추적 | 모든 평가 이력 영구 보존, 수정/삭제 금지 |
| 보안 | 계약 정보 로깅 금지 (환율, 금액), 인증된 사용자만 접근 |
| 단위테스트 | 핵심 계산 로직 커버리지 100% |

---

## 7. 예외 처리

| 상황 | 처리 방법 | 에러코드 |
|------|-----------|---------|
| 만기 초과 계약 평가 요청 | BusinessException 발생 | FX_001 |
| 현물환율 0 이하 | BusinessException 발생 | FX_002 |
| 이자율 음수 | BusinessException 발생 | FX_003 |
| 존재하지 않는 계약 | BusinessException 발생 | FX_004 |
| 동일 기준일 중복 평가 | 기존 평가 반환 (idempotent) | - |

---

## 8. 데모 시나리오 검증값

**가나금융투자 박지영 과장 케이스 (참조값)**

```
- 명목원금: USD 10,000,000
- 계약 선물환율: 1,380.00 KRW/USD
- 평가기준일: 계약 3개월 후
- 현물환율(S₀): 1,350.00 KRW/USD
- 원화이자율: 3.50% (연)
- 달러이자율: 5.30% (연)
- 잔존일수: 92일

계산 (코드 정밀 계산 기준, Day Count: KRW ACT/365, USD ACT/360):
- krwFactor = 1 + 0.035 × 92/365 = 1.008822 (6dp 반올림)
- usdFactor = 1 + 0.053 × 92/360 = 1.013544 (6dp 반올림)  ← USD ACT/360
- 현재 선물환율 = 1,350 × 1.008822 / 1.013544 ≈ 1,343.7098 KRW/USD (4dp)
- 현가계수 = 1 / 1.008822 ≈ 0.991252 (6dp)
- 공정가치 = (1,343.7098 - 1,380.00) × 10,000,000 × 0.991252
           ≈ -359,728,422 원 (손실: 달러 약세로 선도매도 포지션 불리)

※ 단위 테스트 기준값: -359,728,422 ±300,000
```

> 주의: 위 계산값은 검증용 참조값이며, 실제 시장금리 반영 시 달라질 수 있음.

---

## 9. 구현 순서 (백엔드 에이전트 참고)

1. `FxForwardContract` 엔티티 및 Repository
2. `FxForwardValuation` 엔티티 및 Repository
3. `DayCountConvention` 열거형 — ACT_365(KRW), ACT_360(USD)
4. `FxForwardPricing` — IRP 공식 계산 (순수 도메인 클래스, @Service X, static 메서드)
5. `FxForwardValuationService` — 평가 오케스트레이션
6. `FxForwardValuationController` — REST API
7. `FxForwardValuationRequest/Response` DTO
8. 단위테스트: `FxForwardPricingTest` (domain 패키지)
9. 통합테스트: `FxForwardValuationControllerTest`

---

## 10. 다음 단계

- **백엔드 에이전트**: 이 요구사항을 기반으로 구현 시작
- **연계 기능**: 유효성 테스트(effectiveness-test) 기능에서 이 평가 결과를 입력으로 사용
- **회계분개**: 공정가치 변동액을 기반으로 분개 자동 생성 (별도 기능)

---

*K-IFRS 근거: 1109호 6.4.1, 6.5.8, B6.4.12, 예시 주석 37 / 1113호 문단 61~66, 89, Level 2 분류 기준*
*작성 기반: RAG 검색 (K-IFRS 1109호, K-IFRS 1113호, 미래에셋증권 헤지회계 발췌본)*
*2026-04-19 업데이트: Day Count Convention 추가 (KRW ACT/365, USD ACT/360), 검증값 재계산*
