package com.example.jariyo_backend.common.config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyParser {
	private RsaKeyParser() {
	}

	public static RSAPublicKey parsePublicKey(String pem) {
		try {
			byte[] encoded = decode(pem, "PUBLIC KEY");
			return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
		} catch (Exception exception) {
			throw new IllegalStateException("JWT_PUBLIC_KEY를 읽을 수 없습니다.", exception);
		}
	}

	public static RSAPrivateKey parsePrivateKey(String pem) {
		try {
			byte[] encoded = decode(pem, "PRIVATE KEY");
			return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
		} catch (Exception exception) {
			throw new IllegalStateException("JWT_PRIVATE_KEY를 읽을 수 없습니다.", exception);
		}
	}

	private static byte[] decode(String pem, String type) {
		if (pem == null || pem.isBlank()) {
			throw new IllegalArgumentException("RSA 키가 비어 있습니다.");
		}
		String normalized = pem.replace("\\n", "\n")
			.replace("-----BEGIN " + type + "-----", "")
			.replace("-----END " + type + "-----", "")
			.replaceAll("\\s", "");
		return Base64.getDecoder().decode(normalized);
	}
}
