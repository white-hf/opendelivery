package com.hf.easydelivery.integration.routing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StationServiceAreaRepository extends JpaRepository<StationServiceAreaEntity, Long> {
    List<StationServiceAreaEntity> findByStatus(String status);
}
