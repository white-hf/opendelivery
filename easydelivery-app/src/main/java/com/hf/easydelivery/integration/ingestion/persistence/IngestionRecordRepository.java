package com.hf.easydelivery.integration.ingestion.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IngestionRecordRepository extends JpaRepository<IngestionRecordEntity, Long> {
    Optional<IngestionRecordEntity> findByPartnerIdAndExternalEventId(long partnerId, String externalEventId);
}
