package com.example.jariyo_backend.common.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenProperties.class})
public class JwtConfig {
	static final String EXPIRED_TOKEN_ERROR_CODE = "access_token_expired";
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	@Bean
	public RSAPublicKey jwtPublicKey(JwtProperties properties) {
		return RsaKeyParser.parsePublicKey(properties.publicKey());
	}

	@Bean
	public RSAPrivateKey jwtPrivateKey(JwtProperties properties) {
		return RsaKeyParser.parsePrivateKey(properties.privateKey());
	}

	@Bean
	public JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
		RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	@Bean
	public JwtDecoder jwtDecoder(RSAPublicKey publicKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
		OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuer());
		OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
			"aud", audience -> audience != null && audience.contains(properties.audience()));
		OAuth2TokenValidator<Jwt> tokenTypeValidator = new JwtClaimValidator<String>(
			"token_type", "access"::equals);
		OAuth2TokenValidator<Jwt> expirationMarker = JwtConfig::validateExpiration;
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			issuerValidator, audienceValidator, tokenTypeValidator, expirationMarker));
		return decoder;
	}

	private static OAuth2TokenValidatorResult validateExpiration(Jwt jwt) {
		Instant expiresAt = jwt.getExpiresAt();
		if (expiresAt != null && expiresAt.isAfter(Instant.now().minus(CLOCK_SKEW))) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(
			EXPIRED_TOKEN_ERROR_CODE, "Access Token이 만료되었습니다.", null));
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
		return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
	}
}
