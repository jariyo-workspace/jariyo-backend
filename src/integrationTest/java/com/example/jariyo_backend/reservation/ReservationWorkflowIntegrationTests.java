package com.example.jariyo_backend.reservation;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.repository.ReservationStatusHistoryRepository;
import com.example.jariyo_backend.domain.reservation.service.ReservationService;
import com.example.jariyo_backend.domain.reservation.service.ReservationHoldExpirationService;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CancelReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CreateHoldCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.CreateReservationCommand;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationCreateResult;
import com.example.jariyo_backend.domain.reservation.service.ReservationService.ReservationHistoryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class ReservationWorkflowIntegrationTests {
	private static final UUID STORE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
	private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000901");
	private static final UUID STAFF_A_ID = UUID.fromString("00000000-0000-7000-8000-000000000902");
	private static final UUID STAFF_B_ID = UUID.fromString("00000000-0000-7000-8000-000000000903");
	private static final UUID STAFF_A_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000904");
	private static final UUID STAFF_B_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000905");
	private static final UUID CUSTOMER_A_ID = UUID.fromString("00000000-0000-7000-8000-000000000906");
	private static final UUID CUSTOMER_B_ID = UUID.fromString("00000000-0000-7000-8000-000000000907");
	private static final UUID CUSTOMER_A_PROFILE_ID = UUID.fromString("00000000-0000-7000-8000-000000000908");
	private static final UUID CUSTOMER_B_PROFILE_ID = UUID.fromString("00000000-0000-7000-8000-000000000909");
	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired ReservationService reservationService;
	@Autowired ReservationRepository reservationRepository;
	@Autowired ReservationStatusHistoryRepository historyRepository;
	@Autowired ReservationHoldExpirationService holdExpirationService;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("security.jwt.public-key", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
		registry.add("security.jwt.private-key", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
		registry.add("security.refresh-token.cookie-secure", () -> false);
	}

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM idempotency_request");
		jdbcTemplate.update("DELETE FROM reservation_status_history");
		jdbcTemplate.update("DELETE FROM reservation");
		jdbcTemplate.update("DELETE FROM staff_schedule WHERE store_member_id in (?, ?)", STAFF_A_ID, STAFF_B_ID);
		jdbcTemplate.update("DELETE FROM staff_service WHERE service_id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM business_hour WHERE id = '00000000-0000-7000-8000-000000000910'");
		jdbcTemplate.update("DELETE FROM service WHERE id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM store_member WHERE id in (?, ?)", STAFF_A_ID, STAFF_B_ID);
		jdbcTemplate.update("DELETE FROM customer_profile WHERE id in (?, ?)", CUSTOMER_A_PROFILE_ID, CUSTOMER_B_PROFILE_ID);
		jdbcTemplate.update("DELETE FROM users WHERE id in (?, ?, ?, ?)",
			CUSTOMER_A_ID, CUSTOMER_B_ID, STAFF_A_USER_ID, STAFF_B_USER_ID);

		insertUser(CUSTOMER_A_ID, "reservation-a@example.com", "+821022220001");
		insertUser(CUSTOMER_B_ID, "reservation-b@example.com", "+821022220002");
		insertUser(STAFF_A_USER_ID, "reservation-staff-a@example.com", "+821022220003");
		insertUser(STAFF_B_USER_ID, "reservation-staff-b@example.com", "+821022220004");
		insertCustomer(CUSTOMER_A_PROFILE_ID, CUSTOMER_A_ID, "예약 고객 A");
		insertCustomer(CUSTOMER_B_PROFILE_ID, CUSTOMER_B_ID, "예약 고객 B");
		insertStaff(STAFF_A_ID, STAFF_A_USER_ID, "직원 A");
		insertStaff(STAFF_B_ID, STAFF_B_USER_ID, "직원 B");
		jdbcTemplate.update("""
			INSERT INTO service (id, store_id, name, duration_minutes, cleanup_minutes, capacity, status, created_at, updated_at)
			VALUES (?, ?, '예약 테스트 서비스', 30, 10, 2, 'ACTIVE', now(), now())
			""", SERVICE_ID, STORE_ID);
		jdbcTemplate.update("""
			INSERT INTO staff_service (id, store_member_id, service_id, active)
			VALUES
				('00000000-0000-7000-8000-000000000911', ?, ?, true),
				('00000000-0000-7000-8000-000000000912', ?, ?, true)
			""", STAFF_A_ID, SERVICE_ID, STAFF_B_ID, SERVICE_ID);

		LocalDate targetDate = targetDate();
		jdbcTemplate.update("""
			INSERT INTO business_hour (id, store_id, day_of_week, open_time, close_time, is_closed)
			VALUES ('00000000-0000-7000-8000-000000000910', ?, ?, '09:00', '18:00', false)
			""", STORE_ID, targetDate.getDayOfWeek().name());
		jdbcTemplate.update("""
			INSERT INTO staff_schedule
				(id, store_member_id, day_of_week, start_time, end_time, valid_from, valid_until, created_at)
			VALUES
				('00000000-0000-7000-8000-000000000913', ?, ?, '09:00', '18:00', ?, null, now()),
				('00000000-0000-7000-8000-000000000914', ?, ?, '09:00', '18:00', ?, null, now())
			""", STAFF_A_ID, targetDate.getDayOfWeek().name(), targetDate.minusDays(1),
			STAFF_B_ID, targetDate.getDayOfWeek().name(), targetDate.minusDays(1));
	}

	@Test
	void createsListsReadsCancelsAndReturnsHistoryIdempotently() {
		CreateReservationCommand command = command(STAFF_A_ID, LocalTime.of(14, 0));

		ReservationCreateResult created = reservationService.create(CUSTOMER_A_ID, "reservation-create", command);
		ReservationCreateResult retried = reservationService.create(CUSTOMER_A_ID, "reservation-create", command);

		assertEquals(created.id(), retried.id());
		assertEquals(ReservationStatus.CONFIRMED, created.status());
		assertEquals(1, reservationRepository.count());
		List<ReservationHistoryResult> createdHistory = reservationService.historyMine(CUSTOMER_A_ID, created.id());
		assertEquals(List.of("CREATED"), createdHistory.stream().map(ReservationHistoryResult::reasonCode).toList());
		assertEquals(created.id(), reservationService.getMine(CUSTOMER_A_ID, created.id()).id());
		assertFalse(reservationService.getMine(CUSTOMER_A_ID, created.id()).checkInAvailable());
		assertTrue(reservationService.getMine(CUSTOMER_A_ID, created.id()).canCancel());
		assertEquals(1, reservationService.listMine(CUSTOMER_A_ID, ReservationStatus.CONFIRMED,
			targetDate(), targetDate()).size());

		var cancelled = reservationService.cancelMine(CUSTOMER_A_ID, created.id(), "reservation-cancel",
			new CancelReservationCommand("개인 일정"));
		var retriedCancellation = reservationService.cancelMine(CUSTOMER_A_ID, created.id(), "reservation-cancel",
			new CancelReservationCommand("개인 일정"));

		assertEquals(cancelled.cancelledAt(), retriedCancellation.cancelledAt());
		assertEquals(ReservationStatus.CANCELLED,
			reservationRepository.findById(created.id()).orElseThrow().getStatus());
		assertEquals(List.of("CREATED", "CUSTOMER_CANCELLED"),
			reservationService.historyMine(CUSTOMER_A_ID, created.id()).stream()
				.map(ReservationHistoryResult::reasonCode).toList());
		assertEquals(2, historyRepository.findAllByReservationIdOrderByOccurredAtAscIdAsc(created.id()).size());
	}

	@Test
	void rejectsDifferentRequestWithSameIdempotencyKeyAndNonOwnerAccess() {
		ReservationCreateResult created = reservationService.create(CUSTOMER_A_ID, "same-key",
			command(STAFF_A_ID, LocalTime.of(14, 0)));

		BusinessException reused = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "same-key", command(STAFF_A_ID, LocalTime.of(15, 0))));
		BusinessException notOwned = assertThrows(BusinessException.class,
			() -> reservationService.getMine(CUSTOMER_B_ID, created.id()));

		assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST, reused.getErrorCode());
		assertEquals(ErrorCode.RESERVATION_NOT_OWNED_BY_USER, notOwned.getErrorCode());
	}

	@Test
	void rejectsCustomerOverlapEvenWithDifferentStaff() {
		reservationService.create(CUSTOMER_A_ID, "staff-a", command(STAFF_A_ID, LocalTime.of(14, 0)));

		BusinessException exception = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "staff-b", command(STAFF_B_ID, LocalTime.of(14, 0))));

		assertEquals(ErrorCode.CUSTOMER_HAS_OVERLAPPING_RESERVATION, exception.getErrorCode());
	}

	@Test
	void filtersAndSortsReservationList() {
		ReservationCreateResult earlier = reservationService.create(CUSTOMER_A_ID, "list-earlier",
			command(STAFF_A_ID, LocalTime.of(14, 0)));
		ReservationCreateResult later = reservationService.create(CUSTOMER_A_ID, "list-later",
			command(STAFF_A_ID, LocalTime.of(15, 0)));

		var reservations = reservationService.listMine(CUSTOMER_A_ID, ReservationStatus.CONFIRMED,
			targetDate(), targetDate());

		assertEquals(List.of(later.id(), earlier.id()), reservations.stream().map(value -> value.id()).toList());
		assertTrue(reservationService.listMine(CUSTOMER_A_ID, null,
			targetDate().plusDays(1), targetDate().plusDays(1)).isEmpty());
	}

	@Test
	void rejectsCancellationAfterDeadlineAndDuplicateCancellation() {
		ReservationCreateResult afterDeadline = reservationService.create(CUSTOMER_A_ID, "deadline",
			command(STAFF_A_ID, LocalTime.of(14, 0)));
		jdbcTemplate.update("""
			UPDATE reservation
			SET start_at = now() + interval '12 hours',
				service_end_at = now() + interval '12 hours 30 minutes',
				occupied_until = now() + interval '12 hours 40 minutes'
			WHERE id = ?
			""", afterDeadline.id());

		BusinessException deadline = assertThrows(BusinessException.class, () -> reservationService.cancelMine(
			CUSTOMER_A_ID, afterDeadline.id(), "deadline-cancel", new CancelReservationCommand("늦은 취소")));

		assertEquals(ErrorCode.RESERVATION_CANCELLATION_DEADLINE_PASSED, deadline.getErrorCode());

		ReservationCreateResult cancellable = reservationService.create(CUSTOMER_A_ID, "duplicate",
			command(STAFF_A_ID, LocalTime.of(16, 0)));
		reservationService.cancelMine(CUSTOMER_A_ID, cancellable.id(), "first-cancel",
			new CancelReservationCommand("첫 취소"));
		BusinessException duplicate = assertThrows(BusinessException.class, () -> reservationService.cancelMine(
			CUSTOMER_A_ID, cancellable.id(), "second-cancel", new CancelReservationCommand("두 번째 취소")));

		assertEquals(ErrorCode.RESERVATION_ALREADY_CANCELLED, duplicate.getErrorCode());
	}

	@Test
	void validatesPartySizeAndBookingWindow() {
		CreateReservationCommand valid = command(STAFF_A_ID, LocalTime.of(14, 0));
		BusinessException partySize = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "invalid-party", new CreateReservationCommand(valid.storeId(), valid.serviceId(),
				valid.staffId(), valid.startAt(), 3, null)));
		BusinessException tooClose = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "too-close", new CreateReservationCommand(STORE_ID, SERVICE_ID, STAFF_A_ID,
				ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(30).toOffsetDateTime(), 1, null)));
		BusinessException outsideWindow = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "outside-window", new CreateReservationCommand(STORE_ID, SERVICE_ID, STAFF_A_ID,
				ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(31).withHour(14).withMinute(0).withSecond(0)
					.withNano(0).toOffsetDateTime(), 1, null)));

		assertEquals(ErrorCode.INVALID_PARTY_SIZE, partySize.getErrorCode());
		assertEquals(ErrorCode.RESERVATION_TOO_CLOSE_TO_START, tooClose.getErrorCode());
		assertEquals(ErrorCode.RESERVATION_OUTSIDE_BOOKING_WINDOW, outsideWindow.getErrorCode());
	}

	@Test
	void validatesActiveStoreServiceAndStaff() {
		jdbcTemplate.update("UPDATE store SET status = 'TEMPORARILY_CLOSED' WHERE id = ?", STORE_ID);
		BusinessException store = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "inactive-store", command(STAFF_A_ID, LocalTime.of(14, 0))));
		jdbcTemplate.update("UPDATE store SET status = 'ACTIVE' WHERE id = ?", STORE_ID);

		jdbcTemplate.update("UPDATE service SET status = 'INACTIVE' WHERE id = ?", SERVICE_ID);
		BusinessException service = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "inactive-service", command(STAFF_A_ID, LocalTime.of(14, 0))));
		jdbcTemplate.update("UPDATE service SET status = 'ACTIVE' WHERE id = ?", SERVICE_ID);

		jdbcTemplate.update("UPDATE store_member SET booking_enabled = false WHERE id = ?", STAFF_A_ID);
		BusinessException staff = assertThrows(BusinessException.class, () -> reservationService.create(
			CUSTOMER_A_ID, "inactive-staff", command(STAFF_A_ID, LocalTime.of(14, 0))));

		assertEquals(ErrorCode.STORE_NOT_ACTIVE, store.getErrorCode());
		assertEquals(ErrorCode.SERVICE_NOT_ACTIVE, service.getErrorCode());
		assertEquals(ErrorCode.STAFF_NOT_AVAILABLE, staff.getErrorCode());
	}

	@Test
	void allowsOnlyOneConcurrentReservationForSameStaffSlot() {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<ErrorCode> first = concurrentCreate(CUSTOMER_A_ID, "concurrent-a", ready, start);
		CompletableFuture<ErrorCode> second = concurrentCreate(CUSTOMER_B_ID, "concurrent-b", ready, start);
		try {
			ready.await();
			start.countDown();
			ErrorCode firstResult = first.join();
			ErrorCode secondResult = second.join();

			assertEquals(1, (firstResult == null ? 1 : 0) + (secondResult == null ? 1 : 0));
			assertEquals(1, (firstResult == ErrorCode.RESERVATION_SLOT_ALREADY_TAKEN ? 1 : 0)
				+ (secondResult == ErrorCode.RESERVATION_SLOT_ALREADY_TAKEN ? 1 : 0));
			assertEquals(1, reservationRepository.count());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	@Test
	void createsConfirmsAndRetriesHoldIdempotently() {
		CreateHoldCommand command = holdCommand(STAFF_A_ID, LocalTime.of(14, 0));

		var held = reservationService.createHold(CUSTOMER_A_ID, "hold-create", command);
		var retriedHold = reservationService.createHold(CUSTOMER_A_ID, "hold-create", command);

		assertEquals(held.reservationId(), retriedHold.reservationId());
		assertEquals(ReservationStatus.HELD, held.status());
		assertEquals(1, reservationRepository.count());

		BusinessException conflict = assertThrows(BusinessException.class, () -> reservationService.createHold(
			CUSTOMER_B_ID, "hold-conflict", holdCommand(STAFF_A_ID, LocalTime.of(14, 0))));
		assertEquals(ErrorCode.RESERVATION_SLOT_ALREADY_TAKEN, conflict.getErrorCode());

		var confirmed = reservationService.confirm(CUSTOMER_A_ID, held.reservationId(), "hold-confirm");
		var retriedConfirmation = reservationService.confirm(CUSTOMER_A_ID, held.reservationId(), "hold-confirm");

		assertEquals(confirmed.confirmedAt(), retriedConfirmation.confirmedAt());
		assertEquals(ReservationStatus.CONFIRMED, confirmed.status());
		assertEquals(List.of("HOLD_CREATED", "HOLD_CONFIRMED"),
			reservationService.historyMine(CUSTOMER_A_ID, held.reservationId()).stream()
				.map(ReservationHistoryResult::reasonCode).toList());
	}

	@Test
	void expiresHoldAndReleasesSlotForAnotherCustomer() {
		var held = reservationService.createHold(CUSTOMER_A_ID, "expire-hold",
			holdCommand(STAFF_A_ID, LocalTime.of(14, 0)));
		jdbcTemplate.update("UPDATE reservation SET hold_expires_at = ? WHERE id = ?",
			Timestamp.from(Instant.now().minusSeconds(1)), held.reservationId());

		holdExpirationService.expireHolds();

		assertEquals(ReservationStatus.EXPIRED,
			reservationRepository.findById(held.reservationId()).orElseThrow().getStatus());
		ReservationCreateResult created = reservationService.create(CUSTOMER_B_ID, "released-slot",
			command(STAFF_A_ID, LocalTime.of(14, 0)));
		assertEquals(ReservationStatus.CONFIRMED, created.status());
	}

	@Test
	void expirationWinsRaceAgainstConfirmationAtBoundary() throws Exception {
		var held = reservationService.createHold(CUSTOMER_A_ID, "race-hold",
			holdCommand(STAFF_A_ID, LocalTime.of(14, 0)));
		jdbcTemplate.update("UPDATE reservation SET hold_expires_at = ? WHERE id = ?",
			Timestamp.from(Instant.now().minusSeconds(1)), held.reservationId());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<ErrorCode> confirmation = CompletableFuture.supplyAsync(() -> {
			ready.countDown();
			try {
				start.await();
				reservationService.confirm(CUSTOMER_A_ID, held.reservationId(), "race-confirm");
				return null;
			} catch (BusinessException exception) {
				return exception.getErrorCode();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		});
		CompletableFuture<Void> expiration = CompletableFuture.runAsync(() -> {
			ready.countDown();
			try {
				start.await();
				holdExpirationService.expireHolds();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		});

		ready.await();
		start.countDown();
		assertEquals(ErrorCode.RESERVATION_HOLD_EXPIRED, confirmation.join());
		expiration.join();
		assertEquals(ReservationStatus.EXPIRED,
			reservationRepository.findById(held.reservationId()).orElseThrow().getStatus());
	}

	private CompletableFuture<ErrorCode> concurrentCreate(UUID customerId, String key, CountDownLatch ready,
		CountDownLatch start) {
		return CompletableFuture.supplyAsync(() -> {
			ready.countDown();
			try {
				start.await();
				reservationService.create(customerId, key, command(STAFF_A_ID, LocalTime.of(14, 0)));
				return null;
			} catch (BusinessException exception) {
				return exception.getErrorCode();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		});
	}

	private CreateReservationCommand command(UUID staffId, LocalTime time) {
		OffsetDateTime startAt = ZonedDateTime.of(targetDate(), time, ZoneId.of("Asia/Seoul")).toOffsetDateTime();
		return new CreateReservationCommand(STORE_ID, SERVICE_ID, staffId, startAt, 1, "테스트 요청");
	}

	private CreateHoldCommand holdCommand(UUID staffId, LocalTime time) {
		OffsetDateTime startAt = ZonedDateTime.of(targetDate(), time, ZoneId.of("Asia/Seoul")).toOffsetDateTime();
		return new CreateHoldCommand(STORE_ID, SERVICE_ID, staffId, startAt, 1);
	}

	private LocalDate targetDate() {
		return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
	}

	private void insertUser(UUID id, String email, String phone) {
		jdbcTemplate.update("""
			INSERT INTO users (id, email, phone_number, password_hash, status, created_at, updated_at)
			VALUES (?, ?, ?, 'hash', 'ACTIVE', now(), now())
			""", id, email, phone);
	}

	private void insertCustomer(UUID profileId, UUID userId, String displayName) {
		jdbcTemplate.update("""
			INSERT INTO customer_profile
				(id, user_id, display_name, marketing_consent, notification_consent, created_at, updated_at)
			VALUES (?, ?, ?, false, true, now(), now())
			""", profileId, userId, displayName);
	}

	private void insertStaff(UUID memberId, UUID userId, String displayName) {
		jdbcTemplate.update("""
			INSERT INTO store_member
				(id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at)
			VALUES (?, ?, ?, 'STAFF', ?, 'ACTIVE', true, now(), now())
			""", memberId, STORE_ID, userId, displayName);
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static String pem(String type, byte[] encoded) {
		return "-----BEGIN " + type + "-----\n"
			+ Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
			+ "\n-----END " + type + "-----";
	}
}
