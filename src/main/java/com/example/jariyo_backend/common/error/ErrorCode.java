package com.example.jariyo_backend.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	UNAUTHORIZED("AUTH-401", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN("AUTH-403", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND("COMMON-404", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	CONFLICT("COMMON-409", HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
	BAD_REQUEST("COMMON-400", HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
	INTERNAL_SERVER_ERROR("COMMON-500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final String code;
	private final HttpStatus status;
	private final String message;

	ErrorCode(String code, HttpStatus status, String message) {
		this.code = code;
		this.status = status;
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}
