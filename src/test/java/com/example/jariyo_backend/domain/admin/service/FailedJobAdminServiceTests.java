package com.example.jariyo_backend.domain.admin.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.async.AsyncEventOutbox;
import com.example.jariyo_backend.common.async.AsyncEventOutboxRepository;
import com.example.jariyo_backend.common.async.AsyncEventStatus;
import com.example.jariyo_backend.common.async.AsyncEventType;
import com.example.jariyo_backend.common.async.FailedAsyncJob;
import com.example.jariyo_backend.common.async.FailedAsyncJobRepository;
import com.example.jariyo_backend.common.async.FailedJobStatus;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailedJobAdminServiceTests {
	@Mock StoreAuthorizationService storeAuthorizationService;
	@Mock FailedAsyncJobRepository failedJobRepository;
	@Mock AsyncEventOutboxRepository outboxRepository;
	@Mock PersistentIdempotencyService idempotencyService;

	@Test
	void listsFailedJobsWithLimit() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		FailedAsyncJob first = failedJob(storeId);
		FailedAsyncJob second = failedJob(storeId);
		when(failedJobRepository.findAllByStoreIdAndFilters(storeId, null, null, null, null))
			.thenReturn(List.of(first, second));

		List<FailedJobAdminService.FailedJobSummary> summaries = service().list(userId, storeId, null, null, null, null, 1);

		assertEquals(1, summaries.size());
		verify(storeAuthorizationService).requireManager(userId, storeId);
	}

	@Test
	void retryRequeuesFailedOutboxEvent() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		FailedAsyncJob job = failedJob(storeId);
		AsyncEventOutbox outbox = new AsyncEventOutbox(AsyncEventType.SLOT_OFFER_CREATED, storeId, "SLOT_OFFER",
			job.getReferenceId(), "{\"slotOfferId\":\"1\"}");
		outbox.markFailed("Timeout", "dispatch failed");
		when(failedJobRepository.findByIdAndStoreId(jobId, storeId)).thenReturn(Optional.of(job));
		when(outboxRepository.findFirstByTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
			job.getType(), job.getReferenceType(), job.getReferenceId())).thenReturn(Optional.of(outbox));
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<FailedJobAdminService.RetryFailedJobResult> action = invocation.getArgument(5);
			return action.get();
		}).when(idempotencyService).execute(any(), any(), any(), any(),
			eq(FailedJobAdminService.RetryFailedJobResult.class), any());

		FailedJobAdminService.RetryFailedJobResult result = service().retry(userId, storeId, jobId, "key");

		assertEquals(AsyncEventStatus.PENDING, result.outboxStatus());
	}

	private FailedJobAdminService service() {
		return new FailedJobAdminService(storeAuthorizationService, failedJobRepository, outboxRepository,
			idempotencyService);
	}

	private FailedAsyncJob failedJob(UUID storeId) {
		return new FailedAsyncJob(storeId, AsyncEventType.SLOT_OFFER_CREATED, "SLOT_OFFER", UUID.randomUUID(), 2,
			"Timeout", "dispatch failed", Instant.parse("2026-07-26T01:00:00Z"));
	}
}
