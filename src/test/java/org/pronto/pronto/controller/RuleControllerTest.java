package org.pronto.pronto.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.repository.RuleRepository;
import org.pronto.pronto.repository.UserRepository;
import org.pronto.pronto.service.DecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.pronto.pronto.TestFixtures.DAY;
import static org.pronto.pronto.TestFixtures.KOLKATA;
import static org.pronto.pronto.TestFixtures.at;
import static org.pronto.pronto.TestFixtures.request;
import static org.pronto.pronto.TestFixtures.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each test rolls back, so system-rule edits never leak. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RuleControllerTest {

    private static final String RULES = "/api/v1/rules";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RuleRepository ruleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DecisionService decisionService;

    @Test
    void sandboxEscapesAreRejected() throws Exception {
        for (String expression : new String[] {"T(java.lang.Runtime).getRuntime().exec('id')",
            "new java.io.File('x')", "''.getClass()"}) {
            send(post(RULES), body("ESCAPE", 1, expression, true, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RULE"));
        }
        assertThat(ruleRepository.findByName("ESCAPE")).isEmpty();
    }

    @Test
    void nonBooleanSyntaxErrorAndUnguardedVariableAreRejected() throws Exception {
        for (String expression : new String[] {"localHour + 1", "localHour >", "variables['x'].length() > 1"}) {
            send(post(RULES), body("BAD", 1, expression, true, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RULE"))
                    .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
        }
        send(post(RULES + "/validate"), Map.of("expression", "localHour >"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").value(not(emptyOrNullString())));
        send(post(RULES + "/validate"), Map.of("expression", "localHour > 1"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void systemRuleCannotBeDeletedButOthersCan() throws Exception {
        long quietHours = ruleRepository.findByName("QUIET_HOURS").orElseThrow().getId();
        mockMvc.perform(delete(RULES + "/" + quietHours))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_RULE_CANNOT_BE_DELETED"));
        send(put(RULES + "/" + quietHours), body("QUIET_HOURS", 10, "localHour >= 22 or localHour < 8", false, 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_RULE_CANNOT_BE_DISABLED"));

        long id = idOf(send(post(RULES), body("TEMP", 99, "false", true, null)).andExpect(status().isCreated()));
        send(post(RULES), body("TEMP", 98, "false", true, null)).andExpect(status().isConflict());
        mockMvc.perform(delete(RULES + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(RULES + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void systemRuleNameAndActionAreLockedButDescriptionCanChange() throws Exception {
        String expression = "localHour >= 22 or localHour < 8";
        long id = ruleRepository.findByName("QUIET_HOURS").orElseThrow().getId();
        send(put(RULES + "/" + id), body("QUIET_HOURS_OFF", 10, expression, true, 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_RULE_IMMUTABLE_FIELD"));
        Map<String, Object> allow = body("QUIET_HOURS", 10, expression, true, 0L);
        allow.put("action", "ALLOW");
        send(put(RULES + "/" + id), allow)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_RULE_IMMUTABLE_FIELD"));

        Map<String, Object> described = body("QUIET_HOURS", 11, expression, true, 0L);
        described.put("description", "edited");
        send(put(RULES + "/" + id), described)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("edited"))
                .andExpect(jsonPath("$.priority").value(11));
    }

    @Test
    void existingRuleNameIsAConflict() throws Exception {
        send(post(RULES), body("QUIET_HOURS", 99, "false", true, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        long id = idOf(send(post(RULES), body("VERSIONED", 99, "false", true, null)));
        send(put(RULES + "/" + id), body("VERSIONED", 98, "false", true, 0L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        send(put(RULES + "/" + id), body("VERSIONED", 97, "false", true, 0L))
                .andExpect(status().isConflict());
    }

    @Test
    void editedSystemRuleTakesEffectOnTheNextDecision() throws Exception {
        long id = ruleRepository.findByName("DUPLICATE_CAMPAIGN_24H").orElseThrow().getId();
        JsonNode rule = objectMapper.readTree(mockMvc.perform(get(RULES + "/" + id))
                .andReturn().getResponse().getContentAsString());
        User user = userRepository.save(user(KOLKATA));
        assertThat(diwali(user, "10:00", 0).getDecision()).isEqualTo(Decision.ALLOWED);

        send(put(RULES + "/" + id), body("DUPLICATE_CAMPAIGN_24H", 30,
                "message.campaignCode != null and hoursSinceLastSameCampaign < 48", true,
                rule.get("version").asLong())).andExpect(status().isOk());

        DecisionResponse after25h = diwali(user, "11:00", 1);
        assertThat(after25h.getDecision()).isEqualTo(Decision.BLOCKED);
        assertThat(after25h.getRuleName()).isEqualTo("DUPLICATE_CAMPAIGN_24H");
    }

    @Test
    void disabledRuleIsSkipped() throws Exception {
        User user = userRepository.save(user(KOLKATA));
        long id = idOf(send(post(RULES), body("ONLY_THIS_USER", 0, "user.id == " + user.getId(), false, null)));

        assertThat(newArrivals(user, "12:00").getDecision()).isEqualTo(Decision.ALLOWED);
        send(put(RULES + "/" + id), body("ONLY_THIS_USER", 0, "user.id == " + user.getId(), true, 0L))
                .andExpect(status().isOk());
        assertThat(newArrivals(user, "12:01").getRuleName()).isEqualTo("ONLY_THIS_USER");
    }

    private DecisionResponse diwali(User user, String time, int plusDays) {
        return decisionService.decide(request(user.getId(), "DIWALI_OFFER", Map.of("discount", "40%")),
                at(DAY.plusDays(plusDays), time, KOLKATA));
    }

    private DecisionResponse newArrivals(User user, String time) {
        return decisionService.decide(request(user.getId(), "NEW_ARRIVALS", Map.of()), at(DAY, time, KOLKATA));
    }

    private static Map<String, Object> body(String name, int priority, String expression, boolean enabled,
                                            Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("priority", priority);
        body.put("expression", expression);
        body.put("action", "BLOCK");
        body.put("enabled", enabled);
        body.put("version", version);
        return body;
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
                               Object body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
