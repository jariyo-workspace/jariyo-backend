package com.example.jariyo_backend.domain.user.support;

import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(UUID id) {
	public static AuthenticatedUser from(Jwt jwt) {
		try {
			return new AuthenticatedUser(UUID.fromString(jwt.getSubject()));
		} catch (RuntimeException exception) {
			throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
		}
	}
}
