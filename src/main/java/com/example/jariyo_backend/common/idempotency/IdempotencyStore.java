package com.example.jariyo_backend.common.idempotency;

import java.time.Duration;

public interface IdempotencyStore {
	boolean putIfAbsent(String key, Duration ttl);
}
