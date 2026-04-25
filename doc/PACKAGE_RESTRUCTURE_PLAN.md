# 패키지 구조 재편 영향 분석 및 실행 기록

> 작성일: 2026-04-23
> 상태: Java 패키지 이동 완료 / 문서·에이전트 정리 완료 / event 재배치 보류
> 목적: `<layer>.<domain>` -> `<domain>.<layer>` 구조 전환 영향 범위 파악

---

## 1. 요약

### 변경 핵심

| 현재 | 목표 |
|---|---|
| 레이어(adapter/application/domain)가 최상위 | 기능 도메인(hedge/journal/valuation/effectiveness)이 최상위 |
| `com.hedge.prototype.domain.hedge.*` | `com.hedge.prototype.hedge.domain.*` |
| `com.hedge.prototype.application.valuation.*` | `com.hedge.prototype.valuation.application.*` |

### 영향 규모

| 구분 | 파일 수 | 비고 |
|---|---|---|
| Java 프로덕션 파일 | 110개 | package/import 변경 대상 |
| Java 테스트 파일 | 14개 | package 선언 + import 변경 대상 |
| Domain 계층 | 39개 | 도메인별 이동 대상 |
| Application 계층 | 16개 | 서비스/유스케이스 |
| Application Port 계층 | 11개 | 리포지토리/스펙 |
| Adapter 계층 | 29개 | Controller + DTO |
| Event 계층 | 6개 | 별도 배치 원칙 필요 |
| 유지 대상(common/config/root) | 9개 | 공통/설정 |

### 선결 판단

- `common.*`, `config.*`는 루트에 유지하는 것이 타당하다.
- 이번 재편은 단순 폴더 이동보다 `타입 소유권`과 `도메인 경계`를 먼저 정리해야 한다.
- 바로 패키지 이동에 들어가기보다, `공유 타입 / 이벤트 / 직접 참조` 원칙을 먼저 확정해야 한다.

---

## 2. 실제 패키지 매핑표

### 2-1. Domain 계층

| 현재 패키지 | 목표 패키지 | 파일 수 |
|---|---|---|
| `c.h.p.domain.hedge` | `c.h.p.hedge.domain` | 12 |
| `c.h.p.domain.valuation` | `c.h.p.valuation.domain` | 13 |
| `c.h.p.domain.effectiveness` | `c.h.p.effectiveness.domain` | 6 |
| `c.h.p.domain.journal` | `c.h.p.journal.domain` | 8 |

> `c.h.p` = `com.hedge.prototype`

### 2-2. Application 계층

| 현재 패키지 | 목표 패키지 | 파일 수 |
|---|---|---|
| `c.h.p.application.hedge` | `c.h.p.hedge.application` | 4 |
| `c.h.p.application.valuation` | `c.h.p.valuation.application` | 7 |
| `c.h.p.application.effectiveness` | `c.h.p.effectiveness.application` | 2 |
| `c.h.p.application.journal` | `c.h.p.journal.application` | 3 |

### 2-3. Application Port 계층

| 현재 패키지 | 목표 패키지 | 파일 수 |
|---|---|---|
| `c.h.p.application.port.hedge` | `c.h.p.hedge.application.port` | 3 |
| `c.h.p.application.port.valuation` | `c.h.p.valuation.application.port` | 6 |
| `c.h.p.application.port.effectiveness` | `c.h.p.effectiveness.application.port` | 1 |
| `c.h.p.application.port.journal` | `c.h.p.journal.application.port` | 1 |

### 2-4. Adapter 계층

| 현재 패키지 | 목표 패키지 | 파일 수 |
|---|---|---|
| `c.h.p.adapter.web.hedge` | `c.h.p.hedge.adapter.web` | 1 |
| `c.h.p.adapter.web.hedge.dto` | `c.h.p.hedge.adapter.web.dto` | 10 |
| `c.h.p.adapter.web.valuation` | `c.h.p.valuation.adapter.web` | 3 |
| `c.h.p.adapter.web.valuation.dto` | `c.h.p.valuation.adapter.web.dto` | 9 |
| `c.h.p.adapter.web.effectiveness` | `c.h.p.effectiveness.adapter.web` | 1 |
| `c.h.p.adapter.web.effectiveness.dto` | `c.h.p.effectiveness.adapter.web.dto` | 2 |
| `c.h.p.adapter.web.journal` | `c.h.p.journal.adapter.web` | 1 |
| `c.h.p.adapter.web.journal.dto` | `c.h.p.journal.adapter.web.dto` | 2 |

### 2-5. Event 계층

이벤트는 초기에는 배치 원칙 검토 대상으로 두었고, 현재는 도메인별 `application/event` 아래로 재배치 완료했다.

| 현재 패키지 | 설명 | 권장 원칙 |
|---|---|---|
| `c.h.p.application.event.designated` | 헤지 지정 이벤트 | `c.h.p.hedge.application.event` 로 이동 완료 |
| `c.h.p.application.event.tested` | 유효성 테스트 완료 이벤트 | `c.h.p.effectiveness.application.event` 로 이동 완료 |
| `c.h.p.application.event.valuated` | 평가 완료 이벤트 | `c.h.p.valuation.application.event` 로 이동 완료 |

### 2-6. 유지 대상

| 패키지 | 파일 수 | 이유 |
|---|---|---|
| `c.h.p.common.audit` | 1 | 기술 공통 |
| `c.h.p.common.dto` | 1 | 기술 공통 |
| `c.h.p.common.exception` | 2 | 기술 공통 |
| `c.h.p.config` | 4 | Spring 설정 |
| `c.h.p` | 1 | 애플리케이션 루트 |

---

## 3. 수정 대상 파일 목록

### 3-1. Java 프로덕션 파일

#### Hedge 도메인
```
hedge/domain/policy/ConditionResult.java
hedge/domain/common/CreditRating.java
hedge/domain/policy/EligibilityCheckResult.java
hedge/domain/common/EligibilityStatus.java
hedge/domain/common/HedgeDiscontinuationReason.java
hedge/domain/model/HedgedItem.java
hedge/domain/common/HedgedItemType.java
hedge/domain/common/HedgedRisk.java
hedge/domain/model/HedgeRelationship.java
hedge/domain/common/HedgeStatus.java
hedge/domain/common/HedgeType.java
hedge/domain/common/InstrumentType.java

hedge/application/HedgeCommandUseCase.java
hedge/application/HedgeDesignationService.java
hedge/application/HedgeIdGenerator.java
hedge/application/HedgeRebalancingService.java

hedge/application/port/HedgedItemRepository.java
hedge/application/port/HedgeRelationshipRepository.java
hedge/application/port/HedgeRelationshipSpec.java

hedge/adapter/web/HedgeDesignationController.java
hedge/adapter/web/HedgeResponseMapper.java
hedge/adapter/web/dto/ConditionResultResponse.java
hedge/adapter/web/dto/DocumentationSummary.java
hedge/adapter/web/dto/EligibilityCheckResultResponse.java
hedge/adapter/web/dto/HedgeDesignationRequest.java
hedge/adapter/web/dto/HedgeDesignationResponse.java
hedge/adapter/web/dto/HedgeDiscontinuationRequest.java
hedge/adapter/web/dto/HedgedItemRequest.java
hedge/adapter/web/dto/HedgedItemResponse.java
hedge/adapter/web/dto/HedgeRelationshipSummaryResponse.java
hedge/adapter/web/dto/HedgingInstrumentResponse.java
```

#### Valuation 도메인
```
valuation/domain/common/ContractStatus.java
valuation/domain/crs/CrsContract.java
valuation/domain/crs/CrsPricing.java
valuation/domain/crs/CrsValuation.java
valuation/domain/common/DayCountConvention.java
valuation/domain/common/FairValueLevel.java
valuation/domain/fxforward/FxForwardContract.java
valuation/domain/fxforward/FxForwardPosition.java
valuation/domain/fxforward/FxForwardPricing.java
valuation/domain/fxforward/FxForwardValuation.java
valuation/domain/irs/IrsContract.java
valuation/domain/irs/IrsPricing.java
valuation/domain/irs/IrsValuation.java

valuation/application/CrsValuationService.java
valuation/application/CrsValuationUseCase.java
valuation/application/FxForwardValuationService.java
valuation/application/FxForwardValuationUseCase.java
valuation/application/IrsValuationService.java
valuation/application/IrsValuationUseCase.java
valuation/application/ValuationResult.java

valuation/application/port/CrsContractRepository.java
valuation/application/port/CrsValuationRepository.java
valuation/application/port/FxForwardContractRepository.java
valuation/application/port/FxForwardValuationRepository.java
valuation/application/port/IrsContractRepository.java
valuation/application/port/IrsValuationRepository.java

valuation/adapter/web/CrsValuationController.java
valuation/adapter/web/FxForwardValuationController.java
valuation/adapter/web/IrsValuationController.java
valuation/adapter/web/dto/CrsContractRequest.java
valuation/adapter/web/dto/CrsValuationRequest.java
valuation/adapter/web/dto/CrsValuationResponse.java
valuation/adapter/web/dto/FxForwardContractResponse.java
valuation/adapter/web/dto/FxForwardValuationRequest.java
valuation/adapter/web/dto/FxForwardValuationResponse.java
valuation/adapter/web/dto/IrsContractRequest.java
valuation/adapter/web/dto/IrsValuationRequest.java
valuation/adapter/web/dto/IrsValuationResponse.java
```

#### Effectiveness 도메인
```
domain/effectiveness/ActionRequired.java
domain/effectiveness/DollarOffsetCalculator.java
domain/effectiveness/EffectivenessTest.java
domain/effectiveness/EffectivenessTestResult.java
domain/effectiveness/EffectivenessTestType.java
domain/effectiveness/LowerOfTestCalculator.java

application/effectiveness/EffectivenessTestService.java
application/effectiveness/EffectivenessTestUseCase.java

application/port/effectiveness/EffectivenessTestRepository.java

hedge/application/event/HedgeDesignatedEvent.java
hedge/application/event/HedgeDesignatedEventHandler.java
effectiveness/application/event/EffectivenessTestCompletedEvent.java
effectiveness/application/event/EffectivenessTestCompletedEventHandler.java
valuation/application/event/ValuationCompletedEvent.java
valuation/application/event/ValuationCompletedEventHandler.java

adapter/web/effectiveness/EffectivenessTestController.java
adapter/web/effectiveness/dto/EffectivenessTestRequest.java
adapter/web/effectiveness/dto/EffectivenessTestResponse.java
```

#### Journal 도메인
```
domain/journal/AccountCode.java
domain/journal/AccountType.java
domain/journal/CashFlowHedgeJournalGenerator.java
domain/journal/FairValueHedgeJournalGenerator.java
domain/journal/JournalEntry.java
domain/journal/JournalEntryType.java
domain/journal/OciReclassificationJournalGenerator.java
domain/journal/ReclassificationReason.java

application/journal/JournalEntryService.java
application/journal/JournalEntryUseCase.java
application/journal/PdfExporter.java

application/port/journal/JournalEntryRepository.java

adapter/web/journal/JournalEntryController.java
adapter/web/journal/dto/JournalEntryRequest.java
adapter/web/journal/dto/JournalEntryResponse.java
```

### 3-2. Java 테스트 파일

현재 테스트 파일 14개는 아래와 같다.

| 현재 경로 | 목표 경로 |
|---|---|
| `adapter/web/hedge/HedgeDesignationControllerTest` | `hedge.adapter.web.HedgeDesignationControllerTest` |
| `adapter/web/journal/dto/JournalEntryRequestTest` | `journal.adapter.web.dto.JournalEntryRequestTest` |
| `effectiveness/application/EffectivenessTestServiceTest` | `effectiveness.application.EffectivenessTestServiceTest` |
| `effectiveness/application/event/EffectivenessTestCompletedEventHandlerTest` | `effectiveness.application.event.EffectivenessTestCompletedEventHandlerTest` |
| `hedge/application/HedgeDesignationServiceCfhDiscontinuationTest` | `hedge.application.HedgeDesignationServiceCfhDiscontinuationTest` |
| `hedge/application/HedgeIdGeneratorTest` | `hedge.application.HedgeIdGeneratorTest` |
| `hedge/application/HedgeRebalancingServiceTest` | `hedge.application.HedgeRebalancingServiceTest` |
| `journal/application/JournalEntryServiceValidationTest` | `journal.application.JournalEntryServiceValidationTest` |
| `effectiveness/domain/LowerOfTestCalculatorTest` | `effectiveness.domain.LowerOfTestCalculatorTest` |
| `hedge/domain/model/HedgeRelationshipTest` | `hedge.domain.model.HedgeRelationshipTest` |
| `valuation/domain/crs/CrsPricingTest` | `valuation.domain.crs.CrsPricingTest` |
| `valuation/domain/fxforward/FxForwardContractTest` | `valuation.domain.fxforward.FxForwardContractTest` |
| `valuation/domain/fxforward/FxForwardPricingTest` | `valuation.domain.fxforward.FxForwardPricingTest` |
| `valuation/domain/irs/IrsPricingTest` | `valuation.domain.irs.IrsPricingTest` |

### 3-3. 문서 수정 대상

현재 확인된 수정 대상은 아래와 같다.

- [ACCOUNTING_VALIDATION_MATRIX.md](/C:/account/hedge-prototype/doc/ACCOUNTING_VALIDATION_MATRIX.md)
- [BACKLOG_REFACTORING.md](/C:/account/hedge-prototype/doc/BACKLOG_REFACTORING.md)
- [DEMO_SCENARIO.md](/C:/account/hedge-prototype/doc/DEMO_SCENARIO.md)
- [DEV_LOG_20260420.md](/C:/account/hedge-prototype/doc/DEV_LOG_20260420.md)
- [PROJECT_BRIEF.md](/C:/account/hedge-prototype/doc/PROJECT_BRIEF.md)
- [REBALANCING_TEST_SUMMARY.md](/C:/account/hedge-prototype/doc/REBALANCING_TEST_SUMMARY.md)
- [code_fix_log_20260423.md](/C:/account/hedge-prototype/doc/knowledge/code_fix_log_20260423.md)
- [code_review_1039_vs_1109.md](/C:/account/hedge-prototype/doc/knowledge/code_review_1039_vs_1109.md)
- [.claude/CLAUDE.md](C:/account/hedge-prototype/.claude/CLAUDE.md)

### 3-4. 에이전트/명령 파일 수정 대상

- [.claude/agents/backend-developer.md](C:/account/hedge-prototype/.claude/agents/backend-developer.md)
- [.claude/agents/backend-validator.md](C:/account/hedge-prototype/.claude/agents/backend-validator.md)
- [.claude/commands/test-domain.md](C:/account/hedge-prototype/.claude/commands/test-domain.md)

---

## 4. 핵심 리스크

### 리스크 1: `HedgeType`의 소유권

`HedgeType`은 hedge 도메인에 속해 보이지만 effectiveness와 journal에서도 적극 사용된다.  
따라서 "많이 쓰이니 common으로 올린다"보다, `정말 공유 커널로 볼 것인지`를 먼저 정해야 한다.

### 리스크 2: `CreditRating`의 성격

`CreditRating`은 `HedgeType`과 같은 급으로 common 승격을 결정할 타입은 아니다.  
실제 사용 범위와 소유 도메인을 따로 판단해야 한다.

### 리스크 3: 이벤트 배치 원칙

이벤트 클래스와 핸들러를 어디에 둘지는 단순 move 문제가 아니다.

- 이벤트 클래스는 발행 도메인 가까이에 둘 수 있다.
- 핸들러는 실제 부수효과를 소유하는 쪽 기준으로 볼 수 있다.
- 단순 감사 로그 핸들러는 굳이 분리하지 않아도 된다.

### 리스크 4: `HedgeRelationship -> FxForwardContract` 직접 참조

현재 구조는 hedge 도메인이 valuation 도메인 엔티티를 직접 안다.  
패키지 이동만으로 이 문제가 해결되지는 않는다.  
장기적으로는 `instrumentType + instrumentId`, port, snapshot DTO 같은 방식으로 끊을지 검토가 필요하다.

### 리스크 5: application 계층의 adapter DTO 의존

현재 일부 application 계층이 adapter DTO를 직접 받거나 생성한다.  
패키지 재편만 해도 되긴 하지만, 구조 개선까지 노린다면 함께 정리할 후보이다.

---

## 5. 자동 이동 가능 / 수작업 검토 필요

### 자동 이동 가능

IDE Refactor -> Move로 비교적 안전하게 처리 가능한 구간:

- journal 도메인 전체
- valuation의 pricing/domain 일부
- valuation controller/dto
- effectiveness calculator 계층
- hedge controller/dto

### 수작업 검토 필요

- `HedgeType`, `CreditRating` 위치 결정
- 이벤트 클래스/핸들러 배치 원칙
- `EffectivenessTestService`의 다중 도메인 import
- `HedgeDesignationService`의 valuation 도메인 참조
- `HedgeRelationship`의 직접 엔티티 참조
- 문서/에이전트 파일 경로 갱신

---

## 6. 권장 실행 순서

### Phase 0 - 현재 확정된 결론

2026-04-23 현재 기준으로 아래처럼 진행한다.

1. `common.*`, `config.*`는 루트에 유지한다.
2. `HedgeType`, `CreditRating`는 이번 단계에서 `common`으로 올리지 않는다.
3. 이벤트는 이번 재편에서 먼저 물리 이동하지 않고, 도메인 이동 후 별도 정리한다.
4. `hedge -> valuation` 직접 참조는 우선 유지하되, 이번 단계에서 더 늘리지 않는다.

### Phase 0 - 선결 원칙 확정

아래 세 가지를 먼저 결정한다.

1. `HedgeType`, `CreditRating` 등 공유 타입의 최종 위치
2. 이벤트 클래스/핸들러 배치 원칙
3. `hedge -> valuation` 직접 참조를 유지할지, 최소화할지

### Phase 1 - Journal 이동

가장 독립적이라 첫 단계로 적합하다.

상태: 2026-04-23 기준 완료

- `domain.journal.*` -> `journal.domain.*`
- `application.journal.*` -> `journal.application.*`
- `application.port.journal.*` -> `journal.application.port.*`
- `adapter.web.journal.*` -> `journal.adapter.web.*`

검증:

- `./gradlew test --tests "*Journal*"`
- 필요 시 `./gradlew test`

### Phase 2 - Effectiveness 이동

- calculator/domain 먼저
- service/usecase/port 다음
- controller/dto 마지막
- event 패키지는 Phase 0 원칙에 따라 별도 처리

상태: 2026-04-23 기준 완료

검증:

- `./gradlew test --tests "*Effectiveness*"`
- `./gradlew test --tests "*LowerOfTest*"`

### Phase 3 - Valuation 이동

- `FxForward -> IRS -> CRS` 또는 패키지 단위 일괄 이동
- controller/dto는 domain/application 이동 뒤 정리

상태: 2026-04-23 기준 완료

검증:

- `./gradlew test --tests "*FxForward*"`
- `./gradlew test --tests "*Irs*"`
- `./gradlew test --tests "*Crs*"`

### Phase 4 - Hedge 이동

가장 마지막이 안전하다.

- cross-domain 참조가 가장 많다.
- 앞선 phases가 끝난 뒤 최종 import 정리를 하는 편이 낫다.

검증:

- `./gradlew test --tests "*Hedge*"`
- `./gradlew test`

상태: 2026-04-23 기준 완료

### Phase 5 - 문서/에이전트 정리

코드 구조가 확정된 뒤 아래를 갱신한다.

- 구조 트리 설명
- grep 경로
- 경로 하드코딩 문서
- 패키지 예시 import

상태: 2026-04-23 기준 완료

### Phase 6 - 최종 검증

```bash
./gradlew build
./gradlew test
```

구 패키지 잔재 확인:

```bash
grep -r "com.hedge.prototype.domain" src/
grep -r "com.hedge.prototype.application.event" src/
grep -r "com.hedge.prototype.adapter" src/
```

Windows 환경에서는 `Select-String` 기반 확인으로 대체 가능하다.

---

## 7. 지금 바로 실행해도 되는가

답은 `예, 하지만 문서에 적힌 순서를 그대로 따르기보다 이 문서의 교정본 순서대로 가는 것이 안전하다`이다.

핵심은 다음 두 줄로 요약된다.

- 먼저 `원칙 3개`를 정한다.
- 그 다음 `journal -> effectiveness -> valuation -> hedge` 순으로 끊어서 옮긴다.

---

## 참조

- 원본 요청: 2026-04-23 패키지 재편 영향 분석 요청
- 실제 코드 기준: 프로덕션 110개, 테스트 14개
- 관련 백로그: [BACKLOG_REFACTORING.md](/C:/account/hedge-prototype/doc/BACKLOG_REFACTORING.md)
