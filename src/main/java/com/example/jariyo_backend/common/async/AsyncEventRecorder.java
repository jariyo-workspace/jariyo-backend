package com.example.jariyo_backend.common.async;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncEventRecorder {
	private final AsyncEventOutboxRepository outboxRepository;
	private final AsyncEventPayloadSerializer payloadSerializer;

	public AsyncEventRecorder(AsyncEventOutboxRepository outboxRepository, AsyncEventPayloadSerializer payloadSerializer) {
		this.outboxRepository = outboxRepository;
		this.payloadSerializer = payloadSerializer;
	}

	@Transactional
	public void record(AsyncEventType type, UUID storeId, String referenceType, UUID referenceId, Object payload) {
		outboxRepository.save(new AsyncEventOutbox(type, storeId, referenceType, referenceId,
			payloadSerializer.serialize(payload)));
	}
}
