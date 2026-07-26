package com.example.jariyo_backend.domain.admin.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.idempotency.IdempotencyKey;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.async.FailedJobStatus;
import com.example.jariyo_backend.domain.admin.service.AdminAnalyticsService;
import com.example.jariyo_backend.domain.admin.service.FailedJobAdminService;
import com.example.jariyo_backend.domain.user.support.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores/{storeId}")
public class AdminOperationsController {
	private final AdminAnalyticsService analyticsService;
	private final FailedJobAdminService failedJobAdminService;

	public AdminOperationsController(AdminAnalyticsService analyticsService, FailedJobAdminService failedJobAdminService) {
		this.analyticsService = analyticsService;
		this.failedJobAdminService = failedJobAdminService;
	}

	@GetMapping("/analytics/summary")
	public ResponseEntity<ApiResponse<AdminAnalyticsService.AnalyticsSummary>> getSummary(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ok(analyticsService.getSummary(userId(jwt), storeId, from, to));
	}

	@GetMapping("/failed-jobs")
	public ResponseEntity<ApiResponse<List<FailedJobAdminService.FailedJobSummary>>> listFailedJobs(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId,
		@RequestParam(required = false) AsyncEventType type,
		@RequestParam(required = false) FailedJobStatus status,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
		@RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
		return ok(failedJobAdminService.list(userId(jwt), storeId, type, status, from, to, limit));
	}

	@GetMapping("/failed-jobs/{jobId}")
	public ResponseEntity<ApiResponse<FailedJobAdminService.FailedJobDetail>> getFailedJob(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID jobId) {
		return ok(failedJobAdminService.get(userId(jwt), storeId, jobId));
	}

	@PostMapping("/failed-jobs/{jobId}/retry")
	public ResponseEntity<ApiResponse<FailedJobAdminService.RetryFailedJobResult>> retryFailedJob(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId, @PathVariable UUID jobId,
		@RequestHeader(IdempotencyKey.HEADER_NAME) String key) {
		return ok(failedJobAdminService.retry(userId(jwt), storeId, jobId, key));
	}

	private UUID userId(Jwt jwt) {
		return AuthenticatedUser.from(jwt).id();
	}

	private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}
}
