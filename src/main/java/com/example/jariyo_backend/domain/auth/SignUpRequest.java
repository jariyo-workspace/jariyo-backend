package com.example.jariyo_backend.domain.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
	@NotBlank @Email @Size(max = 320) String email,
	@NotBlank String password,
	@NotBlank @Size(max = 100) String displayName,
	@NotBlank @Size(max = 32) String phoneNumber,
	@NotNull @Valid Agreements agreements
) {
	public record Agreements(boolean terms, boolean privacy, boolean marketing) {
	}
}
