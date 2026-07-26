package com.example.jariyo_backend.common.config;

import java.io.IOException;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiErrorResponseWriter;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.entity.UserStatus;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveUserFilter extends OncePerRequestFilter {
	private final UserAccountRepository userAccountRepository;
	private final ApiErrorResponseWriter errorResponseWriter;

	public ActiveUserFilter(UserAccountRepository userAccountRepository, ApiErrorResponseWriter errorResponseWriter) {
		this.userAccountRepository = userAccountRepository;
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
			UserAccount user = findUser(jwtAuthenticationToken.getName());
			if (user == null) {
				write(response, ErrorCode.INVALID_ACCESS_TOKEN);
				return;
			}
			if (user.getStatus() == UserStatus.SUSPENDED) {
				write(response, ErrorCode.USER_SUSPENDED);
				return;
			}
			if (user.getStatus() != UserStatus.ACTIVE) {
				write(response, ErrorCode.INVALID_ACCESS_TOKEN);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private UserAccount findUser(String subject) {
		try {
			return userAccountRepository.findById(UUID.fromString(subject)).orElse(null);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		SecurityContextHolder.clearContext();
		errorResponseWriter.write(response, errorCode);
	}
}
