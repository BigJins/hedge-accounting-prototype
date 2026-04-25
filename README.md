# Hedge Accounting Automation Prototype

K-IFRS 1109/1113 기반 금융자산 위험회피회계 자동화 웹 프로토타입입니다.
유가증권 운용사가 수행하는 공정가치 위험회피, 현금흐름 위험회피, 유효성 테스트,
회계 분개 생성을 하나의 흐름으로 검증할 수 있도록 구현했습니다.

## 프로젝트 목적

이 프로젝트는 채용 포트폴리오 제출을 목적으로 만든 PoC입니다.
운영 제품이 아니라, 금융 도메인 이해와 구현력, AI 기반 요구사항 분석 및 검증 루프를 보여주는 데 초점을 맞췄습니다.

핵심 범위는 다음과 같습니다.

- FX Forward 기반 공정가치 위험회피(FVH) 및 현금흐름 위험회피(CFH)
- IRS 기반 고정금리채권 공정가치 위험회피(FVH)
- Dollar-offset 유효성 테스트 자동화
- K-IFRS 조항 근거가 포함된 자동 분개 생성
- 검증 결과와 회계 판단 근거 문서화

## 주요 기능

| 영역 | 구현 내용 |
|---|---|
| 공정가치 평가 | FX Forward IRP 평가, IRS DCF 평가 |
| 헤지 지정 | 헤지 유형, 위험 유형, 피헤지항목, 헤지수단 지정 |
| 유효성 테스트 | Periodic/Cumulative Dollar-offset, PASS/WARNING/FAIL 판정 |
| 자동 분개 | FVH, CFH, OCI, IRS FVH 장부조정상각 분개 생성 |
| 감사 근거 | K-IFRS 1109/1113 조항과 분개 근거 표시 |
| 문서화 | 요구사항, 검증 매트릭스, 데모 시나리오, 리팩토링 백로그 |

## 기술 스택

### Backend

- Java 25
- Spring Boot 4
- Gradle
- PostgreSQL 16
- JPA / Bean Validation

### Frontend

- React 19
- TypeScript
- Vite
- TanStack Query
- React Hook Form / Zod
- Tailwind CSS

## 프로젝트 구조

```text
.
├── backend/       # Spring Boot API 서버
├── frontend/      # React/Vite 웹 클라이언트
├── doc/           # 요구사항, 검증, 데모 문서
├── workflows/     # AI 에이전트 기반 개발 워크플로우
├── .claude/       # 역할별 에이전트 정의 일부
└── docker-compose.yml
```

## 실행 방법

### 1. PostgreSQL 실행

```bash
docker compose up -d
```

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

기본 포트:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8090`
- PostgreSQL: `localhost:5432`

## 검증 명령

```bash
cd backend
./gradlew test --tests "com.hedge.prototype.journal.*" --tests "com.hedge.prototype.effectiveness.*"
```

```bash
cd frontend
npm run build
```

## 구현 범위와 한계

| 구분 | 상태 |
|---|---|
| FX Forward FVH/CFH | 구현 및 시연 가능 |
| IRS FVH | 백엔드/프론트 연동 및 분개 타입 구현 |
| CRS | 요구사항 및 갭 분석 중심, 후속 확장 범위 |
| Level 1/3 평가 | 본 PoC 범위 제외 |
| 운영 보안/권한 | 포트폴리오 PoC 범위 밖 |

## 핵심 문서

- `doc/PROJECT_BRIEF.md`
- `doc/REQUIREMENTS.md`
- `doc/DEMO_SCENARIO.md`
- `doc/ACCOUNTING_VALIDATION_MATRIX.md`
- `doc/requirements/IRS_HEDGE_REQUIREMENTS.md`
- `doc/requirements/SWAP_HEDGE_STAGE2_REQUIREMENTS.md`

## 포트폴리오 관점의 강조점

이 프로젝트는 단순 CRUD가 아니라 회계 기준, 금융상품 평가, 예외 검증, 자동 분개까지 이어지는 도메인 흐름을 구현한 사례입니다.
또한 회계사 역할, 백엔드 개발자 역할, 검증자 역할, 문서화 역할을 나눈 AI 에이전트 파이프라인으로 요구사항 정의부터 검증까지 반복 개선했습니다.
