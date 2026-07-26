package com.example.jariyo_backend.common.async;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsyncEventOutboxRepository extends JpaRepository<AsyncEventOutbox, UUID> {
	List<AsyncEventOutbox> findTop50ByStatusOrderByCreatedAtAsc(AsyncEventStatus status);

	List<AsyncEventOutbox> findTop50ByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(AsyncEventStatus status,
		int attemptCount);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from AsyncEventOutbox e where e.id = :id")
	AsyncEventOutbox findLockedById(@Param("id") UUID id);

	Optional<AsyncEventOutbox> findFirstByTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(AsyncEventType type,
		String referenceType, UUID referenceId);
}
