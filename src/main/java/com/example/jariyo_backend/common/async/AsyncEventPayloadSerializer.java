package com.example.jariyo_backend.common.async;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AsyncEventPayloadSerializer {
	private final ObjectMapper objectMapper;

	public AsyncEventPayloadSerializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String serialize(Object payload) {
		return objectMapper.writeValueAsString(payload);
	}
}
