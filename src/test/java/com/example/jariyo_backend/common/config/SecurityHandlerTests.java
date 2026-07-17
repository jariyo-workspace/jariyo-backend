package com.example.jariyo_backend.common.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHandlerTests {
	@Test
	void returnsAuthenticationRequiredForRequestWithoutBearerToken() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AuthenticationEntryPointHandler handler = new AuthenticationEntryPointHandler(JsonMapper.builder().build());

		handler.commence(request, response, new InsufficientAuthenticationException("required"));

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void returnsAccessDeniedForAuthenticatedRequestWithoutPermission() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		AccessDeniedHandler handler = new AccessDeniedHandler(JsonMapper.builder().build());

		handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

		assertEquals(403, response.getStatus());
		assertTrue(response.getContentAsString().contains("ACCESS_DENIED"));
	}

	@Test
	void identifiesExpiredTokenByStructuredErrorCode() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer expired");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AuthenticationEntryPointHandler handler = new AuthenticationEntryPointHandler(JsonMapper.builder().build());
		JwtValidationException cause = new JwtValidationException("validation failed", List.of(
			new OAuth2Error(JwtConfig.EXPIRED_TOKEN_ERROR_CODE, "message text can change", null)));

		handler.commence(request, response, new InsufficientAuthenticationException("invalid", cause));

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("ACCESS_TOKEN_EXPIRED"));
	}
}
