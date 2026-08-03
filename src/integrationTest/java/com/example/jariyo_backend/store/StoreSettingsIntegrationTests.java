package com.example.jariyo_backend.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService;
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

@Testcontainers
@SpringBootTest
class StoreSettingsIntegrationTests {
	private static final UUID STORE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
	private static final UUID OWNER_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b01");
	private static final UUID OWNER_MEMBER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b02");
	private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b03");
	private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b04");
	private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000b05");
	private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-7000-8000-000000000b06");
	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired StoreSettingsService settingsService;

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
		jdbcTemplate.update("DELETE FROM audit_log WHERE actor_id = ?", OWNER_MEMBER_ID);
		jdbcTemplate.update("DELETE FROM reservation WHERE id = ?", RESERVATION_ID);
		jdbcTemplate.update("DELETE FROM staff_service WHERE store_member_id = ?", OWNER_MEMBER_ID);
		jdbcTemplate.update("DELETE FROM service WHERE id = ?", SERVICE_ID);
		jdbcTemplate.update("DELETE FROM store_member WHERE id = ?", OWNER_MEMBER_ID);
		jdbcTemplate.update("DELETE FROM customer_profile WHERE id = ?", CUSTOMER_ID);
		jdbcTemplate.update("DELETE FROM users WHERE id in (?, ?)", OWNER_USER_ID, CUSTOMER_USER_ID);
		insertUser(OWNER_USER_ID, "settings-owner@example.com", "+821033330001");
		insertUser(CUSTOMER_USER_ID, "settings-customer@example.com", "+821033330002");
		jdbcTemplate.update("""
			INSERT INTO store_member
				(id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at)
			VALUES (?, ?, ?, 'OWNER', '설정 OWNER', 'ACTIVE', true, now(), now())
			""", OWNER_MEMBER_ID, STORE_ID, OWNER_USER_ID);
		jdbcTemplate.update("""
			INSERT INTO customer_profile
				(id, user_id, display_name, marketing_consent, notification_consent, created_at, updated_at)
			VALUES (?, ?, '설정 고객', false, true, now(), now())
			""", CUSTOMER_ID, CUSTOMER_USER_ID);
		jdbcTemplate.update("""
			INSERT INTO service
				(id, store_id, name, duration_minutes, cleanup_minutes, capacity, status, created_at, updated_at)
			VALUES (?, ?, '설정 테스트', 30, 10, 1, 'ACTIVE', now(), now())
			""", SERVICE_ID, STORE_ID);
		jdbcTemplate.update("""
			INSERT INTO staff_service (id, store_member_id, service_id, active)
			VALUES ('00000000-0000-7000-8000-000000000b07', ?, ?, true)
			""", OWNER_MEMBER_ID, SERVICE_ID);
		Instant start = Instant.now().plusSeconds(86400);
		jdbcTemplate.update("""
			INSERT INTO reservation
				(id, store_id, customer_id, service_id, assigned_staff_id, source, status, start_at,
				 service_end_at, occupied_until, party_size, version, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, 'CUSTOMER_BOOKING', 'CONFIRMED', ?, ?, ?, 1, 0, now(), now())
			""", RESERVATION_ID, STORE_ID, CUSTOMER_ID, SERVICE_ID, OWNER_MEMBER_ID, Timestamp.from(start),
			Timestamp.from(start.plusSeconds(1800)), Timestamp.from(start.plusSeconds(2400)));
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(OWNER_USER_ID.toString(), "", List.of()));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void rejectsConflictingServiceDeactivationWithoutAuditThenUpdatesStoreInfo() {
		StoreSettingsService.UpdateResult conflict = settingsService.deactivateService(OWNER_USER_ID, STORE_ID, SERVICE_ID);

		assertFalse(conflict.updated());
		assertEquals(RESERVATION_ID, conflict.conflicts().get(0).reservationId());
		assertEquals("ACTIVE", jdbcTemplate.queryForObject("SELECT status FROM service WHERE id = ?", String.class,
			SERVICE_ID));
		assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE target_id = ?",
			Integer.class, SERVICE_ID));

		StoreSettingsService.UpdateResult assignments = settingsService.replaceStaffServices(OWNER_USER_ID, STORE_ID,
			OWNER_MEMBER_ID, List.of(new StoreSettingsService.StaffServiceCommand(SERVICE_ID, true, 35)));

		assertEquals(true, assignments.updated());
		assertEquals(35, jdbcTemplate.queryForObject("SELECT custom_duration_minutes FROM staff_service "
			+ "WHERE store_member_id = ? AND service_id = ?", Integer.class, OWNER_MEMBER_ID, SERVICE_ID));

		settingsService.updateStore(OWNER_USER_ID, STORE_ID,
			new StoreSettingsService.StoreCommand("자리요 설정", "설명", "0212345678", "서울"));

		assertEquals("자리요 설정", jdbcTemplate.queryForObject("SELECT name FROM store WHERE id = ?", String.class,
			STORE_ID));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'STORE_UPDATED'",
			Integer.class));
	}

	private void insertUser(UUID id, String email, String phone) {
		jdbcTemplate.update("""
			INSERT INTO users (id, email, phone_number, password_hash, status, created_at, updated_at)
			VALUES (?, ?, ?, 'hash', 'ACTIVE', now(), now())
			""", id, email, phone);
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
			+ Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
			+ "\n-----END " + type + "-----";
	}
}
