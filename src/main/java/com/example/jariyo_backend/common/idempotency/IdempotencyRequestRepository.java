package com.example.jariyo_backend.common.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, UUID> {
	Optional<IdempotencyRequest> findByActorIdAndOperationAndIdempotencyKey(UUID actorId, String operation,
		String idempotencyKey);
}
