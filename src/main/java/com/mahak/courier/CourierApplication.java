package com.mahak.courier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CourierApplication {

	public static void main(String[] args) {
		SpringApplication.run(CourierApplication.class, args);
	}

}
