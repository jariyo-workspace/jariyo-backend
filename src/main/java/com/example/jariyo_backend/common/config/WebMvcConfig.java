package com.example.jariyo_backend.common.config;

import com.example.jariyo_backend.common.idempotency.IdempotencyKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
	private final IdempotencyKeyInterceptor idempotencyKeyInterceptor;

	public WebMvcConfig(IdempotencyKeyInterceptor idempotencyKeyInterceptor) {
		this.idempotencyKeyInterceptor = idempotencyKeyInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(idempotencyKeyInterceptor)
			.excludePathPatterns("/api/v1/auth/**");
	}
}
