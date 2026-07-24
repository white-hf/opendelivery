package com.hf.easydelivery.operations.arrival.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArrivalTripRepository extends JpaRepository<ArrivalTripEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ArrivalTripEntity t WHERE t.id = :id")
    Optional<ArrivalTripEntity> findByIdForUpdate(@Param("id") long id);
}
