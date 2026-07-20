package com.hf.easydelivery;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class ApplicationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void loginAndReadOwnTasks() throws Exception {
        String token = login("driver123", "password123");
        mvc.perform(get("/delivery/parcels/tasks")
                        .param("criteria", "UNSCANNED")
                        .param("driver_id", "101")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"));
    }

    @Test
    void rejectMissingTokenAndCrossDriverAccess() throws Exception {
        mvc.perform(get("/delivery/parcels/delivering").param("driver_id", "101"))
                .andExpect(status().isUnauthorized());

        String token = login("driver123", "password123");
        mvc.perform(get("/delivery/parcels/delivering")
                        .param("driver_id", "102")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.biz_code").value("AUTH.UNAUTHORIZED"));
    }

    @Test
    void localizesDriverApiAndFallsBackToEnglish() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language","fr-CA")
                        .content("{\"credential_id\":\"missing\",\"password\":\"bad-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("AUTH.INVALID.CREDENTIALS"))
                .andExpect(jsonPath("$.biz_message").value("Nom d’utilisateur ou mot de passe invalide"));

        mvc.perform(get("/delivery/parcels/delivering").param("driver_id","101")
                        .header("Accept-Language","de-DE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.biz_message").value("Authentication is required or has expired"));
    }

    @Test
    void persistsDriverLocaleAndUsesItWhenHeaderIsAbsent() throws Exception {
        String token=login("driver123","password123");
        mvc.perform(put("/auth/locale").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization","Bearer "+token).content("{\"locale\":\"zh-CN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_data.preferred_locale").value("zh-CN"));
        mvc.perform(get("/delivery/parcels/tasks").param("criteria","UNSCANNED").param("driver_id","101")
                        .header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_message").value("操作成功"));
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
