package org.pronto.pronto.service;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.dto.DecideRequest;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.engine.RuleEngine;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.entity.RuleAction;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.exception.ConflictException;
import org.pronto.pronto.repository.NotificationDecisionRepository;
import org.pronto.pronto.repository.NotificationRepository;
import org.pronto.pronto.repository.RuleRepository;
import org.pronto.pronto.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.pronto.pronto.TestFixtures.DAY;
import static org.pronto.pronto.TestFixtures.KOLKATA;
import static org.pronto.pronto.TestFixtures.NEW_YORK;
import static org.pronto.pronto.TestFixtures.at;
import static org.pronto.pronto.TestFixtures.request;
import static org.pronto.pronto.TestFixtures.rule;
import static org.pronto.pronto.TestFixtures.user;

/** Every test rolls back, so rules added or toggled here never leak into other tests. */
@SpringBootTest
@Transactional
class DecisionServiceTest {

    private static final LocalDate NEXT_DAY = DAY.plusDays(1);
    private static final Map<String, Object> SHIPPED_VARS = Map.of("order_id", "A1", "eta", "5pm");

    @Autowired
    private DecisionService decisionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RuleRepository ruleRepository;
    @Autowired
    private NotificationDecisionRepository decisionRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void quietHoursFollowTheUserTimezone() {
        User user = newUser(KOLKATA);

        assertThat(newArrivals(user, DAY, "21:59").getDecision()).isEqualTo(Decision.ALLOWED);
        assertBlocked(newArrivals(user, DAY, "22:00"), "QUIET_HOURS");
        assertBlocked(newArrivals(user, NEXT_DAY, "07:59"), "QUIET_HOURS");
        assertThat(newArrivals(user, NEXT_DAY, "08:00").getDecision()).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void capAllowsThreeAndBlockedAttemptsDoNotCount() {
        User user = newUser(KOLKATA);

        assertThat(diwali(user, DAY, "10:00").getDecision()).isEqualTo(Decision.ALLOWED);
        assertBlocked(diwali(user, DAY, "10:01"), "DUPLICATE_CAMPAIGN_24H");
        assertThat(newArrivals(user, DAY, "10:02").getDecision()).isEqualTo(Decision.ALLOWED);
        assertThat(newArrivals(user, DAY, "10:03").getDecision()).isEqualTo(Decision.ALLOWED);
        assertBlocked(newArrivals(user, DAY, "10:04"), "DAILY_MARKETING_CAP");
        assertThat(notificationRepository.countByUser_Id(user.getId())).isEqualTo(3);
    }

    @Test
    void capResetsAtLocalMidnightNotUtcMidnight() {
        setEnabled("QUIET_HOURS", false);
        User user = newUser(NEW_YORK);
        newArrivals(user, DAY, "22:57");
        newArrivals(user, DAY, "22:58");
        newArrivals(user, DAY, "22:59");

        Instant lateEvening = at(DAY, "23:00", NEW_YORK);
        Instant afterMidnight = at(NEXT_DAY, "01:00", NEW_YORK);
        assertThat(lateEvening.atZone(ZoneOffset.UTC).toLocalDate())
                .isEqualTo(afterMidnight.atZone(ZoneOffset.UTC).toLocalDate());
        assertBlocked(decide(user, "NEW_ARRIVALS", Map.of(), lateEvening), "DAILY_MARKETING_CAP");
        assertThat(decide(user, "NEW_ARRIVALS", Map.of(), afterMidnight).getDecision()).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void sameCampaignIsBlockedWithin24HoursOnly() {
        User user = newUser(KOLKATA);

        assertThat(diwali(user, DAY, "10:00").getDecision()).isEqualTo(Decision.ALLOWED);
        assertThat(flashSale(user, DAY, "11:00", Map.of()).getDecision()).isEqualTo(Decision.ALLOWED);
        assertBlocked(diwali(user, NEXT_DAY, "09:00"), "DUPLICATE_CAMPAIGN_24H");
        assertThat(diwali(user, NEXT_DAY, "11:00").getDecision()).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void transactionalPassesCapQuietHoursAndAnyRule() {
        User user = newUser(KOLKATA);
        setEnabled("QUIET_HOURS", false);
        newArrivals(user, DAY, "00:10");
        newArrivals(user, DAY, "00:20");
        newArrivals(user, DAY, "00:30");
        setEnabled("QUIET_HOURS", true);
        assertBlocked(newArrivals(user, DAY, "03:00"), "QUIET_HOURS");

        DecisionResponse shipped = decide(user, "ORDER_SHIPPED", SHIPPED_VARS, at(DAY, "03:00", KOLKATA));
        assertThat(shipped.getDecision()).isEqualTo(Decision.ALLOWED);
        assertThat(shipped.getReason()).isEqualTo(DecisionService.TRANSACTIONAL_BYPASS);

        ruleRepository.save(rule("BLOCK_EVERYTHING", 0, "true", RuleAction.BLOCK));
        DecisionResponse delivered = decide(user, "ORDER_DELIVERED", Map.of("order_id", "A1"),
                at(DAY, "03:05", KOLKATA));
        assertThat(delivered.getDecision()).isEqualTo(Decision.ALLOWED);
        assertThat(delivered.getReason()).isEqualTo(DecisionService.TRANSACTIONAL_BYPASS);
    }

    @Test
    void allowRuleAtHigherPriorityBeatsQuietHours() {
        User vip = newUser(KOLKATA);
        User other = newUser(KOLKATA);
        ruleRepository.save(rule("VIP_ALLOW", 1, "user.id == " + vip.getId(), RuleAction.ALLOW));

        DecisionResponse response = newArrivals(vip, DAY, "23:00");
        assertThat(response.getDecision()).isEqualTo(Decision.ALLOWED);
        assertThat(response.getRuleName()).isEqualTo("VIP_ALLOW");
        assertBlocked(newArrivals(other, DAY, "23:00"), "QUIET_HOURS");
    }

    @Test
    void ruleThatThrowsAtRuntimeFailsClosed() {
        User user = newUser(KOLKATA);
        Rule broken = ruleRepository.save(rule("BROKEN", 1, "variables['missing'].length() > 3", RuleAction.BLOCK));

        DecisionResponse response = newArrivals(user, DAY, "12:00");
        assertThat(response.getDecision()).isEqualTo(Decision.BLOCKED);
        assertThat(response.getReason()).startsWith(RuleEngine.RULE_ERROR + ": BROKEN");
        assertThat(response.getRuleId()).isEqualTo(broken.getId());
        assertThat(response.getTrace()).last().satisfies(t -> assertThat(t.getError()).isNotBlank());
        assertThat(notificationRepository.countByUser_Id(user.getId())).isZero();
    }

    @Test
    void allowlistedMethodsAndIntegerHubIdWorkAtRuntime() {
        ruleRepository.save(rule("HUB_1_2_BENGALURU", 1,
                "{1,2}.contains(user.hubId) and user.city.startsWith('Ben')", RuleAction.BLOCK));
        User otherHub = user(KOLKATA);
        otherHub.setHubId(3L);

        assertBlocked(newArrivals(newUser(KOLKATA), DAY, "12:00"), "HUB_1_2_BENGALURU");
        assertThat(newArrivals(userRepository.save(otherHub), DAY, "12:00").getDecision())
                .isEqualTo(Decision.ALLOWED);
    }

    @Test
    void methodOutsideTheAllowlistFailsClosedAtRuntime() {
        ruleRepository.save(rule("REPEAT", 1, "user.name.repeat(2).length() > 1", RuleAction.BLOCK));

        DecisionResponse response = newArrivals(newUser(KOLKATA), DAY, "12:00");
        assertThat(response.getDecision()).isEqualTo(Decision.BLOCKED);
        assertThat(response.getReason()).startsWith(RuleEngine.RULE_ERROR + ": REPEAT");
    }

    @Test
    void nullGuardedVariableRuleOnlyMatchesWhenPresent() {
        ruleRepository.save(rule("SMALL_CART", 5,
                "variables['cartValue'] != null and variables['cartValue'] < 199", RuleAction.BLOCK));

        DecisionResponse absent = flashSale(newUser(KOLKATA), DAY, "12:00", Map.of());
        assertThat(absent.getDecision()).isEqualTo(Decision.ALLOWED);
        assertThat(absent.getReason()).isEqualTo(RuleEngine.DEFAULT_ALLOW);
        assertBlocked(flashSale(newUser(KOLKATA), DAY, "12:00", Map.of("cartValue", 150)), "SMALL_CART");
        assertThat(flashSale(newUser(KOLKATA), DAY, "12:00", Map.of("cartValue", 500)).getDecision())
                .isEqualTo(Decision.ALLOWED);
    }

    @Test
    void allowedStoresDecisionAndRenderedNotificationBlockedStoresDecisionOnly() {
        User user = newUser(KOLKATA);
        Instant noon = at(DAY, "12:00", KOLKATA);

        DecisionResponse allowed = decide(user, "NEW_ARRIVALS", Map.of(), noon);
        assertThat(allowed.getMessage().getBody()).isEqualTo("Aryant, check what's new today.");
        assertThat(decisionRepository.countByUser_Id(user.getId())).isEqualTo(1);
        assertThat(notificationRepository.findByDecision_Id(allowed.getDecisionId())).hasValueSatisfying(n -> {
            assertThat(n.getBody()).isEqualTo("Aryant, check what's new today.");
            assertThat(n.getSentAt()).isEqualTo(noon);
        });

        DecisionResponse blocked = newArrivals(user, DAY, "23:00");
        assertThat(blocked.getMessage()).isNull();
        assertThat(blocked.getNotificationId()).isNull();
        assertThat(decisionRepository.countByUser_Id(user.getId())).isEqualTo(2);
        assertThat(notificationRepository.countByUser_Id(user.getId())).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReturnsTheStoredDecision() {
        User user = newUser(KOLKATA);
        DecideRequest request = request(user.getId(), "NEW_ARRIVALS", Map.of());
        request.setIdempotencyKey(UUID.randomUUID().toString());

        DecisionResponse first = decisionService.decide(request, at(DAY, "12:00", KOLKATA));
        DecisionResponse retry = decisionService.decide(request, at(DAY, "12:05", KOLKATA));

        assertThat(retry.getDecisionId()).isEqualTo(first.getDecisionId());
        assertThat(retry.getNotificationId()).isEqualTo(first.getNotificationId());
        assertThat(retry.getMessage()).isEqualTo(first.getMessage());
        assertThat(decisionRepository.countByUser_Id(user.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByUser_Id(user.getId())).isEqualTo(1);

        request.setUserId(newUser(KOLKATA).getId());
        assertThatThrownBy(() -> decisionService.decide(request, at(DAY, "12:10", KOLKATA)))
                .isInstanceOf(ConflictException.class);
    }

    private User newUser(ZoneId zone) {
        return userRepository.save(user(zone));
    }

    private void setEnabled(String ruleName, boolean enabled) {
        Rule rule = ruleRepository.findByName(ruleName).orElseThrow();
        rule.setEnabled(enabled);
        ruleRepository.saveAndFlush(rule);
    }

    private DecisionResponse newArrivals(User user, LocalDate day, String time) {
        return decide(user, "NEW_ARRIVALS", Map.of(), at(day, time, ZoneId.of(user.getTimezone())));
    }

    private DecisionResponse diwali(User user, LocalDate day, String time) {
        return decide(user, "DIWALI_OFFER", Map.of("discount", "40%"), at(day, time, KOLKATA));
    }

    private DecisionResponse flashSale(User user, LocalDate day, String time, Map<String, Object> extra) {
        Map<String, Object> variables = new HashMap<>(extra);
        variables.put("ends_at", "6pm");
        return decide(user, "FLASH_SALE", variables, at(day, time, KOLKATA));
    }

    private DecisionResponse decide(User user, String templateCode, Map<String, Object> variables, Instant now) {
        return decisionService.decide(request(user.getId(), templateCode, variables), now);
    }

    private static void assertBlocked(DecisionResponse response, String ruleName) {
        assertThat(response.getDecision()).isEqualTo(Decision.BLOCKED);
        assertThat(response.getRuleName()).isEqualTo(ruleName);
        assertThat(response.getMessage()).isNull();
    }
}
