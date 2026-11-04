package com.example.sporty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FacilityServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FacilityServiceApplication.class, args);
	}

}
