# HedgeRebalancingService 테스트 보강 — 평가 대응 요약본

> 작성 기준일: 2026-04-23  
> 대상 PR 범위: HedgeRebalancingServiceTest 테스트 1건 → 11건으로 보강

---

## 1. 실제 수정된 파일 목록

| 파일 | 변경 유형 |
|---|---|
| `backend/src/test/java/com/hedge/prototype/hedge/application/HedgeRebalancingServiceTest.java` | 대폭 보강 (1개 → 10개 테스트) |

프로덕션 코드는 변경 없음. 테스트 픽스처/헬퍼도 동일 파일 내 private 메서드로 최소화 (총 11개 @Test).

---

## 2. 각 파일에서 무엇을 왜 고쳤는가

### `HedgeRebalancingServiceTest.java`

**무엇을:** relationship mismatch 1건만 있던 테스트를 3개 Nested 클래스, 11개 테스트 메서드(3 + 4 + 4)로 확장.

**왜:** `HedgeRebalancingService`의 핵심 분기(비효과성 금액 유무에 따른 분개 생성 여부, FAIR_VALUE/CASH_FLOW 분개 형식 분리, ±20% 클램프 비율 계산)가 EventHandler 테스트에서도 간접적으로 닿지만, **서비스 자체 계약**으로 테스트 보호가 없어 리그레션 발생 시 원인 추적이 어려웠기 때문.

---

## 3. 추가된 / 강화된 테스트 목록

### `GuardClauseTest` — 방어 분기

| # | 테스트 DisplayName 요약 | 핵심 검증 |
|---|---|---|
| 1 | mismatch 테스트로 리밸런싱 불가 → save/createEntries 부작용 없음 | `BusinessException` 발생 + 메시지에 hedgeRelationshipId 포함 확인 + 사이드이펙트 zero |
| 2 | ET not found → `BusinessException` 발생 + 부작용 없음 | `findById` Optional.empty → 메시지에 testId 포함 확인 |
| 3 | 관계 not found → `BusinessException` 발생 + 분개 미생성 | 메시지에 hedgeRelationshipId 포함 확인 + relationship 로드 실패 시 분개 미호출 |

### `PreRebalancingIneffectivenessTest` — B6.5.8 분기

| # | 테스트 DisplayName 요약 | 핵심 검증 |
|---|---|---|
| 4 | `ineffectiveAmount == 0` → 분개 생략, 재조정 자체는 진행 | `never().createEntries` + `rebalance/save` 1회 |
| 5 | `ineffectiveAmount == null` → 0과 동등 취급, 분개 생략 | null 방어 로직 확인 |
| 6 | `FAIR_VALUE + ineffective != 0` → FVH 형식으로 분개 1회 | `hedgeType=FAIR_VALUE`, `effectiveAmount==null` |
| 7 | `CASH_FLOW + ineffective != 0` → CFH 형식으로 분개 1회 | `effectiveAmount/ineffectiveAmount` 값 포함 확인 |

### `RebalancingRatioTest` — 비율 계산 + save 연동

| # | 테스트 DisplayName 요약 | 핵심 검증 |
|---|---|---|
| 8 | 경미 over-hedge (eff=−1.052632) → [0.80, 1.20] 이내 & 현재보다 감소, 감사 사유에 "6.5.5" 포함 | 비율 방향·범위 + 사유 문자열 |
| 9 | 극단 under-hedge (eff=−0.50) → +20% 상한 클램프 → `1.2000` | `isEqualByComparingTo("1.2000")` |
| 10 | 극단 over-hedge (eff=−3.00) → −20% 하한 클램프 → `0.8000` | `isEqualByComparingTo("0.8000")` |
| 11 *(기존+강화)* | `effectivenessRatio == null` → 현재 비율 유지, 분개 미생성 | null 방어 + ratio 불변 |

---

## 4. 이번 수정의 의미 — 항목별 요약

### 보안성
K-IFRS 준거 분개가 엉뚱한 헤지관계에 대해 생성되는 것을 차단하는 mismatch 방어 로직을 테스트로 명시적으로 보호. 감사 추적 사유 문자열(K-IFRS 6.5.5 조항 포함)이 항상 기록됨을 테스트가 강제하므로, 향후 코드 변경 시 추적 근거 누락을 조기에 감지할 수 있다.

### 효율성
분개 생성(`createEntries`)이 `ineffectiveAmount == 0`일 때 호출되지 않음을 단위 테스트가 보장하므로, 불필요한 DB INSERT를 운영 중 우연히 유발하는 실수를 사전에 방지. Mockito BDD 스타일(`then(...).should(never())`)로 부작용 검증이 명확해 CI에서 회귀를 즉시 감지 가능.

### 정확성
FAIR_VALUE / CASH_FLOW 분기에서 각각 `forAutoGenerationFvh` / `forAutoGenerationCfh` 팩토리가 올바르게 선택되는지, 그리고 CFH에서 `effectiveAmount` / `ineffectiveAmount`가 실제로 분개 요청에 담기는지를 `ArgumentCaptor`로 필드 수준까지 검증. ±20% 클램프 로직은 경계값(1.2000, 0.8000)을 `BigDecimal.isEqualByComparingTo`로 스케일에 관계없이 정밀하게 검증.

### 디버깅
Nested 클래스 + 의미 있는 `@DisplayName`으로 실패 메시지만 봐도 어느 분기가 깨졌는지 즉시 식별 가능. 각 테스트가 단일 책임(분개 스킵 / FVH 분개 / CFH 분개 / 클램프 상한 / 클램프 하한)만 검증하므로, 스택트레이스 없이도 근본 원인을 좁힐 수 있다.

---

## 5. 면접 / 평가 자리에서 설명할 수 있는 1분 답변 초안

> "기존 테스트는 헤지관계 mismatch 방어 1건만 있어서 서비스의 실제 핵심 계약이 보호받지 못하는 상태였습니다.
>
> 저는 서비스 책임을 세 축으로 나눠 테스트를 설계했습니다. 첫 번째는 **방어 분기** — 존재하지 않는 유효성 테스트나 헤지관계, 혹은 헤지관계 ID가 불일치할 때 `BusinessException`이 발생하고 메시지에 식별자가 포함되는지, 그리고 분개·저장 같은 부작용이 전혀 없는지 검증했습니다. 에러 코드 상수 자체를 단언하진 않았지만, 메시지 내용과 사이드이펙트 zero를 확인하는 것만으로도 계약 위반 시 테스트가 즉시 깨집니다. 두 번째는 **K-IFRS 1109호 B6.5.8 — 재조정 전 비효과성 분개** — `ineffectiveAmount`가 0이나 null이면 분개가 생략되고, 0이 아닐 때는 FAIR_VALUE와 CASH_FLOW에 따라 각각 FVH·CFH 형식의 올바른 요청이 생성되는지를 `ArgumentCaptor`로 필드 수준까지 확인했습니다. 세 번째는 **비율 계산 클램프** — Dollar-offset 비율이 극단값일 때 현재 비율 대비 ±20% 이내로 클램프되는지, `BigDecimal.isEqualByComparingTo`로 스케일에 무관하게 정밀하게 검증했습니다.
>
> 결과적으로 테스트가 문서 역할도 겸하게 됐습니다. Nested 클래스와 의미 있는 DisplayName 덕분에 CI에서 어떤 테스트가 실패했는지만 봐도 어느 분기가 깨졌는지 바로 파악할 수 있고, 리그레션 원인 추적 시간이 크게 단축됩니다."

---

*이 문서는 평가 대응용 요약본입니다. 상세 구현은 `HedgeRebalancingServiceTest.java` 참조.*
