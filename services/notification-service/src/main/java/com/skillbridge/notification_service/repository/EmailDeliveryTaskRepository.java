package com.skillbridge.notification_service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.notification_service.domain.EmailDeliveryStatus;
import com.skillbridge.notification_service.domain.EmailDeliveryTask;

public interface EmailDeliveryTaskRepository extends JpaRepository<EmailDeliveryTask, Long> {

    List<EmailDeliveryTask> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Collection<EmailDeliveryStatus> statuses,
            Instant nextAttemptAt
    );
}
