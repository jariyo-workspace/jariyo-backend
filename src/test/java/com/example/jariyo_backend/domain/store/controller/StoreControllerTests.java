package com.example.jariyo_backend.domain.store.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.example.jariyo_backend.domain.store.service.StoreQueryService;
import com.example.jariyo_backend.domain.store.service.StoreSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StoreControllerTests {
	private StoreQueryService storeQueryService;
	private StoreSettingsService storeSettingsService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		storeQueryService = mock();
		storeSettingsService = mock();
		mockMvc = MockMvcBuilders.standaloneSetup(new StoreController(storeQueryService, storeSettingsService)).build();
	}

	@Test
	void getStoreReturnsStoreDetail() throws Exception {
		UUID storeId = UUID.fromString("00000000-0000-7000-8000-000000000001");
		given(storeQueryService.getStore(storeId)).willReturn(new StoreQueryService.StoreDetail(storeId, "자리요 헤어",
			"예약과 현장 대기가 가능한 헤어숍", "053-123-4567", "대구광역시 중구 달구벌대로 123", "Asia/Seoul", "ACTIVE",
			List.of(), null));

		mockMvc.perform(get("/api/v1/stores/{storeId}", storeId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.name").value("자리요 헤어"));
	}
}
