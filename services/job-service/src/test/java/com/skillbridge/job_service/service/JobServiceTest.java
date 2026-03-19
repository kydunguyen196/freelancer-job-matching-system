package com.skillbridge.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import com.skillbridge.job_service.domain.EmploymentType;
import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.domain.SavedJob;
import com.skillbridge.job_service.dto.CreateJobRequest;
import com.skillbridge.job_service.dto.JobResponse;
import com.skillbridge.job_service.repository.FollowedCompanyRepository;
import com.skillbridge.job_service.repository.JobRepository;
import com.skillbridge.job_service.repository.SavedJobRepository;
import com.skillbridge.job_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private SavedJobRepository savedJobRepository;

    @Mock
    private FollowedCompanyRepository followedCompanyRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void createJobShouldRejectNonClientRole() {
        CreateJobRequest request = new CreateJobRequest(
                "Build API",
                "Need backend implementation",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(200),
                List.of("java"),
                "Acme",
                "Ho Chi Minh",
                EmploymentType.FULL_TIME,
                true,
                3,
                JobStatus.OPEN,
                Instant.now().plusSeconds(86_400)
        );
        JwtUserPrincipal freelancer = new JwtUserPrincipal(5L, "freelancer@example.com", "FREELANCER");

        assertThatThrownBy(() -> jobService.createJob(request, freelancer))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createJobShouldNormalizeFieldsAndSetOwner() {
        CreateJobRequest request = new CreateJobRequest(
                " Build API ",
                " Create microservice ",
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(500),
                List.of("Java", " spring ", "JAVA"),
                " Acme Corp ",
                " Ho Chi Minh City ",
                EmploymentType.FULL_TIME,
                true,
                4,
                JobStatus.DRAFT,
                Instant.now().plusSeconds(86_400)
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
        assertThat(saved.getStatus()).isEqualTo(JobStatus.DRAFT);
        assertThat(saved.getTags()).containsExactly("java", "spring");
        assertThat(saved.getTitle()).isEqualTo("Build API");
        assertThat(saved.getDescription()).isEqualTo("Create microservice");
        assertThat(saved.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(saved.getLocation()).isEqualTo("Ho Chi Minh City");
        assertThat(saved.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(saved.isRemote()).isTrue();
        assertThat(saved.getExperienceYears()).isEqualTo(4);

        assertThat(response.clientId()).isEqualTo(77L);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.tags()).containsExactly("java", "spring");
        assertThat(response.companyName()).isEqualTo("Acme Corp");
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
                null,
                null,
                null,
                null,
                null,
                null,
                "latest",
                0,
                20,
                null
        )).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveJobShouldCreateSavedRecordForFreelancer() {
        Job existing = new Job();
        existing.setId(10L);
        existing.setClientId(999L);
        existing.setTitle("Build API");
        existing.setDescription("Need backend");
        existing.setBudgetMin(BigDecimal.valueOf(100));
        existing.setBudgetMax(BigDecimal.valueOf(200));
        existing.setEmploymentType(EmploymentType.CONTRACT);
        existing.setStatus(JobStatus.OPEN);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(jobRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(savedJobRepository.existsByUserIdAndJobId(50L, 10L)).thenReturn(false).thenReturn(true);
        JwtUserPrincipal freelancer = new JwtUserPrincipal(50L, "freelancer@example.com", "FREELANCER");

        JobResponse response = jobService.saveJob(10L, freelancer);

        ArgumentCaptor<SavedJob> captor = ArgumentCaptor.forClass(SavedJob.class);
        verify(savedJobRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(50L);
        assertThat(captor.getValue().getJobId()).isEqualTo(10L);
        assertThat(captor.getValue().getJobOwnerClientId()).isEqualTo(999L);
        assertThat(response.savedByCurrentUser()).isTrue();
    }

    @Test
    void saveJobShouldRejectClientRole() {
        JwtUserPrincipal client = new JwtUserPrincipal(99L, "client@example.com", "CLIENT");

        assertThatThrownBy(() -> jobService.saveJob(10L, client))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(savedJobRepository, never()).save(any(SavedJob.class));
    }
}
