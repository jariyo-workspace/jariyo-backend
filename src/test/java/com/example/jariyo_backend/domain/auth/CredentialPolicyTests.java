package com.example.jariyo_backend.domain.auth;

import com.example.jariyo_backend.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialPolicyTests {
	@Test
	void normalizesEmailAndKoreanMobileNumber() {
		assertEquals("user@example.com", EmailNormalizer.normalize(" User@Example.COM "));
		assertEquals("+821012345678", PhoneNumberNormalizer.normalize("010-1234-5678"));
		assertEquals("010-****-5678", PhoneNumberNormalizer.mask("+821012345678"));
	}

	@Test
	void rejectsInvalidPhoneNumber() {
		assertThrows(BusinessException.class, () -> PhoneNumberNormalizer.normalize("02-1234-5678"));
	}

	@Test
	void requiresPasswordBetweenFifteenAndSixtyFourCharacters() {
		PasswordPolicy policy = new PasswordPolicy();
		policy.validate("충분히 긴 비밀번호입니다 1234");
		assertThrows(BusinessException.class, () -> policy.validate("too-short"));
	}
}
