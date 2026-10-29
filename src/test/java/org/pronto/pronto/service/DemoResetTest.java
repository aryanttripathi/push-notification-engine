package org.pronto.pronto.service;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.dto.RuleRequest;
import org.pronto.pronto.dto.RuleResponse;
import org.pronto.pronto.dto.UserRequest;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.entity.RuleAction;
import org.pronto.pronto.repository.NotificationDecisionRepository;
import org.pronto.pronto.repository.NotificationRepository;
import org.pronto.pronto.repository.RuleRepository;
import org.pronto.pronto.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.pronto.pronto.TestFixtures.DAY;
import static org.pronto.pronto.TestFixtures.KOLKATA;
import static org.pronto.pronto.TestFixtures.at;
import static org.pronto.pronto.TestFixtures.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional: reset is DDL. It re-seeds exactly what Flyway seeds on first boot, which is all any other
 * test relies on (each creates its own users), so no @DirtiesContext is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoResetTest {

    private static final String DEDUPE = "DUPLICATE_CAMPAIGN_24H";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RuleService ruleService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private DecisionService decisionService;
    @Autowired
    private DemoService demoService;
    @Autowired
    private RuleRepository ruleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationDecisionRepository decisionRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void resetRestoresTheSeededDatabaseAndTheDemoStillPasses() throws Exception {
        ruleService.create(ruleRequest("CUSTOM_BLOCK_ALL", 1, "true", null));
        RuleResponse dedupe = ruleService.list().stream().filter(r -> DEDUPE.equals(r.getName())).findFirst().orElseThrow();
        ruleService.update(dedupe.getId(), ruleRequest(DEDUPE, dedupe.getPriority(),
                "message.campaignCode != null and hoursSinceLastSameCampaign < 48", dedupe.getVersion()));
        long userId = catalogService.createUser(userRequest()).getId();
        decisionService.decide(request(userId, "ORDER_SHIPPED", Map.of("order_id", "A1", "eta", "5pm")),
                at(DAY, "12:00", KOLKATA));

        mockMvc.perform(post("/api/v1/demo/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules").value(3))
                .andExpect(jsonPath("$.users").value(2))
                .andExpect(jsonPath("$.campaigns").value(3))
                .andExpect(jsonPath("$.templates").value(6))
                .andExpect(jsonPath("$.decisions").value(0))
                .andExpect(jsonPath("$.notifications").value(0));

        assertThat(ruleRepository.findAll()).extracting(Rule::getName, Rule::getExpression).containsExactlyInAnyOrder(
                tuple("QUIET_HOURS", "localHour >= 22 or localHour < 8"),
                tuple("DAILY_MARKETING_CAP", "marketingSentToday >= 3"),
                tuple(DEDUPE, "message.campaignCode != null and hoursSinceLastSameCampaign < 24"));
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(decisionRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(demoService.run().getPassed()).isEqualTo(15);
    }

    private static RuleRequest ruleRequest(String name, int priority, String expression, Long version) {
        RuleRequest request = new RuleRequest();
        request.setName(name);
        request.setPriority(priority);
        request.setExpression(expression);
        request.setAction(RuleAction.BLOCK);
        request.setVersion(version);
        return request;
    }

    private static UserRequest userRequest() {
        UserRequest request = new UserRequest();
        request.setName("Before Reset");
        request.setTimezone(KOLKATA.getId());
        request.setCity("Pune");
        return request;
    }
}
