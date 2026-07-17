package com.example.jariyo_backend.domain.store.dto;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.domain.store.entity.BusinessHour;
import com.example.jariyo_backend.domain.store.entity.Store;
import com.example.jariyo_backend.domain.store.entity.StorePolicy;

public record StoreDetail(UUID id, String name, String description, String phoneNumber, String address, String timezone,
	String status, List<BusinessHourSummary> businessHours, StorePolicySummary policySummary) {
	public static StoreDetail from(Store store, StorePolicy policy, List<BusinessHour> businessHours) {
		return new StoreDetail(store.getId(), store.getName(), store.getDescription(), store.getPhoneNumber(),
			store.getAddress(), store.getTimezone(), store.getStatus().name(),
			businessHours.stream().map(BusinessHourSummary::from).toList(),
			policy == null ? null : StorePolicySummary.from(policy));
	}
}
