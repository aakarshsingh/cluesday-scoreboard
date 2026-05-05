package com.cluesday.scoreboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CluesdayApplication {

	public static void main(String[] args) {
		SpringApplication.run(CluesdayApplication.class, args);
	}

}
