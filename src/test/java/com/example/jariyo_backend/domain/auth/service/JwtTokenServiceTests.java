package com.example.jariyo_backend.domain.auth.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.config.JwtConfig;
import com.example.jariyo_backend.common.config.JwtProperties;
import com.example.jariyo_backend.domain.auth.dto.AccessToken;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTests {
	@Test
	void issuesRs256TokenWithRequiredClaimsOnly() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		JwtProperties properties = new JwtProperties("https://api.jariyo.local", "jariyo-web",
			Duration.ofMinutes(15), "unused", "unused");
		JwtConfig config = new JwtConfig();
		JwtEncoder encoder = config.jwtEncoder(publicKey, privateKey);
		JwtDecoder decoder = config.jwtDecoder(publicKey, properties);
		UserAccount user = new UserAccount("user@example.com", "+821012345678", "hash");
		UUID userId = UUID.randomUUID();
		ReflectionTestUtils.setField(user, "id", userId);

		AccessToken accessToken = new JwtTokenService(encoder, properties).issue(user);
		Jwt jwt = decoder.decode(accessToken.value());

		assertEquals(userId.toString(), jwt.getSubject());
		assertEquals("access", jwt.getClaimAsString("token_type"));
		assertEquals(900, accessToken.expiresIn());
		assertEquals("https://api.jariyo.local", jwt.getIssuer().toString());
		assertEquals("jariyo-web", jwt.getAudience().get(0));

		Instant now = Instant.now();
		JwtClaimsSet wrongTypeClaims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.audience(List.of(properties.audience()))
			.subject(userId.toString())
			.issuedAt(now)
			.notBefore(now)
			.expiresAt(now.plusSeconds(900))
			.claim("token_type", "refresh")
			.build();
		String wrongTypeToken = encoder.encode(JwtEncoderParameters.from(
			JwsHeader.with(SignatureAlgorithm.RS256).build(), wrongTypeClaims)).getTokenValue();
		assertThrows(JwtException.class, () -> decoder.decode(wrongTypeToken));
	}
}
