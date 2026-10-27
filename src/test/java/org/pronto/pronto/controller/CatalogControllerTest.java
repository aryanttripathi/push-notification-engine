package org.pronto.pronto.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsUserCampaignAndTemplateWithRequiredVariables() throws Exception {
        post("/api/v1/users", Map.of("name", "Ravi", "timezone", "Asia/Kolkata", "city", "Pune", "hubId", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
        post("/api/v1/campaigns", Map.of("code", "CART_26", "name", "Cart nudges"))
                .andExpect(status().isCreated());
        post("/api/v1/templates", Map.of("code", "CART_REMINDER", "type", "MARKETING", "campaignCode", "CART_26",
                "title", "Hi {{user_name}}", "body", "{{ item_count }} items in {{user_city}}",
                "deepLink", "app://cart/{{cart_id}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignCode").value("CART_26"))
                .andExpect(jsonPath("$.requiredVariables.length()").value(2))
                .andExpect(jsonPath("$.requiredVariables[0]").value("item_count"))
                .andExpect(jsonPath("$.requiredVariables[1]").value("cart_id"));

        mockMvc.perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("CART_REMINDER")));
        mockMvc.perform(get("/api/v1/users")).andExpect(jsonPath("$[*].name", hasItem("Ravi")));
        mockMvc.perform(get("/api/v1/campaigns")).andExpect(jsonPath("$[*].code", hasItem("CART_26")));
    }

    @Test
    void rejectsBadTimezoneUnknownCampaignDuplicatesAndMissingFields() throws Exception {
        for (String timezone : new String[] {"Mars/Olympus", "+05:30", "UTC+5", "IST"}) {
            post("/api/v1/users", Map.of("name", "Ravi", "timezone", timezone, "city", "Pune"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
        }
        post("/api/v1/users", Map.of("name", "Ravi", "timezone", "Asia/Kolkata"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("request validation failed"))
                .andExpect(jsonPath("$.details[0]").value("city: must not be blank"));
        post("/api/v1/templates", Map.of("code", "X3", "type", "MARKETING", "title", "t", "body", "b",
                "deepLink", "javascript:alert(1)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("deepLink:")));
        post("/api/v1/templates", Map.of("code", "X1", "type", "MARKETING", "campaignCode", "NOPE",
                "title", "t", "body", "b"))
                .andExpect(status().isNotFound());
        post("/api/v1/templates", Map.of("code", "ORDER_SHIPPED", "type", "TRANSACTIONAL", "title", "t", "body", "b"))
                .andExpect(status().isConflict());
        post("/api/v1/campaigns", Map.of("code", "DIWALI_26", "name", "again")).andExpect(status().isConflict());
        post("/api/v1/templates", Map.of("code", "X2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private ResultActions post(String url, Map<String, Object> body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
