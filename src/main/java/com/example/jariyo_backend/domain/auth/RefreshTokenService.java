package com.example.jariyo_backend.domain.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import com.example.jariyo_backend.common.config.RefreshTokenProperties;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.user.RefreshToken;
import com.example.jariyo_backend.domain.user.RefreshTokenRepository;
import com.example.jariyo_backend.domain.user.RefreshTokenStatus;
import com.example.jariyo_backend.domain.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenProperties properties;
	private final SecureRandom secureRandom;
	private final Clock clock;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenProperties properties) {
		this(refreshTokenRepository, properties, new SecureRandom(), Clock.systemUTC());
	}

	RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenProperties properties,
		SecureRandom secureRandom, Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.properties = properties;
		this.secureRandom = secureRandom;
		this.clock = clock;
	}

	@Transactional
	public String issue(UserAccount user) {
		return create(user, UUID.randomUUID()).rawToken();
	}

	@Transactional(noRollbackFor = {RefreshTokenReuseException.class, RefreshTokenExpiredException.class})
	public RefreshResult rotate(String rawToken) {
		RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		Instant now = clock.instant();
		if (current.getStatus() == RefreshTokenStatus.ROTATED || current.getStatus() == RefreshTokenStatus.REUSED) {
			current.markReused(now);
			revokeFamily(current.getFamilyId(), now);
			throw new RefreshTokenReuseException();
		}
		if (current.getStatus() != RefreshTokenStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		if (!current.getExpiresAt().isAfter(now)) {
			current.revoke(now);
			throw new RefreshTokenExpiredException();
		}

		IssuedRefreshToken replacement = create(current.getUser(), current.getFamilyId());
		current.rotate(replacement.entity(), now);
		return new RefreshResult(current.getUser(), replacement.rawToken());
	}

	@Transactional
	public void revokeFamily(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(rawToken))
			.ifPresent(token -> revokeFamily(token.getFamilyId(), clock.instant()));
	}

	private IssuedRefreshToken create(UserAccount user, UUID familyId) {
		byte[] tokenBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(tokenBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		RefreshToken entity = new RefreshToken(user, familyId, hash(rawToken), clock.instant().plus(properties.ttl()));
		refreshTokenRepository.save(entity);
		return new IssuedRefreshToken(rawToken, entity);
	}

	private void revokeFamily(UUID familyId, Instant now) {
		refreshTokenRepository.findAllByFamilyId(familyId).forEach(token -> token.revoke(now));
	}

	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}

	private record IssuedRefreshToken(String rawToken, RefreshToken entity) {
	}
}
