package com.example.jariyo_backend.domain.auth.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import com.example.jariyo_backend.common.config.RefreshTokenProperties;
import com.example.jariyo_backend.domain.auth.dto.RefreshResult;
import com.example.jariyo_backend.domain.auth.entity.RefreshToken;
import com.example.jariyo_backend.domain.auth.entity.RefreshTokenStatus;
import com.example.jariyo_backend.domain.auth.exception.RefreshTokenReuseException;
import com.example.jariyo_backend.domain.auth.repository.RefreshTokenRepository;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {
	@Mock RefreshTokenRepository refreshTokenRepository;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		refreshTokenService = new RefreshTokenService(
			refreshTokenRepository, new RefreshTokenProperties(Duration.ofDays(14), false));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void rotatesTokenAndDetectsReuse() {
		UserAccount user = new UserAccount("user@example.com", "+821012345678", "{argon2}hash");
		String originalRawToken = refreshTokenService.issue(user);
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		RefreshToken original = captor.getValue();
		when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(originalRawToken)))
			.thenReturn(Optional.of(original));

		RefreshResult result = refreshTokenService.rotate(originalRawToken);

		assertNotEquals(originalRawToken, result.refreshToken());
		assertNotEquals(RefreshTokenStatus.ACTIVE, original.getStatus());
		when(refreshTokenRepository.findAllByFamilyId(original.getFamilyId())).thenReturn(List.of(original));
		assertThrows(RefreshTokenReuseException.class, () -> refreshTokenService.rotate(originalRawToken));
	}
}
