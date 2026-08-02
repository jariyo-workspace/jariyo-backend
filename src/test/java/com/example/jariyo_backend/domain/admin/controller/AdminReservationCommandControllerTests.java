package com.example.jariyo_backend.domain.admin.controller;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.admin.service.ServiceSessionCommandService;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.service.ReservationAdminService;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
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
class AdminReservationCommandControllerTests {
	@Mock ReservationAdminService reservationAdminService;
	@Mock ServiceSessionCommandService serviceSessionCommandService;
	@Mock Jwt jwt;

	@Test
	void checkInDelegatesToService() {
		AdminReservationCommandController controller = new AdminReservationCommandController(reservationAdminService,
			serviceSessionCommandService);
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		ReservationAdminService.ReservationCheckInResult payload =
			new ReservationAdminService.ReservationCheckInResult(reservationId, ReservationStatus.CHECKED_IN,
				OffsetDateTime.parse("2026-08-02T14:55:00+09:00"));
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(reservationAdminService.checkIn(userId, storeId, reservationId, "key")).thenReturn(payload);

		ResponseEntity<ApiResponse<ReservationAdminService.ReservationCheckInResult>> response =
			controller.checkIn(jwt, storeId, reservationId, "key");

		assertEquals(ReservationStatus.CHECKED_IN, response.getBody().data().status());
		verify(reservationAdminService).checkIn(userId, storeId, reservationId, "key");
	}

	@Test
	void completeServiceDelegatesToSharedService() {
		AdminReservationCommandController controller = new AdminReservationCommandController(reservationAdminService,
			serviceSessionCommandService);
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		ServiceSessionCommandService.CompleteServiceResult payload =
			new ServiceSessionCommandService.CompleteServiceResult(sessionId, ServiceSessionStatus.COMPLETED,
				OffsetDateTime.parse("2026-08-02T15:10:00+09:00"),
				OffsetDateTime.parse("2026-08-02T15:40:00+09:00"), 30);
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(serviceSessionCommandService.completeService(userId, storeId, sessionId, "key",
			new ServiceSessionCommandService.CompleteServiceCommand("정상 완료"))).thenReturn(payload);

		ResponseEntity<ApiResponse<ServiceSessionCommandService.CompleteServiceResult>> response =
			controller.completeService(jwt, storeId, sessionId, "key",
				new AdminReservationCommandController.CompleteServiceRequest("정상 완료"));

		assertEquals(ServiceSessionStatus.COMPLETED, response.getBody().data().status());
		verify(serviceSessionCommandService).completeService(userId, storeId, sessionId, "key",
			new ServiceSessionCommandService.CompleteServiceCommand("정상 완료"));
	}
}
