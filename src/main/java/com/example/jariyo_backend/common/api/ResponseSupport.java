package com.example.jariyo_backend.common.api;

import com.example.jariyo_backend.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ResponseSupport {
	private ResponseSupport() {
	}

	public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	public static <T> ResponseEntity<ApiResponse<T>> ok(T data, ApiResponse.PageBody page) {
		return ResponseEntity.ok(ApiResponse.success(data, page));
	}

	public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
	}

	public static ResponseEntity<ApiResponse<Void>> noContent() {
		return ResponseEntity.noContent().build();
	}

	public static <T> ResponseEntity<ApiResponse<T>> of(HttpStatus status, T data) {
		return ResponseEntity.status(status).body(ApiResponse.success(data));
	}

	public static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
	}

	public static ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.failure(errorCode));
	}

	public static ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.failure(errorCode, message));
	}
}
