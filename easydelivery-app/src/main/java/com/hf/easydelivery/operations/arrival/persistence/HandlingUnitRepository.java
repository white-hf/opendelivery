package com.hf.easydelivery.operations.arrival.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HandlingUnitRepository extends JpaRepository<HandlingUnitEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM HandlingUnitEntity u WHERE u.id = :id")
    Optional<HandlingUnitEntity> findByIdForUpdate(@Param("id") long id);
}
