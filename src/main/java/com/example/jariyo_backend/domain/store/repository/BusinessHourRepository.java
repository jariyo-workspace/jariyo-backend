package com.example.jariyo_backend.domain.store.repository;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessHourRepository extends JpaRepository<BusinessHour, UUID> {
	List<BusinessHour> findAllByStoreId(UUID storeId);

	List<BusinessHour> findAllByStoreIdOrderByDayOfWeekAsc(UUID storeId);

	void deleteAllByStoreId(UUID storeId);
}
