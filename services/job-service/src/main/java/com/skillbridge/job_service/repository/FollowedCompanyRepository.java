package com.skillbridge.job_service.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.skillbridge.job_service.domain.FollowedCompany;

public interface FollowedCompanyRepository extends JpaRepository<FollowedCompany, Long> {

    boolean existsByFollowerUserIdAndClientId(Long followerUserId, Long clientId);

    Page<FollowedCompany> findByFollowerUserIdOrderByCreatedAtDesc(Long followerUserId, Pageable pageable);

    void deleteByFollowerUserIdAndClientId(Long followerUserId, Long clientId);

    long countByClientId(Long clientId);

    @Query("select company.followerUserId from FollowedCompany company where company.clientId = :clientId")
    List<Long> findFollowerUserIdsByClientId(Long clientId);

    @Query("select company.clientId from FollowedCompany company where company.followerUserId = :userId and company.clientId in :clientIds")
    List<Long> findFollowedClientIds(Long userId, Collection<Long> clientIds);
}
