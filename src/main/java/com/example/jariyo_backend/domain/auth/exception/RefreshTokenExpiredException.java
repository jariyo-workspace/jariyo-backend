package com.example.jariyo_backend.domain.auth.exception;

import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;

public class RefreshTokenExpiredException extends BusinessException {
	public RefreshTokenExpiredException() {
		super(ErrorCode.REFRESH_TOKEN_EXPIRED);
	}
}
