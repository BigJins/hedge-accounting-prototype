package com.hedge.prototype.common.dto;

import java.time.LocalDateTime;

/**
 * API 에러 응답 DTO.
 * 스택트레이스 외부 노출 금지 — 코드와 메시지만 반환.
 */
public record ErrorResponse(
        String errorCode,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, LocalDateTime.now());
    }
}
