package com.skillbridge.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
class GatewayServiceApplicationTests {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void contextLoads() {
	}

	@Test
	void shouldExposeExpectedRouteIds() {
		List<String> routeIds = routeLocator.getRoutes()
				.map(Route::getId)
				.collectList()
				.block(Duration.ofSeconds(3));

		assertThat(routeIds).isNotNull();
		assertThat(routeIds).containsExactlyInAnyOrder(
				"auth-service",
				"user-service",
				"proposal-service-by-job",
				"job-service",
				"proposal-service",
				"contract-service",
				"contract-service-milestones",
				"notification-service"
		);
	}

}
