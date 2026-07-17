package com.example.jariyo_backend.domain.auth.support;

import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.domain.auth.dto.SignInRequest;
import com.example.jariyo_backend.domain.auth.dto.SignUpRequest;
import com.example.jariyo_backend.domain.auth.service.PasswordPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialPolicyTests {
	@Test
	void normalizesEmailAndKoreanMobileNumber() {
		assertEquals("user@example.com", EmailNormalizer.normalize(" User@Example.COM "));
		assertEquals("+821012345678", PhoneNumberNormalizer.normalize("010-1234-5678"));
		assertEquals("010-****-5678", PhoneNumberNormalizer.mask("+821012345678"));
	}

	@Test
	void normalizesEmailBeforeBeanValidationAndLimitsSignInPassword() {
		SignUpRequest signUp = new SignUpRequest(" User@Example.COM ", "long-enough-password",
			"자리요", "01012345678", new SignUpRequest.Agreements(true, true, false));
		SignInRequest signIn = new SignInRequest(" User@Example.COM ", "x".repeat(65));
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			assertEquals("user@example.com", signUp.email());
			assertTrue(validator.validate(signUp).isEmpty());
			assertEquals(1, validator.validate(signIn).size());
		}
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
