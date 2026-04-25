package com.hedge.prototype.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 헤지회계 시스템 비즈니스 예외 기본 클래스.
 *
 * <p>시스템 예외(RuntimeException)와 분리하여
 * 회계 규칙 위반, K-IFRS 요건 미충족 등 비즈니스 오류를 표현합니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }

}
