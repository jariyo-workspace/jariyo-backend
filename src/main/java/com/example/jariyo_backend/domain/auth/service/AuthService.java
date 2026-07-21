package com.example.jariyo_backend.domain.auth.service;

import java.time.Clock;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.auth.dto.AuthResult;
import com.example.jariyo_backend.domain.auth.dto.RefreshResult;
import com.example.jariyo_backend.domain.auth.dto.SignInRequest;
import com.example.jariyo_backend.domain.auth.dto.SignUpRequest;
import com.example.jariyo_backend.domain.auth.exception.RefreshTokenExpiredException;
import com.example.jariyo_backend.domain.auth.exception.RefreshTokenReuseException;
import com.example.jariyo_backend.domain.auth.support.PhoneNumberNormalizer;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.entity.UserStatus;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
	private final UserAccountRepository userAccountRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokenService refreshTokenService;
	private final Clock clock;

	@Autowired
	public AuthService(
		UserAccountRepository userAccountRepository,
		CustomerProfileRepository customerProfileRepository,
		PasswordEncoder passwordEncoder,
		PasswordPolicy passwordPolicy,
		JwtTokenService jwtTokenService,
		RefreshTokenService refreshTokenService
	) {
		this(userAccountRepository, customerProfileRepository, passwordEncoder, passwordPolicy,
			jwtTokenService, refreshTokenService, Clock.systemUTC());
	}

	AuthService(
		UserAccountRepository userAccountRepository,
		CustomerProfileRepository customerProfileRepository,
		PasswordEncoder passwordEncoder,
		PasswordPolicy passwordPolicy,
		JwtTokenService jwtTokenService,
		RefreshTokenService refreshTokenService,
		Clock clock
	) {
		this.userAccountRepository = userAccountRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenService = refreshTokenService;
		this.clock = clock;
	}

	@Transactional
	public AuthResult signUp(SignUpRequest request) {
		if (!request.agreements().terms() || !request.agreements().privacy()) {
			throw new BusinessException(ErrorCode.REQUIRED_AGREEMENT_MISSING);
		}
		passwordPolicy.validate(request.password());
		String email = request.email();
		String phoneNumber = PhoneNumberNormalizer.normalize(request.phoneNumber());
		UserAccount user = saveUser(email, phoneNumber, request.password());
		customerProfileRepository.save(
			new CustomerProfile(user, request.displayName(), request.agreements().marketing(), true));
		return issueTokens(user);
	}

	@Transactional
	public AuthResult signIn(SignInRequest request) {
		String email = request.email();
		UserAccount user = userAccountRepository.findByEmailAndStatusNot(email, UserStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new BusinessException(ErrorCode.USER_SUSPENDED);
		}
		user.recordLogin(clock.instant());
		return issueTokens(user);
	}

	@Transactional(noRollbackFor = {RefreshTokenReuseException.class, RefreshTokenExpiredException.class})
	public AuthResult refresh(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		RefreshResult refreshResult = refreshTokenService.rotate(rawRefreshToken);
		UserAccount user = refreshResult.user();
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new BusinessException(ErrorCode.USER_SUSPENDED);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		return new AuthResult(user.getId(), jwtTokenService.issue(user), refreshResult.refreshToken());
	}

	public void signOut(String rawRefreshToken) {
		refreshTokenService.revokeFamily(rawRefreshToken);
	}

	private AuthResult issueTokens(UserAccount user) {
		return new AuthResult(user.getId(), jwtTokenService.issue(user), refreshTokenService.issue(user));
	}

	private UserAccount saveUser(String email, String phoneNumber, String password) {
		try {
			return userAccountRepository.saveAndFlush(
				new UserAccount(email, phoneNumber, passwordEncoder.encode(password)));
		} catch (DataIntegrityViolationException exception) {
			throw duplicateUserException(exception);
		}
	}

	private RuntimeException duplicateUserException(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				return switch (constraintViolation.getConstraintName()) {
					case "uk_users_active_email" -> new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
					case "uk_users_active_phone_number" ->
						new BusinessException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
					default -> exception;
				};
			}
			cause = cause.getCause();
		}
		return exception;
	}
}
