package com.hf.easydelivery.driver.domain;

import com.hf.easydelivery.common.domain.ParcelDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverTaskDomainServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ParcelDomainService parcelDomainService;

    @InjectMocks
    private DriverTaskDomainService driverTaskDomainService;

    @Test
    @DisplayName("Confirms scan batch and advances status to OUT_FOR_DELIVERY")
    void shouldConfirmScanBatchAndTransitStatus() {
        when(jdbc.query(startsWith("SELECT ti.task_id FROM driver_task_item"), any(RowMapper.class), eq(101L), eq(200L)))
                .thenReturn(List.of(10L));

        driverTaskDomainService.confirmScanBatch(101L, List.of(200L), "EVENT-01");

        verify(jdbc, times(1)).update(eq("UPDATE driver_task_item SET item_status='OUT_FOR_DELIVERY' WHERE task_id=? AND parcel_id=?"), eq(10L), eq(200L));
        verify(parcelDomainService, times(1)).transitStatus(eq(200L), eq("OUT_FOR_DELIVERY"), eq("101"), contains("EVENT-01"));
    }

    @Test
    @DisplayName("Completes POD delivery attempt and advances parcel status to DELIVERED")
    void shouldCompleteDeliveryAttemptAndTransitStatus() {
        when(jdbc.query(eq("SELECT id FROM parcel WHERE waybill_id = ?"), any(RowMapper.class), eq(10003L)))
                .thenReturn(List.of(200L));

        driverTaskDomainService.completeDeliveryAttempt(101L, 10003L, 0, null, "John Doe", "IDEM-KEY-1");

        verify(jdbc, times(1)).update(startsWith("INSERT INTO delivery_attempt"), eq(200L), eq(101L), eq("DELIVERED"), any(), eq("John Doe"), eq("IDEM-KEY-1"));
        verify(parcelDomainService, times(1)).transitStatus(eq(200L), eq("DELIVERED"), eq("101"), contains("John Doe"));
    }
}
