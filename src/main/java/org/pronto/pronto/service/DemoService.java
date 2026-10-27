package org.pronto.pronto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.dto.DecideRequest;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.dto.DemoDataResponse;
import org.pronto.pronto.dto.DemoRunResponse;
import org.pronto.pronto.dto.DemoStep;
import org.pronto.pronto.dto.RuleRequest;
import org.pronto.pronto.dto.RuleResponse;
import org.pronto.pronto.dto.UserRequest;
import org.pronto.pronto.dto.UserResponse;
import org.pronto.pronto.engine.RuleEngine;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.RuleAction;
import org.pronto.pronto.exception.MissingTemplateVariableException;
import org.pronto.pronto.repository.NotificationDecisionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The scripted scenario behind /demo: a fresh user per run, every step in chronological order, each with
 * expected vs actual. Not transactional on purpose: every decide commits like a real API call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String CITY = "Bengaluru";
    private static final int RECENT_ROWS = 100;
    private static final int DEMO_RULE_PRIORITY = 5;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String MISSING_VARIABLE = "400 MISSING_TEMPLATE_VARIABLE ";
    private static final String ALLOWED_BYPASS = Decision.ALLOWED + " " + DecisionService.TRANSACTIONAL_BYPASS;
    private static final String ALLOWED_DEFAULT = Decision.ALLOWED + " " + RuleEngine.DEFAULT_ALLOW;
    private static final String BLOCKED = Decision.BLOCKED + " ";

    private final DecisionService decisionService;
    private final RuleService ruleService;
    private final CatalogService catalogService;
    private final NotificationDecisionRepository decisionRepository;
    private final Clock clock;

    /** One decide call of the script. */
    private record Call(int number, LocalDateTime localTime, String template, Map<String, Object> variables,
                        String expected, String expectedTitle, String note) {
    }

    public DemoRunResponse run() {
        LocalDate day = LocalDate.now(clock.withZone(ZONE)).minusDays(2);
        List<DemoStep> steps = new ArrayList<>();
        UserResponse user = createUser(steps);
        firstDay(day, user).forEach(call -> steps.add(decide(user, call)));
        RuleResponse rule = null;
        try {
            rule = createScopedRule(steps, user);
            for (Call call : secondDay(day.plusDays(1), rule.getName())) {
                steps.add(decide(user, call));
            }
        } finally {
            steps.add(deleteRule(rule));
        }
        int passed = (int) steps.stream().filter(DemoStep::isPassed).count();
        log.info("demo finished userId={} passed={} total={}", user.getId(), passed, steps.size());
        return DemoRunResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .total(steps.size())
                .passed(passed)
                .allPassed(passed == steps.size())
                .steps(steps)
                .build();
    }

    public DemoDataResponse snapshot() {
        return DemoDataResponse.builder()
                .users(catalogService.users())
                .campaigns(catalogService.campaigns())
                .templates(catalogService.templates())
                .rules(ruleService.list())
                .decisions(decisionService.latestDecisions(RECENT_ROWS))
                .notifications(decisionService.latestSent(RECENT_ROWS))
                .build();
    }

    /** Steps 2-10: day D (two days ago, local). */
    private static List<Call> firstDay(LocalDate d, UserResponse user) {
        return List.of(
                new Call(2, d.atTime(3, 0), "ORDER_SHIPPED", Map.of("order_id", "D-1001", "eta", "6 PM"),
                        ALLOWED_BYPASS, "Hi " + user.getName() + ", your order is on the way",
                        "transactional passes despite quiet hours"),
                new Call(3, d.atTime(3, 5), "ORDER_SHIPPED", Map.of("eta", "6 PM"),
                        MISSING_VARIABLE + "[order_id]", null, "caller bug fails fast, nothing stored"),
                new Call(4, d.atTime(10, 0), "DIWALI_OFFER", Map.of("discount", "40%"),
                        ALLOWED_DEFAULT, null, "marketing 1/3 today"),
                new Call(5, d.atTime(11, 0), "DIWALI_OFFER", Map.of("discount", "40%"),
                        BLOCKED + "DUPLICATE_CAMPAIGN_24H", null, "same campaign 1h after step 4"),
                new Call(6, d.atTime(12, 0), "FLASH_SALE", Map.of("ends_at", "2 PM"),
                        ALLOWED_DEFAULT, null, "marketing 2/3 today"),
                new Call(7, d.atTime(13, 0), "WEEKEND_DEALS", Map.of(),
                        ALLOWED_DEFAULT, null, "marketing 3/3 today"),
                new Call(8, d.atTime(14, 0), "NEW_ARRIVALS", Map.of(),
                        BLOCKED + "DAILY_MARKETING_CAP", null, "4th marketing push today"),
                new Call(9, d.atTime(15, 0), "ORDER_DELIVERED", Map.of("order_id", "D-1001"),
                        ALLOWED_BYPASS, null, "transactional ignores the cap"),
                new Call(10, d.atTime(23, 30), "NEW_ARRIVALS", Map.of(),
                        BLOCKED + "QUIET_HOURS", null, "23:30 local is inside 22:00-08:00"));
    }

    /** Steps 12-14: day D+1, with the scoped dynamic rule in place. */
    private static List<Call> secondDay(LocalDate d, String ruleName) {
        return List.of(
                new Call(12, d.atTime(10, 30), "FLASH_SALE", Map.of("ends_at", "2 PM", "cartValue", 150),
                        BLOCKED + ruleName, null, "cartValue 150 < 199"),
                new Call(13, d.atTime(10, 45), "FLASH_SALE", Map.of("ends_at", "2 PM", "cartValue", 500),
                        BLOCKED + "DUPLICATE_CAMPAIGN_24H", null, "22h45m since step 6"),
                new Call(14, d.atTime(11, 0), "DIWALI_OFFER", Map.of("discount", "40%", "cartValue", 500),
                        ALLOWED_DEFAULT, null, "25h > 24h since step 4; cap reset at local midnight"));
    }

    private UserResponse createUser(List<DemoStep> steps) {
        UserRequest request = new UserRequest();
        request.setName("demo-" + clock.millis());
        request.setTimezone(ZONE.getId());
        request.setCity(CITY);
        UserResponse user = catalogService.createUser(request);
        log.info("demo step=1 user created userId={}", user.getId());
        steps.add(setupStep(1, "create demo user", "user row created", user.getId()));
        return user;
    }

    private DemoStep decide(UserResponse user, Call call) {
        DecideRequest request = new DecideRequest();
        request.setUserId(user.getId());
        request.setTemplateCode(call.template());
        request.setVariables(call.variables());
        long rowsBefore = decisionRepository.countByUser_Id(user.getId());
        DecisionResponse response = null;
        String actual;
        try {
            response = decisionService.decide(request, call.localTime().atZone(ZONE).toInstant());
            actual = response.getDecision() + " " + response.getReason();
        } catch (MissingTemplateVariableException e) {
            boolean stored = decisionRepository.countByUser_Id(user.getId()) != rowsBefore;
            actual = MISSING_VARIABLE + e.getMissing() + (stored ? ", but a decision row was stored" : "");
        } catch (RuntimeException e) {
            actual = "ERROR " + e.getMessage();
        }
        boolean passed = StringUtils.equals(call.expected(), actual) && titleMatches(call, response);
        log.info("demo step={} userId={} template={} expected={} actual={} passed={}", call.number(),
                user.getId(), call.template(), call.expected(), actual, passed);
        return DemoStep.builder()
                .number(call.number())
                .localTime(TIME_FORMAT.format(call.localTime()))
                .action("decide " + call.template())
                .variables(call.variables())
                .expected(call.expectedTitle() == null ? call.expected() : call.expected() + " | " + call.expectedTitle())
                .actual(call.expectedTitle() == null || response == null ? actual
                        : actual + " | " + response.getMessage().getTitle())
                .passed(passed)
                .note(call.note())
                .rule(response == null ? null : response.getRuleName())
                .message(response == null ? null : response.getMessage())
                .decisionId(response == null ? null : response.getDecisionId())
                .notificationId(response == null ? null : response.getNotificationId())
                .build();
    }

    private static boolean titleMatches(Call call, DecisionResponse response) {
        if (call.expectedTitle() == null) {
            return true;
        }
        return response != null && response.getMessage() != null
                && StringUtils.equals(call.expectedTitle(), response.getMessage().getTitle());
    }

    /** Step 11: same path as the dashboard (validated by RuleService), scoped to this run's user. */
    private RuleResponse createScopedRule(List<DemoStep> steps, UserResponse user) {
        RuleRequest request = new RuleRequest();
        request.setName("DEMO_SMALL_CART_" + user.getId());
        request.setDescription("demo rule, scoped to demo user " + user.getId());
        request.setPriority(DEMO_RULE_PRIORITY);
        request.setExpression("user.id == " + user.getId()
                + " and variables['cartValue'] != null and variables['cartValue'] < 199");
        request.setAction(RuleAction.BLOCK);
        RuleResponse rule = ruleService.create(request);
        log.info("demo step=11 rule created ruleId={} name={}", rule.getId(), rule.getName());
        steps.add(setupStep(11, "create rule " + rule.getName() + ": " + rule.getExpression(),
                "rule row created", rule.getId()));
        return rule;
    }

    /** Step 15: always runs, so a failed run never leaves the dynamic rule behind. */
    private DemoStep deleteRule(RuleResponse rule) {
        if (rule == null) {
            return failedStep(15, "delete demo rule", "rule deleted", "no rule was created");
        }
        try {
            ruleService.delete(rule.getId());
            log.info("demo step=15 rule deleted ruleId={}", rule.getId());
            return setupStep(15, "delete rule " + rule.getName(), "rule deleted", rule.getId());
        } catch (RuntimeException e) {
            log.error("demo cleanup failed ruleId={}", rule.getId(), e);
            return failedStep(15, "delete rule " + rule.getName(), "rule deleted", "ERROR " + e.getMessage());
        }
    }

    private static DemoStep setupStep(int number, String action, String outcome, Long rowId) {
        return DemoStep.builder()
                .number(number)
                .action(action)
                .expected(outcome)
                .actual(outcome)
                .passed(true)
                .rowId(rowId)
                .build();
    }

    private static DemoStep failedStep(int number, String action, String expected, String actual) {
        return DemoStep.builder().number(number).action(action).expected(expected).actual(actual).build();
    }
}
