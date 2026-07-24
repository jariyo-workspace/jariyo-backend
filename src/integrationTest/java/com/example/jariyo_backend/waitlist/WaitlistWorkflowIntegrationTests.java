package com.example.jariyo_backend.waitlist;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.reservation.entity.ReservationSource;
import com.example.jariyo_backend.domain.reservation.entity.ReservationStatus;
import com.example.jariyo_backend.domain.reservation.repository.ReservationRepository;
import com.example.jariyo_backend.domain.reservation.service.ReservationService;
import com.example.jariyo_backend.domain.waitlist.entity.SlotOfferStatus;
import com.example.jariyo_backend.domain.waitlist.entity.StaffPreferenceType;
import com.example.jariyo_backend.domain.waitlist.entity.WaitlistStatus;
import com.example.jariyo_backend.domain.waitlist.repository.SlotOfferRepository;
import com.example.jariyo_backend.domain.waitlist.repository.WaitlistEntryRepository;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.AcceptSlotOfferResult;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.CreateWaitlistCommand;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.SlotOfferSummary;
import com.example.jariyo_backend.domain.waitlist.service.WaitlistService.WaitlistSummary;
import org.junit.jupiter.api.AfterEach;
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

@Testcontainers
@SpringBootTest
class WaitlistWorkflowIntegrationTests {
	private static final UUID WAITLIST_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000801");
	private static final UUID WAITLIST_CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-000000000802");
	private static final UUID RESERVATION_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000803");
	private static final UUID RESERVATION_CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-000000000804");
	private static final UUID STAFF_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000805");
	private static final UUID STAFF_MEMBER_ID = UUID.fromString("00000000-0000-7000-8000-000000000806");
	private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000807");
	private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-7000-8000-000000000808");
	private static final UUID STORE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired WaitlistService waitlistService;
	@Autowired ReservationService reservationService;
	@Autowired WaitlistEntryRepository waitlistEntryRepository;
	@Autowired SlotOfferRepository slotOfferRepository;
	@Autowired ReservationRepository reservationRepository;

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
		jdbcTemplate.update("DELETE FROM slot_offer_status_history");
		jdbcTemplate.update("DELETE FROM slot_offer");
		jdbcTemplate.update("DELETE FROM waitlist_entry");
		jdbcTemplate.update("DELETE FROM reservation");
		jdbcTemplate.update("DELETE FROM staff_service WHERE service_id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM service WHERE id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM store_member WHERE id = ?", STAFF_MEMBER_ID);
		jdbcTemplate.update("DELETE FROM customer_profile WHERE id in (?, ?)", WAITLIST_CUSTOMER_ID, RESERVATION_CUSTOMER_ID);
		jdbcTemplate.update("DELETE FROM users WHERE id in (?, ?, ?)", WAITLIST_USER_ID, RESERVATION_USER_ID, STAFF_USER_ID);

		insertUser(WAITLIST_USER_ID, "waitlist-user@example.com", "+821011110001");
		insertUser(RESERVATION_USER_ID, "reservation-user@example.com", "+821011110002");
		insertUser(STAFF_USER_ID, "waitlist-staff@example.com", "+821011110003");
		insertCustomer(WAITLIST_CUSTOMER_ID, WAITLIST_USER_ID, "대기 고객");
		insertCustomer(RESERVATION_CUSTOMER_ID, RESERVATION_USER_ID, "예약 고객");
		jdbcTemplate.update("""
			INSERT INTO service (id, store_id, name, duration_minutes, cleanup_minutes, capacity, status, created_at, updated_at)
			VALUES (?, ?, '대기 테스트 서비스', 30, 10, 2, 'ACTIVE', now(), now())
			""", SERVICE_ID, STORE_ID);
		jdbcTemplate.update("""
			INSERT INTO store_member (id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at)
			VALUES (?, ?, ?, 'STAFF', '대기 테스트 직원', 'ACTIVE', true, now(), now())
			""", STAFF_MEMBER_ID, STORE_ID, STAFF_USER_ID);
		jdbcTemplate.update("""
			INSERT INTO staff_service (id, store_member_id, service_id, active)
			VALUES ('00000000-0000-7000-8000-000000000809', ?, ?, true)
			""", STAFF_MEMBER_ID, SERVICE_ID);
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("DELETE FROM idempotency_request");
	}

	@Test
	void convertsCancelledReservationIntoAcceptedWaitlistReservation() {
		LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
		WaitlistSummary waitlist = waitlistService.create(WAITLIST_USER_ID, "waitlist-create",
			new CreateWaitlistCommand(STORE_ID, SERVICE_ID, STAFF_MEMBER_ID, StaffPreferenceType.SPECIFIC_PREFERRED,
				targetDate, java.time.LocalTime.of(14, 0), java.time.LocalTime.of(17, 0), 1));
		insertReservation(targetDate, java.time.LocalTime.of(15, 0));

		reservationService.cancelMine(RESERVATION_USER_ID, RESERVATION_ID, "reservation-cancel",
			new ReservationService.CancelReservationCommand("CUSTOMER_SCHEDULE_CHANGED", "일정 변경"));

		List<SlotOfferSummary> offers = waitlistService.listOffers(WAITLIST_USER_ID, SlotOfferStatus.PENDING);
		assertEquals(1, offers.size());
		AcceptSlotOfferResult accepted = waitlistService.accept(WAITLIST_USER_ID, offers.get(0).id(), "offer-accept");

		assertEquals(SlotOfferStatus.ACCEPTED, accepted.offer().status());
		assertEquals(ReservationSource.WAITLIST_OFFER, accepted.reservation().source());
		assertEquals(ReservationStatus.CONFIRMED, accepted.reservation().status());
		assertEquals(WaitlistStatus.RESERVED, accepted.waitlist().status());
		assertEquals(2, reservationRepository.count());
		assertEquals(WaitlistStatus.RESERVED,
			waitlistEntryRepository.findById(waitlist.id()).orElseThrow().getStatus());
	}

	@Test
	void expiresPendingOfferAndReturnsWaitlistToWaiting() {
		LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
		WaitlistSummary waitlist = waitlistService.create(WAITLIST_USER_ID, "waitlist-create-expire",
			new CreateWaitlistCommand(STORE_ID, SERVICE_ID, STAFF_MEMBER_ID, StaffPreferenceType.SPECIFIC_ONLY,
				targetDate, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(18, 0), 1));
		insertReservation(targetDate, java.time.LocalTime.of(11, 0));

		reservationService.cancelMine(RESERVATION_USER_ID, RESERVATION_ID, "reservation-cancel-expire",
			new ReservationService.CancelReservationCommand("CUSTOMER_SCHEDULE_CHANGED", "일정 변경"));
		UUID offerId = waitlistService.listOffers(WAITLIST_USER_ID, SlotOfferStatus.PENDING).get(0).id();
		jdbcTemplate.update("UPDATE slot_offer SET expires_at = ? WHERE id = ?",
			Timestamp.from(Instant.now().minusSeconds(5)), offerId);

		waitlistService.expirePendingOffers();

		assertEquals(SlotOfferStatus.EXPIRED, slotOfferRepository.findById(offerId).orElseThrow().getStatus());
		assertEquals(WaitlistStatus.WAITING, waitlistEntryRepository.findById(waitlist.id()).orElseThrow().getStatus());
		assertFalse(waitlistService.listOffers(WAITLIST_USER_ID, SlotOfferStatus.PENDING).stream().anyMatch(offer -> offer.id().equals(offerId)));
	}

	private void insertReservation(LocalDate date, java.time.LocalTime time) {
		ZonedDateTime startAt = ZonedDateTime.of(date, time, ZoneId.of("Asia/Seoul"));
		jdbcTemplate.update("""
			INSERT INTO reservation
				(id, store_id, customer_id, service_id, assigned_staff_id, source, status, start_at, service_end_at,
				 occupied_until, party_size, confirmed_at, version, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, 'CUSTOMER_BOOKING', 'CONFIRMED', ?, ?, ?, 1, now(), 0, now(), now())
			""", RESERVATION_ID, STORE_ID, RESERVATION_USER_ID, SERVICE_ID, STAFF_MEMBER_ID,
			Timestamp.from(startAt.toInstant()), Timestamp.from(startAt.plusMinutes(30).toInstant()),
			Timestamp.from(startAt.plusMinutes(40).toInstant()));
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
