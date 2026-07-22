package com.example.jariyo_backend.domain.walkin.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalkInEntryTests {
	@Test
	void followsWalkInLifecycle() {
		WalkInEntry entry = WalkInEntry.forCustomer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
			1, LocalDate.now(), 1, 0);
		Instant now = Instant.now();

		entry.call(now, now.plusSeconds(180));
		entry.transitionTo(WalkInStatus.CHECKED_IN, now.plusSeconds(10));
		entry.transitionTo(WalkInStatus.IN_SERVICE, now.plusSeconds(20));
		entry.transitionTo(WalkInStatus.COMPLETED, now.plusSeconds(1200));

		assertEquals(WalkInStatus.COMPLETED, entry.getStatus());
		assertEquals(now.plusSeconds(10), entry.getCheckedInAt());
		assertEquals(now.plusSeconds(1200), entry.getCompletedAt());
	}

	@Test
	void rejectsInvalidTransition() {
		WalkInEntry entry = WalkInEntry.forCustomer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
			1, LocalDate.now(), 1, 0);

		assertThrows(BusinessException.class,
			() -> entry.transitionTo(WalkInStatus.IN_SERVICE, Instant.now()));
	}

	@Test
	void supportsSkipAndRestore() {
		WalkInEntry entry = WalkInEntry.forGuest(UUID.randomUUID(), "비회원", "+821012345678", UUID.randomUUID(),
			null, 1, LocalDate.now(), 1, 0);
		Instant now = Instant.now();

		entry.call(now, now.plusSeconds(180));
		entry.transitionTo(WalkInStatus.SKIPPED, now.plusSeconds(181));
		entry.transitionTo(WalkInStatus.WAITING, now.plusSeconds(182));

		assertEquals(WalkInStatus.WAITING, entry.getStatus());
	}

	@Test
	void recallKeepsCalledStatusWithoutFakeTransition() {
		WalkInEntry entry = WalkInEntry.forCustomer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
			1, LocalDate.now(), 1, 0);
		Instant first = Instant.now();
		Instant second = first.plusSeconds(30);

		entry.call(first, first.plusSeconds(180));
		entry.recall(second, second.plusSeconds(180));

		assertEquals(WalkInStatus.CALLED, entry.getStatus());
		assertEquals(second, entry.getCalledAt());
	}
}
