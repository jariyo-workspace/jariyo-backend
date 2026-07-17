package com.example.jariyo_backend.domain.auth;

import java.util.Optional;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.user.CustomerProfile;
import com.example.jariyo_backend.domain.user.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.UserAccount;
import com.example.jariyo_backend.domain.user.UserAccountRepository;
import com.example.jariyo_backend.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
	@Mock UserAccountRepository userAccountRepository;
	@Mock CustomerProfileRepository customerProfileRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock JwtTokenService jwtTokenService;
	@Mock RefreshTokenService refreshTokenService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userAccountRepository, customerProfileRepository, passwordEncoder,
			new PasswordPolicy(), jwtTokenService, refreshTokenService);
	}

	@Test
	void signUpCreatesUserAndCustomerProfileWithNormalizedValues() {
		SignUpRequest request = new SignUpRequest(" User@Example.COM ", "long-enough-password",
			"자리요", "010-1234-5678", new SignUpRequest.Agreements(true, true, false));
		when(passwordEncoder.encode(request.password())).thenReturn("{argon2}hash");
		when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtTokenService.issue(any(UserAccount.class))).thenReturn(new AccessToken("access", 900));
		when(refreshTokenService.issue(any(UserAccount.class))).thenReturn("refresh");

		AuthResult result = authService.signUp(request);

		ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
		verify(userAccountRepository).save(userCaptor.capture());
		assertEquals("user@example.com", userCaptor.getValue().getEmail());
		assertEquals("+821012345678", userCaptor.getValue().getPhoneNumber());
		ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
		verify(customerProfileRepository).save(profileCaptor.capture());
		assertTrue(profileCaptor.getValue().isNotificationConsent());
		assertEquals("refresh", result.refreshToken());
	}

	@Test
	void signUpRequiresTermsAndPrivacyAgreement() {
		SignUpRequest request = new SignUpRequest("user@example.com", "long-enough-password",
			"자리요", "01012345678", new SignUpRequest.Agreements(false, true, false));

		BusinessException exception = assertThrows(BusinessException.class, () -> authService.signUp(request));

		assertEquals(ErrorCode.REQUIRED_AGREEMENT_MISSING, exception.getErrorCode());
	}

	@Test
	void signInDoesNotRevealWhetherEmailOrPasswordWasWrong() {
		when(userAccountRepository.findByEmailAndStatusNot("missing@example.com", UserStatus.WITHDRAWN))
			.thenReturn(Optional.empty());

		BusinessException exception = assertThrows(BusinessException.class,
			() -> authService.signIn(new SignInRequest("missing@example.com", "password")));

		assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
	}
}
