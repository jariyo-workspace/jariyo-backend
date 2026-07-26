package com.example.jariyo_backend.common.config;

import java.io.IOException;
import com.example.jariyo_backend.common.api.ApiErrorResponseWriter;
import com.example.jariyo_backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AccessDeniedHandler implements org.springframework.security.web.access.AccessDeniedHandler {
	private final ApiErrorResponseWriter errorResponseWriter;

	public AccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException exception) throws IOException {
		errorResponseWriter.write(response, ErrorCode.ACCESS_DENIED);
	}
}
