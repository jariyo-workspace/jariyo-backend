package com.example.jariyo_backend.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class StoreDomainForeignKeyIntegrationTests {
	private static final String STORE_ID = "00000000-0000-7000-8000-000000000001";
	private static final String USER_ID = "00000000-0000-7000-8000-000000000201";
	private static final String MEMBER_ID = "00000000-0000-7000-8000-000000000301";
	private static final String MISSING_ID = "00000000-0000-7000-8000-000000000999";

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@BeforeAll
	static void migrate() {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.load()
			.migrate();
	}

	@Test
	void rejectsMissingStoreAndExceptionCreatorReferences() throws Exception {
		try (Connection connection = DriverManager.getConnection(
			POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
			Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				INSERT INTO users (id, email, phone_number, password_hash, status, created_at, updated_at)
				VALUES ('%s', 'fk-test@example.com', '010-0000-0013', 'hash', 'ACTIVE', now(), now())
				""".formatted(USER_ID));

			assertForeignKeyViolation(() -> statement.executeUpdate("""
				INSERT INTO store_member
					(id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at)
				VALUES ('%s', '%s', '%s', 'STAFF', '외래 키 테스트', 'ACTIVE', true, now(), now())
				""".formatted(MEMBER_ID, MISSING_ID, USER_ID)));

			statement.executeUpdate("""
				INSERT INTO store_member
					(id, store_id, user_id, role, display_name, status, booking_enabled, created_at, updated_at)
				VALUES ('%s', '%s', '%s', 'STAFF', '외래 키 테스트', 'ACTIVE', true, now(), now())
				""".formatted(MEMBER_ID, STORE_ID, USER_ID));

			assertForeignKeyViolation(() -> statement.executeUpdate("""
				INSERT INTO schedule_exception
					(id, store_id, target_date, type, created_by_member_id, created_at)
				VALUES ('00000000-0000-7000-8000-000000000401', '%s', '2026-07-19',
					'CLOSED_ALL_DAY', '%s', now())
				""".formatted(STORE_ID, MISSING_ID)));

			assertForeignKeyViolation(() -> statement.executeUpdate("""
				INSERT INTO staff_schedule_exception
					(id, store_member_id, target_date, type, created_by_member_id, created_at)
				VALUES ('00000000-0000-7000-8000-000000000501', '%s', '2026-07-19',
					'DAY_OFF', '%s', now())
				""".formatted(MEMBER_ID, MISSING_ID)));
		}
	}

	private static void assertForeignKeyViolation(SqlAction action) {
		SQLException exception = assertThrows(SQLException.class, action::run);
		assertEquals("23503", exception.getSQLState());
	}

	@FunctionalInterface
	private interface SqlAction {
		void run() throws SQLException;
	}
}
