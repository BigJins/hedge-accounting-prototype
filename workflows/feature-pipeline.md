# 기능 개발 파이프라인 (Feature Development Pipeline)

## 현재 상태 (2026-04-23)

| 단계 | 상태 | 비고 |
|---|---|---|
| 백엔드 (헤지지정·평가·유효성·분개) | 완성 | 97개 테스트 PASS |
| 백엔드 검증 | 대기 | 오늘 수정분 미검증 |
| 프론트엔드 | 미시작 | P2 단계 |
| 문서화·최종검증 | 미시작 | P4~P5 단계 |

**시연 목표**: 2026-04-30

---

## 파이프라인 흐름

```
회계사 에이전트 → 백엔드 에이전트 → 백엔드 검증 → 프론트 에이전트 → 프론트 검증 → 문서화 → 최종 검증
```

FAIL 시 해당 원인 에이전트로 루프백. 최대 3회 초과 시 사람 개입.

---

## 시연 우선순위 기준 파이프라인

### P1 — 지금 당장 (백엔드 완성 기준)

> 백엔드는 구현 완료. 검증 에이전트 호출만 남은 상태.

```
"백엔드 검증 에이전트로서 오늘(2026-04-23) 수정된 6개 이슈 검증해줘"
```

검증 대상:
- Dollar-offset 참고 등급 분리 (BC6.234)
- 헤지비율 WARNING/FAIL 분리 (B6.4.9)
- 헤지 중단 사유 enum (6.5.6)
- Rebalancing 로직 (6.5.5)
- OCI 누적 계산 (6.5.11)
- EffectivenessTestResult.WARNING 추가

---

### P2 — 프론트엔드 (백엔드 검증 PASS 후)

```
"프론트 에이전트로서 DEMO_SCENARIO.md 기반 6개 화면 구현해줘"
```

시연 핵심 화면 (우선순위 순):
1. **CFH 전체 흐름** — 지정 → Dollar-offset → OCI → 재분류
2. **FVH 분개** — IRP 평가 → P&L 분개 자동 생성
3. **헤지 중단** — 허용 사유 선택 → 자발적 차단 시연
4. 대시보드 (포지션 요약)
5. Excel/PDF 수출

---

### P3 — 프론트 검증

```
"프론트 검증 에이전트로서 구현된 UI 검증해줘"
```

---

### P4 — 문서화

```
"문서화 에이전트로서 수주용 기획서와 API 명세서 작성해줘"
```

---

### P5 — 최종 검증

```
"최종 검증 에이전트로서 전체 파이프라인 종합 검증해줘"
```

---

## 에이전트 역할 원칙

| 에이전트 | 하는 것 | 하지 않는 것 |
|---|---|---|
| accounting-expert | RAG 검색, K-IFRS 해석, 요구사항 정의 | 코드 작성, UI 결정 |
| backend-developer | 엔티티·서비스·API·테스트 구현 | 회계 판단, UI 작업 |
| backend-validator | PASS/FAIL 판정, 루프백 결정 | 직접 코드 수정 |
| frontend-developer | 화면 구현, API 연동 | 회계 판단, 백엔드 수정 |
| frontend-validator | UI/UX 검증, 연동 정확성 | 직접 코드 수정 |
| documentation-writer | 기획서, 매뉴얼, 감사 자료 | 코드 작성 |
| final-validator | 종합 APPROVED/REJECTED 판정 | 직접 수정 |

---

## 완료 체크리스트

### 시연 전 필수
- [ ] 백엔드 검증 PASS
- [ ] 프론트 CFH 흐름 화면 동작
- [ ] K-IFRS 근거 표시 확인
- [ ] Excel 수출 정상 동작
- [ ] 헤지 중단 차단 시연 동작

### 문서 확인
- [x] `doc/ACCOUNTING_VALIDATION_MATRIX.md` 완성
- [x] `doc/api/hedge-designation.md` 완성
- [x] `doc/api/valuation.md` 완성
- [ ] 수주용 기획서 (P4에서 작성 예정)

---

## 참조
- [피드백 루프 가이드](feedback-loop.md)
- [회계 검증표](../doc/ACCOUNTING_VALIDATION_MATRIX.md)
- [데모 시나리오](../doc/DEMO_SCENARIO.md)
- [요구사항 명세서](../doc/REQUIREMENTS.md)
