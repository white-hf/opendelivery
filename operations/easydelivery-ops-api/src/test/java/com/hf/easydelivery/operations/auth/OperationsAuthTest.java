package com.hf.easydelivery.operations.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationsAuthTest {

    private MockMvc mvc;

    @Mock
    private OperatorSessionService sessions;

    @InjectMocks
    private OperationsAuthController operationsAuthController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mvc = MockMvcBuilders.standaloneSetup(operationsAuthController).build();
    }

    @Test
    void testOperationsLoginEndpoint() throws Exception {
        Mockito.when(sessions.login("opsadmin", "password123"))
                .thenReturn(new OperatorSessionService.Tokens("Bearer", "access", "refresh", 7200));

        mvc.perform(post("/ops/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"opsadmin\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andExpect(jsonPath("$.biz_data.accessToken").value("access"));
    }
}
