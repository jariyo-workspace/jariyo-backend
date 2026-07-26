package com.example.jariyo_backend.domain.user.controller;

import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.user.dto.MeResponse;
import com.example.jariyo_backend.domain.user.service.MeService;
import com.example.jariyo_backend.domain.user.support.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {
	private final MeService meService;

	public MeController(MeService meService) {
		this.meService = meService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<MeResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(ApiResponse.success(meService.get(currentUser(jwt))));
	}

	private UUID currentUser(Jwt jwt) {
		return AuthenticatedUser.from(jwt).id();
	}
}
