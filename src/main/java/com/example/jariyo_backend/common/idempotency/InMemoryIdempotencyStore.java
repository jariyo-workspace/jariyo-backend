package com.example.jariyo_backend.common.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {
	private final Map<String, Instant> keys = new ConcurrentHashMap<>();

	@Override
	public boolean putIfAbsent(String key, Duration ttl) {
		Instant now = Instant.now();
		Instant expiry = now.plus(ttl);
		cleanup(now);
		return keys.putIfAbsent(key, expiry) == null;
	}

	private void cleanup(Instant now) {
		keys.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
	}
}
