package com.hf.easydelivery.common.domain;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
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
class ParcelDomainServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private ParcelDomainService parcelDomainService;

    @Test
    @DisplayName("Throws exception when target status is invalid")
    void shouldThrowExceptionWhenTargetStatusIsInvalid() {
        BizException ex = assertThrows(BizException.class, () ->
                parcelDomainService.transitStatus(100L, "INVALID_STATUS", "user1", "Test note"));
        assertEquals("PARCEL.STATUS.INVALID", ex.getBizCode());
    }

    @Test
    @DisplayName("Throws exception when parcel does not exist")
    void shouldThrowExceptionWhenParcelNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(100L))).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () ->
                parcelDomainService.transitStatus(100L, "SORTED", "user1", "Test note"));
        assertEquals("PARCEL.NOT_FOUND", ex.getBizCode());

    }

    @Test
    @DisplayName("Successfully transits status and writes parcel_event log")
    void shouldTransitStatusAndWriteEventLog() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(100L))).thenReturn(List.of("RECEIVED"));

        parcelDomainService.transitStatus(100L, "SORTED", "user1", "Test note");

        verify(jdbc, times(1)).update(eq("UPDATE parcel SET status=?, updated_at=CURRENT_TIMESTAMP(3) WHERE id=?"), eq("SORTED"), eq(100L));
        verify(jdbc, times(1)).update(startsWith("INSERT INTO parcel_event"), eq(100L), eq("RECEIVED"), eq("SORTED"), eq("user1"), eq("Test note"));
    }
}
