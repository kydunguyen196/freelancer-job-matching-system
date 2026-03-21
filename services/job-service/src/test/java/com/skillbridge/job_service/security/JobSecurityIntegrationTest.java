package com.skillbridge.job_service.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.skillbridge.job_service.config.JwtProperties;
import com.skillbridge.job_service.config.SecurityConfig;
import com.skillbridge.job_service.controller.JobController;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.dto.PagedResult;
import com.skillbridge.job_service.service.JobSearchAdminService;
import com.skillbridge.job_service.service.JobService;
import com.skillbridge.job_service.service.RecruiterReportService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = JobController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=abcdefghijklmnopqrstuvwxyz123456",
        "app.jwt.access-token-expiration-ms=900000",
        "app.jwt.refresh-token-expiration-ms=604800000",
        "app.internal.api-key=internal-key"
})
class JobSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobSearchAdminService jobSearchAdminService;

    @MockitoBean
    private RecruiterReportService recruiterReportService;

    @Test
    void createJobShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Build API",
                                  "description":"Need backend",
                                  "budgetMin":100,
                                  "budgetMax":200,
                                  "tags":["java"],
                                  "employmentType":"FULL_TIME"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createJobShouldRejectFreelancerRole() throws Exception {
        mockMvc.perform(post("/jobs")
                        .header("Authorization", "Bearer " + tokenFor(50L, "FREELANCER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Build API",
                                  "description":"Need backend",
                                  "budgetMin":100,
                                  "budgetMax":200,
                                  "tags":["java"],
                                  "employmentType":"FULL_TIME"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createJobShouldAllowClientRole() throws Exception {
        when(jobService.createJob(any(), any())).thenReturn(new JobResponse(
                1L,
                "Build API",
                "Need backend",
                null,
                null,
                null,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(200),
                List.of("java"),
                "OPEN",
                99L,
                "Acme",
                "HCM",
                "FULL_TIME",
                "REMOTE",
                true,
                3,
                null,
                "PUBLIC",
                1,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                false,
                false
        ));

        mockMvc.perform(post("/jobs")
                        .header("Authorization", "Bearer " + tokenFor(99L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Build API",
                                  "description":"Need backend",
                                  "budgetMin":100,
                                  "budgetMax":200,
                                  "tags":["java"],
                                  "employmentType":"FULL_TIME"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void listJobsShouldBePublic() throws Exception {
        when(jobService.listJobs(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("latest"),
                eq(0),
                eq(20),
                eq(null)
        )).thenReturn(new PagedResult<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk());
    }

    private String tokenFor(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor("abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("userId", userId)
                .claim("role", role)
                .signWith(key)
                .compact();
    }
}
