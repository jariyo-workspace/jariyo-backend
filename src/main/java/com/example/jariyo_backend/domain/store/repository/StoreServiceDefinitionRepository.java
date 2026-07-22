package com.example.jariyo_backend.domain.store.repository;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import com.example.jariyo_backend.domain.store.entity.StoreServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreServiceDefinitionRepository extends JpaRepository<StoreServiceDefinition, UUID> {
	Optional<StoreServiceDefinition> findByIdAndStoreIdAndStatus(UUID id, UUID storeId, ServiceStatus status);
}
