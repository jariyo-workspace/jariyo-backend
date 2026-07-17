package com.example.jariyo_backend.domain.auth.support;

import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;

public final class PhoneNumberNormalizer {
	private PhoneNumberNormalizer() {
	}

	public static String normalize(String phoneNumber) {
		String compact = phoneNumber.replaceAll("[\\s()-]", "");
		if (compact.matches("010\\d{8}")) {
			return "+82" + compact.substring(1);
		}
		if (compact.matches("\\+8210\\d{8}")) {
			return compact;
		}
		throw new BusinessException(ErrorCode.BAD_REQUEST, "휴대전화 번호 형식이 올바르지 않습니다.");
	}

	public static String mask(String normalizedPhoneNumber) {
		if (!normalizedPhoneNumber.matches("\\+8210\\d{8}")) {
			return "****";
		}
		String local = "0" + normalizedPhoneNumber.substring(3);
		return local.substring(0, 3) + "-****-" + local.substring(7);
	}
}
