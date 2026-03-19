package com.skillbridge.job_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    java.util.List<Job> findByClientIdOrderByUpdatedAtDesc(Long clientId);

    long countByClientId(Long clientId);

    long countByClientIdAndStatus(Long clientId, JobStatus status);
}
