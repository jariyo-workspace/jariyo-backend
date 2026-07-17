package com.example.jariyo_backend.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.auth.dto.AuthResult;
import com.example.jariyo_backend.domain.auth.dto.SignInRequest;
import com.example.jariyo_backend.domain.auth.dto.SignUpRequest;
import com.example.jariyo_backend.domain.auth.exception.RefreshTokenReuseException;
import com.example.jariyo_backend.domain.auth.service.AuthService;
import com.example.jariyo_backend.domain.user.dto.MeResponse;
import com.example.jariyo_backend.domain.user.service.MeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class AuthenticationFlowIntegrationTests {
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Autowired AuthService authService;
	@Autowired MeService meService;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("security.jwt.public-key", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
		registry.add("security.jwt.private-key", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
		registry.add("security.refresh-token.cookie-secure", () -> false);
	}

	@Test
	void signUpRefreshReuseDetectionSignOutAndMeFlow() {
		SignUpRequest signUpRequest = new SignUpRequest(
			"integration@example.com",
			"integration-password",
			"통합테스트",
			"010-1234-5678",
			new SignUpRequest.Agreements(true, true, false));

		AuthResult signedUp = authService.signUp(signUpRequest);

		assertEquals(7, signedUp.userId().version());
		MeResponse me = meService.get(signedUp.userId());
		assertEquals("integration@example.com", me.email());
		assertEquals("010-****-5678", me.phoneNumber());

		AuthResult refreshed = authService.refresh(signedUp.refreshToken());
		assertNotEquals(signedUp.refreshToken(), refreshed.refreshToken());
		assertThrows(RefreshTokenReuseException.class,
			() -> authService.refresh(signedUp.refreshToken()));

		AuthResult signedIn = authService.signIn(
			new SignInRequest("integration@example.com", "integration-password"));
		authService.signOut(signedIn.refreshToken());
		BusinessException revoked = assertThrows(BusinessException.class,
			() -> authService.refresh(signedIn.refreshToken()));
		assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, revoked.getErrorCode());

		BusinessException duplicate = assertThrows(BusinessException.class,
			() -> authService.signUp(signUpRequest));
		assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, duplicate.getErrorCode());
	}

	@Test
	void revokesWholeFamilyWhenOldAndCurrentTokensAreUsedConcurrently() throws Exception {
		AuthResult signedUp = authService.signUp(new SignUpRequest(
			"concurrent@example.com",
			"integration-password",
			"동시성테스트",
			"010-9876-5432",
			new SignUpRequest.Agreements(true, true, false)));
		AuthResult current = authService.refresh(signedUp.refreshToken());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AuthResult> currentRefresh = executor.submit(() -> {
				start.await();
				try {
					return authService.refresh(current.refreshToken());
				} catch (BusinessException exception) {
					return null;
				}
			});
			Future<RuntimeException> oldTokenReuse = executor.submit(() -> {
				start.await();
				try {
					authService.refresh(signedUp.refreshToken());
					throw new AssertionError("이전 Refresh Token 재사용이 허용되었습니다.");
				} catch (RuntimeException exception) {
					return exception;
				}
			});
			start.countDown();

			AuthResult possiblyIssued = currentRefresh.get();
			assertInstanceOf(RefreshTokenReuseException.class, oldTokenReuse.get());
			if (possiblyIssued != null) {
				assertThrows(BusinessException.class,
					() -> authService.refresh(possiblyIssued.refreshToken()));
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static String pem(String type, byte[] encoded) {
		return "-----BEGIN " + type + "-----\n"
			+ Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
			+ "\n-----END " + type + "-----";
	}
}
