package com.example.jariyo_backend.common.config;

import java.io.IOException;
import com.example.jariyo_backend.common.api.ApiErrorResponseWriter;
import com.example.jariyo_backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEntryPointHandler implements AuthenticationEntryPoint {
	private final ApiErrorResponseWriter errorResponseWriter;

	public AuthenticationEntryPointHandler(ApiErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException {
		ErrorCode errorCode = hasExpiredError(exception)
			? ErrorCode.ACCESS_TOKEN_EXPIRED
			: request.getHeader("Authorization") == null
				? ErrorCode.AUTHENTICATION_REQUIRED
				: ErrorCode.INVALID_ACCESS_TOKEN;
		write(response, errorCode);
	}

	private boolean hasExpiredError(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof JwtValidationException validationException
				&& validationException.getErrors().stream()
					.anyMatch(error -> JwtConfig.EXPIRED_TOKEN_ERROR_CODE.equals(error.getErrorCode()))) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		errorResponseWriter.write(response, errorCode);
	}
}
