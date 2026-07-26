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
class OperationalCaseDomainServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ParcelDomainService parcelDomainService;

    @InjectMocks
    private OperationalCaseDomainService caseDomainService;

    @Test
    @DisplayName("Resolves case and automatically unfreezes parcel when no other open cases remain")
    void shouldResolveCaseAndUnfreezeParcel() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(500L)))
                .thenReturn(List.of(new OperationalCaseDomainService.MapCaseInfo(500L, 100L, 1L, "OPEN")));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM operational_case"), eq(Integer.class), eq(100L), eq(500L)))
                .thenReturn(0);

        caseDomainService.resolveCase(500L, "RESOLVED", "Issue fixed", "user1");

        verify(jdbc, times(1)).update(startsWith("UPDATE operational_case SET status=?"), eq("RESOLVED"), eq("Issue fixed"), eq("user1"), eq(500L));
        verify(jdbc, times(1)).update(startsWith("INSERT INTO operational_case_event"), eq(500L), eq("OPEN"), eq("RESOLVED"), eq("user1"), eq("Issue fixed"));
        verify(parcelDomainService, times(1)).transitStatus(eq(100L), eq("AT_STATION"), eq("user1"), contains("500"));
    }
}

