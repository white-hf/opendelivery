package com.hf.easydelivery.common.persistence.driver;

import com.hf.easydelivery.common.dto.DeliveringListData;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.*;

class DriverTaskQueryRepositoryTest {
    @Test
    void returnsEmptyUnscannedListWhenDatabaseHasNoRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<DeliveringListData>>any(), anyInt(), anyString(), anyString()))
                .thenReturn(List.<DeliveringListData>of());

        List<DeliveringListData> result = new DriverTaskQueryRepository(jdbc).unscannedParcels(101);

        assertTrue(result.isEmpty());
        verify(jdbc).query(anyString(), ArgumentMatchers.<RowMapper<DeliveringListData>>any(), eq(101), eq("ASSIGNED"), eq("ASSIGNED"));
    }
}
