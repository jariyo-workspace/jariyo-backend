package com.example.jariyo_backend.domain.auth.service;

import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
	private static final int MIN_LENGTH = 15;
	private static final int MAX_LENGTH = 64;

	public void validate(String password) {
		int length = password.codePointCount(0, password.length());
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
		}
	}
}
