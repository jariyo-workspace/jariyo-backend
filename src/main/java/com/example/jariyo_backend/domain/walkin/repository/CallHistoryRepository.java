package com.example.jariyo_backend.domain.walkin.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.walkin.entity.CallHistory;
import com.example.jariyo_backend.domain.walkin.entity.CallResponseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallHistoryRepository extends JpaRepository<CallHistory, UUID> {
	Optional<CallHistory> findFirstByWalkInEntryIdAndResponseStatus(UUID walkInEntryId, CallResponseStatus status);
	long countByWalkInEntryId(UUID walkInEntryId);
}
