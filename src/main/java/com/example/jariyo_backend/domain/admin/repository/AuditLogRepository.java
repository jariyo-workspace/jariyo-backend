package com.example.jariyo_backend.domain.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.admin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
	@Query("""
		select a from AuditLog a
		where a.storeId = :storeId
			and (:actorId is null or a.actorId = :actorId)
			and (:action is null or a.action = :action)
			and (:targetType is null or a.targetType = :targetType)
			and (:targetId is null or a.targetId = :targetId)
			and (:from is null or a.occurredAt >= :from)
			and (:to is null or a.occurredAt < :to)
		order by a.occurredAt desc, a.id desc
		""")
	List<AuditLog> findAllByStoreIdAndFilters(
		@Param("storeId") UUID storeId,
		@Param("actorId") UUID actorId,
		@Param("action") String action,
		@Param("targetType") String targetType,
		@Param("targetId") UUID targetId,
		@Param("from") Instant from,
		@Param("to") Instant to);
}
