package org.pronto.pronto;

import org.pronto.pronto.dto.DecideRequest;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.entity.RuleAction;
import org.pronto.pronto.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

public final class TestFixtures {

    public static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    /** A Thursday, outside DST changes. */
    public static final LocalDate DAY = LocalDate.of(2026, 1, 15);

    private TestFixtures() {
    }

    public static User user(ZoneId zone) {
        User user = new User();
        user.setName("Aryant");
        user.setTimezone(zone.getId());
        user.setCity("Bengaluru");
        user.setHubId(1L);
        return user;
    }

    public static Instant at(LocalDate day, String localTime, ZoneId zone) {
        return LocalTime.parse(localTime).atDate(day).atZone(zone).toInstant();
    }

    public static DecideRequest request(Long userId, String templateCode, Map<String, Object> variables) {
        DecideRequest request = new DecideRequest();
        request.setUserId(userId);
        request.setTemplateCode(templateCode);
        request.setVariables(variables);
        return request;
    }

    public static Rule rule(String name, int priority, String expression, RuleAction action) {
        Rule rule = new Rule();
        rule.setName(name);
        rule.setPriority(priority);
        rule.setExpression(expression);
        rule.setAction(action);
        rule.setEnabled(true);
        return rule;
    }
}
