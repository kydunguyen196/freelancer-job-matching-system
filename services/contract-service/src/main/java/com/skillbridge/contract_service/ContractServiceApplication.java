package com.skillbridge.contract_service;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContractServiceApplication {

	public static void main(String[] args) {
		normalizeTimezoneAlias();
		SpringApplication.run(ContractServiceApplication.class, args);
	}

	private static void normalizeTimezoneAlias() {
		if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		}
	}

}
