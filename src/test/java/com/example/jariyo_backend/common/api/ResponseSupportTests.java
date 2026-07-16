package com.example.jariyo_backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ResponseSupportTests {
	@Test
	void okWrapsSuccessResponse() {
		ResponseEntity<ApiResponse<String>> response = ResponseSupport.ok("hello");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isTrue();
		assertThat(response.getBody().data()).isEqualTo("hello");
	}

	@Test
	void errorWrapsFailureResponse() {
		ResponseEntity<ApiResponse<Void>> response = ResponseSupport.error(HttpStatus.BAD_REQUEST, "COMMON-400", "bad");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().error().code()).isEqualTo("COMMON-400");
	}
}
