package com.example.jariyo_backend.domain.auth.controller;

import java.time.Duration;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.config.RefreshTokenProperties;
import com.example.jariyo_backend.domain.auth.dto.AccessToken;
import com.example.jariyo_backend.domain.auth.dto.AuthResult;
import com.example.jariyo_backend.domain.auth.dto.SignUpRequest;
import com.example.jariyo_backend.domain.auth.dto.SignUpResponse;
import com.example.jariyo_backend.domain.auth.service.AuthService;
import com.example.jariyo_backend.domain.auth.service.RefreshTokenCookieManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {
	@Mock AuthService authService;

	@Test
	void signUpReturnsAccessTokenAndSecureRefreshCookie() {
		RefreshTokenCookieManager cookieManager = new RefreshTokenCookieManager(
			new RefreshTokenProperties(Duration.ofDays(14), true));
		AuthController controller = new AuthController(authService, cookieManager);
		SignUpRequest request = new SignUpRequest("user@example.com", "long-enough-password",
			"자리요", "01012345678", new SignUpRequest.Agreements(true, true, false));
		UUID userId = UUID.randomUUID();
		when(authService.signUp(request)).thenReturn(
			new AuthResult(userId, new AccessToken("access", 900), "refresh"));

		ResponseEntity<ApiResponse<SignUpResponse>> response = controller.signUp(request);

		assertEquals(201, response.getStatusCode().value());
		assertEquals(userId, response.getBody().data().userId());
		String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertTrue(cookie.contains("jariyo_refresh=refresh"));
		assertTrue(cookie.contains("HttpOnly"));
		assertTrue(cookie.contains("Secure"));
		assertTrue(cookie.contains("SameSite=Strict"));
	}

	@Test
	void signOutIsIdempotentAndClearsCookie() {
		AuthController controller = new AuthController(authService,
			new RefreshTokenCookieManager(new RefreshTokenProperties(Duration.ofDays(14), true)));

		ResponseEntity<Void> response = controller.signOut(null);

		verify(authService).signOut(null);
		assertEquals(204, response.getStatusCode().value());
		assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
	}
}
