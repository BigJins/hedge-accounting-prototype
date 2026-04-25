package com.hedge.prototype.common.exception;

import com.hedge.prototype.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리 핸들러.
 * 스택트레이스 외부 노출 금지 — 로그에만 기록.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리 (K-IFRS 요건 위반, 회계 규칙 위반 등)
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("비즈니스 예외 발생: errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return ResponseEntity
                .status(e.getHttpStatus())
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    /**
     * JSON 파싱/역직렬화 실패 처리 (잘못된 enum 값, 날짜 형식 오류, 지원하지 않는 값 등).
     *
     * <p>Jackson이 요청 본문을 역직렬화하는 과정에서 오류가 발생하면
     * Spring이 HttpMessageNotReadableException으로 감쌉니다.
     * 이 핸들러 없이는 {@link #handleException(Exception)}이 캐치하여 500을 반환합니다.
     *
     * <p>원인(cause) 체인을 순회하여 {link IllegalArgumentException}에 설정된
     * 상세 메시지를 추출합니다. 예: HedgedItemType.fromJson에서 지원하지 않는 값 입력 시.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        String detail = extractReadableMessage(e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("HTTP_400", detail));
    }

    /**
     * 입력 유효성 검증 실패 처리 (@Valid 어노테이션 기반).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("입력값 유효성 오류: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    /**
     * 그 외 미처리 예외 — 500 반환, 스택트레이스 외부 미노출.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "내부 오류가 발생했습니다. 관리자에게 문의하세요."));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * HttpMessageNotReadableException 원인 체인에서 가장 구체적인 메시지 추출.
     */
    private String extractReadableMessage(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return "요청 본문을 파싱할 수 없습니다. JSON 형식 및 필드 값을 확인하세요.";
    }
}

