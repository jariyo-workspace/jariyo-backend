package com.example.jariyo_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JariyoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(JariyoBackendApplication.class, args);
	}

}
