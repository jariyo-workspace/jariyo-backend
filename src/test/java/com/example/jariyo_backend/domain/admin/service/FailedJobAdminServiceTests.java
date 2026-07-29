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
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.idempotency.PersistentIdempotencyService;
import com.example.jariyo_backend.domain.user.service.StoreAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

		setField(first, "id", UUID.randomUUID());
		setField(second, "id", UUID.randomUUID());
		FailedJobAdminService.FailedJobListResult result = service().list(userId, storeId, null, null, null, null, null, 1);

		assertEquals(1, result.items().size());
		assertEquals(result.items().get(0).id().toString(), result.page().cursor());
		assertEquals(true, result.page().hasNext());
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

		assertEquals(jobId, result.jobId());
		assertEquals(FailedJobStatus.PENDING, result.status());
		assertEquals(AsyncEventStatus.PENDING, outbox.getStatus());
		assertEquals(FailedJobStatus.PENDING, job.getStatus());
	}

	@Test
	void ignoreMarksFailedJobIgnored() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		FailedAsyncJob job = failedJob(storeId);
		when(failedJobRepository.findByIdAndStoreId(jobId, storeId)).thenReturn(Optional.of(job));

		FailedJobAdminService.IgnoreFailedJobResult result = service().ignore(userId, storeId, jobId,
			new FailedJobAdminService.IgnoreFailedJobCommand("고객에게 전화로 직접 안내 완료"));

		assertEquals(jobId, result.jobId());
		assertEquals(FailedJobStatus.IGNORED, result.status());
		assertEquals("고객에게 전화로 직접 안내 완료", result.ignoredReason());
		assertEquals(FailedJobStatus.IGNORED, job.getStatus());
	}

	@Test
	void retryRejectsIgnoredJob() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		FailedAsyncJob job = failedJob(storeId);
		job.markIgnored("이미 수동 처리됨");
		when(failedJobRepository.findByIdAndStoreId(jobId, storeId)).thenReturn(Optional.of(job));
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<FailedJobAdminService.RetryFailedJobResult> action = invocation.getArgument(5);
			return action.get();
		}).when(idempotencyService).execute(any(), any(), any(), any(),
			eq(FailedJobAdminService.RetryFailedJobResult.class), any());

		assertThrows(BusinessException.class, () -> service().retry(userId, storeId, jobId, "key"));
	}

	@Test
	void listUsesCursorToAdvancePage() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		FailedAsyncJob first = failedJob(storeId);
		FailedAsyncJob second = failedJob(storeId);
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		setField(first, "id", firstId);
		setField(second, "id", secondId);
		when(failedJobRepository.findAllByStoreIdAndFilters(storeId, null, null, null, null))
			.thenReturn(List.of(first, second));

		FailedJobAdminService.FailedJobListResult result =
			service().list(userId, storeId, null, null, null, null, firstId.toString(), 10);

		assertEquals(1, result.items().size());
		assertEquals(secondId, result.items().get(0).id());
		assertEquals(null, result.page().cursor());
		assertEquals(false, result.page().hasNext());
	}

	private FailedJobAdminService service() {
		return new FailedJobAdminService(storeAuthorizationService, failedJobRepository, outboxRepository,
			idempotencyService);
	}

	private FailedAsyncJob failedJob(UUID storeId) {
		return new FailedAsyncJob(storeId, AsyncEventType.SLOT_OFFER_CREATED, "SLOT_OFFER", UUID.randomUUID(), 2,
			"Timeout", "dispatch failed", Instant.parse("2026-07-26T01:00:00Z"));
	}

	private void setField(Object target, String name, Object value) {
		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
