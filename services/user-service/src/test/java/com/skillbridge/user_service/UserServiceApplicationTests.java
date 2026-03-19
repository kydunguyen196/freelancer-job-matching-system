package com.skillbridge.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:userdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.jwt.secret=abcdefghijklmnopqrstuvwxyz123456",
		"app.jwt.access-token-expiration-ms=900000",
		"app.jwt.refresh-token-expiration-ms=604800000"
})
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
