package com.hf.easydelivery.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hf.easydelivery.driver.DriverApiApplication;

@SpringBootTest(classes = DriverApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class DriverDeliveryD03Test {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void testDeliveryEvidenceGateAndIdempotency() throws Exception {
        String token = login("driver123", "password123");

        // 1. Missing photo evidence for successful delivery should be rejected with POD.EVIDENCE.REQUIRED
        mvc.perform(multipart("/delivery")
                        .param("order_id", "10003")
                        .param("longitude", "-123.1207")
                        .param("latitude", "49.2827")
                        .param("delivery_result", "0")
                        .param("recipient_name", "John Doe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("POD.EVIDENCE.REQUIRED"));

        // 2. Successful delivery with POD photo file
        MockMultipartFile podPhoto = new MockMultipartFile("pod_images[]", "pod.jpg", "image/jpeg", "sample_pod_content".getBytes());
        mvc.perform(multipart("/delivery")
                        .file(podPhoto)
                        .param("order_id", "10003")
                        .param("longitude", "-123.1207")
                        .param("latitude", "49.2827")
                        .param("delivery_result", "0")
                        .param("recipient_name", "John Doe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"));

        // 3. Retry delivery endpoint
        mvc.perform(multipart("/delivery/retry")
                        .param("order_id", "10003")
                        .param("longitude", "-123.1207")
                        .param("latitude", "49.2827")
                        .param("driver_id", "101")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"));
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
