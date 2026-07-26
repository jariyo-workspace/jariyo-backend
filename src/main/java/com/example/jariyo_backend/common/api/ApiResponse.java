package com.example.jariyo_backend.common.api;

import java.time.Instant;
import com.example.jariyo_backend.common.error.ErrorCode;

public record ApiResponse<T>(
	Timestamp timestamp,
	boolean success,
	T data,
	ErrorBody error
) {
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(new Timestamp(Instant.now()), true, data, null);
	}

	public static <T> ApiResponse<T> failure(String code, String message) {
		return new ApiResponse<>(new Timestamp(Instant.now()), false, null, new ErrorBody(code, message));
	}

	public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
		return failure(errorCode.getCode(), errorCode.getMessage());
	}

	public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
		return failure(errorCode.getCode(), message);
	}

	public record Timestamp(Instant value) {
	}

	public record ErrorBody(String code, String message) {
	}
}
