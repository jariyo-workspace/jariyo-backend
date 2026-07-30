package com.example.jariyo_backend.domain.reservation.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.service.ReservationService;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ConfirmReservationResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CreateHoldCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CreateReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.NamedRef;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationCreateResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationHoldResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationListResult;
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
class ReservationControllerTests {
	@Mock ReservationService reservationService;
	@Mock Jwt jwt;

	@Test
	void createReturnsCreatedReservation() {
		ReservationController controller = new ReservationController(reservationService);
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		OffsetDateTime startAt = OffsetDateTime.parse("2026-07-28T14:00:00+09:00");
		ReservationController.CreateReservationRequest request = new ReservationController.CreateReservationRequest(
			storeId, serviceId, staffId, startAt, 1, "요청");
		CreateReservationCommand command = new CreateReservationCommand(storeId, serviceId, staffId, startAt, 1, "요청");
		ReservationCreateResult result = new ReservationCreateResult(reservationId,
			new NamedRef(storeId, "매장"), new NamedRef(serviceId, "서비스"), new NamedRef(staffId, "직원"),
			ReservationSource.CUSTOMER_BOOKING, ReservationStatus.CONFIRMED, startAt, startAt.plusMinutes(30),
			startAt.plusMinutes(40), OffsetDateTime.parse("2026-07-24T10:00:00+09:00"));
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(reservationService.create(userId, "create-key", command)).thenReturn(result);

		ResponseEntity<ApiResponse<ReservationCreateResult>> response = controller.create(jwt, "create-key", request);

		assertEquals(201, response.getStatusCode().value());
		assertEquals(reservationId, response.getBody().data().id());
		verify(reservationService).create(userId, "create-key", command);
	}

	@Test
	void cancelPassesOnlyFreeTextReason() {
		ReservationController controller = new ReservationController(reservationService);
		UUID userId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		ReservationController.CancelReservationRequest request =
			new ReservationController.CancelReservationRequest("개인 일정");
		CancelReservationResult result = new CancelReservationResult(reservationId, ReservationStatus.CANCELLED,
			OffsetDateTime.parse("2026-07-24T10:00:00+09:00"), "CUSTOMER");
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(reservationService.cancelMine(userId, reservationId, "cancel-key",
			new CancelReservationCommand("개인 일정"))).thenReturn(result);

		ResponseEntity<ApiResponse<CancelReservationResult>> response =
			controller.cancelMine(jwt, reservationId, "cancel-key", request);

		assertEquals(200, response.getStatusCode().value());
		assertEquals(ReservationStatus.CANCELLED, response.getBody().data().status());
		verify(reservationService).cancelMine(userId, reservationId, "cancel-key",
			new CancelReservationCommand("개인 일정"));
	}

	@Test
	void createHoldReturnsCreatedHold() {
		ReservationController controller = new ReservationController(reservationService);
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID staffId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		OffsetDateTime startAt = OffsetDateTime.parse("2026-07-28T14:00:00+09:00");
		OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-28T10:10:00+09:00");
		ReservationController.CreateHoldRequest request =
			new ReservationController.CreateHoldRequest(storeId, serviceId, staffId, startAt, 1);
		CreateHoldCommand command = new CreateHoldCommand(storeId, serviceId, staffId, startAt, 1);
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(reservationService.createHold(userId, "hold-key", command))
			.thenReturn(new ReservationHoldResult(reservationId, ReservationStatus.HELD, expiresAt));

		ResponseEntity<ApiResponse<ReservationHoldResult>> response =
			controller.createHold(jwt, "hold-key", request);

		assertEquals(201, response.getStatusCode().value());
		assertEquals(reservationId, response.getBody().data().reservationId());
		assertEquals(ReservationStatus.HELD, response.getBody().data().status());
		verify(reservationService).createHold(userId, "hold-key", command);
	}

	@Test
	void confirmReturnsConfirmedReservation() {
		ReservationController controller = new ReservationController(reservationService);
		UUID userId = UUID.randomUUID();
		UUID reservationId = UUID.randomUUID();
		OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-07-28T10:03:00+09:00");
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(reservationService.confirm(userId, reservationId, "confirm-key"))
			.thenReturn(new ConfirmReservationResult(reservationId, ReservationStatus.CONFIRMED, confirmedAt));

		ResponseEntity<ApiResponse<ConfirmReservationResult>> response =
			controller.confirm(jwt, reservationId, "confirm-key");

		assertEquals(200, response.getStatusCode().value());
		assertEquals(ReservationStatus.CONFIRMED, response.getBody().data().status());
		verify(reservationService).confirm(userId, reservationId, "confirm-key");
	}

	@Test
	void listMineWrapsItemsWithPage() {
		ReservationController controller = new ReservationController(reservationService);
		UUID userId = UUID.randomUUID();
		ReservationListResult result =
			new ReservationListResult(List.of(), new ApiResponse.PageBody("next-cursor", true));
		when(jwt.getSubject()).thenReturn(userId.toString());
		when(reservationService.listMine(userId, ReservationStatus.CONFIRMED, null, null, "cursor", 10))
			.thenReturn(result);

		var response = controller.listMine(jwt, ReservationStatus.CONFIRMED, null, null, "cursor", 10);

		assertEquals("next-cursor", response.getBody().page().cursor());
		assertEquals(true, response.getBody().page().hasNext());
		verify(reservationService).listMine(userId, ReservationStatus.CONFIRMED, null, null, "cursor", 10);
	}
}
