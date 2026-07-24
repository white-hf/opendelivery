package com.hf.easydelivery.integration.ingestion.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WaybillRepository extends JpaRepository<WaybillEntity, Long> {
    Optional<WaybillEntity> findByPartnerIdAndExternalWaybillNo(long partnerId, String externalWaybillNo);
}
