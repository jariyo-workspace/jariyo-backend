package com.example.jariyo_backend.admin;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.admin.service.AdminOperationQueryService;
import com.example.jariyo_backend.domain.admin.service.ServiceSessionCommandService;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.service.ReservationAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest
class AdminReservationWorkflowIntegrationTests {
	private static final UUID STORE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
	private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b01");
	private static final UUID ADMIN_MEMBER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b02");
	private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b03");
	private static final UUID CUSTOMER_PROFILE_ID = UUID.fromString("00000000-0000-7000-8000-000000000b04");
	private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000b05");
	private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-7000-8000-000000000b06");
	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired ReservationAdminService reservationAdminService;
	@Autowired ServiceSessionCommandService serviceSessionCommandService;
	@Autowired AdminOperationQueryService adminOperationQueryService;

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
		jdbcTemplate.update("DELETE FROM idempotency_request WHERE actor_id = ? OR actor_id = ?",
			ADMIN_USER_ID, CUSTOMER_USER_ID);
		jdbcTemplate.update("DELETE FROM audit_log");
		jdbcTemplate.update("DELETE FROM reservation_status_history WHERE reservation_id = ?", RESERVATION_ID);
		jdbcTemplate.update("DELETE FROM service_session WHERE reservation_id = ?", RESERVATION_ID);
		jdbcTemplate.update("DELETE FROM reservation WHERE id = ?", RESERVATION_ID);
		jdbcTemplate.update("DELETE FROM staff_service WHERE service_id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM service WHERE id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM store_member WHERE id = ?", ADMIN_MEMBER_ID);
		jdbcTemplate.update("DELETE FROM customer_profile WHERE id = ?", CUSTOMER_PROFILE_ID);
		jdbcTemplate.update("DELETE FROM users WHERE id in (?, ?)", ADMIN_USER_ID, CUSTOMER_USER_ID);

		insertUser(ADMIN_USER_ID, "admin-e2e@example.com", "+821055550001");
		insertUser(CUSTOMER_USER_ID, "customer-e2e@example.com", "+821055550002");
		insertCustomer(CUSTOMER_PROFILE_ID, CUSTOMER_USER_ID, "예약 고객");
		insertAdminMember();
		insertService();
		insertReservationWithinCheckInWindow();

		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(ADMIN_USER_ID.toString(), "", List.of()));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void staffProcessesReservationFromDashboardToCompletion() {
		AdminOperationQueryService.TodayDashboard before = adminOperationQueryService.getTodayDashboard(ADMIN_USER_ID, STORE_ID);
		assertEquals(1, before.summary().reservationCount());
		assertEquals(0, before.summary().checkedInCount());
		assertEquals(0, before.summary().inServiceCount());

		ReservationAdminService.ReservationCheckInResult checkedIn = reservationAdminService.checkIn(
			ADMIN_USER_ID, STORE_ID, RESERVATION_ID, "admin-check-in");
		assertEquals(ReservationStatus.CHECKED_IN, checkedIn.status());

		ReservationAdminService.StartReservationServiceResult started = reservationAdminService.startService(
			ADMIN_USER_ID, STORE_ID, RESERVATION_ID, "admin-start-service",
			new ReservationAdminService.StartReservationServiceCommand(ADMIN_MEMBER_ID));
		assertNotNull(started.serviceSessionId());
		assertEquals(ReservationStatus.IN_SERVICE,
			adminOperationQueryService.getReservation(ADMIN_USER_ID, STORE_ID, RESERVATION_ID).status());

		ServiceSessionCommandService.CompleteServiceResult completed = serviceSessionCommandService.completeService(
			ADMIN_USER_ID, STORE_ID, started.serviceSessionId(), "admin-complete-service",
			new ServiceSessionCommandService.CompleteServiceCommand("시나리오 완료"));
		assertEquals("COMPLETED", completed.status().name());

		AdminOperationQueryService.AdminReservationDetail detail =
			adminOperationQueryService.getReservation(ADMIN_USER_ID, STORE_ID, RESERVATION_ID);
		assertEquals(ReservationStatus.COMPLETED, detail.status());
		assertNotNull(detail.checkedInAt());
		assertNotNull(detail.serviceStartedAt());
		assertNotNull(detail.completedAt());

		List<AdminOperationQueryService.AdminReservationItem> reservations =
			adminOperationQueryService.listReservations(ADMIN_USER_ID, STORE_ID, null, null, null, null, null, null);
		assertEquals(1, reservations.size());
		assertEquals(ReservationStatus.COMPLETED, reservations.get(0).status());

		AdminOperationQueryService.TodayDashboard after = adminOperationQueryService.getTodayDashboard(ADMIN_USER_ID, STORE_ID);
		assertEquals(1, after.summary().reservationCount());
		assertEquals(0, after.summary().checkedInCount());
		assertEquals(0, after.summary().inServiceCount());
		assertEquals(0, after.summary().noShowCandidateCount());
	}

	private void insertUser(UUID id, String email, String phone) {
		jdbcTemplate.update("""
			INSERT INTO users (id, email, phone_number, password_hash, status, created_at, updated_at)
			VALUES (?, ?, ?, 'hash', 'ACTIVE', now(), now())
			""", id, email, phone);
	}

	private void insertCustomer(UUID customerId, UUID userId, String displayName) {
		jdbcTemplate.update("""
			INSERT INTO customer_profile (id, user_id, display_name, marketing_consent, notification_consent, created_at, updated_at)
			VALUES (?, ?, ?, false, true, now(), now())
			""", customerId, userId, displayName);
	}

	private void insertAdminMember() {
		jdbcTemplate.update("""
			INSERT INTO store_member (id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at)
			VALUES (?, ?, ?, 'MANAGER', '운영자', 'ACTIVE', true, now(), now())
			""", ADMIN_MEMBER_ID, STORE_ID, ADMIN_USER_ID);
	}

	private void insertService() {
		jdbcTemplate.update("""
			INSERT INTO service (id, store_id, name, duration_minutes, cleanup_minutes, capacity, status, created_at, updated_at)
			VALUES (?, ?, '운영자 시나리오 서비스', 30, 10, 1, 'ACTIVE', now(), now())
			""", SERVICE_ID, STORE_ID);
		jdbcTemplate.update("""
			INSERT INTO staff_service (id, store_member_id, service_id, active)
			VALUES ('00000000-0000-7000-8000-000000000b07', ?, ?, true)
			""", ADMIN_MEMBER_ID, SERVICE_ID);
	}

	private void insertReservationWithinCheckInWindow() {
		Instant now = Instant.now();
		Instant startAt = now.plusSeconds(10 * 60);
		Instant serviceEndAt = startAt.plusSeconds(30 * 60);
		Instant occupiedUntil = serviceEndAt.plusSeconds(10 * 60);
		jdbcTemplate.update("""
			INSERT INTO reservation
				(id, store_id, customer_id, service_id, assigned_staff_id, source, status, start_at, service_end_at,
				 occupied_until, party_size, confirmed_at, version, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, 'CUSTOMER_BOOKING', 'CONFIRMED', ?, ?, ?, 1, ?, 0, now(), now())
			""", RESERVATION_ID, STORE_ID, CUSTOMER_PROFILE_ID, SERVICE_ID, ADMIN_MEMBER_ID,
			Timestamp.from(startAt), Timestamp.from(serviceEndAt), Timestamp.from(occupiedUntil), Timestamp.from(now));
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
