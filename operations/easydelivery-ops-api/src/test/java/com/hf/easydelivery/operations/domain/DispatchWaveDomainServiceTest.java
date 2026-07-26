package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.domain.ParcelDomainService;
import com.hf.easydelivery.common.exception.BizException;
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
class DispatchWaveDomainServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ParcelDomainService parcelDomainService;

    @InjectMocks
    private DispatchWaveDomainService waveDomainService;

    @Test
    @DisplayName("Freezes DRAFT wave and updates associated tasks")
    void shouldFreezeDraftWave() {
        when(jdbc.update(startsWith("UPDATE dispatch_wave SET status='FROZEN'"), eq("user1"), eq(10L))).thenReturn(1);

        waveDomainService.freezeWave(10L, "user1");

        verify(jdbc, times(1)).update(eq("UPDATE driver_task SET status='FROZEN', version=version+1 WHERE wave_id=?"), eq(10L));
    }

    @Test
    @DisplayName("Publishes FROZEN wave and advances parcel statuses via ParcelDomainService")
    void shouldPublishFrozenWaveAndTransitParcels() {
        when(jdbc.update(startsWith("UPDATE dispatch_wave SET status='PUBLISHED'"), eq("user1"), eq(10L))).thenReturn(1);
        when(jdbc.query(startsWith("SELECT ti.parcel_id FROM driver_task_item"), any(RowMapper.class), eq(10L))).thenReturn(List.of(101L, 102L));


        waveDomainService.publishWave(10L, "user1");

        verify(parcelDomainService, times(1)).transitStatus(eq(101L), eq("READY_FOR_DISPATCH"), eq("user1"), anyString());
        verify(parcelDomainService, times(1)).transitStatus(eq(102L), eq("READY_FOR_DISPATCH"), eq("user1"), anyString());
    }
}
