package com.hf.easydelivery.operations.reconciliation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanSessionRepository extends JpaRepository<ScanSessionEntity, Long> {
    List<ScanSessionEntity> findByTaskId(Long taskId);
    List<ScanSessionEntity> findByTaskIdAndStatus(Long taskId, String status);
}
