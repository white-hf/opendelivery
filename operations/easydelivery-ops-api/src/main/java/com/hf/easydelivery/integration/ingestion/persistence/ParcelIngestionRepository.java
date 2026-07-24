package com.hf.easydelivery.integration.ingestion.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ParcelIngestionRepository extends JpaRepository<ParcelIngestionEntity, Long> {
    Optional<ParcelIngestionEntity> findByTrackingNo(String trackingNo);
    List<ParcelIngestionEntity> findByWaybillId(long waybillId);
}
