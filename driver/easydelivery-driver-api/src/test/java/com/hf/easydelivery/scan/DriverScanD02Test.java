package com.hf.easydelivery.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hf.easydelivery.driver.DriverApiApplication;

@SpringBootTest(classes = DriverApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class DriverScanD02Test {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void testDriverScanLifecycleIdempotencyAndBatchLocking() throws Exception {
        String token = login("driver123", "password123");

        // 1. Create scan batch
        String createBatchRes = mvc.perform(post("/delivery/scan/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"driver_id\":101,\"operator_role\":1,\"scan_as\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andReturn().getResponse().getContentAsString();

        long batchId = objectMapper.readTree(createBatchRes).path("biz_data").path("scan_batch_id").asLong();

        // 2. Scan a parcel with device_event_id
        String deviceEventId = "EVT-1001-XYZ";
        mvc.perform(post("/delivery/ext/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"tracking_no\":\"BAUNI000300014438615\",\"scan_batch_id\":" + batchId + ",\"device_event_id\":\"" + deviceEventId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andExpect(jsonPath("$.biz_data.trackingNo").value("BAUNI000300014438615"));

        // 3. Scan same tracking_no or device_event_id (idempotency/duplicate handling)
        mvc.perform(post("/delivery/ext/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"tracking_no\":\"BAUNI000300014438615\",\"scan_batch_id\":" + batchId + ",\"device_event_id\":\"" + deviceEventId + "\"}"))
                .andExpect(status().isOk());

        // 4. Submit scan batch (lock batch)
        mvc.perform(put("/delivery/ext/scan/batch/" + batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_data.status").value("SUBMITTED"));

        // 5. Attempt scanning into locked batch (expect error SCAN.BATCH.LOCKED)
        mvc.perform(post("/delivery/ext/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"tracking_no\":\"BAUNI000300014438616\",\"scan_batch_id\":" + batchId + ",\"device_event_id\":\"EVT-1002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("SCAN.BATCH.LOCKED"));
    }

    private String login(String credential, String password) throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential_id\":\"" + credential + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("biz_data").path("access_token").asText();
    }
}
