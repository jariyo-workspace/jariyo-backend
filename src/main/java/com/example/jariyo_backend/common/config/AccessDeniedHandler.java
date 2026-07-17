package com.example.jariyo_backend.common.config;

import java.io.IOException;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AccessDeniedHandler implements org.springframework.security.web.access.AccessDeniedHandler {
	private final ObjectMapper objectMapper;

	public AccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException exception) throws IOException {
		ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
			ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
	}
}
