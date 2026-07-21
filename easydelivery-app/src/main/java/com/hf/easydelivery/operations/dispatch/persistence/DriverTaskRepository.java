package com.hf.easydelivery.operations.dispatch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverTaskRepository extends JpaRepository<DriverTaskEntity, Long> {
    List<DriverTaskEntity> findByWaveId(Long waveId);
    List<DriverTaskEntity> findByStationIdAndServiceDate(Long stationId, LocalDate serviceDate);
    Optional<DriverTaskEntity> findByTaskCode(String taskCode);
}
