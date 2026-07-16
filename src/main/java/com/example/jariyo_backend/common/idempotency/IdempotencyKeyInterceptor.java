package com.example.jariyo_backend.common.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyKeyInterceptor implements HandlerInterceptor {
	private static final Duration DEFAULT_TTL = Duration.ofHours(24);

	private final IdempotencyStore idempotencyStore;

	public IdempotencyKeyInterceptor(IdempotencyStore idempotencyStore) {
		this.idempotencyStore = idempotencyStore;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String method = request.getMethod();
		if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
			String idempotencyKey = request.getHeader(IdempotencyKey.HEADER_NAME);
			if (idempotencyKey == null || idempotencyKey.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다.");
			}
			boolean accepted = idempotencyStore.putIfAbsent(idempotencyKey, DEFAULT_TTL);
			if (!accepted) {
				throw new IdempotencyException("이미 처리된 Idempotency-Key 입니다.");
			}
		}
		return true;
	}
}
