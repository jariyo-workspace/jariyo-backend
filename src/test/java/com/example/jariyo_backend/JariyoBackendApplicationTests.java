package com.example.jariyo_backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JariyoBackendApplicationTests {

	@Test
	void applicationClassIsAvailable() {
		assertDoesNotThrow(() -> Class.forName(JariyoBackendApplication.class.getName()));
	}

}
