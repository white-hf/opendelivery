package com.hf.easydelivery.operations.dayclose.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverHoldApprovalRepository extends JpaRepository<DriverHoldApprovalEntity, Long> {
    List<DriverHoldApprovalEntity> findByParcelId(Long parcelId);
    List<DriverHoldApprovalEntity> findByDriverIdAndStatus(Long driverId, String status);
}
