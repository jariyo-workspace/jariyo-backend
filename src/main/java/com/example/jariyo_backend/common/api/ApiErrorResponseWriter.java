package com.example.jariyo_backend.common.api;

import java.io.IOException;
import com.example.jariyo_backend.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorResponseWriter {
	private final ObjectMapper objectMapper;

	public ApiErrorResponseWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		write(response, errorCode, errorCode.getMessage());
	}

	public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode, message));
	}
}
