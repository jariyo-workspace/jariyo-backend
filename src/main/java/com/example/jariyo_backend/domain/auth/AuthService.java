package com.example.jariyo_backend.domain.auth;

import java.time.Clock;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.user.CustomerProfile;
import com.example.jariyo_backend.domain.user.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.UserAccount;
import com.example.jariyo_backend.domain.user.UserAccountRepository;
import com.example.jariyo_backend.domain.user.UserStatus;
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
		String email = EmailNormalizer.normalize(request.email());
		String phoneNumber = PhoneNumberNormalizer.normalize(request.phoneNumber());
		if (userAccountRepository.existsByEmailAndStatusNot(email, UserStatus.WITHDRAWN)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		if (userAccountRepository.existsByPhoneNumberAndStatusNot(phoneNumber, UserStatus.WITHDRAWN)) {
			throw new BusinessException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
		}

		UserAccount user = userAccountRepository.save(
			new UserAccount(email, phoneNumber, passwordEncoder.encode(request.password())));
		customerProfileRepository.save(
			new CustomerProfile(user, request.displayName(), request.agreements().marketing(), true));
		return issueTokens(user);
	}

	@Transactional
	public AuthResult signIn(SignInRequest request) {
		String email = EmailNormalizer.normalize(request.email());
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
}
