package com.hf.easydelivery.integration.ingestion.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UpstreamPartnerRepository extends JpaRepository<UpstreamPartnerEntity, Long> {
    Optional<UpstreamPartnerEntity> findByPartnerCodeAndStatus(String partnerCode, String status);
}
