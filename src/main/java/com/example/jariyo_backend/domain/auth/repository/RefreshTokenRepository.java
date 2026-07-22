package com.example.jariyo_backend.domain.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	@Query("SELECT token.familyId FROM RefreshToken token WHERE token.tokenHash = :tokenHash")
	Optional<UUID> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT token FROM RefreshToken token WHERE token.tokenHash = :tokenHash")
	Optional<RefreshToken> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

	@Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:familyId AS text), 0))", nativeQuery = true)
	void lockFamily(@Param("familyId") UUID familyId);

	List<RefreshToken> findAllByFamilyId(UUID familyId);
}
