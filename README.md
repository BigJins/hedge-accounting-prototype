# Hedge Accounting Automation Prototype

K-IFRS 1109/1113 기반 금융자산 위험회피회계 자동화 웹 프로토타입입니다.

유가증권 운용사의 통화선도와 IRS 위험회피회계 업무를 대상으로 공정가치 평가, 헤지 지정, 유효성 테스트, 자동 분개 생성을 하나의 흐름으로 연결했습니다.

## Links

- Live Demo: https://hedge-accounting-prototype.vercel.app
- Backend Health: https://hedge-backend-l1vx.onrender.com/api/health
- GitHub: https://github.com/BigJins/hedge-accounting-prototype

## Purpose

이 프로젝트는 취업 포트폴리오 제출을 목적으로 만든 PoC입니다.
운영 제품화보다 금융 도메인 이해, 회계 기준 반영, 구현력, 검증 루프, AI 활용 방식을 보여주는 데 초점을 맞췄습니다.

## Implemented Scope

| Area | Status |
|---|---|
| FX Forward FVH/CFH | Implemented |
| IRS FVH | Implemented |
| Dollar-offset effectiveness test | Implemented |
| Automated journal entries | Implemented |
| IRS FVH amortization journal | Implemented |
| CRS | Requirements and gap analysis only |
| Net investment hedge | Out of scope |

## Tech Stack and Rationale

| Technology | Why |
|---|---|
| React + Vite | Fast iteration for form-heavy workflow screens |
| TypeScript | Type-safety for hedge types, instrument types, journal types, and API DTOs |
| Spring Boot + Java | Familiar enterprise backend stack with validation, transaction, and JPA support |
| PostgreSQL | Relational consistency for contracts, valuations, hedge relationships, tests, and journals |
| Vercel | Fast static frontend deployment |
| Render Starter | Simple Spring Boot + PostgreSQL deployment without free-tier sleep delay |
| RAG + AI Agents | K-IFRS based requirement analysis, implementation, validation, and documentation loop |

### Why Render/Vercel instead of AWS?

AWS EC2/RDS is suitable for production operations, but it requires more setup around VPC, security groups, database networking, and instance management.
For this portfolio PoC, the priority was a stable public demo link with minimal operational overhead, so Vercel and Render were selected.

## Core Flow

```text
Fair value valuation
  -> Hedge designation
  -> Effectiveness test
  -> Automated journal entries
```

## Main Features

- FX Forward valuation using Level 2 observable inputs
- Hedge designation validation under K-IFRS 1109 6.4.1
- Dollar-offset effectiveness test
- FVH and CFH journal generation
- OCI / P&L / BS account tagging
- IRS FVH journal and amortization entry
- Validation matrix and submission PDF documents

## Run Locally

### Backend

```powershell
cd backend
.\gradlew.bat bootRun
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

## Verification

```powershell
cd backend
.\gradlew.bat test --tests "com.hedge.prototype.journal.*" --tests "com.hedge.prototype.effectiveness.*" --no-daemon
```

```powershell
cd frontend
npm run build
```

## Submission Documents

- `doc/submission_final/01_기획서_최종_금융자산_위험회피회계_자동화.pdf`
- `doc/submission_final/02_개발문서_최종_위험회피회계_자동화_프로토타입.pdf`
- `doc/submission_final/03_검증부록_최종_위험회피회계_자동화.pdf`

## Accounting Notes

- 80~125% is treated as a reference range, not as an automatic pass/fail bright-line.
- CFH journals separate effective OCI and ineffective P/L amounts.
- FVH journals recognize both hedging instrument gain/loss and hedged item adjustment through P/L.
- CRS is intentionally left as a documented extension because currency and interest-rate risk separation needs additional accounting review.
