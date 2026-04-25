package com.hedge.prototype.effectiveness.application.event;

import com.hedge.prototype.effectiveness.domain.ActionRequired;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestResult;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;

/**
 * 유효성 테스트 완료 내부 이벤트.
 *
 * <p>Zero-Payload 원칙: ID만 전달, 핸들러에서 DB 재조회.
 * PASS 시 {@link com.hedge.prototype.journal.application.JournalEntryUseCase}를
 * 통해 분개가 자동 생성된다.
 *
 * @param effectivenessTestId  유효성 테스트 결과 ID
 * @param hedgeRelationshipId  위험회피관계 ID
 * @param testResult           테스트 결과 (PASS / FAIL / WARNING)
 * @param actionRequired       후속 조치 (NONE / REBALANCE / DISCONTINUE)
 * @param hedgeType            헤지 유형 (공정가치 / 현금흐름)
 * @param instrumentType       위험회피수단 유형 (null = FX_FORWARD 하위호환)
 * @see K-IFRS 1109호 6.4.4 (유효성 요건)
 * @see K-IFRS 1109호 6.5.8  (공정가치헤지 — FVH IRS 분개 라우팅)
 * @see K-IFRS 1109호 6.5.11 (현금흐름헤지 — OCI 재분류)
 */
public record EffectivenessTestCompletedEvent(
        Long effectivenessTestId,
        String hedgeRelationshipId,
        EffectivenessTestResult testResult,
        ActionRequired actionRequired,
        HedgeType hedgeType,
        InstrumentType instrumentType   // null → FX_FORWARD 하위호환 (1단계 이벤트 소스)
) {}

