package com.hedge.prototype.hedge.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.HedgeDiscontinuationReason;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 헤지회계 중단 요청 DTO.
 *
 * <p>K-IFRS 1109호 6.5.6에 따라 허용된 사유 코드({@link HedgeDiscontinuationReason})만 수신합니다.
 * 자발적 중단({@link HedgeDiscontinuationReason#VOLUNTARY_DISCONTINUATION})은
 * 도메인 레이어에서 차단됩니다.
 *
 * <p>현금흐름 위험회피(CFH) 중단 시 K-IFRS 1109호 6.5.12에 따라
 * 예상거래 발생 가능 여부({@code forecastTransactionExpected})를 반드시 명시해야 합니다.
 * 예상거래 발생 불가 확정 시 {@code currentOciBalance}와 {@code plAccount}를 함께 제공하면
 * OCI 잔액이 즉시 P&amp;L로 재분류됩니다.
 *
 * @param discontinuationDate          중단일 (null이면 요청일 기준으로 처리)
 * @param reason                       중단 사유 코드 (필수) — 허용된 enum 값만 가능
 * @param details                      중단 상세 사유 서술 (선택 — 감사 추적용)
 * @param forecastTransactionExpected  예상거래 발생 가능 여부 (CFH 전용, K-IFRS 1109호 6.5.12)
 *                                     true=예상거래 여전히 발생 가능 → OCI 유지
 *                                     false=예상거래 발생 불가 확정 → OCI 즉시 P&L 재분류
 *                                     CFH가 아닌 경우 null 허용
 * @param currentOciBalance            현재 OCI 적립금 잔액 (CFH + forecastTransactionExpected=false 시 필요)
 *                                     K-IFRS 1109호 6.5.12(2): 즉시 P&L 재분류 대상 금액
 *                                     ZERO이면 분개를 생성하지 않고 로그만 기록
 * @param plAccount                    OCI → P&L 재분류 시 대응 손익 계정
 *                                     (TRANSACTION_NO_LONGER_EXPECTED 시 필요, 예: FX_GAIN_PL, FX_LOSS_PL)
 * @see K-IFRS 1109호 6.5.6  (자발적 취소 불가 원칙)
 * @see K-IFRS 1109호 6.5.12 (CFH 중단 후 OCI 후속 처리)
 * @see K-IFRS 1109호 6.5.12(2) (예상거래 발생불가 시 즉시 P&L 재분류)
 */
public record HedgeDiscontinuationRequest(
        LocalDate discontinuationDate,

        @NotNull(message = "중단 사유 코드는 필수입니다.")
        HedgeDiscontinuationReason reason,

        String details,

        /**
         * 예상거래 발생 가능 여부 (CFH 전용, K-IFRS 1109호 6.5.12).
         * - true : 예상거래 여전히 발생 가능 → OCI 유지, 분개 미생성
         * - false: 예상거래 발생 불가 확정 → OCI 잔액 즉시 P&L 재분류
         * - null : CFH가 아닌 경우(FVH 등)에 null 허용. CFH인데 null이면 HD_017 예외
         */
        @Nullable
        Boolean forecastTransactionExpected,

        /**
         * 현재 OCI 적립금 잔액 (K-IFRS 1109호 6.5.12(2)).
         * forecastTransactionExpected=false 인 경우 P&L 재분류 대상 금액.
         * ZERO 이면 분개를 생성하지 않음.
         */
        @Nullable
        BigDecimal currentOciBalance,

        /**
         * OCI → P&L 재분류 시 대응 손익 계정 코드 (문자열).
         * {@link com.hedge.prototype.journal.domain.AccountCode} enum 이름.
         * 예: "FX_GAIN_PL", "FX_LOSS_PL", "RECLASSIFY_PL"
         */
        @Nullable
        String plAccount
) {}
