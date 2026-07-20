package com.zentrapay.repository;

import com.zentrapay.entity.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /** Unprocessed events, oldest first — used by reconciliation/replay. */
    Page<WebhookEvent> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);
}
