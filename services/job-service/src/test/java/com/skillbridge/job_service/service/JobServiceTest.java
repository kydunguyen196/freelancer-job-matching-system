package com.skillbridge.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.repository.JobRepository;
import com.skillbridge.job_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void createJobShouldRejectNonClientRole() {
        CreateJobRequest request = new CreateJobRequest(
                "Build API",
                "Need backend implementation",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(200),
                List.of("java")
        );
        JwtUserPrincipal freelancer = new JwtUserPrincipal(5L, "freelancer@example.com", "FREELANCER");

        assertThatThrownBy(() -> jobService.createJob(request, freelancer))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createJobShouldNormalizeTagsAndSetOwner() {
        CreateJobRequest request = new CreateJobRequest(
                " Build API ",
                " Create microservice ",
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(500),
                List.of("Java", " spring ", "JAVA")
        );
        JwtUserPrincipal client = new JwtUserPrincipal(77L, "client@example.com", "CLIENT");

        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(900L);
            job.setCreatedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            return job;
        });

        JobResponse response = jobService.createJob(request, client);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job saved = captor.getValue();
        assertThat(saved.getClientId()).isEqualTo(77L);
        assertThat(saved.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(saved.getTags()).containsExactly("java", "spring");
        assertThat(saved.getTitle()).isEqualTo("Build API");
        assertThat(saved.getDescription()).isEqualTo("Create microservice");

        assertThat(response.clientId()).isEqualTo(77L);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.tags()).containsExactly("java", "spring");
    }

    @Test
    void closeJobShouldRejectNonOwnerClient() {
        Job existing = new Job();
        existing.setId(10L);
        existing.setClientId(999L);
        existing.setStatus(JobStatus.OPEN);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(existing));

        JwtUserPrincipal anotherClient = new JwtUserPrincipal(12L, "client2@example.com", "CLIENT");

        assertThatThrownBy(() -> jobService.closeJob(10L, anotherClient))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void listJobsShouldRejectInvalidBudgetRange() {
        assertThatThrownBy(() -> jobService.listJobs(
                null,
                JobStatus.OPEN,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(100),
                null,
                List.of(),
                0,
                20
        )).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
