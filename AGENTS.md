# 헤지회계 자동화 시스템 프로토타입

## 프로젝트 개요

### 목표
1조원 이상 유가증권 운용사를 위한
금융자산 위험회피회계(헤지회계) 자동화 시스템 프로토타입 개발.

### 범위
- ✅ 공정가치 위험회피 회계처리
- ✅ 현금흐름 위험회피 회계처리
- ✅ 유효성 테스트 자동화
- ✅ 회계 분개 자동 생성
- ❌ 해외사업장순투자 (제외)

### 현재 단계
PoC (Proof of Concept) - 수주용 시연

---

## 기술 스택

### Backend
- Java 25
- Spring Boot 4.0.5
- Gradle
- PostgreSQL 16

### Frontend
- (추후 추가 - 에이전트 파이프라인에서 결정)

### Infrastructure
- Docker Compose (PostgreSQL)

### 외부 시스템
- RAG 시스템: http://localhost:8080 (별도 실행)

---

## RAG 시스템 — 에이전트 전용 지식 공급

> RAG 시스템은 에이전트가 회계 판단을 내리기 전 K-IFRS 원문 근거를 확보하기 위한
> **내부 지식 조회 도구**입니다. 사용자가 직접 호출하는 제품 기능이 아닙니다.

### 적재된 지식 (2026-04-23 기준)
- K-IFRS 1109호 (금융상품 — 결론도출근거 BCZ 포함)
- K-IFRS 1113호 (공정가치 측정)
- K-IFRS 1107호 (금융상품 공시)
- K-IFRS 1039호 관련 내용 (1109호 결론도출근거에서 추출)
- 미래에셋증권 사업보고서 (헤지회계 발췌본)

### 에이전트가 사용하는 검색 경로

#### 1순위: 에이전트 컨텍스트 API (자연어 쿼리)
```bash
curl -X POST http://localhost:8080/api/agent/context \
  -H "Content-Type: application/json" \
  -d '{
    "query": "<검색어>",
    "topK": 5,
    "formatType": "markdown"
  }'
```

#### 2순위: 하이브리드 검색 (조항 번호 등 정확 매칭)
```bash
curl -X POST http://localhost:8080/api/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{
    "query": "6.5.11",
    "topK": 5,
    "keywordWeight": 0.5,
    "vectorWeight": 0.5
  }'
```

#### 3순위: 일반 벡터 검색 (폭넓은 의미 검색)
```bash
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "<검색어>",
    "topK": 5,
    "threshold": 0.5
  }'
```

> 검색이 실패하거나 결과가 빈 경우, 에이전트는 `doc/knowledge/` 폴더의
> 사전 정리된 지식 파일을 보조 참조 자료로 사용합니다.

### 에이전트별 주요 검색 키워드

| 에이전트 | 주제 | 검색어 예시 |
|---|---|---|
| accounting-expert | 헤지 지정 | "헤지 지정 요건 K-IFRS 6.4.1" |
| accounting-expert | 공정가치 헤지 | "공정가치 위험회피 회계처리 6.5.8" |
| accounting-expert | 현금흐름 헤지 | "현금흐름 위험회피 OCI 6.5.11" |
| accounting-expert | 유효성 테스트 | "유효성 테스트 Dollar-offset BC6.234" |
| accounting-expert | 헤지 중단 | "헤지 중단 6.5.6 자발적 취소" |
| accounting-expert | 공정가치 측정 | "공정가치 측정 Level 1 Level 2 Level 3" |
| accounting-expert | 통화선도 | "통화선도 이자율 평형 IRP" |
| accounting-expert | 회계 분개 | "위험회피 회계처리 분개 OCI 재분류" |
| accounting-expert | 실무 사례 | "미래에셋증권 통화선도 헤지" |
| accounting-expert | 공시 | "K-IFRS 1107 헤지회계 공시" |

---

## 멀티 에이전트 파이프라인

### 구조
1. RAG (지식 공급)
↓
2. accounting-expert (회계사 도메인)
↓
3. backend-developer (백엔드)
↓
4. backend-validator (백엔드 검증)
↓
5. frontend-developer (프론트)
↓
6. frontend-validator (프론트 검증)
↓
7. documentation-writer (문서화)
↓
8. final-validator (최종 검증)
↓
9. (문제 시 해당 에이전트로 루프백)

### 에이전트 위치
- `.claude/agents/` 폴더에 각 에이전트 정의

### 워크플로우
- `workflows/feature-pipeline.md` (기능 개발 순서)
- `workflows/feedback-loop.md` (피드백 루프)

### 에이전트 호출
"회계사 에이전트로서 [기능명] 요구사항 정의해줘"
"백엔드 에이전트로서 [기능명] 구현해줘"
"백엔드 검증 에이전트로서 [기능명] 검증해줘"

---

## 개발 원칙

### 1. RAG 우선 원칙
- 모든 회계 로직 구현 전 RAG 검색 필수
- K-IFRS 조항 근거 확인
- 실무 사례 (미래에셋) 참고

### 2. 근거 명시 원칙
- 코드 주석에 K-IFRS 조항 표시
- Javadoc에 근거 문서화
- 커밋 메시지에도 조항 포함

예시:
```java
/**
 * 공정가치 헤지 평가손익 계산
 * 
 * @see K-IFRS 1109호 6.5.8 (공정가치헤지 회계처리)
 * @see K-IFRS 1109호 6.5.10 (공정가치헤지 중단)
 */
public BigDecimal calculateFairValueChange(...) {
    // ...
}
```

### 3. 금융권 코딩 표준

#### 숫자 처리
- 금액/환율/금리: **BigDecimal 필수**
- double/float 금지 (부동소수점 오차)

```java
// ❌ 금지
double amount = 1000000.0;

// ✅ 올바른 방식
BigDecimal amount = new BigDecimal("1000000");
```

#### Null 안전성
- Optional 활용
- @NotNull, @Nullable 명시
- null 체크 철저

#### 예외 처리
- 비즈니스 예외 vs 시스템 예외 분리
- 명확한 에러 메시지
- 스택트레이스 외부 노출 금지

#### 감사 추적
- 모든 엔티티에 CreatedAt, UpdatedAt
- @EntityListeners 활용
- 변경 이력 기록

### 4. 보안 원칙
- 민감 정보 로깅 금지
- SQL Injection 방지 (JPA/Prepared Statement)
- 입력 검증 (@Valid)
- 인증/인가 철저

---

## 프로젝트 구조

```
hedge-prototype/
├── .Codex/
│   ├── AGENTS.md
│   ├── commands/                # 커스텀 슬래시 명령어
│   │   ├── check.md
│   │   ├── review.md
│   │   └── test-domain.md
│   └── skills/                  # 에이전트 스킬 정의
│
├── backend/                     # Spring Boot
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradlew / gradlew.bat
│   ├── gradle/wrapper/
│   └── src/
│       ├── main/java/com/hedge/prototype/
│       │   ├── HedgePrototypeApplication.java
│       │   ├── config/              # (에이전트가 생성)
│       │   ├── domain/              # 엔티티
│       │   ├── repository/
│       │   ├── service/
│       │   ├── controller/
│       │   └── dto/
│       └── main/resources/
│           └── application.yml
│
├── doc/                         # 프로젝트 문서
│   ├── PROJECT_BRIEF.md
│   ├── REQUIREMENTS.md
│   └── DEMO_SCENARIO.md
│
├── workflows/                   # 에이전트 워크플로우
│   ├── feature-pipeline.md
│   └── feedback-loop.md
│
├── docker-compose.yml           # PostgreSQL
├── README.md
└── skills-lock.json
```

---
## 실행 방법

### 1. RAG 시스템 실행 (사전 조건)
별도 프로젝트에서:
```bash
cd ../hedge-rag  # RAG 프로젝트 경로
docker-compose up -d
./gradlew bootRun
```

### 2. 프로토타입 실행
```bash
# PostgreSQL 시작
docker-compose up -d

# 백엔드 실행
cd backend
./gradlew bootRun
```

### 3. 포트
- RAG 시스템: 8080
- 프로토타입 백엔드: 8090
- PostgreSQL: 5432

---
## 작업 체크리스트

### 새 기능 개발 시
1. [ ] 회계사 에이전트로 요구사항 정의 (RAG 검색)
2. [ ] 백엔드 에이전트로 구현
3. [ ] 백엔드 검증 에이전트로 검증
4. [ ] 프론트 에이전트로 UI 구현
5. [ ] 프론트 검증 에이전트로 검증
6. [ ] 문서화 에이전트로 문서 작성
7. [ ] 최종 검증 에이전트로 최종 확인

### 검증 실패 시
- 해당 원인 에이전트로 루프백
- 최대 3회 루프
- 초과 시 사람(개발자) 개입

---

## 참조 문서
- [프로젝트 개요](../doc/PROJECT_BRIEF.md)
- [요구사항 명세서](../doc/REQUIREMENTS.md)
- [데모 시나리오](../doc/DEMO_SCENARIO.md)
- [기능 개발 파이프라인](../workflows/feature-pipeline.md)
- [피드백 루프 가이드](../workflows/feedback-loop.md)

---

## 주의사항

### ⚠️ 금지 사항
- RAG 검색 없이 회계 로직 작성 금지
- double/float로 금액 처리 금지
- 민감 정보 로깅 금지
- 하드코딩된 비밀키/비밀번호
- System.out.println (SLF4J 사용)

### ✅ 필수 사항
- K-IFRS 조항 주석 명시
- BigDecimal 사용
- Javadoc 작성
- 단위 테스트 작성
- 감사 추적 로그

---

## 도움말

### 에이전트가 무엇을 해야 할지 모를 때
1. 해당 에이전트의 `.Codex/agents/[name].md` 파일 재확인
2. RAG 검색으로 지식 보강
3. workflows/ 문서 참조
4. 사람(개발자)에게 질문

### 피드백 루프에 갇혔을 때
- 3회 이상 같은 문제 반복 시 사람 개입
- logs/feedback-loops/에 기록
- 근본 원인 파악 후 진행