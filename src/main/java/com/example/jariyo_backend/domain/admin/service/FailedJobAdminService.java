package com.example.jariyo_backend.domain.admin.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.async.AsyncEventOutbox;
import com.example.jariyo_backend.common.async.AsyncEventOutboxRepository;
import com.example.jariyo_backend.common.async.AsyncEventStatus;
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
	public FailedJobListResult list(UUID userId, UUID storeId, AsyncEventType type, FailedJobStatus status,
		Instant from, Instant to, String cursor, Integer limit) {
		storeAuthorizationService.requireManager(userId, storeId);
		int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
		List<FailedAsyncJob> jobs = failedJobRepository.findAllByStoreIdAndFilters(storeId, type, status, from, to);
		int startIndex = resolveCursorIndex(jobs, cursor);
		List<FailedJobSummary> items = jobs.stream()
			.skip(startIndex)
			.limit(max)
			.map(this::summary)
			.toList();
		boolean hasNext = startIndex + items.size() < jobs.size();
		String nextCursor = hasNext ? items.get(items.size() - 1).id().toString() : null;
		return new FailedJobListResult(items, new ApiResponse.PageBody(nextCursor, hasNext));
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
				requireFailed(job);
				AsyncEventOutbox event = outboxRepository.findFirstByTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
					job.getType(), job.getReferenceType(), job.getReferenceId())
					.orElseThrow(() -> new BusinessException(ErrorCode.FAILED_JOB_NOT_FOUND));
				if (event.getStatus() == AsyncEventStatus.PROCESSED) {
					job.markResolved();
					return new RetryFailedJobResult(jobId, job.getStatus());
				}
				event.requeue();
				job.markPending();
				return new RetryFailedJobResult(jobId, job.getStatus());
			});
	}

	@Transactional
	public IgnoreFailedJobResult ignore(UUID userId, UUID storeId, UUID jobId, IgnoreFailedJobCommand command) {
		storeAuthorizationService.requireManager(userId, storeId);
		FailedAsyncJob job = requireJob(storeId, jobId);
		requireFailed(job);
		job.markIgnored(command.reason());
		return new IgnoreFailedJobResult(jobId, job.getStatus(), job.getIgnoredReason());
	}

	private FailedAsyncJob requireJob(UUID storeId, UUID jobId) {
		return failedJobRepository.findByIdAndStoreId(jobId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FAILED_JOB_NOT_FOUND));
	}

	private FailedJobSummary summary(FailedAsyncJob job) {
		return new FailedJobSummary(job.getId(), job.getType(), job.getReferenceType(), job.getReferenceId(),
			job.getStatus(), job.getAttemptCount(), new ErrorSummary(job.getLastErrorCode(), job.getLastErrorMessage()),
			job.getFailedAt(), job.getIgnoredReason());
	}

	private FailedJobDetail detail(FailedAsyncJob job, AsyncEventOutbox outbox) {
		return new FailedJobDetail(job.getId(), job.getType(), job.getReferenceType(), job.getReferenceId(),
			job.getStatus(), job.getAttemptCount(), new ErrorSummary(job.getLastErrorCode(), job.getLastErrorMessage()),
			job.getFailedAt(), job.getIgnoredReason(), outbox == null ? null : outbox.getId(),
			outbox == null ? null : outbox.getStatus().name(), outbox == null ? null : outbox.getPayloadJson());
	}

	private int resolveCursorIndex(List<FailedAsyncJob> jobs, String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		UUID cursorId;
		try {
			cursorId = UUID.fromString(cursor.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "cursor 형식이 올바르지 않습니다.");
		}
		for (int index = 0; index < jobs.size(); index++) {
			if (cursorId.equals(jobs.get(index).getId())) {
				return index + 1;
			}
		}
		return jobs.size();
	}

	private void requireFailed(FailedAsyncJob job) {
		if (job.getStatus() != FailedJobStatus.FAILED) {
			throw new BusinessException(ErrorCode.FAILED_JOB_INVALID_STATE);
		}
	}

	public record ErrorSummary(String code, String message) { }

	public record FailedJobSummary(UUID id, AsyncEventType type, String referenceType, UUID referenceId,
		FailedJobStatus status, int attemptCount, ErrorSummary lastError, Instant failedAt, String ignoredReason) { }

	public record FailedJobListResult(List<FailedJobSummary> items, ApiResponse.PageBody page) { }

	public record FailedJobDetail(UUID id, AsyncEventType type, String referenceType, UUID referenceId,
		FailedJobStatus status, int attemptCount, ErrorSummary lastError, Instant failedAt, String ignoredReason,
		UUID outboxEventId, String outboxStatus, String payloadJson) { }

	public record RetryFailedJobCommand(UUID storeId, UUID jobId) { }

	public record RetryFailedJobResult(UUID jobId, FailedJobStatus status) { }

	public record IgnoreFailedJobCommand(String reason) { }

	public record IgnoreFailedJobResult(UUID jobId, FailedJobStatus status, String ignoredReason) { }
}
