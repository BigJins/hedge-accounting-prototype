# 회계원칙 검증표 (Accounting Validation Matrix)

## 목적

이 문서는 헤지회계 자동화 프로토타입의 핵심 기능이 K-IFRS 기준서와 어떻게 연결되는지,
그리고 현재 구현 상태가 어디까지 검증되었는지를 한 눈에 확인하기 위한 시연용 검증표다.

이 문서는 다음 목적에 사용한다.

- 시연 전 내부 품질 점검
- 회계 담당자 리뷰 체크리스트
- 개발 구현과 회계 근거의 연결 문서
- 데모/시연 시 "왜 이 결과가 맞는가" 설명 자료

**최종 업데이트**: 2026-04-24 (프론트엔드 5개 화면 구현 완료 반영; AVM-007 구현 규칙 아카이브)

---

## 상태 정의

검증 상태는 서로 겹치지 않도록 네 가지로 정의한다. 회계 적합성과 시연 분류는 별도 축이다.

| 상태 | 의미 | 판정 기준 |
|---|---|---|
| **적합** | 시연 범위 안에서 K-IFRS 기준과 구현/설명이 일치한다고 판단 | 기준서 조항 확인 + 코드 위치 확인 + 테스트 통과 |
| **검토중** | 기준서와 대조 중이거나 일부만 확인됨 | 리뷰 진행 중이라 근거·코드 중 일부 미확정 |
| **시연 제외** | 구현은 되어 있으나 본 시연 시나리오에서는 실행하지 않음 | 질문 받을 때 설명만 하고, API 호출은 하지 않음 |
| 이슈있음 | 구현 또는 설명에 수정이 필요함 | 현재 검증표 기준 해당 항목 없음 (과거 AVM-007/014/015는 해소 완료) |

> **시연 필수 / 시연 보조 / 시연 제외**는 README "기능 상태 매트릭스"의 시연 분류와 1:1 대응한다.
> "적합"이라도 "시연 제외" 분류로 묶일 수 있다 — 판단 축이 다르기 때문이다.

### 상태 요약 (2026-04-24 기준)

| 상태 | ID 개수 | 해당 ID |
|---|---|---|
| 적합 | 14 | AVM-001~005, 007~011, 013~017 |
| 검토중 | 2 | AVM-006 (사전/사후 구분 — 시연 보조), AVM-012 (독립 예상거래 취소 — 시연 제외) |
| 시연 제외 | 2 | AVM-012 (독립 경로 미구현, 수동 API 대응), 일부 IRS/CRS Day Count 세부(검토 항목 4) |
| 이슈있음 | 0 | — |

IRS/CRS 공정가치(AVM-004)는 구현 상태는 "적합"이지만 README 매트릭스 기준 시연 분류는 "시연 제외"다. 이 검증표는 회계 적합성 관점이므로 시연 분류를 보려면 README를 병행 참조한다.

---

## 검증표

| ID | 업무 영역 | 기능/판정 포인트 | 적용 기준서 | 회계 핵심 논점 | 입력 예시 | 기대 결과 | 구현 코드 위치 | 검증 상태 | 리스크/쟁점 | 최종 확인일 |
|---|---|---|---|---|---|---|---|---|---|---|
| AVM-001 | 헤지지정 | 적격요건 3가지 자동 검증 | K-IFRS 1109호 6.4.1(3)(가)(나)(다) | 조건1 경제적 관계(명목금액 50~200%, 만기, 기초변수), 조건2 신용위험 지배 아님(BBB 이상), 조건3 헤지비율 적정성(위험관리 목적 부합) | USD 외화예금 1,000만 / FX Forward 매도 1,000만 / BBB등급 | 3조건 PASS → ELIGIBLE, 지정일·목적·전략 문서화 자동 생성 | `HedgeRelationship.java:481`(validateEligibility), `checkEconomicRelationship():531`, `checkCreditRiskNotDominant():601`, `checkHedgeRatio():659` | **적합** | 조건3 헤지비율: 80~125% 이탈은 WARNING(재조정 신호), 자동 FAIL 아님. BC6.234 준수 확인. 극단값(10% 미만, 300% 초과)만 FAIL 처리 | 2026-04-23 |
| AVM-002 | 헤지지정 | 헤지지정 문서화 자동 생성 | K-IFRS 1109호 6.4.1(2) | 위험관리 목적, 위험회피전략, 수단·대상 식별, 위험 유형 지정 시점 문서화 의무 | riskManagementObjective, hedgeStrategy 필드 필수 입력 | DocumentationSummary 자동 반환 (API 응답에 포함) | `HedgeRelationship.java:202`(riskManagementObjective), `dto/DocumentationSummary.java` | **적합** | 문서화 내용이 자유 텍스트이므로 실무 적용 시 표준 양식 필요. PoC 범위에서는 필드 존재 자체로 충족 | 2026-04-23 |
| AVM-003 | 공정가치평가 | FX Forward IRP 기반 공정가치 | K-IFRS 1113호 72~90항(Level 2), 1109호 6.5.8 | 이자율 평형 이론(IRP): F = S × (1+r_KRW×T/365) / (1+r_USD×T/360), KRW Act/365 Fixed, USD Act/360 | Spot=1,300, r_KRW=3.5%, r_USD=5.2%, 만기 90일 | 선물환율 계산, Fair Value 산출, 공정가치 Level 2 분류 | `FxForwardPricing.java`, `FxForwardValuationService.java`, `DayCountConvention.java` | **적합** | 할인율(OIS 기준)이 아닌 단순 IRP 공식 사용. 실무에서는 OIS 할인 적용 가능성 있음. PoC 시연 범위에서는 충분 | 2026-04-23 |
| AVM-004 | 공정가치평가 | IRS·CRS 공정가치 (DCF) | K-IFRS 1113호 72~90항(Level 2) | IRS: 고정다리 PV − 변동다리 PV (OIS 할인). CRS: 원화다리 + 외화다리 × 환율 이중 할인 | IRS: 고정금리 3%, 변동금리 SOFR, 명목 1,000억. CRS: 원화 3.5%/USD 5.0%, Spot 1,300 | 수단별 Fair Value, Level 2 분류, Append-Only 이력 | `IrsPricing.java`, `CrsPricing.java`, `IrsValuationService.java`, `CrsValuationService.java` | **적합** (시연 제외) | 시연 분류: **시연 제외** — 코드·단위 테스트는 통과. 본 시연은 FX Forward 중심. 변동금리 커브(SOFR/KOFR)가 Mock 데이터로 단일 금리 입력을 사용함은 PoC 범위 내 허용. Day Count 세부는 검토 항목 4 참조 | 2026-04-23 |
| AVM-005 | 유효성테스트 | Dollar-offset 참고 등급 산출 | K-IFRS 1109호 B6.4.12, B6.4.13, BC6.234 | BC6.234: 80~125%는 단독 PASS/FAIL 기준 아님. 동방향(비율 양수)만 FAIL. 반대방향+이탈은 WARNING(재조정 신호). | instrumentFvChange=−500만, hedgedItemPvChange=+480만 | ratio = −1.04(−104%), 반대방향 + 참고범위 이내 → PASS. actionRequired=NONE | `DollarOffsetCalculator.java:121`(evaluateReferenceGrade), `EffectivenessTestService.java:100` | **적합** | 2026-04-23 수정: 80~125% 단독 판정 구조 폐지, PASS/WARNING/FAIL 3단계로 전환. FAIL 판정은 동방향(경제적 관계 훼손)에만 적용 | 2026-04-23 |
| AVM-006 | 유효성테스트 | 사전(Prospective)·사후(Retrospective) 구분 | K-IFRS 1109호 B6.4.12 | 기간별(PERIODIC)=당기 변동 기준, 누적(CUMULATIVE)=지정 이후 누적 변동 기준. 두 방법 중 선택 또는 병용 | testType=DOLLAR_OFFSET_PERIODIC 또는 DOLLAR_OFFSET_CUMULATIVE | testType에 따라 referenceInstrument/referenceHedgedItem 자동 선택 | `EffectivenessTestService.java:274`(resolveReferenceValue), `EffectivenessTestType.java` | **적합** | 시연 범위 판정: **시연 보조**. `resolveReferenceValue()`에서 PERIODIC→periodicValue, CUMULATIVE→cumulativeValue 분기 코드 확인 완료. 시연에서는 testType 파라미터를 PERIODIC으로 고정하여 진행. 질문 시 "CUMULATIVE로 바꾸면 누적 기준 비율이 산출된다"는 설명으로 대응 충분 | 2026-04-23 |
| AVM-007 | 현금흐름헤지 | Lower of Test — OCI/P&L 분리 | K-IFRS 1109호 6.5.11⑴⑵⑷㈐, BC6.280 | effectiveAmount = MIN(\|수단누적\|, \|대상누적\|) × sign(수단누적). BC6.280: Lower of Test는 OCI 인식 한도(규모) 제한 장치이며 부호 방향은 헤지수단 손익 방향을 따름. 6.5.11⑷㈐: OCI 적립금이 차손(음수)인 경우 명시적 규정 존재 | 수단누적=−500만(손실), 대상누적=+480만 → effective=−480만(OCI 감소), ineffective=0 | OCI Reserve 감소 −480만, 분개: 차:OCI적립금 / 대:파생상품부채 | `LowerOfTestCalculator.java`(calculateSignedEffectivePortion, calculateSignedIneffectivePortion 신규), `EffectivenessTestService.java:391`(calculateCashFlowIneffectiveness — Signed 메서드 호출로 교체), `CashFlowHedgeJournalGenerator.java:95`(손실 분기 로직 입력값 부호 복원으로 정상 동작) | **적합** | 2026-04-23 수정 완료: `calculateSignedEffectivePortion()` / `calculateSignedIneffectivePortion()` 신규 추가, 손실(−)/이익(+) 양방향 7개 테스트 케이스 PASS. DB 스키마 `oci_reserve_balance` CHECK 제약 없음(음수 허용 확인). 102개 전체 테스트 PASS | 2026-04-23 |
| AVM-008 | 현금흐름헤지 | OCI Reserve 누적 잔액 계산 | K-IFRS 1109호 6.5.11(OCI 누적 관리) | 현금흐름위험회피적립금 = 이전 기간 OCI 잔액 + 당기 유효분. 당기 단순 대입 금지 | 1기: OCI 500만, 2기: 유효 300만 → OCI Reserve = 800만 | ociReserveBalance 필드에 누적값 저장. 최신 레코드 조회 후 누적 계산 | `EffectivenessTestService.java:404`(cumulativeOciBalance), `resolvePreviousOciBalance()` | **적합** | 2026-04-23 수정 완료: 단순 대입→`resolvePreviousOciBalance()` + `previousOciBalance.add(effectiveAmount)` 누적 계산. AVM-007 부호 수정으로 손실 방향 누적도 정합. `oci_reserve_balance` 컬럼 음수 허용 확인 | 2026-04-23 |
| AVM-009 | 분개생성 | 공정가치헤지 분개 자동 생성 | K-IFRS 1109호 6.5.8(가)(나) | 수단 변동 → 차변/대변 P&L(파생상품자산/부채, 평가손익). 대상 변동 → 장부금액조정+P&L(위험회피이익/손실). 차변=대변 균형 | instrumentFvChange=+500만, hedgedItemFvChange=−480만 | 분개 2건: ①파생상품자산↑/평가이익 ②위험회피손실/장부조정↑. 비효과 20만 P&L 반영 | `FairValueHedgeJournalGenerator.java:62`(generate), `AccountCode.java` | **적합** | 분개 후 차변−대변=0 확인 필요. 계정과목명(DRV_ASSET, HEDGE_GAIN_PL 등)이 PoC용이므로 실무 적용 시 고객사 회계계정체계와 매핑 필요 | 2026-04-23 |
| AVM-010 | 분개생성 | 현금흐름헤지 분개 자동 생성 | K-IFRS 1109호 6.5.11(가)(나) | 유효분 → OCI(현금흐름위험회피적립금). 비유효분 → 즉시 P&L(헤지비효과손익). 비유효분 0이면 단건 분개 | effectiveAmount=500만, ineffectiveAmount=50만 | 분개 2건: ①파생상품자산/CFHR_OCI(OCI 적립) ②파생상품자산/헤지비효과이익(P&L) | `CashFlowHedgeJournalGenerator.java:69`(generate), line 116(이익 분기), line 121(손실 분기) | **적합** | 코드 직접 검증 완료(2026-04-23): 손실 케이스 분개 방향 — 유효분 손실: 차:CFHR_OCI/대:DRV_LIAB(line 123-124), 비유효분 손실: 차:INEFF_LOSS_PL/대:DRV_LIAB(line 166-167). AVM-007 Signed 메서드 교체 이후 effectiveAmount 부호 체인 정합, Generator 수정 불필요. 시연 분류: 시연 필수 — API 사전 확인 불필요, 코드 레벨 검증으로 충분 | 2026-04-23 |
| AVM-011 | 분개생성 | OCI 재분류 분개 (예상거래 실현 시) | K-IFRS 1109호 6.5.11(다) | 예상거래 실현 시 OCI 잔액 → 해당 손익 계정으로 재분류. 사유: TRANSACTION_REALIZED | reclassificationAmount=800만, plAccount=FX_GAIN_PL | 차변=CFHR_OCI 800만 / 대변=외환이익 800만. 사유코드 TRANSACTION_REALIZED 기록 | `OciReclassificationJournalGenerator.java:55`(generate), `ReclassificationReason.java` | **적합** | plAccount(재분류 대상 P&L 계정)를 호출 측에서 선택해야 함. 적절한 계정(외환이익 vs 이자수익 등)을 결정하는 로직이 서비스 계층에 없음 — 현재 입력값 의존 | 2026-04-23 |
| AVM-012 | 분개생성 | OCI 재분류 — 예상거래 미발생 | K-IFRS 1109호 6.5.12(나) | 예상거래 발생가능성 희박 시 OCI 잔액 전액 즉시 P&L. 사유: TRANSACTION_NO_LONGER_EXPECTED | OCI잔액=−300만, 예상거래 미발생 판정 | 차변=외환손실 300만 / 대변=CFHR_OCI 300만. 즉시 전액 이전 | `OciReclassificationJournalGenerator.java`(TRANSACTION_NO_LONGER_EXPECTED), `HedgeDesignationService.java`(reclassifyOciToPlOnTransactionNoLongerExpected — 헤지 중단 경로) | **검토중 / 시연 제외** | 헤지 중단+예상거래 미발생 경로는 AVM-014(PATCH /{id}/discontinue, forecastTransactionExpected=false)로 완전 커버됨. 미구현 경로: 헤지 중단 없이 예상거래만 독립 취소(별도 API 미존재 — 백로그). 시연 플로우에서 이 경로를 재현할 필요 없음 — 질문 시 "중단 시나리오는 AVM-014로 시연"으로 대응 | 2026-04-23 |
| AVM-013 | 중단처리 | 헤지 중단 사유 검증 + 자발적 중단 차단 | K-IFRS 1109호 6.5.6 | 자발적 중단(VOLUNTARY_DISCONTINUATION) 시도 → BusinessException HD_012. 허용 사유(4가지)만 통과 | reason=VOLUNTARY_DISCONTINUATION | BusinessException 발생. reason=HEDGE_INSTRUMENT_EXPIRED는 정상 중단 처리 | `HedgeDiscontinuationReason.java:91`(isAllowed), `HedgeRelationship.java:768`(discontinue), `PATCH /{id}/discontinue` API | **적합** | 2026-04-23 신규 구현. 허용 사유: RISK_MANAGEMENT_OBJECTIVE_CHANGED, HEDGE_INSTRUMENT_EXPIRED, HEDGE_ITEM_NO_LONGER_EXISTS, ELIGIBILITY_CRITERIA_NOT_MET | 2026-04-23 |
| AVM-014 | 중단처리 | 현금흐름헤지 중단 후 OCI 후속 처리 | K-IFRS 1109호 6.5.7, 6.5.12, B6.5.26 | 중단 후 OCI 잔액: ①예상거래 여전히 발생 가능한 경우(forecastTransactionExpected=true) → OCI 유보, 분개 미생성 ②발생 불가 확정(forecastTransactionExpected=false) → 즉시 P&L 재분류 분개 자동 생성 | reason=ELIGIBILITY_CRITERIA_NOT_MET, forecastTransactionExpected=false, currentOciBalance=500만, plAccount=FX_GAIN_PL | CFH: forecastExpected=false → CFHR_OCI / FX_GAIN_PL 재분류 분개 생성. FVH: OCI 분기 진입 안 함 | `HedgeDesignationService.java`(processCfhOciAfterDiscontinuation, reclassifyOciToPlOnTransactionNoLongerExpected), `HedgeDiscontinuationRequest.java`(forecastTransactionExpected 필드), `OciReclassificationJournalGenerator.java`(TRANSACTION_NO_LONGER_EXPECTED) | **적합** | 2026-04-23 구현 완료. 11개 신규 테스트 PASS(케이스 A~E). forecastTransactionExpected=null 시 HD_017 BusinessException. currentOciBalance=ZERO 시 분개 미생성+로그. 일부 발생 가능 안분은 시연 범위 외(수동 처리). 전체 113개 테스트 PASS | 2026-04-23 |
| AVM-015 | Rebalancing | 헤지비율 재조정 (의무 이행) | K-IFRS 1109호 6.5.5, B6.5.7~B6.5.21, B6.5.8 | 비율 이탈이나 위험관리 목적 유지 시 재조정 의무. B6.5.8: 재조정 전 비효과성 당기손익 선행 인식 후 재조정 | effectivenessRatio=WARNING, actionRequired=REBALANCE | HedgeRebalancingService 호출 → 비효과성 분개 생성(1회) → 헤지비율 재조정 → 상태 REBALANCED | `HedgeRebalancingService.java`(processRebalancing, recognizePreRebalancingIneffectiveness), `HedgeRelationship.java:806`(rebalance), `EffectivenessTestCompletedEventHandler.java`(delegateToRebalancingService — 분개 생성 없이 위임) | **적합** | 2026-04-23 수정 완료(AVM-015): EventHandler WARNING+REBALANCE 분기에서 직접 분개 생성 제거 → HedgeRebalancingService 단독 책임. 분개 생성 경로: PASS(EventHandler 1회), WARNING+REBALANCE(RebalancingService 1회), FAIL+DISCONTINUE(없음). ineffectiveAmount==0 시 분개 미생성(B6.5.8 적합). FVH·CFH 각 2건 포함 총 10개 테스트 PASS, 백엔드 검증 PASS | 2026-04-23 |
| AVM-016 | 감사추적 | Append-Only 이력 + 변경 불가 | K-IFRS 1107호, 내부통제 | 동일 계약 재평가·재테스트·재분개 시 이전 레코드 덮어쓰기 금지. createdAt DESC 최신 우선 조회 | 동일 hedgeRelationshipId 유효성 테스트 2회 실행 | 2개 레코드 모두 DB 보존. 최신 레코드가 응답 기준 | `BaseAuditEntity.java:23`(createdAt/updatedAt), `FxForwardValuationService.java:174`(Append-Only 주석), 모든 Repository `.save()` | **적합** | 역분개(REVERSING_ENTRY) 패턴 정의됨. 논리 삭제 없음. 시연 시 "이전 이력도 전부 남아 있다"를 직접 보여줄 수 있음 | 2026-04-23 |
| AVM-017 | 감사추적 | Excel·PDF 수출 (분개장 수출) | K-IFRS 1107호 (공시 지원) | 분개장, OCI변동표, 재분류이력 3개 시트. PDF에 K-IFRS 조항 각주 자동 포함 | GET /api/v1/journal-entries/export/excel | .xlsx 3시트 다운로드, 각 분개에 ifrsReference 필드 표시 | `JournalEntryService.java`(exportToExcel, exportToPdf), `PdfExporter.java` | **적합** | 공식 K-IFRS 1107호 공시 양식(정형화된 헤지 공시표)과는 형식이 다름. 시연용 내부 보고 형식으로 포지셔닝. 정식 공시 자동화는 추가 개발 필요 | 2026-04-23 |

---

## 시연 전 필수 확인 항목

### 1. 회계 적합성

- [x] 헤지지정 3요건이 기준서 취지와 맞는가 (AVM-001 적합)
- [x] 80~125%를 기계적 탈락 기준으로 오해하지 않게 구현되었는가 (AVM-005 적합 — BC6.234 준수)
- [x] OCI 적립금 부호 방향성이 손실·이익 방향 모두에서 정확한가 (AVM-007 적합 — 2026-04-23 수정 완료, 7개 테스트 PASS)
- [x] 헤지 중단 후 CFH OCI 후속 처리가 자동으로 트리거되는가 (AVM-014 적합 — 2026-04-23 구현 완료, 11개 테스트 PASS)
- [x] 자발적 중단이 코드 레벨에서 차단되는가 (AVM-013 적합)
- [x] Rebalancing 비효과성 분개가 정확히 1회만 생성되는가 (AVM-015 적합 — 2026-04-23 수정 완료, 10개 테스트 PASS)

### 2. 구현 적합성

- [x] 금액 계산에 `BigDecimal`만 사용하는가 (전 영역 확인)
- [x] Append-Only 원칙이 실제 저장 로직에 반영되는가 (AVM-016 적합)
- [x] Dollar-offset 단독 판정이 제거되었는가 (AVM-005 — 2026-04-23 수정 완료)
- [x] Lower of Test effectiveAmount 부호 처리가 올바른가 (AVM-007 — 2026-04-23 수정 완료)
- [x] OCI 누적 계산이 이전 기간 잔액을 반영하는가 (AVM-008 — 2026-04-23 수정 완료)

### 3. 시연 적합성

- [x] 결과 수치만 아니라 "K-IFRS 근거"를 함께 보여줄 수 있는가 (ifrsReference 필드 전 분개에 포함)
- [x] 한 번의 시연 흐름으로 지정→평가→테스트→분개→추적이 이어지는가 (프론트엔드 5개 화면 구현 완료 — 2026-04-24)
- [x] 대표 케이스(FX Forward CFH)를 중심으로 집중할 수 있는가

---

## 권장 시연 시나리오 매핑

| 시나리오 | 목적 | 검증표 ID | 주의사항 |
|---|---|---|---|
| CFH 지정 → Dollar-offset → OCI 인식 → 예상거래 실현 → 재분류 | 핵심 메인 플로우 | AVM-001, AVM-005, AVM-007, AVM-010, AVM-011 | 손실·이익 양방향 검증 완료, 시연 가능 |
| FVH 지정 → IRP 평가 → 비효과성 → 분개 생성 | 공정가치 헤지 직관성 | AVM-001, AVM-003, AVM-009 | 가장 안정적인 시연 경로 |
| 중단 사유 발생 → 자발적 차단 → 허용 사유 처리 | 기준 준수·통제 강조 | AVM-013, AVM-014 | AVM-014 이슈 — CFH 중단 후 OCI는 수동 설명 필요 |
| Rebalancing → 비효과성 선행 인식 → 비율 재조정 | 1109호 의무 이행 | AVM-005, AVM-015 | 2026-04-23 신규 구현, 실제 API 호출 검증 필요 |
| Append-Only 이력 → Excel 수출 → 감사 추적 | 내부통제·감사 대응 | AVM-016, AVM-017 | 가장 설득력 있는 차별화 포인트 |

---

## 추가 회계 검토 필요 항목

> 시연 전 우선순위 확인 항목. 2026-04-23 기준 주요 이슈 해소 완료.

### 검토 항목 1 — Lower of Test effectiveAmount 부호 방향성 [완료 — 2026-04-23]

**수정 완료**: `calculateSignedEffectivePortion()` / `calculateSignedIneffectivePortion()` 신규 추가.
손실·이익 양방향 7개 테스트 케이스 PASS. OCI 누적 누산 정합. (AVM-007, AVM-008 참조)

K-IFRS 근거:
- 6.5.11⑴: 수단 손실이면 OCI도 손실 방향으로 인식
- 6.5.11⑷㈐: OCI 적립금이 차손(음수)인 경우 명시적 규정
- BC6.280: Lower of Test는 OCI 인식 한도(규모) 제한 장치, 부호 변환 장치 아님

| 수단 누적 | 대상 누적 | OCI 인식 방향 | 올바른 분개 |
|---|---|---|---|
| +500만(이익) | -480만 | +480만 OCI 증가 | 차:파생상품자산 / 대:OCI적립금 |
| -500만(손실) | +480만 | -480만 OCI 감소 | 차:OCI적립금 / 대:파생상품부채 |
| +600만(이익,과대) | -480만 | +480만 OCI 증가 + 120만 P&L이익 | 유효분+비유효분 각각 분개 |
| -600만(손실,과대) | +480만 | -480만 OCI 감소 + 120만 P&L손실 | 유효분+비유효분 각각 분개 |

---

### 검토 항목 2 — 예상거래 미발생 시 즉시 전액 P&L 처리 자동화 [후속 과제]

- **상태**: 헤지 중단 동반 경로(AVM-014, PATCH /{id}/discontinue + forecastTransactionExpected=false)로 커버됨.
- **미구현**: 헤지 중단 없이 예상거래만 독립 취소하는 별도 API 없음. 시연 범위 외, 수동 API 호출로 대응 가능.
- **K-IFRS 근거**: 1109호 6.5.12(나) — 예상거래 발생 불가 시 즉시 전액 P&L
- **본 개발 과제**: `HedgedItem.expectedTransactionLikely` 상태 필드 추가 후 자동 이벤트 발행

---

### 검토 항목 3 — 현금흐름 헤지 중단 후 OCI 후속 처리 [완료 — 2026-04-23]

**수정 완료**: `HedgeDesignationService.processCfhOciAfterDiscontinuation()` 구현.
forecastTransactionExpected=true이면 OCI 유보, false이면 즉시 P&L 재분류 분개 자동 생성.
11개 테스트 케이스 PASS. (AVM-014 참조)

---

### 검토 항목 4 — IRS/CRS Day Count Convention 정확성 [**검토중** — 시연 제외]

- **쟁점**: FX Forward는 KRW Act/365 Fixed, USD Act/360 Day Count가 `DayCountConvention.java`에 명시되어 있다. IRS/CRS의 경우 KRW 고정다리(3/6개월 단위, Act/365 또는 30/360)와 USD 다리(SOFR Act/360) Day Count Convention이 실무와 일치하는지 확인이 필요하다.
- **K-IFRS 근거**: 1113호 72~90항(Level 2 관측가능 입력변수 — 시장 관행 반영 의무)
- **수정 방향**: `IrsPricing.java`, `CrsPricing.java`의 Day Count 로직 검토. ISDA 표준(Actual/360, 30/360, Actual/365 Fixed)과 대조
- **영향 범위**: IRS/CRS 공정가치 수치 정확도, Level 2 분류 근거

---

### 검토 항목 5 — K-IFRS 1107호 공식 공시 양식 자동화 [낮음-후속]

- **쟁점**: 현재 Excel/PDF 수출 기능은 내부 관리 목적의 분개장 형식이다. K-IFRS 1107호 22A~24G항은 헤지회계 공시 요건(헤지비율, OCI 변동표, 위험관리전략, 유효성 테스트 정보)을 구체적으로 규정하며, 사업보고서에 반영되어야 한다.
- **K-IFRS 근거**: 1107호 22A(위험관리전략), 22B(헤지 지정 영향), 23A(공정가치 헤지 손익), 24A(현금흐름 헤지 OCI 변동)
- **수정 방향**: 미래에셋증권 공시 발췌본 참조하여 정형화된 헤지 공시표 생성 API 추가
- **영향 범위**: PoC 범위 외. 본 개발 단계에서 추가 필요

---

## 이슈 요약표

| ID | 상태 | 이슈 요약 | 우선순위 | 관련 검토 항목 |
|---|---|---|---|---|
| AVM-007 | ~~이슈있음~~ **적합** | Lower of Test 부호 방향성 수정 완료 — `calculateSignedEffectivePortion()` 추가, 손실/이익 7개 케이스 PASS (2026-04-23) | 완료 | 검토 항목 1 |
| AVM-014 | ~~이슈있음~~ **적합** | CFH 중단 후 OCI 후속 처리 구현 완료 — forecastTransactionExpected 분기, 11개 테스트 PASS (2026-04-23) | 완료 | 검토 항목 3 |
| AVM-015 | ~~이슈있음~~ **적합** | Rebalancing 이중 분개 수정 완료 — EventHandler WARNING+REBALANCE에서 분개 생성 제거, HedgeRebalancingService 단독 책임 위임. FVH·CFH 각 2건 포함 10개 테스트 PASS, 백엔드 검증 PASS (2026-04-23) | 완료 | — |
| AVM-012 | 검토중 | 헤지 중단 동반 경로는 AVM-014로 커버됨. 독립 예상거래 취소 경로 미구현 — PoC 범위 외 수동 대응 | 후속 과제 | 검토 항목 2 |
| AVM-006 | ~~검토중~~ **적합** | resolveReferenceValue() 분기 코드 확인 완료 — 시연 보조, testType 파라미터 설명 준비로 충분 | 완료 | — |
| AVM-010 | 적합 (유지) | AVM-007 부호 수정 후 분개 방향 정합 확인. 시연 전 손실 케이스 API 1회 실행 확인 권장 | 시연 전 확인 | 검토 항목 1 |

---

## 오늘 수정으로 해소된 이슈 (2026-04-23)

| 이슈 | 수정 전 | 수정 후 | 코드 위치 |
|---|---|---|---|
| Dollar-offset 단독 판정 | 80~125% 이탈=자동 FAIL | PASS/WARNING/FAIL 3단계, FAIL=동방향만 | `DollarOffsetCalculator.java:121` |
| 헤지비율 자동 FAIL | 80~125% 이탈=자동 FAIL | WARNING 처리, 극단값(10%↓, 300%↑)만 FAIL | `HedgeRelationship.java:659` |
| 헤지 중단 사유 검증 미구현 | 임의 문자열 허용 | enum 코드화, VOLUNTARY_DISCONTINUATION 차단 | `HedgeDiscontinuationReason.java`, `HedgeRelationship.java:768` |
| Rebalancing 로그만 기록 | 수동 처리 필요 경고 로그 | `HedgeRebalancingService` 실제 재조정 로직 | `HedgeRebalancingService.java` |
| Rebalancing 이중 분개 (AVM-015) | EventHandler + RebalancingService 양쪽에서 분개 생성 | EventHandler WARNING+REBALANCE 분기에서 분개 제거 → RebalancingService 단독 책임 | `EffectivenessTestCompletedEventHandler.java`(delegateToRebalancingService) |
| OCI 누적 계산 단순 대입 | 당기 유효분 단순 저장 | 이전 잔액 + 당기 유효분 누적 계산 | `EffectivenessTestService.java:404` |
| `EffectivenessTestResult.WARNING` 미정의 | Javadoc에만 언급 | enum 값 추가 완료 | `EffectivenessTestResult.java` |

---

## ~~AVM-007 수정을 위한 backend-developer 구현 규칙~~ [아카이브 — 구현 완료]

> **⚑ 이 섹션은 2026-04-23 구현 완료로 아카이브된 작업 지침이다.**
> 현재 코드 상태: `calculateSignedEffectivePortion()` / `calculateSignedIneffectivePortion()` 구현됨,
> 손실·이익 양방향 7개 테스트 케이스 PASS, OCI 누적 누산 정합.
> 회계 근거와 분개 방향 레퍼런스로만 보존하며, 추가 수정 지시로 사용하지 않는다.
>
> 원본 회계사 에이전트 검토일: 2026-04-23. K-IFRS 1109호 6.5.11⑴, 6.5.11⑷㈐, BC6.280 근거.

### 규칙 1 — effectiveAmount에 헤지수단 부호 복원 (핵심)

**기준서 취지**: 6.5.11⑴은 "위험회피수단의 손익 중 유효한 위험회피로 결정된 부분"을 OCI로 인식한다고 규정한다. 수단이 이익이면 OCI는 증가, 손실이면 OCI는 감소한다. BC6.280은 Lower of Test가 OCI 인식 **한도(규모)** 제한 장치임을 확인한다 — 부호를 변환하는 장치가 아니다.

**현재 코드** (`EffectivenessTestService.java:398`):
```java
BigDecimal effectiveAmount = LowerOfTestCalculator.calculateEffectivePortion(
    instrumentFvCumulative, hedgedItemPvCumulative); // 항상 양수
```

**수정 방향**:
```java
BigDecimal effectiveMagnitude = LowerOfTestCalculator.calculateEffectivePortion(
    instrumentFvCumulative, hedgedItemPvCumulative); // 규모(양수)
// 부호는 헤지수단 누적 방향을 따름 (6.5.11⑴)
BigDecimal effectiveAmount = instrumentFvCumulative.signum() >= 0
    ? effectiveMagnitude
    : effectiveMagnitude.negate();
```

---

### 규칙 2 — ineffectiveAmount에도 동일한 부호 복원 적용

**기준서 취지**: 6.5.11⑵는 비효과적 부분을 당기손익(P/L)에 즉시 인식한다고 규정한다. 과대헤지 초과분도 수단의 손익 방향을 따른다. 수단이 손실 과대헤지이면 P/L 손실로 인식해야 한다.

**현재 코드** (`EffectivenessTestService.java:401`):
```java
BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateIneffectivePortion(
    instrumentFvCumulative, effectiveAmount); // 항상 양수(0 이상)
```

**수정 방향**:
```java
BigDecimal ineffectiveMagnitude = LowerOfTestCalculator.calculateIneffectivePortion(
    instrumentFvCumulative, effectiveMagnitude);
BigDecimal ineffectiveAmount = instrumentFvCumulative.signum() >= 0
    ? ineffectiveMagnitude
    : ineffectiveMagnitude.negate();
```

---

### 규칙 3 — OCI 누적 잔액은 부호 있는 effectiveAmount로 계산

**기준서 취지**: 6.5.11⑷㈐는 "현금흐름위험회피적립금이 **차손**"인 경우를 명시적으로 규정한다. OCI 적립금은 음수 방향으로도 형성될 수 있다.

**현재 코드** (`EffectivenessTestService.java:409`):
```java
BigDecimal cumulativeOciBalance = previousOciBalance
    .add(effectiveAmount) // 항상 양수 — 방향 오류
    .setScale(2, RoundingMode.HALF_UP);
```

**수정 후** (규칙 1 적용 시 자동 해결):
```java
// effectiveAmount가 부호 있는 값으로 공급되면 자동으로 올바르게 계산됨
BigDecimal cumulativeOciBalance = previousOciBalance
    .add(effectiveAmount) // 부호 복원 후 정상
    .setScale(2, RoundingMode.HALF_UP);
```

---

### 규칙 4 — CashFlowHedgeJournalGenerator는 수정 불필요, 입력값만 확인

**기준서 취지**: 6.5.11⑴ 유효분 이익 시 `차:파생상품자산/대:OCI적립금`, 손실 시 `차:OCI적립금/대:파생상품부채`.

**현재 코드** (`CashFlowHedgeJournalGenerator.java:107`):
```java
if (signum >= 0) {
    debit = AccountCode.DRV_ASSET;   // 이익 — 올바름
    credit = AccountCode.CFHR_OCI;
} else {
    debit = AccountCode.CFHR_OCI;   // 손실 분기 — 이미 올바르게 구현됨
    credit = AccountCode.DRV_LIAB;
}
```

**수정 방향**: Generator 자체 수정 불필요. 규칙 1 적용 후 부호 있는 effectiveAmount가 전달되면 손실 분기(else)가 자동으로 정상 동작한다. 단, Generator 호출부에서 `부호 있는 effectiveAmount`를 전달하는지 확인 필수.

---

### 규칙 5 — DB 스키마 `oci_reserve_balance` 음수 허용 여부 확인

**기준서 취지**: OCI 적립금이 차손(음수)으로 형성될 수 있으므로 DB 컬럼이 음수를 허용해야 한다.

**확인 대상**: `EffectivenessTest` 엔티티 및 DB migration 스크립트에서 `oci_reserve_balance` 컬럼에 `CHECK >= 0` 등의 양수 제약이 없는지 확인. BigDecimal 자체는 음수 지원하나 JPA 컬럼 어노테이션 또는 DB DDL에 제약이 있을 수 있다.

**수정 방향**: 제약이 있다면 DDL에서 제거. 없다면 통과.
