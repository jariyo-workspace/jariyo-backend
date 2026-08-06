package com.example.jariyo_backend.domain.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMemberRepository extends JpaRepository<StoreMember, UUID> {
	List<StoreMember> findAllByUser_IdOrderByCreatedAtAsc(UUID userId);

	Optional<StoreMember> findByUser_IdAndStoreId(UUID userId, UUID storeId);

	Optional<StoreMember> findByIdAndStoreId(UUID id, UUID storeId);

	List<StoreMember> findAllByStoreIdOrderByCreatedAtAsc(UUID storeId);

	List<StoreMember> findAllByStoreIdAndIdInOrderByCreatedAtAsc(UUID storeId, Collection<UUID> ids);

	List<StoreMember> findAllByStoreIdAndStatusAndBookingEnabledTrue(UUID storeId, StoreMemberStatus status);

	List<StoreMember> findAllByStoreIdAndStatusAndBookingEnabledTrueAndIdIn(
		UUID storeId, StoreMemberStatus status, Collection<UUID> ids);

	long countByStoreIdAndRoleAndStatus(UUID storeId, com.example.jariyo_backend.domain.user.entity.StoreMemberRole role,
		StoreMemberStatus status);

	boolean existsByUser_IdAndStoreId(UUID userId, UUID storeId);
}
