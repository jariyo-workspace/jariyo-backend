package com.example.jariyo_backend.domain.admin.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.AdminReservationItem;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.AdminWaitlistItem;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.TodayDashboard;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores/{storeId}")
public class AdminOperationController {
	private final AdminOperationQueryService adminOperationQueryService;

	public AdminOperationController(AdminOperationQueryService adminOperationQueryService) {
		this.adminOperationQueryService = adminOperationQueryService;
	}

	@GetMapping("/dashboard/today")
	public ResponseEntity<ApiResponse<TodayDashboard>> getTodayDashboard(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId) {
		return ok(adminOperationQueryService.getTodayDashboard(userId(jwt), storeId));
	}

	@GetMapping("/reservations")
	public ResponseEntity<ApiResponse<List<AdminReservationItem>>> listReservations(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@RequestParam(required = false) UUID staffId, @RequestParam(required = false) UUID serviceId,
		@RequestParam(required = false) ReservationStatus status,
		@RequestParam(required = false) String customerQuery) {
		return ok(adminOperationQueryService.listReservations(userId(jwt), storeId, from, to, staffId, serviceId, status,
			customerQuery));
	}

	@GetMapping("/waitlists")
	public ResponseEntity<ApiResponse<List<AdminWaitlistItem>>> listWaitlists(@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID storeId,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
		@RequestParam(required = false) UUID serviceId, @RequestParam(required = false) UUID staffId,
		@RequestParam(required = false) WaitlistStatus status) {
		return ok(adminOperationQueryService.listWaitlists(userId(jwt), storeId, date, serviceId, staffId, status));
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}
}
