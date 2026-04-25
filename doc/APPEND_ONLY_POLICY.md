# Append-Only 정책 및 책임 추적 원칙

## 목적

이 문서는 헤지회계 자동화 프로토타입에서 **왜 Append-Only 저장 방식을 채택했는지**, 어디에 적합하고 어디에는 부족한지, 그리고 보완 통제가 무엇인지를 설명한다.

심사/면접에서 설계 의도를 설명하거나, 코드 리뷰 시 저장 전략의 근거를 확인할 때 이 문서를 기준으로 사용한다.

**최종 업데이트**: 2026-04-24

---

## 1. Append-Only를 쓰는 이유

### 1-1. 회계 기준이 요구하는 감사추적

K-IFRS 1107호는 헤지회계 적용 내역의 공시를 의무화한다. 공시 가능성(Disclosability)은 과거 판단 이력이 원본 그대로 보존될 때 확보된다.

> "위험회피회계를 적용하는 기업은 각 헤지관계에 대해 위험관리 전략, 유효성 테스트 방법, 인식된 손익 내역을 공시해야 한다." — K-IFRS 1107호 22A~24G항

이 요건을 코드 레벨에서 직접 구현하는 방법이 Append-Only다. 기존 레코드를 덮어쓰지 않고 매번 신규 INSERT하면, 어느 시점의 평가·테스트·분개 결과도 DB에서 복원할 수 있다.

### 1-2. "당시의 판단"을 보존해야 한다

공정가치 평가, 유효성 테스트, 분개 생성은 모두 **특정 시점의 시장 데이터와 입력값을 기반으로 한 판단**이다.

예를 들어 2026-03-31 유효성 테스트에서 Dollar-offset 비율이 −0.97이었고 PASS가 나왔다면, 2026-04-30에 시장 상황이 바뀌어 재테스트를 해도 3월 판단은 바뀌지 않는다. 3월 레코드가 그대로 남아 있어야 "그때 왜 PASS라고 판단했는가"를 재현할 수 있다.

이 원칙은 세 가지 도메인에 모두 적용된다.

- **공정가치 평가**: 같은 계약에 대해 재평가할 때마다 새 `FxForwardValuation` 레코드 INSERT
- **유효성 테스트**: 같은 헤지관계에 대해 재테스트할 때마다 새 `EffectivenessTest` 레코드 INSERT
- **분개**: 승인된 분개는 수정하지 않으며, 오류 정정이 필요하면 `REVERSING_ENTRY` 패턴으로 역분개 레코드를 추가로 INSERT

### 1-3. 책임 소재 추적의 기반

모든 엔티티는 `BaseAuditEntity`를 상속하며, Spring Data Auditing으로 다음 4개 필드를 자동으로 기록한다.

```java
@CreatedDate   private LocalDateTime createdAt;   // 생성 시각 (updatable = false)
@LastModifiedDate private LocalDateTime updatedAt; // 최종 수정 시각
@CreatedBy     private String createdBy;           // 생성자 (updatable = false)
@LastModifiedBy private String updatedBy;          // 최종 수정자
```

`createdAt`과 `createdBy`는 `updatable = false`로 고정되어 있어, 생성 이후 변경 불가다. Append-Only + 불변 감사 필드의 조합으로 "언제, 누가, 어떤 입력으로 이 판단을 내렸는가"를 추적할 수 있다.

---

## 2. Append-Only가 적합한 위치

### 2-1. 공정가치 평가 (valuation)

```
FxForwardValuation   — FX Forward IRP 기반 공정가치
IrsValuation         — IRS DCF 기반 공정가치  (시연 제외)
CrsValuation         — CRS DCF 기반 공정가치  (시연 제외)
```

**이유**: 시장 데이터(현물환율, 이자율, 잔존일수)는 매일 변한다. 재평가할 때마다 "그날의 공정가치"가 별도 레코드로 누적된다. 최신 공정가치는 `createdAt DESC`로 첫 번째 레코드를 가져오는 방식으로 조회한다.

```java
// FxForwardValuationService.java:179
// 평가 결과 저장 — 항상 신규 INSERT (Append-Only).
// 동일 계약 재평가 시 이전 레코드를 덮어쓰지 않는다.
```

**한계**: 계약 자체의 `status`는 변경 가능하다(`ACTIVE → MATURED → TERMINATED`). 계약 메타데이터는 Append-Only 대상이 아니며, 상태 전이는 도메인 규칙으로 통제한다 (아래 섹션 3 참조).

### 2-2. 유효성 테스트 (effectiveness test)

```
EffectivenessTest    — Dollar-offset 비율, OCI 적립금 누적 잔액
```

**이유**: 유효성 테스트는 매 보고기간 말 반복 실행하는 판단 행위다. 같은 헤지관계에 대해 1분기 테스트 결과와 2분기 테스트 결과가 별도 레코드로 누적된다. 다음 기간의 OCI 적립금 누적값은 이전 마지막 레코드에서 읽어 계산한다.

```java
// EffectivenessTestService.java
// 이전 이력에서 누적값 계산
CumulativeValues cumulatives = computeCumulatives(
    request.hedgeRelationshipId(),
    request.instrumentFvChange(),
    request.hedgedItemPvChange());
// ...
// 결과 생성 및 저장 (Append-Only INSERT)
EffectivenessTest saved = effectivenessTestRepository.save(result);
```

**한계**: 0/0 케이스는 `isHedgedItemChangeNegligible()` 처리로 분모 0을 방지하지만, 백엔드 도메인 레벨에서 "중단된 헤지관계에 테스트를 추가하는 것"을 차단하는 가드는 없다. 프론트엔드에서 두 변동액이 모두 0인 경우를 `superRefine` 유효성 검사로 차단해 의미 없는 Append-Only 레코드 생성을 줄이고 있다 (아래 섹션 4-5 참조).

### 2-3. 분개 (journal entry)

```
JournalEntry   — 공정가치헤지·현금흐름헤지 분개, OCI 재분류 분개, 역분개
```

**이유**: 확정된 분개를 수정하는 것은 회계 부정의 소지가 있다. K-IFRS와 내부통제 원칙 모두 분개의 불가역성을 요구한다. 오류가 있다면 원 분개를 취소하는 **역분개(Reversing Entry)**를 새 레코드로 추가한다.

```java
// JournalEntry.java:274
// 역분개 생성.
// 원 분개의 차대변을 반전하여 취소합니다.
entry.entryType = JournalEntryType.REVERSING_ENTRY;
entry.cancelsEntryId = original.journalEntryId;
```

역분개 연결은 `cancelsEntryId` / `cancelledByEntryId` 쌍으로 관리한다. 원 분개는 삭제되지 않으며, 역분개 레코드가 연결된 채로 두 레코드가 모두 DB에 남는다.

---

## 3. Append-Only만으로 부족한 위치

### 3-1. 헤지관계 현재 상태 (HedgeRelationship status)

```
HedgeRelationship.status  →  DESIGNATED | DISCONTINUED | REBALANCED | MATURED
```

`HedgeRelationship` 자체는 새 지정 시 신규 레코드를 INSERT한다는 점에서 Append-Only 성격이 있다. 그러나 **현재 어떤 상태인가**는 단순 이력 추적으로 답할 수 없다.

예를 들어 "이 헤지관계가 지금도 DESIGNATED 상태인가?"는 가장 최신 레코드의 `status` 필드를 읽어야 한다. 이 상태값은 중단(PATCH /discontinue), 재조정(rebalancing 이벤트), 만기 도달 시점에 갱신된다.

**문제**: Append-Only를 고수하면 "현재 활성 상태 계약에 같은 헤지관계가 중복 지정됐는가"를 쿼리로 확인하기 어렵다. 현재 코드에서는 이 문제를 다음과 같이 처리하고 있다.

```java
// HedgeDesignationService.java:578
// HD_005: 중복 지정 — Append-Only 정책에 따라 예외 대신 경고 로그로 처리.
// 이전 지정 이력은 보존되며, 신규 지정 레코드가 최신 상태로 사용됩니다.
hedgeRelationshipRepository
    .findByFxForwardContractIdAndStatus(contractId, HedgeStatus.DESIGNATED)
    .ifPresent(r -> log.warn("계약 재지정: contractId={}, 이전 관계={}",
        contractId, r.getHedgeRelationshipId()));
```

즉, **동일 계약에 대해 DESIGNATED 관계가 이미 존재해도 예외를 던지지 않는다**. 경고 로그만 남기고 신규 지정을 허용한다. 이는 PoC 범위의 설계 결정이며, 실무 시스템에서는 중복 지정을 `BusinessException`으로 차단하거나 이전 지정을 자동 중단 처리해야 할 수 있다.

### 3-2. 계약 현재 상태 (FxForwardContract status)

```
FxForwardContract.status  →  ACTIVE | MATURED | TERMINATED
```

계약 상태는 헤지수단으로서의 적격성과 직결된다. `MATURED` 또는 `TERMINATED` 계약을 헤지수단으로 신규 지정하는 것은 K-IFRS 1109호 6.4.1(2) 위반이다. 이 통제는 도메인 메서드로 구현되어 있다.

```java
// FxForwardContract.java:257
public void validateForHedgeDesignation(LocalDate designationDate) {
    if (this.status != ContractStatus.ACTIVE) {
        throw new BusinessException("HD_006",
            "ACTIVE 상태의 계약만 위험회피수단으로 지정할 수 있습니다.");
    }
    // 만기 초과 검증...
}
```

이 통제는 Append-Only와 별개다. **현재 상태를 단일 필드로 관리하지 않으면 이 검증을 할 수 없다.** Append-Only 이력 테이블만 있었다면 "이 계약이 지금 ACTIVE인가?"를 집계 쿼리로 판단해야 하는 복잡도가 생긴다.

---

## 4. 이번에 추가/보강한 통제

### 4-1. 헤지 중단 사유 검증 — 자발적 중단 차단

```java
// HedgeDiscontinuationReason.java:91
// K-IFRS 1109호 6.5.6: 자발적 위험회피 관계 중단 금지
if (reason == VOLUNTARY_DISCONTINUATION) {
    throw new BusinessException("HD_012", "자발적 중단은 허용되지 않습니다.");
}
```

허용되는 중단 사유는 4가지로 열거화해 관리한다: `RISK_MANAGEMENT_OBJECTIVE_CHANGED`, `HEDGE_INSTRUMENT_EXPIRED`, `HEDGE_ITEM_NO_LONGER_EXISTS`, `ELIGIBILITY_CRITERIA_NOT_MET`. 이 검증이 없으면 Append-Only로 누적된 이력이 의미를 잃는다 — "언제 중단됐는가"는 기록되지만 "왜 중단됐는가"의 정당성 확인이 불가능하다.

### 4-2. Dollar-offset 판정 구조 개선 (AVM-005, 2026-04-23)

80~125% 기계적 FAIL 구조를 폐지하고 PASS/WARNING/FAIL 3단계로 전환했다. FAIL은 동방향(경제적 관계 훼손)에만 적용한다. Append-Only 이력과 결합하면 "이 판정이 어떤 기준으로 내려졌는가"를 재현할 수 있다.

### 4-3. Lower-of-Test 부호 방향 수정 (AVM-007, 2026-04-23)

```java
// LowerOfTestCalculator — calculateSignedEffectivePortion() 신규 추가
// 수단 손실 → OCI 감소(음수), 수단 이익 → OCI 증가(양수)
// K-IFRS 1109호 6.5.11⑴, BC6.280
```

Append-Only로 저장되는 `ociReserveBalance` 필드가 부호 방향을 정확히 반영하게 됐다. 이력 조회 시 OCI 적립금의 누적 방향이 정합하게 보인다.

### 4-5. 유효성 테스트 무의미 입력 차단 — 프론트엔드 superRefine (2026-04-24)

```ts
// EffectivenessTestForm.tsx — Zod superRefine
if (data.instrumentFvChange === 0 && data.hedgedItemPvChange === 0) {
  // 두 필드 모두 0이면 테스트 자체가 의미 없음 → Append-Only 레코드 생성 차단
  ctx.addIssue({ code: z.ZodIssueCode.custom, message: BOTH_ZERO_MESSAGE, path: ['instrumentFvChange'] })
  ctx.addIssue({ code: z.ZodIssueCode.custom, message: BOTH_ZERO_MESSAGE, path: ['hedgedItemPvChange'] })
}
```

"이번 기간에 아무 변동도 없었다"는 입력은 백엔드까지 도달하지 않는다. 프론트엔드에서 두 필드 모두 동시에 붉게 표시되어 심사관이 원인을 즉시 파악할 수 있다. 단, 이 가드는 프론트엔드 전용이며, API를 직접 호출하면 우회 가능하다.

### 4-6. CFH 중단 후 OCI 후속 처리 자동화 (AVM-014, 2026-04-23)

중단 API 호출 시 `forecastTransactionExpected` 값에 따라 OCI 재분류 분개가 자동으로 Append-Only INSERT된다. 사람이 수동으로 분개를 추가하는 절차 없이 이력이 자동 완성된다.

---

## 5. 구조 요약 — 어디에 무엇을 쓰는가

| 영역 | 저장 방식 | 이유 | 현재 한계 |
|---|---|---|---|
| `FxForwardValuation` | Append-Only INSERT | 공정가치 이력 보존 | 계약 메타 자체는 mutable |
| `EffectivenessTest` | Append-Only INSERT | 기간별 판단 이력 보존 | 입력값 무결성 가드 미흡 |
| `JournalEntry` | Append-Only + REVERSING_ENTRY | 분개 불가역성 | 역분개 여부는 수동 판단 |
| `HedgeRelationship` | 신규 지정 시 INSERT, 상태는 UPDATE | 현재 상태 추적 필요 | 중복 지정 예외 미발생 (HD-005) |
| `FxForwardContract` | 상태 전이 UPDATE + 검증 | 적격성 실시간 통제 | 상태 이력 별도 관리 없음 |

---

## 6. 심사/면접용 1분 설명 초안

> "헤지회계에서 공정가치 평가, 유효성 테스트, 분개는 모두 Append-Only로 저장합니다. 이유는 세 가지입니다. 첫째, K-IFRS 1107호 감사추적 의무 — 과거의 판단을 원본 그대로 보존해야 공시할 수 있습니다. 둘째, 재현 가능성 — '왜 그 시점에 PASS였는가'를 나중에 재현하려면 당시의 입력값과 결과가 DB에 남아 있어야 합니다. 셋째, 역분개 패턴 — 분개 오류는 삭제하지 않고 차대변을 반전한 새 분개를 추가하는 방식으로 수정해, 원 분개와 수정 의도 둘 다 남깁니다.
>
> 다만 Append-Only만으로 모든 것이 해결되는 건 아닙니다. '이 계약이 지금 ACTIVE 상태인가', '이 헤지관계가 현재 DESIGNATED 상태인가'처럼 현재 상태를 실시간으로 통제해야 하는 부분은 상태 필드로 관리하고, 진입 조건에 도메인 검증을 추가합니다. 이 두 가지를 조합해 '이력은 Append-Only로 쌓고, 현재 상태는 도메인 규칙으로 통제한다'는 구조입니다."

---

## 7. 현재 시점 솔직한 한계

1. **중복 지정 미차단 (HD-005)**: 동일 계약에 DESIGNATED 헤지관계가 이미 존재해도 신규 지정이 가능하다. 경고 로그만 남긴다. 실무에서는 이전 관계를 자동 중단하거나 예외를 발생시켜야 한다.

2. **유효성 테스트 백엔드 가드 부재**: 프론트엔드에서 0/0 입력은 차단되지만, "이 헤지관계가 현재 DESIGNATED 상태인가"를 백엔드 도메인 레벨에서 검증하지 않는다. API를 직접 호출하면 중단된 헤지관계에도 테스트 레코드를 추가할 수 있다.

3. **분개 역분개 수동 의존**: 역분개가 필요한 상황(입력 오류, 기준일 수정)을 시스템이 자동으로 감지하지 않는다. 현재는 API 수동 호출로만 역분개가 생성된다.

4. **Append-Only가 적용되지 않는 계약 상태 이력**: `FxForwardContract.status` 변경 이력은 별도로 관리되지 않는다. 계약이 언제 ACTIVE에서 MATURED로 바뀌었는지 추적하려면 `createdAt/updatedAt`으로 추정해야 한다.

---

## 관련 문서

- [회계원칙 검증표 — AVM-016 감사추적](ACCOUNTING_VALIDATION_MATRIX.md#avm-016--감사추적--append-only-이력--변경-불가)
- [README — 기능 상태 매트릭스](../README.md)
- K-IFRS 1107호 22A~24G항 (헤지회계 공시 요건)
- K-IFRS 1109호 6.5.6 (자발적 중단 금지 원칙)
