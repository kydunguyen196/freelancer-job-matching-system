package com.skillbridge.contract_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

import com.skillbridge.contract_service.config.JwtProperties;
import com.skillbridge.contract_service.config.SecurityConfig;
import com.skillbridge.contract_service.dto.ContractResponse;
import com.skillbridge.contract_service.dto.MilestoneResponse;
import com.skillbridge.contract_service.security.JwtAuthenticationFilter;
import com.skillbridge.contract_service.service.ContractService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = {ContractController.class, MilestoneController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=abcdefghijklmnopqrstuvwxyz123456",
        "app.jwt.access-token-expiration-ms=900000",
        "app.jwt.refresh-token-expiration-ms=604800000",
        "app.internal.api-key=test-internal-key"
})
class ContractControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContractService contractService;

    @Test
    void createFromProposalShouldRejectInvalidInternalApiKey() throws Exception {
        mockMvc.perform(post("/contracts/internal/from-proposal")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "proposalId":1,
                                  "jobId":2,
                                  "clientId":3,
                                  "freelancerId":4,
                                  "milestoneAmount":100,
                                  "durationDays":10
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFromProposalShouldValidatePayload() throws Exception {
        mockMvc.perform(post("/contracts/internal/from-proposal")
                        .header("X-Internal-Api-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "proposalId":1,
                                  "jobId":2,
                                  "clientId":3,
                                  "freelancerId":4,
                                  "milestoneAmount":0,
                                  "durationDays":0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMilestoneShouldValidateInput() throws Exception {
        mockMvc.perform(post("/contracts/10/milestones")
                        .header("Authorization", "Bearer " + tokenFor(3L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":" ",
                                  "amount":100,
                                  "dueDate":"2000-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeMilestoneShouldRequireAuthentication() throws Exception {
        mockMvc.perform(patch("/milestones/20/complete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyContractsShouldAllowAuthenticatedUser() throws Exception {
        when(contractService.getMyContracts(any())).thenReturn(List.of(new ContractResponse(
                1L,
                10L,
                20L,
                30L,
                40L,
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                null,
                List.of(new MilestoneResponse(
                        99L,
                        1L,
                        "Default milestone",
                        BigDecimal.valueOf(100),
                        LocalDate.now().plusDays(1),
                        "PENDING",
                        null,
                        Instant.now(),
                        Instant.now()
                ))
        )));

        mockMvc.perform(get("/contracts/me")
                        .header("Authorization", "Bearer " + tokenFor(30L, "CLIENT")))
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
