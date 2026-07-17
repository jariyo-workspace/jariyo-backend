package com.example.jariyo_backend.domain.auth.dto;

import com.example.jariyo_backend.domain.auth.support.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignInRequest(
	@NotBlank @Email @Size(max = 320) String email,
	@NotBlank @Size(max = 64) String password
) {
	public SignInRequest {
		if (email != null) {
			email = EmailNormalizer.normalize(email);
		}
	}
}
