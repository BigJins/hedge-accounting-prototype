# 리팩토링 백로그

> 작성일: 2026-04-23
> 작성자: 코드 검토 에이전트 (코드 리뷰 세션)

---

## 구조 재배치 현황 (현재 / 향후)

문서 정합성을 위해 구조 변경 진행도를 이 문서에서 한 번 더 요약한다. 상세는 [PACKAGE_RESTRUCTURE_PLAN.md](PACKAGE_RESTRUCTURE_PLAN.md), [ARCHITECTURE_MEMO.md](ARCHITECTURE_MEMO.md) 참조.

| 항목 | 상태 | 메모 |
|---|---|---|
| `<layer>.<domain>` → `<domain>.<layer>` 패키지 이동 | **완료** | `hedge / valuation / effectiveness / journal / common / config` 6개 최상위 패키지로 정리 완료 |
| 각 BC 내부 `adapter / application / domain` 3계층 유지 | **완료** | `application/port`, `application/event`, `domain/{model,common,policy}` 하위 구조 포함 |
| BC 간 타입 직접 참조 제거 (포트 기반 연결) | **진행 전** | 현재 `effectiveness → hedge.domain` 등 직접 import 일부 잔존 |
| 단일 모듈 → 멀티 모듈 (`hedge-core`, `valuation`, `effectiveness`, `journal`) 분리 | **진행 전** | 시연 이후 단계 |
| `HedgeDesignationService` 책임 분리 (지정/중단/Rebalancing 연동/재분류) | **진행 전** | AVM-014·AVM-015 해소 이후에도 여전히 단일 서비스에 집중 |
| 이벤트 의미 단위 세분화 (`EffectivenessTestCompleted` 1개 → 복수) | **진행 전** | 이벤트 핸들러 분기 축소 목적 |

"이미 다 바꿨다"가 아니라 **패키지 단위 1차 분리까지만 완료**된 상태다. 내부 BC 경계 강화와 모듈 분리는 이 백로그의 범위이며, PoC 시연 이후 재검토 대상이다.

---

## 배경 및 결정 요약

IRS/CRS valuation 서비스·컨트롤러 중복 제거 설계안을 검토하였으나,
시연 전 단계인 현 시점에서 **구조 변경 범위가 과대**하다는 판단 하에 구현을 보류한다.

### 보류 이유
- 현재 우선순위: PoC 시연 완성도
- 설계안의 주요 변경 항목(JPA 상속, 제네릭 repository, abstract controller/service)은
  구조 전반을 건드리므로 시연 안정성에 리스크
- 시연 이후 작은 단위부터 단계적으로 재검토하는 것이 적절

### 유지 결정 사항
- IRS/CRS valuation 대규모 공통화: **보류**
- JPA 상속 전략 (`TABLE_PER_CLASS`) 도입: **보류**
- 제네릭 repository 인터페이스 추출: **보류**
- Abstract controller / service 도입: **보류**

---

## 백로그 항목

우선순위는 시연 후 재검토 시 결정한다.

### BL-01. PricingUtils 추출 가능성 검토

**대상 파일**
- `valuation/domain/irs/IrsPricing.java`
- `valuation/domain/crs/CrsPricing.java`

**중복 내용**
- `calculateDiscountFactor()` — 완전 동일
- `resolvePeriodsPerYear()` — 완전 동일
- `validatePositive()`, `validateNonNegative()`, `validateRemainingDays()` — 완전 동일

**접근 방향**
- `PricingUtils` (final class, static 메서드) 추출
- `IrsPricing`, `CrsPricing` 내부에서 호출하도록 변경
- 사이드이펙트 없는 유틸 추출이므로 위험도 낮음

---

### BL-02. 서비스 내부 반복 helper 최소 공통화

**대상 파일**
- `valuation/application/IrsValuationService.java`
- `valuation/application/CrsValuationService.java`

**중복 내용**
- `registerContract()` upsert 흐름 (find → update / save)
- `deleteContract()` 계층적 삭제 흐름 (valuation 먼저, contract 다음)
- `findById()` / `findAllContracts()` 조회 패턴

**접근 방향**
- 별도 추상 클래스 도입 없이, 각 서비스 내에서 private helper 메서드로 분리 우선
- 이후 공통화 필요 시 abstract service 검토 (BL-04 연계)

---

### BL-03. Controller 응답·페이지네이션 패턴 정리

**대상 파일**
- `valuation/adapter/web/IrsValuationController.java`
- `valuation/adapter/web/CrsValuationController.java`

**정리 대상**
- 페이지네이션 파라미터 (`page`, `size`) 기본값 및 검증 방식 통일
- 응답 래퍼(`ResponseEntity`) 사용 패턴 통일
- HTTP 상태코드 규칙 문서화 (등록 201, 삭제 204 등)

**접근 방향**
- `@ControllerAdvice` / 공통 응답 포맷 클래스 도입 검토
- AbstractController 도입 없이 컨벤션 문서화로 우선 해결 가능

---

### BL-04. 템플릿 서비스 패턴 도입 검토 (조건부)

**조건**
- 파생상품 유형이 3종 이상으로 늘어날 경우에만 검토
- BL-01 ~ BL-03 완료 후 진행

**접근 방향**
- 설계안 참조: `AbstractSwapValuationService` 템플릿 메서드 패턴
- 제네릭 타입 파라미터 복잡도와 유지보수성 트레이드오프 재평가 필요

---

## 참조

- 원본 설계안: 2026-04-23 코드 검토 세션 (IRS/CRS Valuation 중복 제거 리팩토링 설계안)
- 중복도 분석 요약:

| 계층 | 중복도 |
|---|---|
| Controller | 99% |
| UseCase 인터페이스 | 100% |
| Service 오케스트레이션 | 85% |
| Repository 인터페이스 | 100% |
| Contract 엔티티 공통 메서드 | 75% |
| Valuation 엔티티 | 60% |
| Pricing 유틸 | 30% |
| **전체 구조적 중복** | **~72%** |
