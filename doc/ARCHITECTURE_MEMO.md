# 아키텍처 설명 메모

**작성일**: 2026-04-23
**버전**: v1.0
**대상**: 면접·평가·고객 기술 설명용

---

## 현재 구조: 헥사고날 아키텍처 (Ports & Adapters)

### 개념도

```
[HTTP 요청]
    |
[adapter/web/]         Inbound Adapter  — REST 컨트롤러
    |
[application/]         UseCase 포트 + Service  — 비즈니스 오케스트레이션
    |
[domain/]              엔티티, 값객체, 도메인 정책  — K-IFRS 계산 핵심
    |
[adapter/persistence/] Outbound Adapter — JPA Repository
    |
[PostgreSQL]
```

### 실제 패키지 구조 (현재)

```
com.hedge.prototype/
├── hedge/
│   ├── adapter/web/          HedgeDesignationController
│   ├── application/          HedgeCommandUseCase (포트), HedgeDesignationService
│   └── domain/               HedgeRelationship, HedgedItem, EligibilityCheckResult
│
├── valuation/
│   ├── adapter/web/          FxForwardValuationController, IrsValuationController, CrsValuationController
│   ├── application/          FxForwardValuationUseCase (포트), FxForwardValuationService
│   └── domain/               FxForwardPricing, IrsPricing, CrsPricing, DayCountConvention
│
├── effectiveness/
│   ├── adapter/web/          EffectivenessTestController
│   ├── application/          EffectivenessTestUseCase (포트), EffectivenessTestService
│   └── domain/               DollarOffsetCalculator, LowerOfTestCalculator
│
├── journal/
│   ├── adapter/web/          JournalEntryController
│   ├── application/          JournalEntryUseCase (포트), JournalEntryService
│   └── domain/               FairValueHedgeJournalGenerator, CashFlowHedgeJournalGenerator
│
└── common/                   BaseAuditEntity, BusinessException, GlobalExceptionHandler
```

현재는 단일 모듈, 패키지로 계층 분리한 상태다.
hedge / valuation / effectiveness / journal 4개 Bounded Context가 같은 모듈 안에 공존한다.

---

## 왜 헥사고날인가

### 핵심 이유: 도메인이 인프라를 모른다

K-IFRS 계산 로직(공정가치 평가, Dollar-offset, Lower of Test)은
HTTP, JPA, PostgreSQL을 전혀 알지 못한다.
계산 규칙이 DB 변경이나 REST 응답 포맷에 영향을 받으면 안 된다.

헥사고날은 이 원칙을 구조로 강제한다:
- `domain/` 패키지: 외부 의존성 zero. K-IFRS 계산만 담당.
- `application/` 패키지: UseCase 포트 인터페이스 정의. 서비스가 포트에만 의존.
- `adapter/web/`, `adapter/persistence/`: 외부 세계와의 연결. 쉽게 교체 가능.

### 단위 테스트 가능성

`DollarOffsetCalculator`, `LowerOfTestCalculator`, `FxForwardPricing` 같은
K-IFRS 핵심 계산 클래스는 Spring Context 없이 단독 테스트 가능하다.
유효성 공식 하나를 바꿀 때 DB를 띄울 필요가 없다.

### 향후 확장 용이성

현재 REST API 기반의 인바운드 어댑터를
나중에 배치 처리, 메시지 큐(Kafka), gRPC로 바꿔도
`application/` 이하 UseCase와 `domain/`은 그대로 재사용한다.

---

## 왜 현재 패키지 구조인가 (도메인 기준 분리)

### PoC 단계의 선택

계층(adapter/application/domain) 우선 분리는 PoC에서 빠른 개발을 가능하게 한다.
4개 Bounded Context가 같은 모듈 안에 있으므로
서로 참조할 때 인터페이스 계약이 느슨해도 된다.

### 현재 구조의 단점

- `HedgeDesignationService.java`가 헤지 지정·중단·Rebalancing·OCI 재분류를 모두 담당.
  단일 서비스가 너무 많은 책임을 가지고 있다.
- hedge 도메인이 effectiveness 도메인의 이벤트를 직접 처리한다.
  `EffectivenessTestCompletedEventHandler`가 hedge와 journal 양쪽에 걸쳐 있다.

---

## 왜 다음 단계에서 도메인 기준 재배치가 필요한가

### Bounded Context별 완전 분리

현재: `hedge/`, `valuation/`, `effectiveness/`, `journal/`이 한 모듈 안에서 느슨하게 연결.

다음 단계 목표:
```
modules/
├── hedge-core/      헤지 지정·중단·Rebalancing
├── valuation/       FX Forward·IRS·CRS 공정가치 평가
├── effectiveness/   유효성 테스트·Dollar-offset·Lower of Test
└── journal/         분개 생성·OCI 재분류·Excel/PDF 수출
```

각 모듈이 자신의 API 경계(포트 인터페이스)만 노출하고,
다른 모듈의 내부 구현을 알지 못하는 구조로 전환한다.

### 이점

- 모듈별 독립 배포 가능 → 추후 마이크로서비스 전환 기반
- HedgeDesignationService의 책임 분산
- K-IFRS 조항별 변경이 해당 모듈에만 영향
- 팀 확장 시 모듈 단위로 담당자 분리 가능

---

## 주요 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| 금액 타입 | BigDecimal | 부동소수점 오차 방지. 금융권 필수 |
| OCI Reserve 저장 | EffectivenessTest 엔티티 내 필드 | 유효성 테스트 이력과 OCI 잔액을 한 레코드로 추적 |
| 유효성 판정 구조 | PASS/WARNING/FAIL 3단계 | BC6.234: 80~125%는 단독 탈락 기준 아님 |
| Append-Only | 모든 Repository .save() 신규 레코드 | K-IFRS 1107호 이력 보존 의무 |
| 헤지 중단 | enum 사유 코드 + VOLUNTARY_DISCONTINUATION 차단 | K-IFRS 6.5.6: 자발적 취소 불가 원칙 |
| 이벤트 기반 Rebalancing | EffectivenessTestCompletedEvent 발행 | 유효성 테스트 → Rebalancing 자동 연결 |

---

## 1분 설명 스크립트

면접관 앞에서 말할 수 있는 자연스러운 한국어 문장.

---

"이 시스템은 헥사고날 아키텍처로 설계했습니다.

가장 중요한 이유는 하나입니다.
K-IFRS 계산 로직이 데이터베이스나 HTTP에 의존하면 안 된다는 겁니다.

공정가치 평가나 Dollar-offset 유효성 계산은
K-IFRS 기준서가 바뀔 때마다 수정해야 하는 핵심 로직입니다.
이 로직이 JPA나 REST 응답 포맷과 뒤섞이면
수정할 때마다 전체를 건드려야 하고, 단위 테스트도 어렵습니다.

그래서 domain 패키지에는 외부 의존성을 완전히 배제했습니다.
DollarOffsetCalculator나 LowerOfTestCalculator는
Spring 없이 JUnit으로 단독 테스트가 됩니다.
현재 102개 이상의 테스트가 이 계층에 집중되어 있습니다.

현재는 단일 모듈에 hedge, valuation, effectiveness, journal
4개 Bounded Context를 패키지로 분리한 상태입니다.
PoC라서 빠른 개발을 우선했습니다.

다음 단계에서는 모듈을 완전히 분리할 계획입니다.
HedgeDesignationService가 지금 너무 많은 책임을 가지고 있고,
각 Context가 독립적으로 배포될 수 있어야
추후 마이크로서비스 전환이나 팀 확장 시 유연하게 대응할 수 있습니다."

---

## 시연 중 아키텍처 질문 대응

### Q: Spring Boot를 쓰는데 헥사고날이라고 할 수 있나요?
**A**: "헥사고날은 프레임워크가 아닌 구조적 원칙입니다.
Spring Boot를 쓰더라도 Controller를 Inbound Adapter로,
JPA Repository를 Outbound Adapter로 다루면서
domain 패키지에 프레임워크 어노테이션이 없으면 헥사고날 원칙을 따르는 겁니다.
현재 domain 패키지의 계산 클래스들은 순수 Java입니다."

### Q: 단일 모듈인데 헥사고날의 이점이 있나요?
**A**: "패키지 경계만으로도 핵심 이점 두 가지를 얻습니다.
첫째, 도메인 로직의 단독 테스트 가능성.
둘째, 인프라 교체 시 adapter 계층만 수정.
모듈 분리는 PoC 이후 단계의 과제입니다."

### Q: 이벤트 기반 처리는 어디에 사용했나요?
**A**: "유효성 테스트 완료 후 Rebalancing 처리에 사용했습니다.
`EffectivenessTestCompletedEvent`를 발행하면
`EffectivenessTestCompletedEventHandler`가 PASS이면 분개 생성,
WARNING+REBALANCE이면 HedgeRebalancingService로 위임합니다.
유효성 테스트 서비스가 Rebalancing 로직을 직접 알 필요가 없습니다."
