package com.hf.easydelivery.operations.dispatch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverTaskItemRepository extends JpaRepository<DriverTaskItemEntity, Long> {
    List<DriverTaskItemEntity> findByTaskId(Long taskId);
    Optional<DriverTaskItemEntity> findByTaskIdAndParcelId(Long taskId, Long parcelId);
    List<DriverTaskItemEntity> findByParcelId(Long parcelId);
}
