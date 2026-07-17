package com.example.jariyo_backend.domain.auth.exception;

import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;

public class RefreshTokenReuseException extends BusinessException {
	public RefreshTokenReuseException() {
		super(ErrorCode.REFRESH_TOKEN_REUSED);
	}
}
