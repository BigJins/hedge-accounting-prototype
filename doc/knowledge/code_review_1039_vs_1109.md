# K-IFRS 1039호 -> 1109호 차이점 기반 코드 리뷰 결과

## 검토 일자
2026-04-23

## RAG 검색 완료
- BC6.234 (80~125% 정량 기준 폐지 배경)
- K-IFRS 1109호 6.5.5~6.5.6 (재조정 및 중단 요건)
- K-IFRS 1109호 B6.4.12~B6.4.13 (유효성 평가 방법)

## 요약
- 총 발견 이슈 수: 6개
- 심각도 HIGH: 3개
- 심각도 MEDIUM: 2개
- 심각도 LOW: 1개

---

## 발견된 이슈 목록

### [HIGH-1] Dollar-offset 80~125%를 단독 합격/불합격 기준으로 사용

**파일**:
- `backend/src/main/java/com/hedge/prototype/effectiveness/domain/DollarOffsetCalculator.java:32~88`
- `backend/src/main/java/com/hedge/prototype/effectiveness/domain/EffectivenessTestResult.java:6~34`
- `backend/src/main/java/com/hedge/prototype/effectiveness/application/EffectivenessTestService.java:112~115`

**문제**:
K-IFRS 1109호는 구 1039호의 80~125% '명확한 구분선(bright line)' 정량 기준을 공식 폐지하였다
(BC6.234: "대부분의 의견제출자들은 80~125%의 정량적 평가를 없애는 것을 지지했다").
현재 코드는 DollarOffsetCalculator.evaluate()에서 80~125% 이탈 시 FAIL을 반환하고,
EffectivenessTestService가 이를 그대로 합격/불합격 판정으로 사용하고 있어
1039호 방식을 그대로 유지하고 있다.

**현재 코드 (DollarOffsetCalculator.java:77~88)**:
```java
public static EffectivenessTestResult evaluate(BigDecimal ratio) {
    if (ratio.compareTo(BigDecimal.ZERO) >= 0) {
        return EffectivenessTestResult.FAIL;
    }
    BigDecimal absRatio = ratio.abs();
    boolean withinRange = absRatio.compareTo(LOWER_BOUND) >= 0
            && absRatio.compareTo(UPPER_BOUND) <= 0;
    return withinRange ? EffectivenessTestResult.PASS : EffectivenessTestResult.FAIL;
}
```

**수정 방향**:
K-IFRS 1109호 B6.4.12~B6.4.13에 따르면 유효성 평가는 경제적 관계 존재 여부를
질적(정성적) 방법 또는 다양한 정량적 방법으로 평가하며, 80~125%는 참고 지표로만
사용 가능하다. Dollar-offset 결과는 ActionRequired 판단을 위한 보조 지표로
활용하되, 단독 PASS/FAIL 판정 기준으로 사용해서는 안 된다.
EffectivenessTestResult를 PASS/FAIL 이진 구조에서
EFFECTIVE(유효) / PARTIALLY_EFFECTIVE(부분유효) / INEFFECTIVE(비효과적) 등으로
확장하거나, Dollar-offset 수치를 정보 제공 목적으로만 노출하고 실제 판정은
정성적 평가(주요조건 일치법 등)를 병용하는 구조로 변경해야 한다.

**K-IFRS 근거**:
- K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
- K-IFRS 1109호 B6.4.12 (유효성 평가 방법 — 정성적/정량적 병용)
- K-IFRS 1109호 B6.4.13 (Dollar-offset 방법은 선택 가능한 방법의 하나일 뿐)

---

### [HIGH-2] 헤지비율 80~125% 적정 범위를 적격요건 검증의 합격/불합격 기준으로 경직적 사용

**파일**:
- `backend/src/main/java/com/hedge/prototype/hedge/domain/model/HedgeRelationship.java:62~73, 630~654`
- `backend/src/main/java/com/hedge/prototype/hedge/application/HedgeDesignationService.java:457~523`

**문제**:
K-IFRS 1109호 6.4.1(3)(다)와 B6.4.9~B6.4.11은 헤지비율이 위험관리 목적에 부합하는
비율이어야 한다고 규정하며, 80~125% 수치 범위를 명시하지 않는다.
현재 코드는 헤지비율이 80% 미만 또는 125% 초과이면 condition3를 FAIL로 처리하여
헤지 지정 자체를 막는다. 이는 1039호의 Dollar-offset 허용범위를
헤지비율 적격요건에 그대로 이식한 오류이다.
1109호에서 헤지비율 적격요건은 "이익 극대화 목적이 아닌 위험관리 목적에 부합하는
비율"인지 여부가 핵심이며, 특정 수치 범위의 엄격한 합격/불합격 기준은 존재하지 않는다.

**현재 코드 (HedgeRelationship.java:62~73)**:
```java
private static final BigDecimal HEDGE_RATIO_LOWER = new BigDecimal("0.80");
private static final BigDecimal HEDGE_RATIO_UPPER = new BigDecimal("1.25");
```

**현재 코드 (HedgeRelationship.java:630~654)**:
```java
public ConditionResult checkHedgeRatio(BigDecimal hedgeRatioToCheck) {
    boolean withinRange = hedgeRatioToCheck.compareTo(HEDGE_RATIO_LOWER) >= 0
            && hedgeRatioToCheck.compareTo(HEDGE_RATIO_UPPER) <= 0;
    if (!withinRange) {
        return ConditionResult.fail(...);  // 80~125% 이탈 시 FAIL
    }
    return ConditionResult.pass(...);
}
```

**수정 방향**:
헤지비율 적격요건(6.4.1(3)(다)) 검증 로직을 "위험관리 목적에 부합하는 비율인지"
정성적 판단 중심으로 재설계해야 한다. 80~125% 참고 범위는 경고(WARNING) 플래그로만
활용하고, 이 범위를 벗어났다고 해서 자동 FAIL 처리하면 안 된다.
B6.4.9~B6.4.11에 따라 헤지비율 산정 근거(위험관리 전략, 헤지 대상 노출 규모 등)를
문서화하는 방식으로 접근해야 한다.

**K-IFRS 근거**:
- K-IFRS 1109호 6.4.1(3)(다) (헤지비율 적정성 — 위험관리 목적 부합)
- K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 원칙)
- K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)

---

### [HIGH-3] 헤지 자발적 중단 API 부재 — 그러나 사유 검증 로직 불완전

**파일**:
- `backend/src/main/java/com/hedge/prototype/hedge/domain/model/HedgeRelationship.java:662~691`
- `backend/src/main/java/com/hedge/prototype/hedge/adapter/web/HedgeDesignationController.java` (중단 엔드포인트 없음)
- `backend/src/main/java/com/hedge/prototype/hedge/application/HedgeCommandUseCase.java` (중단 메서드 없음)

**문제**:
K-IFRS 1109호 6.5.6은 자발적 헤지 중단을 명시적으로 금지한다.
"위험회피관계 또는 위험회피관계의 일부가 적용조건을 충족하지 않는 경우에만
전진적으로 위험회피회계를 중단한다"는 원칙이다.

HedgeRelationship.discontinue() 메서드는 중단 사유(discontinuationReason)가
blank가 아닌 경우 중단을 허용한다. 그러나 사유가 실제로 "위험관리 목적 변경"에
해당하는지를 검증하는 코드가 없어, 임의의 문자열만 입력하면 사실상 자발적 중단이
가능한 상태다. 또한 Controller와 UseCase 인터페이스에 중단 API 엔드포인트가
존재하지 않아 현재 시스템에서는 중단 기능 자체를 외부에서 호출할 수 없다.
이는 중단 기능의 미완성을 의미하며, 향후 구현 시 1109호 요건을 정확히 반영해야 한다.

**현재 코드 (HedgeRelationship.java:682~685)**:
```java
if (discontinuationReason.isBlank()) {
    throw new BusinessException("HD_012",
            "중단 사유는 필수입니다 ...");
}
// blank가 아니면 어떤 사유든 중단 허용 — 사유 유형 검증 없음
```

**수정 방향**:
(1) 헤지 중단 API 엔드포인트(PATCH /api/v1/hedge-relationships/{id}/discontinue)를
구현하되, 허용 사유 코드(INSTRUMENT_MATURED, INSTRUMENT_TERMINATED, RISK_MANAGEMENT_OBJECTIVE_CHANGED)를
enum으로 정의하고 이를 필수 입력으로 받도록 한다.
(2) 위험관리 목적 변경(RISK_MANAGEMENT_OBJECTIVE_CHANGED) 사유일 경우에만
자발적 중단을 허용하고, 그 외 사유는 헤지수단 소멸/만기 등 비자발적 중단 사유로 구분한다.
(3) 중단 사유 유형을 DB에 코드로 저장하여 감사 추적이 가능하도록 한다.

**K-IFRS 근거**:
- K-IFRS 1109호 6.5.6 (자발적 취소 불가 원칙)
- K-IFRS 1109호 B6.5.26 (중단 후 OCI 잔액 처리)

---

### [MEDIUM-1] Rebalancing 로직 미구현

**파일**:
- `backend/src/main/java/com/hedge/prototype/effectiveness/domain/ActionRequired.java:32` (REBALANCE enum 정의만 존재)
- `backend/src/main/java/com/hedge/prototype/effectiveness/application/EffectivenessTestService.java:397~427` (ActionRequired.REBALANCE 반환만 함)
- `backend/src/main/java/com/hedge/prototype/effectiveness/application/event/EffectivenessTestCompletedEventHandler.java:60` ("수동 처리 필요" 경고 로그만)

**문제**:
K-IFRS 1109호 6.5.5는 헤지비율 재조정(Rebalancing)을 의무 사항으로 규정한다.
위험관리 목적이 동일하게 유지되는 경우 재조정이 가능하다면 재조정을 해야 하며,
선택적으로 중단할 수 없다. 현재 코드는 ActionRequired.REBALANCE를 결과값으로만
반환할 뿐 실제 재조정 처리 로직(헤지비율 변경, B6.5.8에 따른 재조정 전 비효과성 인식,
재조정 이력 기록)이 전혀 구현되어 있지 않다. 이벤트 핸들러는 REBALANCE 시
"수동 처리 필요" 경고 로그만 출력한다.

**현재 코드 (EffectivenessTestCompletedEventHandler.java:60)**:
```java
log.warn("[이벤트] 헤지 중단 필요 — 분개 생성 생략, 수동 처리 필요. hedgeRelationshipId={}",
```

**수정 방향**:
재조정 처리 서비스를 신설하여:
(1) B6.5.8에 따라 재조정 전 비효과성을 먼저 당기손익 인식 (분개 생성 선행)
(2) 헤지비율 변경 내용을 HedgeRelationship 엔티티에 이력으로 기록
(3) 재조정 후 새로운 헤지비율로 지정 문서 갱신
(4) 재조정은 헤지관계 종료 없이 연속성 유지 (새 지정이 아님)

**K-IFRS 근거**:
- K-IFRS 1109호 6.5.5 (재조정 의무)
- K-IFRS 1109호 B6.5.7~B6.5.21 (재조정 상세 지침)
- K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 먼저 인식)

---

### [MEDIUM-2] OCI 적립금 잔액 계산의 단순화 처리 — 이전 잔액 미반영

**파일**:
- `backend/src/main/java/com/hedge/prototype/effectiveness/application/EffectivenessTestService.java:383~387`

**문제**:
현금흐름 헤지의 OCI Reserve 잔액은 "이전 OCI 잔액 + 당기 유효 변동분"으로
누적 계산해야 한다. 현재 코드는 주석으로 "PoC 범위: 단순화하여 당기 유효 부분의
변동분을 OCI 잔액으로 기록"이라고 명시하고 있으나, 이는 K-IFRS 1109호 6.5.11에서
요구하는 OCI 적립금 누적 관리와 다르다. 실제로는 과거 기간 OCI 인식 잔액이
새 기간에 잘못된 숫자로 덮어쓰여지는 버그가 발생할 수 있다.

**현재 코드 (EffectivenessTestService.java:383~387)**:
```java
// 여기서는 단순화하여 당기 유효 부분의 변동분을 OCI 잔액으로 기록합니다.
// 실제 시스템에서는 이전 OCI 잔액 + 당기 유효 변동분으로 계산해야 합니다.
BigDecimal ociReserveBalance = effectiveAmount.setScale(2, RoundingMode.HALF_UP);
```

**수정 방향**:
이전 EffectivenessTest 레코드의 ociReserveBalance를 조회하고,
거기에 당기 유효 부분 변동액을 가산하여 새 잔액을 계산해야 한다.
이미 이전 누적값 조회 로직(computeCumulatives)이 존재하므로
동일 패턴으로 OCI 잔액도 누적 관리하도록 확장하면 된다.

**K-IFRS 근거**:
- K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI 적립금 누적 인식)
- K-IFRS 1109호 6.5.12 (OCI 잔액의 재분류 시점)

---

### [LOW-1] EffectivenessTestResult에 WARNING 값 누락

**파일**:
- `backend/src/main/java/com/hedge/prototype/effectiveness/domain/EffectivenessTestResult.java`
- `backend/src/main/java/com/hedge/prototype/effectiveness/application/event/EffectivenessTestCompletedEvent.java:16`

**문제**:
EffectivenessTestCompletedEvent의 Javadoc에는 "PASS / FAIL / WARNING" 3가지
결과가 언급되어 있으나 (라인 16: "@param testResult 테스트 결과 (PASS / FAIL / WARNING)"),
실제 EffectivenessTestResult enum에는 PASS와 FAIL 두 가지만 정의되어 있다.
WARNING은 미래에 추가 예정인 값으로 보이나, Javadoc 불일치는 유지보수 혼란을 유발한다.
또한 K-IFRS 1109호 체계에서는 PASS/FAIL 이진 판정보다 유효성 정도를 연속적으로
측정하는 방식이 권장되므로 (BC6.234, BC6.238 참조), 향후 구조 개선 시 고려가 필요하다.

**수정 방향**:
(1) Javadoc에서 WARNING 참조를 제거하거나, WARNING 값을 enum에 추가하여 일관성 유지
(2) HIGH-1 이슈 수정 시 유효성 결과 체계 전면 재검토 병행

**K-IFRS 근거**:
- K-IFRS 1109호 BC6.238 (효과성 평가 — 경제적 관계 중심 원칙)

---

## Rebalancing 구현 현황

- **구현 여부**: NO (enum 정의 및 반환값만 존재, 실제 처리 로직 없음)
- **필요 여부**: YES
- **권고사항**: MEDIUM-1 이슈 참조. K-IFRS 1109호 6.5.5는 재조정을 선택이 아닌
  의무로 규정하므로 PoC 완성도를 위해서도 최소한 재조정 이력 기록과 헤지비율 변경
  처리는 구현해야 한다. 수주용 데모에서 재조정 시나리오가 등장할 경우 현행 코드는
  "수동 처리 필요" 경고만 출력하여 시연 신뢰도에 영향을 줄 수 있다.

---

## 이자율 포트폴리오 헤지 현황

- **구현 여부**: NO
- **필요 여부**: NO (현재 PoC 범위 — 개별 헤지 관계 중심 시연 목적)
- **비고**: K-IFRS 1039호 89~94조항의 이자율 포트폴리오 헤지 특례는 1109호에서도
  병존 적용이 가능하나, 현재 PoC 범위에서는 개별 계약 단위 헤지만 다루고 있어
  구현 불필요로 판단. 추후 은행 여수신 포트폴리오 헤지 기능 확장 시 검토 필요.

---

## 결론 및 우선순위

### 1. 즉시 수정 필요 (HIGH)

| 순위 | 이슈 | 파일 | 핵심 위반 |
|------|------|------|-----------|
| 1 | HIGH-1: Dollar-offset 80~125% 단독 PASS/FAIL 기준 | DollarOffsetCalculator.java:32~88 | BC6.234 (정량 기준 폐지) |
| 2 | HIGH-2: 헤지비율 80~125% 경직적 적격요건 기준 | HedgeRelationship.java:62~73, 630~654 | B6.4.9~11 (위험관리 목적 기반 판단) |
| 3 | HIGH-3: 헤지 중단 사유 유형 검증 부재 | HedgeRelationship.java:682~685 | 6.5.6 (자발적 취소 불가) |

### 2. 단기 수정 필요 (MEDIUM)

| 순위 | 이슈 | 파일 | 핵심 내용 |
|------|------|------|-----------|
| 4 | MEDIUM-1: Rebalancing 로직 미구현 | EffectivenessTestService.java:397~427 | 6.5.5 (재조정 의무) |
| 5 | MEDIUM-2: OCI 잔액 누적 계산 누락 | EffectivenessTestService.java:383~387 | 6.5.11 (OCI 누적 관리) |

### 3. 장기 검토 (LOW)

| 순위 | 이슈 | 파일 | 핵심 내용 |
|------|------|------|-----------|
| 6 | LOW-1: WARNING enum 값 미정의 | EffectivenessTestResult.java | Javadoc 불일치 |

---

## 수주 데모 리스크 평가

HIGH-1과 HIGH-2는 기술적으로 시스템이 동작하는 것처럼 보이지만,
감리 전문가나 회계법인 파트너가 검토할 경우 "1039호 기준을 그대로 이식한 것"으로
지적받을 가능성이 높다. 수주용 데모에서 K-IFRS 1109호 준거성을 주요 차별점으로
내세우는 경우 이 두 이슈는 반드시 수정하거나, 최소한 "보조 지표로만 활용"임을
명시하는 UI 레이블 수정과 주석 정정이 필요하다.

---

*K-IFRS 근거: BC6.234, B6.4.9~B6.4.11, B6.4.12~B6.4.13, 6.4.1(3)(다), 6.5.5, 6.5.6, 6.5.11, B6.5.7~B6.5.21*
*작성 기반: RAG 검색 (BC6.234 — 80~125% 정량기준 폐지 배경, 6.5.5~6.5.6 — 재조정 및 중단 요건, B6.4.12~B6.4.13 — 유효성 평가 방법)*
*검토 에이전트: accounting-expert*
