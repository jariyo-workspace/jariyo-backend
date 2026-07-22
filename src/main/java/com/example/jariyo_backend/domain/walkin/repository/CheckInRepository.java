package com.example.jariyo_backend.domain.walkin.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.walkin.entity.CheckIn;
import com.example.jariyo_backend.domain.walkin.entity.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {
	Optional<CheckIn> findByWalkInEntryIdAndStatus(UUID walkInEntryId, CheckInStatus status);
}
