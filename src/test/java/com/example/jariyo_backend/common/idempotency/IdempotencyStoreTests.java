package com.example.jariyo_backend.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdempotencyStoreTests {
	@Test
	void rejectsDuplicateKey() {
		IdempotencyStore store = new InMemoryIdempotencyStore();

		assertThat(store.putIfAbsent("key-1", Duration.ofHours(1))).isTrue();
		assertThat(store.putIfAbsent("key-1", Duration.ofHours(1))).isFalse();
	}
}
