package com.example.jariyo_backend.walkin;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.admin.service.ServiceSessionCommandService;
import com.example.jariyo_backend.domain.walkin.repository.QueueNumberIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.jariyo_backend.domain.walkin.entity.WalkInStatus;
import com.example.jariyo_backend.domain.walkin.service.WalkInService;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CallCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CallResponse;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.CallResponseCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.RegisterCustomerCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.ReasonCommand;
import com.example.jariyo_backend.domain.walkin.service.WalkInService.StartServiceCommand;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class WalkInWorkflowIntegrationTests {
	private static final String STORE_ID = "00000000-0000-7000-8000-000000000001";
	private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000701");
	private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-000000000702");
	private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000703");
	private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-7000-8000-000000000704");
	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired QueueNumberIssuer queueNumberIssuer;
	@Autowired PersistentIdempotencyService idempotencyService;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired WalkInService walkInService;
	@Autowired ServiceSessionCommandService serviceSessionCommandService;

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
		jdbcTemplate.update("DELETE FROM idempotency_request WHERE actor_id = ?", USER_ID);
		jdbcTemplate.update("DELETE FROM walk_in_status_history");
		jdbcTemplate.update("DELETE FROM service_session");
		jdbcTemplate.update("DELETE FROM check_in");
		jdbcTemplate.update("DELETE FROM call_history");
		jdbcTemplate.update("DELETE FROM walk_in_entry");
		jdbcTemplate.update("DELETE FROM queue_sequence WHERE store_id = ?::uuid", STORE_ID);
		jdbcTemplate.update("INSERT INTO users (id, email, phone_number, password_hash, status, created_at, updated_at) "
			+ "VALUES (?, 'walk-in@example.com', '+821011112222', 'hash', 'ACTIVE', now(), now()) ON CONFLICT DO NOTHING", USER_ID);
		jdbcTemplate.update("INSERT INTO customer_profile (id, user_id, display_name, marketing_consent, notification_consent, created_at, updated_at) "
			+ "VALUES (?, ?, '현장 고객', false, true, now(), now()) ON CONFLICT DO NOTHING", CUSTOMER_ID, USER_ID);
		jdbcTemplate.update("INSERT INTO service (id, store_id, name, duration_minutes, cleanup_minutes, capacity, status, created_at, updated_at) "
			+ "VALUES (?, ?::uuid, '통합 테스트 서비스', 30, 5, 1, 'ACTIVE', now(), now()) ON CONFLICT DO NOTHING", SERVICE_ID, STORE_ID);
		jdbcTemplate.update("INSERT INTO store_member (id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at) "
			+ "VALUES (?, ?::uuid, ?, 'STAFF', '현장 직원', 'ACTIVE', true, now(), now()) ON CONFLICT DO NOTHING",
			MEMBER_ID, STORE_ID, USER_ID);
		jdbcTemplate.update("INSERT INTO staff_service (id, store_member_id, service_id, active) "
			+ "VALUES ('00000000-0000-7000-8000-000000000705', ?, ?, true) ON CONFLICT DO NOTHING", MEMBER_ID, SERVICE_ID);
		jdbcTemplate.update("DELETE FROM business_hour WHERE store_id = ?::uuid", STORE_ID);
		jdbcTemplate.update("INSERT INTO business_hour (id, store_id, day_of_week, open_time, close_time, is_closed) "
			+ "VALUES ('00000000-0000-7000-8000-000000000706', ?::uuid, ?, '00:00', '23:59:59', false)",
			STORE_ID, LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfWeek().name());
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(USER_ID.toString(), "", List.of()));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void completesWalkInLifecycleEndToEnd() {
		WalkInService.WalkInSummary registered = walkInService.registerCustomer(USER_ID, "flow-register",
			new RegisterCustomerCommand(UUID.fromString(STORE_ID), SERVICE_ID, MEMBER_ID, 1));
		assertEquals(WalkInStatus.WAITING, registered.status());

		WalkInService.WalkInSummary called = walkInService.call(USER_ID, UUID.fromString(STORE_ID), registered.id(),
			"flow-call", new CallCommand(3), false);
		assertEquals(WalkInStatus.CALLED, called.status());

		WalkInService.WalkInSummary checkedIn = walkInService.respondCall(USER_ID, registered.id(), "flow-check-in",
			new CallResponseCommand(CallResponse.ENTERING));
		assertEquals(WalkInStatus.CHECKED_IN, checkedIn.status());

		WalkInService.StartServiceResult started = walkInService.startService(USER_ID, UUID.fromString(STORE_ID),
			registered.id(), "flow-start", new StartServiceCommand(MEMBER_ID));
		ServiceSessionCommandService.CompleteServiceResult completed = serviceSessionCommandService.completeService(USER_ID,
			UUID.fromString(STORE_ID), started.serviceSessionId(), "flow-complete",
			new ServiceSessionCommandService.CompleteServiceCommand("정상 완료"));

		assertEquals("COMPLETED", completed.status().name());
		assertEquals(5, jdbcTemplate.queryForObject(
			"SELECT count(*) FROM walk_in_status_history WHERE walk_in_entry_id = ?", Integer.class, registered.id()));
	}

	@Test
	void filtersWaitingCountByServiceAndStaff() {
		UUID otherServiceId = UUID.fromString("00000000-0000-7000-8000-000000000707");
		jdbcTemplate.update("INSERT INTO service (id, store_id, name, duration_minutes, cleanup_minutes, capacity, status, created_at, updated_at) "
			+ "VALUES (?, ?::uuid, '다른 서비스', 20, 0, 1, 'ACTIVE', now(), now())", otherServiceId, STORE_ID);
		jdbcTemplate.update("INSERT INTO staff_service (id, store_member_id, service_id, active) "
			+ "VALUES ('00000000-0000-7000-8000-000000000708', ?, ?, true)", MEMBER_ID, otherServiceId);

		walkInService.registerGuest(USER_ID, "filter-first", UUID.fromString(STORE_ID),
			new WalkInService.RegisterGuestCommand("첫 고객", "010-2222-3333", SERVICE_ID, MEMBER_ID, 1));
		walkInService.registerGuest(USER_ID, "filter-second", UUID.fromString(STORE_ID),
			new WalkInService.RegisterGuestCommand("둘째 고객", "010-3333-4444", otherServiceId, MEMBER_ID, 1));

		WalkInService.WalkInAvailability availability = walkInService.getAvailability(UUID.fromString(STORE_ID),
			SERVICE_ID, MEMBER_ID);
		assertEquals(1, availability.waitingCount());
	}

	@Test
	void issuesUniqueQueueNumbersConcurrently() throws Exception {
		int count = 8;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(count);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < count; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return new TransactionTemplate(transactionManager).execute(status ->
						queueNumberIssuer.issue(UUID.fromString(STORE_ID), LocalDate.of(2026, 7, 22)));
				}));
			}
			start.countDown();
			List<Integer> numbers = futures.stream().map(future -> {
				try { return future.get(); } catch (Exception exception) { throw new IllegalStateException(exception); }
			}).toList();
			assertEquals(count, new HashSet<>(numbers).size());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void replaysSameIdempotentResultAndRejectsDifferentRequest() {
		AtomicInteger executions = new AtomicInteger();
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		Result first = transaction.execute(status -> idempotencyService.execute(USER_ID, "test:operation", "same-key",
			new Request("same"), Result.class, () -> new Result(executions.incrementAndGet())));
		Result replay = transaction.execute(status -> idempotencyService.execute(USER_ID, "test:operation", "same-key",
			new Request("same"), Result.class, () -> new Result(executions.incrementAndGet())));

		assertEquals(first, replay);
		assertEquals(1, executions.get());
		BusinessException exception = assertThrows(BusinessException.class, () -> transaction.execute(status ->
			idempotencyService.execute(USER_ID, "test:operation", "same-key", new Request("different"), Result.class,
				() -> new Result(99))));
		assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST, exception.getErrorCode());
	}

	@Test
	void rejectsSecondActiveWalkInForSameCustomer() {
		insertWalkIn(UUID.fromString("00000000-0000-7000-8000-000000000711"), 1);
		assertThrows(DataIntegrityViolationException.class,
			() -> insertWalkIn(UUID.fromString("00000000-0000-7000-8000-000000000712"), 2));
	}

	@Test
	void recalculatesWaitingAheadAfterEarlierEntryLeavesQueue() {
		WalkInService.WalkInSummary first = walkInService.registerGuest(USER_ID, "ahead-first",
			UUID.fromString(STORE_ID), new WalkInService.RegisterGuestCommand("첫 고객", "010-2222-3333",
				SERVICE_ID, MEMBER_ID, 1));
		WalkInService.WalkInSummary second = walkInService.registerCustomer(USER_ID, "ahead-second",
			new RegisterCustomerCommand(UUID.fromString(STORE_ID), SERVICE_ID, MEMBER_ID, 1));

		assertEquals(1, second.waitingAhead());
		walkInService.cancelAdmin(USER_ID, UUID.fromString(STORE_ID), first.id(), "ahead-cancel",
			new ReasonCommand("대기 취소"));
		assertEquals(0, walkInService.getMine(USER_ID, second.id()).waitingAhead());
	}

	@Test
	void keepsCustomerCallResponsesAndExpirationConsistent() {
		WalkInService.WalkInSummary waiting = walkInService.registerCustomer(USER_ID, "response-register",
			new RegisterCustomerCommand(UUID.fromString(STORE_ID), SERVICE_ID, MEMBER_ID, 1));
		assertEquals(WalkInStatus.CANCELLED, walkInService.cancelMine(USER_ID, waiting.id(), "response-cancel",
			new ReasonCommand("고객 취소")).status());

		WalkInService.WalkInSummary delayed = walkInService.registerCustomer(USER_ID, "response-register-delayed",
			new RegisterCustomerCommand(UUID.fromString(STORE_ID), SERVICE_ID, MEMBER_ID, 1));
		walkInService.call(USER_ID, UUID.fromString(STORE_ID), delayed.id(), "response-call-delayed",
			new CallCommand(3), false);
		assertEquals(WalkInStatus.SKIPPED, walkInService.respondCall(USER_ID, delayed.id(), "response-delayed",
			new CallResponseCommand(CallResponse.DELAYED)).status());
		walkInService.call(USER_ID, UUID.fromString(STORE_ID), delayed.id(), "response-recall",
			new CallCommand(3), true);
		assertEquals(WalkInStatus.CANCELLED, walkInService.respondCall(USER_ID, delayed.id(), "response-cancel-call",
			new CallResponseCommand(CallResponse.CANCEL)).status());

		WalkInService.WalkInSummary expired = walkInService.registerCustomer(USER_ID, "response-register-expired",
			new RegisterCustomerCommand(UUID.fromString(STORE_ID), SERVICE_ID, MEMBER_ID, 1));
		walkInService.call(USER_ID, UUID.fromString(STORE_ID), expired.id(), "response-call-expired",
			new CallCommand(3), false);
		jdbcTemplate.update("UPDATE walk_in_entry SET call_expires_at = ? WHERE id = ?",
			java.sql.Timestamp.from(Instant.now().minusSeconds(1)), expired.id());
		BusinessException exception = assertThrows(BusinessException.class,
			() -> walkInService.respondCall(USER_ID, expired.id(), "response-expired",
				new CallResponseCommand(CallResponse.ENTERING)));
		assertEquals(ErrorCode.WALK_IN_CALL_EXPIRED, exception.getErrorCode());
	}

	private void insertWalkIn(UUID id, int queueNumber) {
		jdbcTemplate.update("""
			INSERT INTO walk_in_entry
			(id, store_id, customer_id, service_id, party_size, operation_date, queue_number, status,
			 estimated_wait_minutes, version, created_at, updated_at)
			VALUES (?, ?::uuid, ?, ?, 1, '2026-07-22', ?, 'WAITING', 0, 0, now(), now())
			""", id, STORE_ID, CUSTOMER_ID, SERVICE_ID, queueNumber);
	}

	private record Request(String value) { }
	private record Result(int value) { }

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
