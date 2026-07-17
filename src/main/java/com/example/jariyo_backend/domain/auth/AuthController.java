package com.example.jariyo_backend.domain.auth;

import com.example.jariyo_backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	private final AuthService authService;
	private final RefreshTokenCookieManager cookieManager;

	public AuthController(AuthService authService, RefreshTokenCookieManager cookieManager) {
		this.authService = authService;
		this.cookieManager = cookieManager;
	}

	@PostMapping("/sign-up")
	public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
		AuthResult result = authService.signUp(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, cookieManager.create(result.refreshToken()).toString())
			.body(ApiResponse.success(SignUpResponse.from(result)));
	}

	@PostMapping("/sign-in")
	public ResponseEntity<ApiResponse<SignInResponse>> signIn(@Valid @RequestBody SignInRequest request) {
		AuthResult result = authService.signIn(request);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookieManager.create(result.refreshToken()).toString())
			.body(ApiResponse.success(SignInResponse.from(result)));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<SignInResponse>> refresh(
		@CookieValue(name = RefreshTokenCookieManager.COOKIE_NAME, required = false) String refreshToken
	) {
		AuthResult result = authService.refresh(refreshToken);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookieManager.create(result.refreshToken()).toString())
			.body(ApiResponse.success(SignInResponse.from(result)));
	}

	@PostMapping("/sign-out")
	public ResponseEntity<Void> signOut(
		@CookieValue(name = RefreshTokenCookieManager.COOKIE_NAME, required = false) String refreshToken
	) {
		authService.signOut(refreshToken);
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookieManager.clear().toString())
			.build();
	}
}
