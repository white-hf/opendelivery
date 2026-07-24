package com.hf.easydelivery.integration.outbox.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    Optional<OutboxEventEntity> findByEventKey(String eventKey);
    List<OutboxEventEntity> findByStatus(String status);
}
