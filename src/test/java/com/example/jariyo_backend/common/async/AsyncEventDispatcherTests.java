package com.example.jariyo_backend.common.async;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncEventDispatcherTests {
	@Mock AsyncEventOutboxRepository outboxRepository;
	@Mock FailedAsyncJobRepository failedJobRepository;
	@Mock AsyncEventGateway asyncEventGateway;

	@Test
	void dispatchesPendingEventAndResolvesFailedJob() throws Exception {
		AsyncEventOutbox event = event(AsyncEventStatus.PENDING);
		FailedAsyncJob failedJob = new FailedAsyncJob(event.getStoreId(), event.getType(), event.getReferenceType(),
			event.getReferenceId(), 1, "Timeout", "dispatch failed", Instant.parse("2026-07-26T00:00:00Z"));
		when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(AsyncEventStatus.PENDING)).thenReturn(List.of(event));
		when(outboxRepository.findTop50ByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(AsyncEventStatus.FAILED, 3))
			.thenReturn(List.of());
		when(outboxRepository.findLockedById(event.getId())).thenReturn(event);
		when(failedJobRepository.findByTypeAndReferenceTypeAndReferenceId(event.getType(), event.getReferenceType(),
			event.getReferenceId())).thenReturn(Optional.of(failedJob));

		new AsyncEventDispatcher(outboxRepository, failedJobRepository, asyncEventGateway).dispatchPendingEvents();

		assertEquals(AsyncEventStatus.PROCESSED, event.getStatus());
		assertEquals(FailedJobStatus.RESOLVED, failedJob.getStatus());
		verify(failedJobRepository, never()).save(any());
	}

	@Test
	void recordsFailureWhenDispatchThrows() throws Exception {
		AsyncEventOutbox event = event(AsyncEventStatus.PENDING);
		when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(AsyncEventStatus.PENDING)).thenReturn(List.of(event));
		when(outboxRepository.findTop50ByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(AsyncEventStatus.FAILED, 3))
			.thenReturn(List.of());
		when(outboxRepository.findLockedById(event.getId())).thenReturn(event);
		when(failedJobRepository.findByTypeAndReferenceTypeAndReferenceId(event.getType(), event.getReferenceType(),
			event.getReferenceId())).thenReturn(Optional.empty());
		doThrow(new IllegalStateException("gateway down")).when(asyncEventGateway).dispatch(event);

		new AsyncEventDispatcher(outboxRepository, failedJobRepository, asyncEventGateway).dispatchPendingEvents();

		assertEquals(AsyncEventStatus.FAILED, event.getStatus());
		assertEquals(1, event.getAttemptCount());
		verify(failedJobRepository).save(any(FailedAsyncJob.class));
	}

	@Test
	void retriesFailedEventBelowAttemptLimit() throws Exception {
		AsyncEventOutbox event = event(AsyncEventStatus.FAILED);
		event.markFailed("Timeout", "first failure");
		when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(AsyncEventStatus.PENDING)).thenReturn(List.of());
		when(outboxRepository.findTop50ByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(AsyncEventStatus.FAILED, 3))
			.thenReturn(List.of(event));
		when(outboxRepository.findLockedById(event.getId())).thenReturn(event);
		when(failedJobRepository.findByTypeAndReferenceTypeAndReferenceId(event.getType(), event.getReferenceType(),
			event.getReferenceId())).thenReturn(Optional.empty());

		new AsyncEventDispatcher(outboxRepository, failedJobRepository, asyncEventGateway).dispatchPendingEvents();

		assertEquals(AsyncEventStatus.PROCESSED, event.getStatus());
	}

	private AsyncEventOutbox event(AsyncEventStatus status) throws Exception {
		AsyncEventOutbox event = new AsyncEventOutbox(AsyncEventType.RESERVATION_CANCELLED, UUID.randomUUID(),
			"RESERVATION", UUID.randomUUID(), "{\"reservationId\":\"res-1\"}");
		if (status == AsyncEventStatus.FAILED) {
			event.markFailed("Timeout", "failed");
		}
		setField(event, "id", UUID.randomUUID());
		setField(event, "status", status);
		return event;
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		var field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
