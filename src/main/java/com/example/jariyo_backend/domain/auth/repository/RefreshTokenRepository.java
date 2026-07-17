package com.example.jariyo_backend.domain.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findAllByFamilyId(UUID familyId);
}
