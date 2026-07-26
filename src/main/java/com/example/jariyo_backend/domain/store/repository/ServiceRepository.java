package com.example.jariyo_backend.domain.store.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.ServiceOffering;
import com.example.jariyo_backend.domain.store.entity.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceOffering, UUID> {
	List<ServiceOffering> findAllByStoreIdOrderByCreatedAtAsc(UUID storeId);

	List<ServiceOffering> findAllByStoreIdAndStatusOrderByCreatedAtAsc(UUID storeId, ServiceStatus status);

	Optional<ServiceOffering> findByIdAndStoreId(UUID id, UUID storeId);
}
