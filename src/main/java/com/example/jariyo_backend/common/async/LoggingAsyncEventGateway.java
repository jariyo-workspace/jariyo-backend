package com.example.jariyo_backend.common.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAsyncEventGateway implements AsyncEventGateway {
	private static final Logger log = LoggerFactory.getLogger(LoggingAsyncEventGateway.class);

	@Override
	public void dispatch(AsyncEventOutbox event) {
		log.info("Dispatch async event type={} referenceType={} referenceId={} payload={}",
			event.getType(), event.getReferenceType(), event.getReferenceId(), event.getPayloadJson());
	}
}
