package com.example.jariyo_backend.common.idempotency;

import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;

public class IdempotencyException extends BusinessException {
	public IdempotencyException(String message) {
		super(ErrorCode.CONFLICT, message);
	}
}
