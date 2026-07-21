package com.hf.easydelivery.operations.dayclose.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyReconciliationRepository extends JpaRepository<DailyReconciliationEntity, Long> {
    Optional<DailyReconciliationEntity> findByStationIdAndBusinessDate(Long stationId, LocalDate businessDate);
}
