# 코드 수정 로그 — K-IFRS 1039호 → 1109호 정합성 수정

## 수정 일자
2026-04-23

## 수정 배경
`code_review_1039_vs_1109.md` 검토 결과 발견된 6개 이슈 전체 수정.
K-IFRS 1039호의 Dollar-offset 80~125% 정량 기준이 1109호에서 폐지(BC6.234)되었음에도
기존 코드에 그대로 이식되어 있던 문제를 전면 교정합니다.

---

## 수정 항목

### [HIGH-1] Dollar-offset 단독 판정 구조 수정

**파일:**
- `effectiveness/domain/DollarOffsetCalculator.java`
- `effectiveness/application/EffectivenessTestService.java:100~130, 392~430`
- `effectiveness/domain/EffectivenessTestResult.java`

**변경 내용:**
- `evaluate(ratio)` 메서드를 `evaluateReferenceGrade(ratio)`로 대체
  - 기존: 80~125% 이탈 시 FAIL 반환 (단독 판정)
  - 변경: PASS / WARNING / FAIL 3단계 참고 등급 반환 (정보 제공용)
- `buildFailureReason()` → `buildReferenceGradeMessage()` 이름 변경, 메시지에 BC6.234 인용
- 동방향(비율 양수): FAIL (경제적 관계 훼손)
- 반대방향 + 참고 범위 이탈: WARNING (재조정 신호 — FAIL 아님)
- 반대방향 + 참고 범위 이내: PASS
- `EffectivenessTestService.determineAction()`:
  - PASS → NONE
  - WARNING → REBALANCE (6.5.5 재조정 의무)
  - FAIL → DISCONTINUE (6.5.6 중단 검토)

**K-IFRS 근거:** K-IFRS 1109호 BC6.234, B6.4.12, B6.4.13

---

### [HIGH-2] 헤지비율 적격요건 기준 완화

**파일:**
- `hedge/domain/model/HedgeRelationship.java:62~73, 630~720`
- `hedge/application/HedgeDesignationService.java:457~525`

**변경 내용:**
- 상수 이름 변경: `HEDGE_RATIO_LOWER/UPPER` → `HEDGE_RATIO_REFERENCE_LOWER/UPPER`
  - 주석 강화: "허용범위"가 아닌 "참고 범위"임을 명시
- `checkHedgeRatio()` 메서드 완전 재작성:
  - 기존: 80~125% 이탈 시 FAIL 반환 (자동 FAIL)
  - 변경: 극단적 비율(10% 미만, 300% 초과)만 FAIL, 참고 범위 이탈은 PASS(WARNING 메시지)
  - 위험관리 목적 부합성 기반 판단 원칙으로 전환
- `HedgeDesignationService.buildHedgeRatioCondition()` 동일 원칙으로 수정
- 중복 상수(`HEDGE_RATIO_LOWER/UPPER`) 서비스에서 제거

**K-IFRS 근거:** K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11, BC6.234

---

### [HIGH-3] 헤지 중단 사유 코드 체계 도입

**신규 파일:**
- `hedge/domain/common/HedgeDiscontinuationReason.java` (신규 생성)
- `hedge/adapter/web/dto/HedgeDiscontinuationRequest.java` (신규 생성)
- `hedge/application/HedgeRebalancingService.java` (MEDIUM-1과 함께 생성)

**수정 파일:**
- `hedge/domain/model/HedgeRelationship.java:682~735`
- `hedge/application/HedgeCommandUseCase.java`
- `hedge/application/HedgeDesignationService.java`
- `hedge/adapter/web/HedgeDesignationController.java`

**변경 내용:**
- `HedgeDiscontinuationReason` enum 생성:
  - 허용 사유: `RISK_MANAGEMENT_OBJECTIVE_CHANGED`, `HEDGE_INSTRUMENT_EXPIRED`, `HEDGE_ITEM_NO_LONGER_EXISTS`, `ELIGIBILITY_CRITERIA_NOT_MET`
  - 차단 사유: `VOLUNTARY_DISCONTINUATION` (isAllowed() 반환 false)
- `HedgeRelationship.discontinue()` 메서드 시그니처 변경:
  - 기존: `(LocalDate, String)` — 문자열 사유 입력, 사유 유형 검증 없음
  - 변경: `(LocalDate, HedgeDiscontinuationReason, String)` — enum 코드 + 상세 설명
  - `VOLUNTARY_DISCONTINUATION` 시도 시 BusinessException HD_012 발생
- `discontinuationReason` 컬럼: `VARCHAR(500)` → `ENUM 타입 + 별도 details 컬럼`
- `HedgeCommandUseCase`에 `discontinue()` 메서드 추가
- `HedgeDesignationService`에 `discontinue()` 구현 추가
- Controller에 `PATCH /api/v1/hedge-relationships/{id}/discontinue` 엔드포인트 추가

**K-IFRS 근거:** K-IFRS 1109호 6.5.6, B6.5.26

---

### [MEDIUM-1] Rebalancing 로직 구현

**신규 파일:**
- `hedge/application/HedgeRebalancingService.java` (신규 생성)

**수정 파일:**
- `hedge/domain/model/HedgeRelationship.java` (rebalance() 메서드 추가)
- `effectiveness/application/event/EffectivenessTestCompletedEventHandler.java`

**변경 내용:**
- `HedgeRelationship.rebalance()` 도메인 메서드 추가:
  - 신규 헤지비율 검증 (극단적 비율 차단)
  - 상태 → `HedgeStatus.REBALANCED`로 변경
  - DISCONTINUED 상태에서 재조정 시도 시 HD_015 예외
- `HedgeRebalancingService` 신규 생성:
  - `processRebalancing()`: B6.5.8 — 재조정 전 비효과성 당기손익 인식 선행
  - `calculateTargetHedgeRatio()`: Dollar-offset 비율 기반 목표 헤지비율 계산
  - `recognizePreRebalancingIneffectiveness()`: 분개 생성 후 재조정 진행
- 이벤트 핸들러에서 `WARNING + REBALANCE` 시 실제 재조정 호출 (`HedgeRebalancingService.processRebalancing()`)
  - 기존: "수동 처리 필요" 경고 로그만
  - 변경: 실제 재조정 처리 실행

**K-IFRS 근거:** K-IFRS 1109호 6.5.5, B6.5.7~B6.5.21, B6.5.8

---

### [MEDIUM-2] 현금흐름 헤지 OCI 누적 잔액 계산 수정

**파일:**
- `effectiveness/application/EffectivenessTestService.java:371~430`

**변경 내용:**
- `calculateCashFlowIneffectiveness()` 파라미터에 `previousOciBalance` 추가
- OCI Reserve 잔액 계산:
  - 기존: `ociReserveBalance = effectiveAmount` (당기만, 이전 잔액 미반영)
  - 변경: `cumulativeOciBalance = previousOciBalance + effectiveAmount` (누적 관리)
- `resolvePreviousOciBalance()` 헬퍼 메서드 추가:
  - `findTopByHedgeRelationshipIdOrderByTestDateDesc()`로 직전 레코드의 OCI 잔액 조회
  - 첫 기간이면 BigDecimal.ZERO
  - 공정가치 헤지는 즉시 ZERO 반환
- `runTest()` 에서 `previousOciBalance` 선 조회 후 `calculateIneffectiveness()`에 전달

**K-IFRS 근거:** K-IFRS 1109호 6.5.11, 6.5.12

---

### [LOW-1] EffectivenessTestResult enum WARNING 값 추가

**파일:**
- `effectiveness/domain/EffectivenessTestResult.java`

**변경 내용:**
- `WARNING` enum 값 추가 및 상세 Javadoc 작성
- 기존 PASS/FAIL Javadoc도 BC6.234 기준으로 재작성
- `EffectivenessTestCompletedEvent` Javadoc과 정합성 확인 (PASS/FAIL/WARNING 3가지 언급)

**K-IFRS 근거:** K-IFRS 1109호 BC6.234, BC6.238

---

## 테스트 영향

### 수정된 테스트 파일
- `src/test/java/.../hedge/domain/model/HedgeRelationshipTest.java`
  - 조건 3 헤지비율 테스트: 80~125% 이탈 FAIL → PASS(WARNING) 기대값 변경
  - 신규 케이스 추가: 극단적 비율(5%, 400%) FAIL 테스트
  - `noFailFast_allConditionsCheckedEvenIfSomeFail()` 기대값 변경 (조건 3이 PASS로 변경됨)

- `src/test/java/.../adapter/web/hedge/HedgeDesignationControllerTest.java`
  - "조건 3 실패 (헤지비율 0.70)" → "조건 3 WARNING (헤지비율 0.70)" 테스트 교체
  - 신규 케이스: "조건 3 FAIL (헤지비율 0.05 — 극단적 저비율)" 추가

### 테스트 결과
- 총 97개 테스트 전체 통과 (BUILD SUCCESSFUL)

---

## 스키마 변경 주의사항

`HedgeRelationship` 엔티티의 `discontinuation_reason` 컬럼 타입이 변경되었습니다:
- 기존: `VARCHAR(500)` — 자유 문자열
- 변경: `VARCHAR(60)` + `EnumType.STRING` (HedgeDiscontinuationReason 코드값)
- 신규 컬럼: `discontinuation_details VARCHAR(500)` — 상세 설명 서술용

PostgreSQL DDL 변경이 필요합니다. 개발 환경에서 `spring.jpa.hibernate.ddl-auto=update` 사용 시 자동 처리됩니다.

---

*K-IFRS 근거: BC6.234, B6.4.9~B6.4.11, B6.4.12~B6.4.13, 6.4.1(3)(다), 6.5.5, 6.5.6, 6.5.11, B6.5.7~B6.5.21*
*수정 에이전트: backend-developer*
