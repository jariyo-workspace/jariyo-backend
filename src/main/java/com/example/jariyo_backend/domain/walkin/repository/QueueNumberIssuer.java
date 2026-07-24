package com.example.jariyo_backend.domain.walkin.repository;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class QueueNumberIssuer {
	private final EntityManager entityManager;

	public QueueNumberIssuer(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public int issue(UUID storeId, LocalDate operationDate) {
		Object result = entityManager.createNativeQuery("""
			INSERT INTO queue_sequence (id, store_id, operation_date, last_issued_number, updated_at)
			VALUES (:id, :storeId, :operationDate, 1, now())
			ON CONFLICT (store_id, operation_date)
			DO UPDATE SET last_issued_number = queue_sequence.last_issued_number + 1, updated_at = now()
			RETURNING last_issued_number
			""")
			.setParameter("id", UUID.randomUUID())
			.setParameter("storeId", storeId)
			.setParameter("operationDate", operationDate)
			.getSingleResult();
		return ((Number) result).intValue();
	}
}
