package com.example.jariyo_backend.common.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "idempotency_request", uniqueConstraints = @UniqueConstraint(
	name = "uk_idempotency_actor_operation_key", columnNames = {"actor_id", "operation", "idempotency_key"}))
public class IdempotencyRequest {
	@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) private UUID id;
	@Column(name = "actor_id", nullable = false) private UUID actorId;
	@Column(nullable = false, length = 160) private String operation;
	@Column(name = "idempotency_key", nullable = false, length = 200) private String idempotencyKey;
	@Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
	@Column(name = "response_body", nullable = false, columnDefinition = "TEXT") private String responseBody;
	@Column(name = "expires_at", nullable = false) private Instant expiresAt;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "completed_at", nullable = false) private Instant completedAt;

	protected IdempotencyRequest() { }

	public IdempotencyRequest(UUID actorId, String operation, String key, String requestHash, String responseBody,
		Instant now, Instant expiresAt) {
		this.actorId = actorId;
		this.operation = operation;
		this.idempotencyKey = key;
		this.requestHash = requestHash;
		this.responseBody = responseBody;
		this.createdAt = now;
		this.completedAt = now;
		this.expiresAt = expiresAt;
	}

	public String getRequestHash() { return requestHash; }
	public String getResponseBody() { return responseBody; }
	public Instant getExpiresAt() { return expiresAt; }
}
