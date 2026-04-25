# 요구사항 명세서: 현금흐름헤지 중단 후 OCI 후속 처리

**기능명**: `cfh-discontinuation-oci-followup`
**이슈 ID**: AVM-014
**작성자**: accounting-expert 에이전트
**작성일**: 2026-04-23
**RAG 검색 완료**: (완료 — 6.5.12 원문 확보, B6.5.26 원문 확보)

---

## 1. 기능 개요

현금흐름위험회피(CFH) 관계가 중단된 시점에, K-IFRS 1109호 6.5.12에 따라 현금흐름위험회피적립금(OCI
잔액)을 "예상거래 발생 가능 여부"에 따라 자동으로 후속 처리한다.

현재 `HedgeDesignationService.discontinue()`는 `HedgeRelationship.status=DISCONTINUED`
상태 변경만 수행한다. 공정가치헤지(FVH)에는 OCI 잔액이 없으므로 이 후속 처리는
`hedgeType == CASH_FLOW`인 경우에만 적용한다.

---

## 2. K-IFRS 근거

### 핵심 조항

| 조항 | 원문 (RAG 확인) | 적용 |
|------|----------------|------|
| K-IFRS 1109호 6.5.12 | "현금흐름위험회피회계를 중단하는 경우에(문단 6.5.6과 6.5.7(2) 참조) 문단 6.5.11(1)에 따른 현금흐름위험회피적립금 누계액은 다음과 같이 회계처리한다." | 중단 후 OCI 처리 의무 근거 |
| K-IFRS 1109호 6.5.12(1) | "위험회피대상의 미래 현금흐름이 여전히 발생할 것으로 예상되는 경우에 현금흐름위험회피적립금 누계액은 미래 현금흐름이 생길 때까지 또는 문단 6.5.11(4)(나)를 적용할 때까지 현금흐름위험회피적립금에 남겨둔다. 미래 현금흐름이 생길 때 문단 6.5.11(4)를 적용한다." | 경우 1: OCI 유보 후 예상거래 실현 시 재분류 |
| K-IFRS 1109호 6.5.12(2) | "위험회피대상의 미래현금흐름이 더 이상 발생할 것으로 예상되지 않는 경우에 현금흐름위험회피적립금 누계액은 재분류조정(기업회계기준서 제1001호 참조)으로 당기손익으로 즉시 재분류한다. 더 이상 발생할 가능성이 매우 크지 않은 위험회피대상 미래현금흐름도 여전히 발생할 것으로 예상될 수 있다." | 경우 2: OCI 즉시 P&L 이전 의무 |
| K-IFRS 1109호 6.5.11(4)(나) | "현금흐름위험회피적립금이 차손이며 그 차손의 전부나 일부가 미래 기간에 회복되지 않을 것으로 예상된다면, 회복되지 않을 것으로 예상되는 그 금액을 재분류조정으로 즉시 당기손익으로 재분류한다." | 경우 1에서도 OCI 차손 회복 불가 시 즉시 P&L 이전 |
| K-IFRS 1109호 B6.5.26 | "위험회피관계가 전체적으로 적용조건을 충족하지 못하는 경우에는 위험회피관계 전체를 중단한다." — 사유 3가지 열거 | 중단 전제조건 확인 |

### 6.5.11(4)(나) 연동 주의 사항

6.5.12(1)에서 예상거래가 여전히 발생 가능한 경우라도, OCI 잔액이 차손(음수)이고
그 차손의 일부 또는 전부가 미래 기간에 회복될 수 없다고 판단되면 6.5.11(4)(나)에
따라 그 금액을 즉시 당기손익으로 재분류해야 한다. 이는 경우 1과 경우 2가 배타적이지
않음을 의미한다.

---

## 3. 현재 구현 상태 (이슈 진단)

### 3.1 이슈 위치

| 파일 | 메서드 | 현황 |
|------|--------|------|
| `HedgeRelationship.java:752` | `discontinue()` | status=DISCONTINUED 상태 변경만 수행. CFH 여부 판단 없음, OCI 후속 처리 트리거 없음. |
| `HedgeDesignationService.java:134` | `discontinue()` | `relationship.discontinue()` 호출 후 save만 수행. OCI 재분류 분개 생성 호출 없음. |
| `OciReclassificationJournalGenerator.java:55` | `generate()` | `HEDGE_DISCONTINUED` 사유코드가 존재하고 분개 생성 로직은 완성되어 있으나, 서비스에서 호출하지 않음. |
| `ReclassificationReason.java` | (enum) | `HEDGE_DISCONTINUED`, `TRANSACTION_NO_LONGER_EXPECTED` 두 사유코드 모두 정의됨. |

### 3.2 이슈 요약

`HedgeDesignationService.discontinue()`에서 `hedgeType=CASH_FLOW`인 경우
OCI 후속 처리 판단(유보 vs 즉시 P&L)을 수행하지 않으며,
분개 생성 인프라(`OciReclassificationJournalGenerator`)가 있음에도
자동으로 호출되지 않는다.

---

## 4. 경우별 K-IFRS 처리 원칙 (3가지)

### 경우 1: 예상거래가 여전히 발생할 것으로 예상되는 경우

**근거**: K-IFRS 1109호 6.5.12(1)

**상황**: 헤지관계는 중단되었으나, 당초 헤지 대상이었던 예상거래(예: 3개월 후 수출 대금
수취)는 여전히 발생 예정이다.

**OCI 처리**:
- OCI 잔액을 현금흐름위험회피적립금에 그대로 유보한다.
- 중단 시점에 재분류 분개를 생성하지 않는다.
- 이후 예상거래가 실현되어 손익에 영향을 미치는 시점에 6.5.11(4)를 적용한다
  (즉, `TRANSACTION_REALIZED` 사유로 OCI를 P&L에 재분류한다).

**주의**: OCI 잔액이 차손(음수)이고 미래 기간에 회복이 불가능하다고 판단되는 금액이
있다면, 6.5.11(4)(나)에 따라 그 부분만큼 즉시 당기손익으로 재분류한다.

**시스템 처리**:
- 중단 이벤트 발생 → 예상거래 발생 가능성 플래그 확인
- 발생 가능: OCI 유보 상태 기록 (`ociPostDiscontinuationStatus = RETAINED`)
- 별도 재분류 분개 미생성
- 이후 예상거래 실현 API 호출 시 `TRANSACTION_REALIZED` 재분류 분개 자동 생성

**트리거 시점**: 예상거래 실현 시 (별도 API 또는 이벤트)

### 경우 2: 예상거래가 더 이상 발생하지 않는 경우 (발생 불가 확정)

**근거**: K-IFRS 1109호 6.5.12(2)

**상황**: 계약 취소, 거래 무산, 예상 수출 거래 자체가 소멸하는 등 예상거래 자체가
발생하지 않을 것으로 확정된 경우.

**OCI 처리**:
- OCI 잔액 전액을 즉시 당기손익(P&L)으로 재분류한다.
- 재분류조정(K-IFRS 1001호)으로 처리한다.
- 분개: `TRANSACTION_NO_LONGER_EXPECTED` 사유코드 사용.

**금액 산정**:
- 재분류 금액 = 중단 시점의 현금흐름위험회피적립금 누계액 전액
- OCI 잔액이 양수(이익)이면: 차변=CFHR_OCI / 대변=외환이익(또는 해당 P&L 계정)
- OCI 잔액이 음수(손실)이면: 차변=외환손실 / 대변=CFHR_OCI

**트리거 시점**: CFH 중단 요청 시 `forecastTransactionExpected=false` 입력 즉시

**기존 `AVM-012` 연결 주의**: AVM-012(예상거래 미발생 OCI 즉시 P&L)는 헤지관계가
유지되는 도중 예상거래 발생 가능성이 희박해진 케이스다. AVM-014의 경우 2는 헤지관계
중단과 동시에 발생하는 케이스다. 두 케이스 모두 6.5.12(2)를 적용하지만, 트리거 경로가
다르다.

### 경우 3: 일부만 발생 가능성이 남는 경우

**근거**: K-IFRS 1109호 6.5.12, B6.5.27 (일부 중단)

**상황**: 당초 1,000만 달러 예상 수출 중 700만 달러는 여전히 발생 예정이지만,
300만 달러는 계약 취소로 발생 불가 확정.

**OCI 처리**:
- OCI 잔액을 예상거래 금액 비율로 안분한다.
- 발생 가능한 비율에 해당하는 OCI: 유보 (경우 1 처리)
- 발생 불가한 비율에 해당하는 OCI: 즉시 P&L 이전 (경우 2 처리)

**금액 안분 기준**:
```
OCI_유보 = OCI_전체 × (발생가능금액 / 전체헤지대상금액)
OCI_즉시P&L = OCI_전체 × (발생불가금액 / 전체헤지대상금액)
```

**주의**: K-IFRS 1109호 B6.5.27은 위험회피관계의 "일부 중단"이 가능함을 인정하나,
비율 배분 기준은 원래 지정한 명목금액 기준이 실무적으로 통용된다.

**시연 범위**: 경우 3은 구현 복잡도가 높다. 2026-04-30 데모에서는 경우 1과 경우 2만
시연하고, 경우 3은 "추후 구현 가능"으로 구술 설명한다.

---

## 5. 비즈니스 요건

### 5.1 중단 요청 입력 데이터 추가

| 항목 | 설명 | 현재 | 변경 |
|------|------|------|------|
| `forecastTransactionExpected` | 예상거래 여전히 발생 가능 여부 | 미존재 | 추가 필요 (boolean) |
| `currentOciBalance` | 중단 시점 OCI 잔액 | 미존재 | 추가 필요 (BigDecimal) |
| `plAccount` | 즉시 재분류 시 대응 P&L 계정 코드 | 미존재 | 추가 필요 (`AccountCode`) |

`forecastTransactionExpected=true` (경우 1): OCI 유보, 재분류 분개 미생성
`forecastTransactionExpected=false` (경우 2): OCI 전액 즉시 P&L 재분류 분개 생성

### 5.2 출력 데이터

| 항목 | 설명 |
|------|------|
| `discontinuationResult.hedgeStatus` | DISCONTINUED |
| `discontinuationResult.ociTreatment` | RETAINED 또는 IMMEDIATELY_RECLASSIFIED |
| `discontinuationResult.reclassificationJournalId` | 즉시 재분류 시 생성된 분개 ID (nullable) |
| `discontinuationResult.ociRetainedBalance` | 유보된 OCI 잔액 (경우 1) |

### 5.3 처리 흐름

```
PATCH /api/v1/hedge-relationships/{id}/discontinue
  |
  v
HedgeDesignationService.discontinue()
  |
  +-- (기존) HedgeRelationship.discontinue() → status=DISCONTINUED
  |
  +-- [신규] hedgeType 확인
        |
        +-- hedgeType == FAIR_VALUE → 기존 처리 완료 (OCI 없음)
        |
        +-- hedgeType == CASH_FLOW
              |
              +-- forecastTransactionExpected == true (경우 1)
              |     → OCI 유보 상태 기록
              |     → ociPostDiscontinuationStatus = RETAINED
              |     → 분개 미생성
              |
              +-- forecastTransactionExpected == false (경우 2)
                    → OciReclassificationJournalGenerator.generate()
                       (reason=TRANSACTION_NO_LONGER_EXPECTED)
                    → JournalEntry 저장
                    → ociPostDiscontinuationStatus = IMMEDIATELY_RECLASSIFIED
```

---

## 6. 시연 범위 권고

### 시연용 최소 자동화 수준 (2026-04-30 데모)

| 기능 | 구현 범위 | 설명 |
|------|-----------|------|
| 경우 2 자동화 | **필수 구현** | `forecastTransactionExpected=false` 입력 시 즉시 P&L 재분류 분개 자동 생성. `OciReclassificationJournalGenerator`가 이미 존재하므로 서비스 계층 연결만 추가하면 됨. |
| 경우 1 상태 기록 | **필수 구현** | `forecastTransactionExpected=true` 입력 시 OCI 유보 플래그 기록. 분개 생성 없이 상태만 기록. |
| 경우 3 안분 | **시연 제외** | 구현 복잡도 높음. "추후 구현 가능한 구조"로 구술 설명. |
| 6.5.11(4)(나) OCI 차손 회복불가 판정 | **시연 제외** | 고급 판단 로직. 정식 제품에서 구현. |

### 정식 제품에서 필요한 처리 (시연 제외)

1. `HedgedItem.expectedTransactionLikely` 필드 추가 및 상태 관리 API
2. 6.5.11(4)(나): OCI 차손의 미래 회복 가능성 판단 로직 (기준: 회귀분석, 경영진 판단)
3. 경우 3: 부분 발생 가능 시 OCI 안분 처리
4. 예상거래 실현 시 경우 1의 유보된 OCI를 자동으로 P&L 재분류하는 트리거
5. OCI 유보 잔액의 후속 기간 모니터링 알람

---

## 7. 이벤트/트리거 제안

### 서비스 계층 추가 처리 흐름

```
HedgeDesignationService.discontinue() 내부:

1. relationship.discontinue() 호출 (기존 — 상태 변경)
2. hedgeRelationshipRepository.save(relationship) (기존)
3. [신규 분기]
   if (relationship.getHedgeType() == HedgeType.CASH_FLOW) {
       OciPostDiscontinuationResult result =
           ociDiscontinuationService.process(
               hedgeRelationshipId,
               request.forecastTransactionExpected(),
               request.currentOciBalance(),
               request.plAccount(),
               effectiveDate
           );
       // result를 응답에 포함
   }
```

### OciDiscontinuationService (신규 클래스 제안)

**책임**: CFH 중단 후 OCI 후속 처리 판단 및 분개 생성

```
OciDiscontinuationService.process():
  IF forecastTransactionExpected == true
    → 유보 기록 (별도 엔티티 또는 HedgeRelationship 필드)
    → return OciPostDiscontinuationResult(RETAINED, ociBalance, null)
  ELSE
    → OciReclassificationJournalGenerator.generate(
         hedgeRelationshipId,
         discontinuationDate,
         currentOciBalance,
         plAccount,
         TRANSACTION_NO_LONGER_EXPECTED,
         null  // originalOciEntryDate — 유보된 최초 인식일 추적 시 필요
       )
    → journalEntryRepository.save(journalEntry)
    → return OciPostDiscontinuationResult(IMMEDIATELY_RECLASSIFIED, ZERO, journalEntry.id)
```

---

## 8. backend-developer용 구현 규칙

### 규칙 1 — CFH 여부 확인 분기 (서비스 계층 핵심)

**근거**: K-IFRS 1109호 6.5.12는 현금흐름위험회피회계를 중단하는 경우에만 적용된다.
공정가치위험회피(FVH)는 OCI 잔액이 없으므로 이 분기를 통과하지 않는다.

`HedgeDesignationService.discontinue()` 내에서 `save(relationship)` 이후에
`relationship.getHedgeType() == HedgeType.CASH_FLOW`를 확인하고,
CFH인 경우에만 OCI 후속 처리 로직을 실행한다.

### 규칙 2 — `HedgeDiscontinuationRequest`에 3개 필드 추가

아래 3개 필드를 `HedgeDiscontinuationRequest` DTO에 추가한다:
- `forecastTransactionExpected` (Boolean): null이면 BusinessException `HD_017` 발생
  (CFH 중단 시 필수, FVH 중단 시 무시)
- `currentOciBalance` (BigDecimal): 중단 시점 OCI 잔액. null이면 `ZERO` 처리.
  음수 허용 필수 (K-IFRS 1109호 6.5.11(4)(나) — OCI 차손 방향 가능).
- `plAccount` (AccountCode): 즉시 재분류 시 대응 P&L 계정.
  `forecastTransactionExpected=false`인 경우 필수. null이면 `FX_GAIN_LOSS_PL` 기본값 사용.

### 규칙 3 — OCI 즉시 재분류 시 기존 Generator 재사용 (신규 코드 최소화)

`OciReclassificationJournalGenerator.generate()`가 이미 구현되어 있으며,
`TRANSACTION_NO_LONGER_EXPECTED` 사유코드도 존재한다.
서비스 계층에서 이 Generator를 호출하는 것만으로 분개 생성이 완료된다.
Generator 자체를 수정하지 않는다.

`currentOciBalance`가 `ZERO`이면 재분류 금액이 0이므로
분개를 생성하지 않고 로그만 남긴다.
```
if (currentOciBalance.compareTo(BigDecimal.ZERO) == 0) {
    log.info("CFH 중단 — OCI 잔액 0, 재분류 분개 미생성: hedgeRelationshipId={}",
             hedgeRelationshipId);
    return OciPostDiscontinuationResult.retained(ZERO);
}
```

### 규칙 4 — BigDecimal 부호 전달 원칙

`currentOciBalance`를 `OciReclassificationJournalGenerator.generate()`의
`reclassificationAmount` 파라미터로 그대로 전달한다.
Generator 내부에서 양수이면 OCI 이익 재분류 분개, 음수이면 OCI 손실 재분류 분개를
자동으로 선택한다 (`OciReclassificationJournalGenerator.java:76` 분기 로직 확인).
절댓값 변환 금지.

### 규칙 5 — 트랜잭션 원자성 보장

`discontinue()` 메서드 전체가 `@Transactional`이어야 한다.
헤지관계 상태 변경과 OCI 재분류 분개 저장이 반드시 하나의 트랜잭션 안에서 처리되어야 한다.
분개 저장이 실패하면 상태 변경도 롤백되어야 한다.
`HedgeDesignationService.discontinue()`의 기존 `@Transactional` 어노테이션이
이미 존재하므로, 추가 생성 없이 범위 내에서 처리한다.

---

## 9. 데모 시나리오 검증값

**시연 케이스: CFH 중단 + 예상거래 발생 불가 (경우 2)**

```
입력:
  hedgeRelationshipId = "HR-2026-001" (hedgeType=CASH_FLOW)
  discontinuationDate = 2026-04-23
  reason = ELIGIBILITY_CRITERIA_NOT_MET
  forecastTransactionExpected = false
  currentOciBalance = -5,000,000 (500만 원 OCI 차손 잔액)
  plAccount = FX_LOSS_PL

기대 결과:
  1. HedgeRelationship.status = DISCONTINUED
  2. 재분류 분개 생성:
     차변: FX_LOSS_PL   5,000,000원
     대변: CFHR_OCI     5,000,000원
     ifrsReference: K-IFRS 1109호 6.5.12(2)
     reclassificationReason: TRANSACTION_NO_LONGER_EXPECTED
  3. HTTP 200 응답:
     ociTreatment = IMMEDIATELY_RECLASSIFIED
     reclassificationJournalId = "JE-2026-XXX"
```

**시연 케이스: CFH 중단 + 예상거래 유보 (경우 1)**

```
입력:
  hedgeRelationshipId = "HR-2026-002" (hedgeType=CASH_FLOW)
  forecastTransactionExpected = true
  currentOciBalance = 8,000,000 (800만 원 OCI 이익 잔액)

기대 결과:
  1. HedgeRelationship.status = DISCONTINUED
  2. 재분류 분개 미생성
  3. HTTP 200 응답:
     ociTreatment = RETAINED
     ociRetainedBalance = 8,000,000
     reclassificationJournalId = null
```

---

## 10. 예외 처리

| 상황 | 처리 방법 | 에러코드 |
|------|-----------|---------|
| CFH 중단인데 `forecastTransactionExpected` 누락 | BusinessException 발생 | HD_017 |
| `currentOciBalance` null | ZERO로 처리 후 분개 미생성, 경고 로그 기록 | — |
| `plAccount` null이고 즉시 재분류 필요 | `FX_GAIN_LOSS_PL` 기본값 적용, 경고 로그 기록 | — |
| FVH 중단인데 `forecastTransactionExpected` 입력됨 | 무시 (CFH 전용 필드) | — |

---

## 11. ACCOUNTING_VALIDATION_MATRIX.md AVM-014 업데이트 기준

### 현재 상태: 이슈있음

### "검토중"으로 올리는 조건

- `HedgeDiscontinuationRequest`에 3개 필드 추가
- `HedgeDesignationService.discontinue()`에 CFH 분기 진입점 추가

### "적합"으로 올리는 조건 (시연 기준)

다음 4가지를 모두 충족해야 한다:

1. **경우 2 자동화**: `forecastTransactionExpected=false` 요청 시
   `OciReclassificationJournalGenerator.generate()`가 자동 호출되어
   `TRANSACTION_NO_LONGER_EXPECTED` 사유코드로 분개가 생성된다.

2. **경우 1 상태 기록**: `forecastTransactionExpected=true` 요청 시
   OCI 유보 상태가 응답에 반영된다. 분개는 생성되지 않는다.

3. **트랜잭션 원자성**: 상태 변경과 분개 생성이 하나의 트랜잭션으로 처리된다.
   (기존 `@Transactional` 범위 내 처리)

4. **시연 시나리오 검증값 일치**: 위 섹션 9의 두 케이스가 API 호출로 재현된다.

### AVM-014 행 수정 내용 (적합 상태 반영 시)

```markdown
| AVM-014 | 중단처리 | 현금흐름헤지 중단 후 OCI 후속 처리 | K-IFRS 1109호 6.5.12, B6.5.26 |
| 6.5.12(1): 예상거래 발생 가능 시 OCI 유보.
  6.5.12(2): 발생 불가 시 즉시 전액 P&L 재분류 (재분류조정).
  forecastTransactionExpected 플래그로 경로 분기. |
| CFH 중단 + forecastTransactionExpected=false + OCI잔액=−500만 |
| 즉시 재분류 분개: 차:FX_LOSS_PL 500만 / 대:CFHR_OCI 500만.
  reason=TRANSACTION_NO_LONGER_EXPECTED |
| `HedgeDesignationService.java:discontinue()` (신규 CFH 분기),
  `OciReclassificationJournalGenerator.java:55` (기존) |
| **적합** | 경우 3(일부 발생) 및 6.5.11(4)(나) OCI차손 회복불가 판정은
  시연 범위 외 — 정식 제품에서 구현 | (날짜) |
```

---

## 12. 구현 순서 (backend-developer 에이전트 참고)

1. `HedgeDiscontinuationRequest` DTO에 3개 필드 추가
2. `OciDiscontinuationService` (또는 `HedgeDesignationService` 내 private 메서드) 작성
   — 분기 로직 + `OciReclassificationJournalGenerator` 호출
3. `HedgeDesignationService.discontinue()` 내부에 CFH 분기 추가 (save 이후 위치)
4. HTTP 응답 DTO에 `ociTreatment`, `reclassificationJournalId`, `ociRetainedBalance` 추가
5. 단위테스트:
   - CFH + forecastTransactionExpected=false + OCI 음수 → 분개 생성 확인
   - CFH + forecastTransactionExpected=false + OCI 양수 → 분개 생성 확인
   - CFH + forecastTransactionExpected=true → 분개 미생성 확인
   - CFH + forecastTransactionExpected=null → HD_017 예외 확인
   - FVH 중단 → OCI 분기 미진입 확인
   - currentOciBalance=ZERO → 분개 미생성 확인

---

## 13. 다음 단계

- **backend-developer 에이전트**: 이 요구사항을 기반으로 구현 시작
- **연계 이슈 AVM-012**: 예상거래 미발생 OCI 즉시 P&L (헤지 유지 중 케이스) — 별도 처리
- **ACCOUNTING_VALIDATION_MATRIX.md**: 구현 완료 후 AVM-014를 "적합"으로 갱신

---

*K-IFRS 근거: 1109호 6.5.12(1)(2), 6.5.11(4)(나), B6.5.26, B6.5.27*
*작성 기반: RAG 검색 (K-IFRS 1109호 6.5.12 원문 확보, B6.5.26 원문 확보)*
*코드 검토: HedgeRelationship.java, HedgeDesignationService.java,*
*           OciReclassificationJournalGenerator.java, ReclassificationReason.java*
