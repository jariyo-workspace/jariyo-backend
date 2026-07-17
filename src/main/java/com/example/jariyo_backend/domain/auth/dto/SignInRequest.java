package com.example.jariyo_backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignInRequest(
	@NotBlank @Email @Size(max = 320) String email,
	@NotBlank String password
) {
}
