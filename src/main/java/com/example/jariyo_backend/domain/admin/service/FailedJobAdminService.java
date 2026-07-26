package com.example.jariyo_backend.domain.admin.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventOutbox;
import com.example.jariyo_backend.common.async.AsyncEventOutboxRepository;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.async.FailedAsyncJob;
import com.example.jariyo_backend.common.async.FailedAsyncJobRepository;
import com.example.jariyo_backend.common.async.FailedJobStatus;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailedJobAdminService {
	private final StoreAuthorizationService storeAuthorizationService;
	private final FailedAsyncJobRepository failedJobRepository;
	private final AsyncEventOutboxRepository outboxRepository;
	private final PersistentIdempotencyService idempotencyService;

	public FailedJobAdminService(StoreAuthorizationService storeAuthorizationService,
		FailedAsyncJobRepository failedJobRepository, AsyncEventOutboxRepository outboxRepository,
		PersistentIdempotencyService idempotencyService) {
		this.storeAuthorizationService = storeAuthorizationService;
		this.failedJobRepository = failedJobRepository;
		this.outboxRepository = outboxRepository;
		this.idempotencyService = idempotencyService;
	}

	@Transactional(readOnly = true)
	public List<FailedJobSummary> list(UUID userId, UUID storeId, AsyncEventType type, FailedJobStatus status,
		Instant from, Instant to, Integer limit) {
		storeAuthorizationService.requireManager(userId, storeId);
		int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
		return failedJobRepository.findAllByStoreIdAndFilters(storeId, type, status, from, to).stream()
			.limit(max)
			.map(this::summary)
			.toList();
	}

	@Transactional(readOnly = true)
	public FailedJobDetail get(UUID userId, UUID storeId, UUID jobId) {
		storeAuthorizationService.requireManager(userId, storeId);
		FailedAsyncJob job = requireJob(storeId, jobId);
		AsyncEventOutbox outbox = outboxRepository.findFirstByTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
			job.getType(), job.getReferenceType(), job.getReferenceId()).orElse(null);
		return detail(job, outbox);
	}

	@Transactional
	public RetryFailedJobResult retry(UUID userId, UUID storeId, UUID jobId, String key) {
		return idempotencyService.execute(userId, "failed-job:retry:" + jobId, key, new RetryFailedJobCommand(storeId, jobId),
			RetryFailedJobResult.class, () -> {
				storeAuthorizationService.requireManager(userId, storeId);
				FailedAsyncJob job = requireJob(storeId, jobId);
				AsyncEventOutbox event = outboxRepository.findFirstByTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
					job.getType(), job.getReferenceType(), job.getReferenceId())
					.orElseThrow(() -> new BusinessException(ErrorCode.FAILED_JOB_NOT_FOUND));
				if (event.getStatus() == com.example.jariyo_backend.common.async.AsyncEventStatus.PROCESSED) {
					job.markResolved();
					return new RetryFailedJobResult(job.getId(), job.getStatus(), event.getStatus(), event.getProcessedAt());
				}
				event.requeue();
				return new RetryFailedJobResult(job.getId(), job.getStatus(), event.getStatus(), event.getProcessedAt());
			});
	}

	private FailedAsyncJob requireJob(UUID storeId, UUID jobId) {
		return failedJobRepository.findByIdAndStoreId(jobId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FAILED_JOB_NOT_FOUND));
	}

	private FailedJobSummary summary(FailedAsyncJob job) {
		return new FailedJobSummary(job.getId(), job.getType(), job.getReferenceType(), job.getReferenceId(),
			job.getStatus(), job.getAttemptCount(), new ErrorSummary(job.getLastErrorCode(), job.getLastErrorMessage()),
			job.getFailedAt());
	}

	private FailedJobDetail detail(FailedAsyncJob job, AsyncEventOutbox outbox) {
		return new FailedJobDetail(job.getId(), job.getType(), job.getReferenceType(), job.getReferenceId(),
			job.getStatus(), job.getAttemptCount(), new ErrorSummary(job.getLastErrorCode(), job.getLastErrorMessage()),
			job.getFailedAt(), outbox == null ? null : outbox.getId(), outbox == null ? null : outbox.getStatus().name(),
			outbox == null ? null : outbox.getPayloadJson());
	}

	public record ErrorSummary(String code, String message) { }

	public record FailedJobSummary(UUID id, AsyncEventType type, String referenceType, UUID referenceId,
		FailedJobStatus status, int attemptCount, ErrorSummary lastError, Instant failedAt) { }

	public record FailedJobDetail(UUID id, AsyncEventType type, String referenceType, UUID referenceId,
		FailedJobStatus status, int attemptCount, ErrorSummary lastError, Instant failedAt, UUID outboxEventId,
		String outboxStatus, String payloadJson) { }

	public record RetryFailedJobCommand(UUID storeId, UUID jobId) { }

	public record RetryFailedJobResult(UUID id, FailedJobStatus jobStatus,
		com.example.jariyo_backend.common.async.AsyncEventStatus outboxStatus, Instant processedAt) { }
}
