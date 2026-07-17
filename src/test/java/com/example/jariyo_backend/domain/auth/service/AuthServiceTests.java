package com.example.jariyo_backend.domain.auth.service;

import java.sql.SQLException;
import java.util.Optional;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.auth.dto.AccessToken;
import com.example.jariyo_backend.domain.auth.dto.AuthResult;
import com.example.jariyo_backend.domain.auth.dto.SignInRequest;
import com.example.jariyo_backend.domain.auth.dto.SignUpRequest;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.entity.UserStatus;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
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
		when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtTokenService.issue(any(UserAccount.class))).thenReturn(new AccessToken("access", 900));
		when(refreshTokenService.issue(any(UserAccount.class))).thenReturn("refresh");

		AuthResult result = authService.signUp(request);

		ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
		verify(userAccountRepository).saveAndFlush(userCaptor.capture());
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
	void mapsDatabaseUniqueIndexesToUserErrors() {
		SignUpRequest request = new SignUpRequest("user@example.com", "long-enough-password",
			"자리요", "01012345678", new SignUpRequest.Agreements(true, true, false));
		when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
			.thenThrow(dataIntegrityViolation("uk_users_active_email"))
			.thenThrow(dataIntegrityViolation("uk_users_active_phone_number"));

		BusinessException emailDuplicate = assertThrows(BusinessException.class, () -> authService.signUp(request));
		BusinessException phoneDuplicate = assertThrows(BusinessException.class, () -> authService.signUp(request));

		assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, emailDuplicate.getErrorCode());
		assertEquals(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS, phoneDuplicate.getErrorCode());
	}

	@Test
	void signInDoesNotRevealWhetherEmailOrPasswordWasWrong() {
		when(userAccountRepository.findByEmailAndStatusNot("missing@example.com", UserStatus.WITHDRAWN))
			.thenReturn(Optional.empty());

		BusinessException exception = assertThrows(BusinessException.class,
			() -> authService.signIn(new SignInRequest("missing@example.com", "password")));

		assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
	}

	private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(
			"duplicate", new SQLException("duplicate"), constraintName);
		return new DataIntegrityViolationException("duplicate", cause);
	}
}
