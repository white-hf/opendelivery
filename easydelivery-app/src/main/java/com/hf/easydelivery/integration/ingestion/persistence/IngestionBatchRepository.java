package com.hf.easydelivery.integration.ingestion.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatchEntity, Long> {
}
