package com.example.jariyo_backend.domain.admin.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.admin.service.AdminAnalyticsService;
import com.example.jariyo_backend.domain.admin.service.FailedJobAdminService;
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
class AdminOperationsControllerTests {
	@Mock AdminAnalyticsService analyticsService;
	@Mock FailedJobAdminService failedJobAdminService;

	@Test
	void getDailyReservationAnalyticsWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		List<AdminAnalyticsService.DailyReservationAnalytics> payload = List.of(
			new AdminAnalyticsService.DailyReservationAnalytics(LocalDate.of(2026, 7, 26), 3, 1, 0, 0, 1, 1, 0));
		when(analyticsService.getDailyReservationAnalytics(userId, storeId, null, null)).thenReturn(payload);
		AdminOperationsController controller = controller();

		ResponseEntity<ApiResponse<List<AdminAnalyticsService.DailyReservationAnalytics>>> response =
			controller.getDailyReservationAnalytics(jwt(userId), storeId, null, null);

		assertEquals(payload, response.getBody().data());
		verify(analyticsService).getDailyReservationAnalytics(userId, storeId, null, null);
	}

	@Test
	void getStaffAnalyticsWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		List<AdminAnalyticsService.StaffAnalytics> payload = List.of(
			new AdminAnalyticsService.StaffAnalytics(UUID.randomUUID(), "민지", 5, 3, 1, 1, 2, 42));
		when(analyticsService.getStaffAnalytics(userId, storeId, null, null)).thenReturn(payload);
		AdminOperationsController controller = controller();

		ResponseEntity<ApiResponse<List<AdminAnalyticsService.StaffAnalytics>>> response =
			controller.getStaffAnalytics(jwt(userId), storeId, null, null);

		assertEquals(payload, response.getBody().data());
		verify(analyticsService).getStaffAnalytics(userId, storeId, null, null);
	}

	@Test
	void getServiceDurationAnalyticsWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		List<AdminAnalyticsService.ServiceDurationAnalytics> payload = List.of(
			new AdminAnalyticsService.ServiceDurationAnalytics(UUID.randomUUID(), "커트", 4, 30, 35, 5));
		when(analyticsService.getServiceDurationAnalytics(userId, storeId, null, null)).thenReturn(payload);
		AdminOperationsController controller = controller();

		ResponseEntity<ApiResponse<List<AdminAnalyticsService.ServiceDurationAnalytics>>> response =
			controller.getServiceDurationAnalytics(jwt(userId), storeId, null, null);

		assertEquals(payload, response.getBody().data());
		verify(analyticsService).getServiceDurationAnalytics(userId, storeId, null, null);
	}

	private AdminOperationsController controller() {
		return new AdminOperationsController(analyticsService, failedJobAdminService);
	}

	private Jwt jwt(UUID userId) {
		return Jwt.withTokenValue("token")
			.header("alg", "none")
			.claim("sub", userId.toString())
			.build();
	}
}
