package com.hf.easydelivery.operations.dispatch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchWaveRepository extends JpaRepository<DispatchWaveEntity, Long> {
    Optional<DispatchWaveEntity> findByStationIdAndWaveCode(Long stationId, String waveCode);
    List<DispatchWaveEntity> findByStationIdAndServiceDate(Long stationId, LocalDate serviceDate);
}
