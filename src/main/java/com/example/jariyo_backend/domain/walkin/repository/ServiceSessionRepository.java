package com.example.jariyo_backend.domain.walkin.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSession;
import com.example.jariyo_backend.domain.walkin.entity.ServiceSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceSessionRepository extends JpaRepository<ServiceSession, UUID> {
	Optional<ServiceSession> findByWalkInEntryIdAndStatus(UUID walkInEntryId, ServiceSessionStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from ServiceSession s where s.id = :id")
	Optional<ServiceSession> findByIdForUpdate(@Param("id") UUID id);
}
