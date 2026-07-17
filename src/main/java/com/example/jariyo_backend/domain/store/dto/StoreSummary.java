package com.example.jariyo_backend.domain.store.dto;

import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.Store;

public record StoreSummary(UUID id, String name, String description, String phoneNumber, String address, String timezone,
	String status) {
	public static StoreSummary from(Store store) {
		return new StoreSummary(store.getId(), store.getName(), store.getDescription(), store.getPhoneNumber(),
			store.getAddress(), store.getTimezone(), store.getStatus().name());
	}
}
