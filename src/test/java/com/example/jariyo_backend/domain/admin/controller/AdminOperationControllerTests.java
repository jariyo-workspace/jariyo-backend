package com.example.jariyo_backend.domain.admin.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.AdminReservationItem;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.AdminWaitlistItem;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.TodayDashboard;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService.TodaySummary;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationControllerTests {
	@Mock AdminOperationQueryService adminOperationQueryService;

	@Test
	void getTodayDashboardWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		TodayDashboard payload = new TodayDashboard(LocalDate.of(2026, 7, 24),
			new TodaySummary(10, 3, 2, 1, 1, 1), List.of(), List.of());
		when(adminOperationQueryService.getTodayDashboard(userId, storeId)).thenReturn(payload);
		AdminOperationController controller = new AdminOperationController(adminOperationQueryService);

		ResponseEntity<ApiResponse<TodayDashboard>> response = controller.getTodayDashboard(jwt(userId), storeId);

		assertEquals(200, response.getStatusCode().value());
		assertEquals(payload, response.getBody().data());
		verify(adminOperationQueryService).getTodayDashboard(userId, storeId);
	}

	@Test
	void listReservationsWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		List<AdminReservationItem> payload = List.of(new AdminReservationItem(UUID.randomUUID(), "고객", "커트", "민수",
			Instant.parse("2026-07-24T01:00:00Z"), Instant.parse("2026-07-24T01:30:00Z"), ReservationStatus.CONFIRMED,
			"NOT_CHECKED_IN", 1));
		when(adminOperationQueryService.listReservations(userId, storeId, null, null, null, null, null, null))
			.thenReturn(payload);
		AdminOperationController controller = new AdminOperationController(adminOperationQueryService);

		ResponseEntity<ApiResponse<List<AdminReservationItem>>> response = controller.listReservations(jwt(userId), storeId,
			null, null, null, null, null, null);

		assertEquals(payload, response.getBody().data());
		verify(adminOperationQueryService).listReservations(userId, storeId, null, null, null, null, null, null);
	}

	@Test
	void listWaitlistsWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		List<AdminWaitlistItem> payload = List.of(new AdminWaitlistItem(UUID.randomUUID(), "고객", "펌", "수진",
			LocalDate.of(2026, 7, 24), LocalTime.of(14, 0), LocalTime.of(16, 0), WaitlistStatus.WAITING, 3, false, null));
		when(adminOperationQueryService.listWaitlists(userId, storeId, null, null, null, null)).thenReturn(payload);
		AdminOperationController controller = new AdminOperationController(adminOperationQueryService);

		ResponseEntity<ApiResponse<List<AdminWaitlistItem>>> response = controller.listWaitlists(jwt(userId), storeId,
			null, null, null, null);

		assertEquals(payload, response.getBody().data());
		verify(adminOperationQueryService).listWaitlists(userId, storeId, null, null, null, null);
	}

	private Jwt jwt(UUID userId) {
		return Jwt.withTokenValue("token")
			.header("alg", "none")
			.claim("sub", userId.toString())
			.build();
	}
}
