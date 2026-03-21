package com.skillbridge.job_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.domain.WorkMode;
import com.skillbridge.job_service.dto.UpdateJobRequest;
import com.skillbridge.job_service.repository.FollowedCompanyRepository;
import com.skillbridge.job_service.repository.JobRepository;
import com.skillbridge.job_service.repository.SavedJobRepository;
import com.skillbridge.job_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class JobServiceUpdateJobTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private SavedJobRepository savedJobRepository;
    @Mock
    private FollowedCompanyRepository followedCompanyRepository;
    @Mock
    private JobSearchService jobSearchService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(
                jobRepository,
                savedJobRepository,
                followedCompanyRepository,
                jobSearchService,
                "http://localhost:8086",
                "internal-key"
        );
    }

    @Test
    void updateJob_shouldRejectWhenJobIsClosed() {
        Job job = buildJob(1L, 100L, JobStatus.CLOSED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> jobService.updateJob(1L, buildUpdateRequest(), new JwtUserPrincipal(100L, "client@test.com", "CLIENT"))
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateJob_shouldApplyWorkModeAndRemote() {
        Job job = buildJob(1L, 100L, JobStatus.OPEN);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jobService.updateJob(
                1L,
                buildUpdateRequest(),
                new JwtUserPrincipal(100L, "client@test.com", "CLIENT")
        );

        assertEquals("HYBRID", response.workMode());
        assertEquals(true, response.remote());
    }

    private UpdateJobRequest buildUpdateRequest() {
        return new UpdateJobRequest(
                "Updated title",
                "Updated description",
                "Requirements",
                "Responsibilities",
                "Benefits",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                java.util.List.of("java", "spring"),
                "SkillBridge",
                "HCM",
                EmploymentType.CONTRACT,
                WorkMode.HYBRID,
                null,
                3,
                "Backend",
                com.skillbridge.job_service.domain.JobVisibility.PUBLIC,
                2,
                Instant.now().plusSeconds(3600)
        );
    }

    private Job buildJob(Long id, Long clientId, JobStatus status) {
        Job job = new Job();
        job.setId(id);
        job.setClientId(clientId);
        job.setStatus(status);
        job.setTitle("Old");
        job.setDescription("Old description");
        job.setBudgetMin(BigDecimal.valueOf(100));
        job.setBudgetMax(BigDecimal.valueOf(200));
        job.setEmploymentType(EmploymentType.CONTRACT);
        job.setWorkMode(WorkMode.ONSITE);
        job.setRemote(false);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
