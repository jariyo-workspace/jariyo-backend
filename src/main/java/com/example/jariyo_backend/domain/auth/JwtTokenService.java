package com.example.jariyo_backend.domain.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.config.JwtProperties;
import com.example.jariyo_backend.domain.user.UserAccount;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;
	private final Clock clock;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
		this(jwtEncoder, properties, Clock.systemUTC());
	}

	JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AccessToken issue(UserAccount user) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.audience(List.of(properties.audience()))
			.subject(user.getId().toString())
			.id(UUID.randomUUID().toString())
			.issuedAt(issuedAt)
			.notBefore(issuedAt)
			.expiresAt(expiresAt)
			.claim("token_type", "access")
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, properties.accessTokenTtl().toSeconds());
	}
}
