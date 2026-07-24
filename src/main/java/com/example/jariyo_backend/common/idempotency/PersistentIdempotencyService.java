package com.example.jariyo_backend.common.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class PersistentIdempotencyService {
	private static final Duration TTL = Duration.ofHours(24);
	private final IdempotencyRequestRepository repository;
	private final EntityManager entityManager;
	private final ObjectMapper objectMapper;

	public PersistentIdempotencyService(IdempotencyRequestRepository repository, EntityManager entityManager,
		ObjectMapper objectMapper) {
		this.repository = repository;
		this.entityManager = entityManager;
		this.objectMapper = objectMapper;
	}

	public <T> T execute(UUID actorId, String operation, String key, Object request, Class<T> responseType,
		Supplier<T> action) {
		validateKey(key);
		String hash = hash(request);
		lock(actorId + ":" + operation + ":" + key);
		Instant now = Instant.now();
		IdempotencyRequest existing = repository.findByActorIdAndOperationAndIdempotencyKey(actorId, operation, key)
			.orElse(null);
		if (existing != null && existing.getExpiresAt().isAfter(now)) {
			if (!existing.getRequestHash().equals(hash)) {
				throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST);
			}
			return read(existing.getResponseBody(), responseType);
		}
		if (existing != null) {
			repository.delete(existing);
			repository.flush();
		}
		T response = action.get();
		repository.save(new IdempotencyRequest(actorId, operation, key, hash, write(response), now, now.plus(TTL)));
		return response;
	}

	private void lock(String lockKey) {
		entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
			.setParameter("lockKey", lockKey)
			.getSingleResult();
	}

	private void validateKey(String key) {
		if (key == null || key.isBlank() || key.length() > 200) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		}
	}

	private String hash(Object request) {
		try {
			byte[] value = objectMapper.writeValueAsBytes(request);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private String write(Object value) {
		return objectMapper.writeValueAsString(value);
	}

	private <T> T read(String value, Class<T> type) {
		return objectMapper.readerFor(type)
			.without(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
			.readValue(value);
	}
}
