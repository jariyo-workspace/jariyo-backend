package com.example.jariyo_backend.common.async;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncEventDispatcher {
	private static final int MAX_RETRY_ATTEMPTS = 3;

	private final AsyncEventOutboxRepository outboxRepository;
	private final FailedAsyncJobRepository failedJobRepository;
	private final AsyncEventGateway asyncEventGateway;

	public AsyncEventDispatcher(AsyncEventOutboxRepository outboxRepository,
		FailedAsyncJobRepository failedJobRepository, AsyncEventGateway asyncEventGateway) {
		this.outboxRepository = outboxRepository;
		this.failedJobRepository = failedJobRepository;
		this.asyncEventGateway = asyncEventGateway;
	}

	@Scheduled(fixedDelay = 5000)
	@Transactional
	public void dispatchPendingEvents() {
		dispatch(AsyncEventStatus.PENDING, Integer.MAX_VALUE);
		dispatch(AsyncEventStatus.FAILED, MAX_RETRY_ATTEMPTS);
	}

	private void dispatch(AsyncEventStatus status, int attemptCountLimit) {
		var candidates = status == AsyncEventStatus.PENDING
			? outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(status)
			: outboxRepository.findTop50ByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(status, attemptCountLimit);
		for (AsyncEventOutbox candidate : candidates) {
			dispatchSingle(candidate);
		}
	}

	private void dispatchSingle(AsyncEventOutbox candidate) {
		AsyncEventOutbox event = outboxRepository.findLockedById(candidate.getId());
		try {
			asyncEventGateway.dispatch(event);
			event.markProcessed(Instant.now());
			failedJobRepository.findByTypeAndReferenceTypeAndReferenceId(event.getType(), event.getReferenceType(),
				event.getReferenceId()).ifPresent(FailedAsyncJob::markResolved);
		} catch (RuntimeException exception) {
			String errorCode = exception.getClass().getSimpleName();
			String errorMessage = exception.getMessage() == null ? "Async dispatch failed" : exception.getMessage();
			event.markFailed(errorCode, errorMessage);
			failedJobRepository.findByTypeAndReferenceTypeAndReferenceId(event.getType(), event.getReferenceType(),
				event.getReferenceId())
				.ifPresentOrElse(
					job -> job.refreshFailure(event.getAttemptCount(), errorCode, errorMessage, Instant.now()),
					() -> failedJobRepository.save(new FailedAsyncJob(event.getStoreId(), event.getType(),
						event.getReferenceType(), event.getReferenceId(), event.getAttemptCount(), errorCode,
						errorMessage, Instant.now()))
				);
		}
	}
}
