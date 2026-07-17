package com.example.jariyo_backend.common.config;

import java.io.IOException;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEntryPointHandler implements AuthenticationEntryPoint {
	private final ObjectMapper objectMapper;

	public AuthenticationEntryPointHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException {
		ErrorCode errorCode = hasExpiredCause(exception)
			? ErrorCode.ACCESS_TOKEN_EXPIRED
			: request.getHeader("Authorization") == null
				? ErrorCode.AUTHENTICATION_REQUIRED
				: ErrorCode.INVALID_ACCESS_TOKEN;
		write(response, errorCode);
	}

	private boolean hasExpiredCause(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage().toLowerCase().contains("expired")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
			ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
	}
}
