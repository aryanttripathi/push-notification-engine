package org.pronto.pronto.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pronto.pronto.entity.RuleAction;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.repository.NotificationDecisionRepository;
import org.pronto.pronto.repository.NotificationRepository;
import org.pronto.pronto.repository.RuleRepository;
import org.pronto.pronto.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.pronto.pronto.TestFixtures.KOLKATA;
import static org.pronto.pronto.TestFixtures.request;
import static org.pronto.pronto.TestFixtures.rule;
import static org.pronto.pronto.TestFixtures.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API-level checks. Uses the real clock, so only time-independent outcomes are asserted. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RuleRepository ruleRepository;
    @Autowired
    private NotificationDecisionRepository decisionRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void decideReturnsTheRenderedMessage() throws Exception {
        User user = userRepository.save(user(KOLKATA));

        decide(user.getId(), "ORDER_SHIPPED", Map.of("order_id", "A1", "eta", "5pm"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.reason").value("TRANSACTIONAL_BYPASS"))
                .andExpect(jsonPath("$.message.title").value("Hi Aryant, your order is on the way"))
                .andExpect(jsonPath("$.message.body").value("Order #A1 will reach you by 5pm."))
                .andExpect(jsonPath("$.message.deepLink").value("app://orders/A1"));
    }

    @Test
    void requestIdIsHonoredWhenSafeAndReplacedOtherwise() throws Exception {
        mockMvc.perform(get("/health").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
        for (String unsafe : new String[] {"x".repeat(65), "abc def", "abc\nINFO forged line", "id;rm"}) {
            mockMvc.perform(get("/health").header("X-Request-Id", unsafe))
                    .andExpect(header().string("X-Request-Id", not(unsafe)));
        }
    }

    @Test
    void missingVariableIs400AndStoresNothing() throws Exception {
        User user = userRepository.save(user(KOLKATA));

        decide(user.getId(), "ORDER_SHIPPED", Map.of("eta", "5pm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TEMPLATE_VARIABLE"))
                .andExpect(jsonPath("$.missing[0]").value("order_id"));
        assertThat(decisionRepository.countByUser_Id(user.getId())).isZero();
        assertThat(notificationRepository.countByUser_Id(user.getId())).isZero();
    }

    @Test
    void renderedMessageOverColumnLimitIs400() throws Exception {
        User user = userRepository.save(user(KOLKATA));

        decide(user.getId(), "ORDER_SHIPPED", Map.of("order_id", "x".repeat(1000), "eta", "5pm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RENDERED_MESSAGE_TOO_LONG"));
        assertThat(decisionRepository.countByUser_Id(user.getId())).isZero();
    }

    @Test
    void badInputUnknownUserAndUnknownTemplateAreRejected() throws Exception {
        User user = userRepository.save(user(KOLKATA));

        decide(null, "ORDER_SHIPPED", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details[0]").value("userId: must not be null"));
        decide(user.getId(), "ORDER_SHIPPED", Map.of("blob", "x".repeat(2100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VARIABLES_TOO_LONG"));
        decide(Long.MAX_VALUE, "ORDER_SHIPPED", Map.of()).andExpect(status().isNotFound());
        decide(user.getId(), "NOPE", Map.of()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/notifications/sent").param("userId", "1").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisionsAndSentListTheUsersHistory() throws Exception {
        User user = userRepository.save(user(KOLKATA));
        ruleRepository.save(rule("BLOCK_" + user.getId(), 0, "user.id == " + user.getId(), RuleAction.BLOCK));
        decide(user.getId(), "ORDER_SHIPPED", Map.of("order_id", "A1", "eta", "5pm")).andExpect(status().isOk());
        decide(user.getId(), "NEW_ARRIVALS", Map.of()).andExpect(jsonPath("$.decision").value("BLOCKED"));

        mockMvc.perform(get("/api/v1/notifications/decisions").param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].decision").value("BLOCKED"))
                .andExpect(jsonPath("$[0].trace[0].matched").value(true))
                .andExpect(jsonPath("$[1].message.title").value("Hi Aryant, your order is on the way"));
        mockMvc.perform(get("/api/v1/notifications/sent").param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].templateCode").value("ORDER_SHIPPED"));
    }

    private ResultActions decide(Long userId, String templateCode, Map<String, Object> variables) throws Exception {
        return mockMvc.perform(post("/api/v1/notifications/decide")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(userId, templateCode, variables))));
    }
}
