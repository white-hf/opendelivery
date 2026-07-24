package com.hf.easydelivery.operations.supervision.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, Long> {
    List<DeliveryAttemptEntity> findByParcelId(Long parcelId);
    List<DeliveryAttemptEntity> findByTaskId(Long taskId);
}
