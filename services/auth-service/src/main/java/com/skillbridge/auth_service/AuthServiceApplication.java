package com.skillbridge.auth_service;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		normalizeTimezoneAlias();
		SpringApplication.run(AuthServiceApplication.class, args);
	}

	private static void normalizeTimezoneAlias() {
		if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		}
	}

}
