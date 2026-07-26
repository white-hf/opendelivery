package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryAreaDomainServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private DeliveryAreaDomainService areaDomainService;

    @Test
    @DisplayName("Deactivates area and cleans up master template rules")
    void shouldDeactivateAreaAndCleanTemplateRules() {
        when(jdbc.update(eq("UPDATE delivery_area SET status='INACTIVE', updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND station_id=?"), eq(50L), eq(1L))).thenReturn(1);

        areaDomainService.deactivateArea(1L, 50L, "Area boundary changed");

        verify(jdbc, times(1)).update(eq("DELETE FROM handling_unit_area_rule WHERE station_id=? AND delivery_area_id=?"), eq(1L), eq(50L));
    }

    @Test
    @DisplayName("Throws exception when deactivating non-existent area")
    void shouldThrowExceptionWhenAreaNotFound() {
        when(jdbc.update(anyString(), eq(50L), eq(1L))).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> areaDomainService.deactivateArea(1L, 50L, "Reason"));
        assertEquals("AREA.NOT_FOUND", ex.getBizCode());

    }
}
