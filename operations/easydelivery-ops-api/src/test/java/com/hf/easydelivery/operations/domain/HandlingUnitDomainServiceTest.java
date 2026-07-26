package com.hf.easydelivery.operations.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandlingUnitDomainServiceTest {

    @Mock
    private com.hf.easydelivery.operations.arrival.persistence.HandlingUnitRepository unitRepo;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private HandlingUnitDomainService domainService;

    private com.hf.easydelivery.operations.arrival.persistence.HandlingUnitEntity sampleUnit;

    @BeforeEach
    void setUp() {
        sampleUnit = new com.hf.easydelivery.operations.arrival.persistence.HandlingUnitEntity(
                100L, 10L, "HU-TEST-01", "PALLET", 50
        );
    }

    @Test
    @DisplayName("Clears old area associations when empty area list is provided")
    void shouldClearOldAssociationsWhenAreaListIsEmpty() {
        when(unitRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(sampleUnit));

        int result = domainService.reassignAreasToUnit(1L, List.of(), "Clearing areas");

        assertEquals(0, result);
        verify(jdbc, times(1)).update(eq("DELETE FROM handling_unit_parcel WHERE handling_unit_id=? AND link_source='AREA_PLAN'"), eq(sampleUnit.getId()));
        verify(jdbc, times(1)).update(eq("DELETE FROM handling_unit_area_rule WHERE station_id=? AND unit_code=?"), eq(sampleUnit.getStationId()), eq(sampleUnit.getExternalUnitNo()));
    }

    @Test
    @DisplayName("Atomically reassigns area and materialize linkage")
    void shouldReassignAreaAndMaterializeLinkage() {
        when(unitRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(sampleUnit));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(5L), eq(sampleUnit.getStationId()))).thenReturn(1);
        lenient().when(jdbc.update(startsWith("INSERT IGNORE INTO handling_unit_parcel"), any(), any(), any(), any(), any())).thenReturn(10);

        int linked = domainService.reassignAreasToUnit(1L, List.of(5L), "Reassigning area");

        assertNotNull(linked);
        verify(jdbc, times(1)).update(eq("DELETE FROM handling_unit_parcel WHERE handling_unit_id=? AND link_source='AREA_PLAN'"), eq(sampleUnit.getId()));
        verify(jdbc, times(1)).update(startsWith("INSERT IGNORE INTO handling_unit_area_rule"), eq(sampleUnit.getStationId()), eq(sampleUnit.getExternalUnitNo()), eq(5L));
    }

}
