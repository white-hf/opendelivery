package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverShiftDomainServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private DriverShiftDomainService shiftDomainService;

    @Test
    @DisplayName("Saves AVAILABLE shift and upserts capacity")
    void shouldSaveAvailableShift() {
        shiftDomainService.saveShift(1L, 101L, LocalDate.now(), "AVAILABLE", 200, "Normal shift");

        verify(jdbc, times(1)).update(startsWith("INSERT INTO driver_shift"), eq(1L), eq(101L), any(), eq("AVAILABLE"), eq(200), eq("Normal shift"));
        verify(jdbc, never()).update(startsWith("UPDATE driver_task_item ti JOIN driver_task t"), any(), any());
    }

    @Test
    @DisplayName("Saves UNAVAILABLE shift and triggers task unassignment cascade guard")
    void shouldUnassignTasksWhenDriverBecomesUnavailable() {
        LocalDate today = LocalDate.now();
        shiftDomainService.saveShift(1L, 101L, today, "UNAVAILABLE", 200, "Sick leave");

        verify(jdbc, times(1)).update(startsWith("INSERT INTO driver_shift"), eq(1L), eq(101L), eq(today), eq("UNAVAILABLE"), eq(200), eq("Sick leave"));
        verify(jdbc, times(1)).update(startsWith("UPDATE driver_task_item ti JOIN driver_task t"), eq(101L), eq(today));
    }
}
