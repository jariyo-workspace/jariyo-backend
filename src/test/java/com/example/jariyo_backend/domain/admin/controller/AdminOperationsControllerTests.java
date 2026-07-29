package com.example.jariyo_backend.domain.admin.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.async.FailedJobStatus;
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

	@Test
	void listFailedJobsWrapsPayloadWithPage() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		List<FailedJobAdminService.FailedJobSummary> items = List.of(
			new FailedJobAdminService.FailedJobSummary(UUID.randomUUID(),
				com.example.jariyo_backend.common.async.AsyncEventType.SLOT_OFFER_CREATED,
				"RESERVATION", UUID.randomUUID(), FailedJobStatus.FAILED,
				2, new FailedJobAdminService.ErrorSummary("Timeout", "dispatch failed"),
				Instant.parse("2026-07-26T01:00:00Z"), null));
		FailedJobAdminService.FailedJobListResult payload =
			new FailedJobAdminService.FailedJobListResult(items, new ApiResponse.PageBody("next-cursor", true));
		when(failedJobAdminService.list(userId, storeId, null, null, null, null, null, null)).thenReturn(payload);
		AdminOperationsController controller = controller();

		ResponseEntity<ApiResponse<List<FailedJobAdminService.FailedJobSummary>>> response =
			controller.listFailedJobs(jwt(userId), storeId, null, null, null, null, null, null);

		assertEquals(items, response.getBody().data());
		assertEquals("next-cursor", response.getBody().page().cursor());
		assertEquals(true, response.getBody().page().hasNext());
		verify(failedJobAdminService).list(userId, storeId, null, null, null, null, null, null);
	}

	@Test
	void ignoreFailedJobWrapsPayload() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		AdminOperationsController.IgnoreFailedJobRequest request =
			new AdminOperationsController.IgnoreFailedJobRequest("고객에게 전화로 직접 안내 완료");
		FailedJobAdminService.IgnoreFailedJobResult payload =
			new FailedJobAdminService.IgnoreFailedJobResult(jobId, FailedJobStatus.IGNORED, request.reason());
		when(failedJobAdminService.ignore(userId, storeId, jobId,
			new FailedJobAdminService.IgnoreFailedJobCommand(request.reason()))).thenReturn(payload);
		AdminOperationsController controller = controller();

		ResponseEntity<ApiResponse<FailedJobAdminService.IgnoreFailedJobResult>> response =
			controller.ignoreFailedJob(jwt(userId), storeId, jobId, request);

		assertEquals(payload, response.getBody().data());
		verify(failedJobAdminService).ignore(userId, storeId, jobId,
			new FailedJobAdminService.IgnoreFailedJobCommand(request.reason()));
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
