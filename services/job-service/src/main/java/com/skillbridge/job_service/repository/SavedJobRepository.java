package com.skillbridge.job_service.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.skillbridge.job_service.domain.SavedJob;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    Page<SavedJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    void deleteByUserIdAndJobId(Long userId, Long jobId);

    long countByJobOwnerClientId(Long jobOwnerClientId);

    @Query("select savedJob.userId from SavedJob savedJob where savedJob.jobId = :jobId")
    List<Long> findUserIdsByJobId(Long jobId);

    @Query("select savedJob.jobId from SavedJob savedJob where savedJob.userId = :userId and savedJob.jobId in :jobIds")
    List<Long> findSavedJobIds(Long userId, Collection<Long> jobIds);
}
