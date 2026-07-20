package com.zentrapay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZentrapayBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZentrapayBackendApplication.class, args);
	}

}
