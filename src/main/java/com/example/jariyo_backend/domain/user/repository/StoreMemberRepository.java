package com.example.jariyo_backend.domain.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMemberRepository extends JpaRepository<StoreMember, UUID> {
	List<StoreMember> findAllByUser_IdOrderByCreatedAtAsc(UUID userId);

	Optional<StoreMember> findByUser_IdAndStoreId(UUID userId, UUID storeId);
}
