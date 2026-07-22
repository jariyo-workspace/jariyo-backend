package com.example.jariyo_backend.domain.store.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePolicyRepository extends JpaRepository<StorePolicy, UUID> {
	Optional<StorePolicy> findByStoreId(UUID storeId);
}
