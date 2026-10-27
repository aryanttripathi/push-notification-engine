package org.pronto.pronto.engine;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.MessageTemplate;
import org.pronto.pronto.entity.MessageType;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.entity.RuleAction;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.repository.NotificationRepository;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.pronto.pronto.TestFixtures.rule;

class RuleEngineTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final RuleEngine engine = new RuleEngine(new ExpressionCompiler());

    @Test
    void databaseFailureInALazyFactPropagatesInsteadOfBecomingARuleError() {
        when(notifications.countByUser_IdAndMessageTypeAndSentAtBetween(any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> engine.evaluate(List.of(saved("marketingSentToday >= 3")), facts(Map.of(), "Pune")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void runtimeExceptionFromAnAllowlistedMethodFailsClosed() {
        EvaluationResult result = engine.evaluate(List.of(saved("'Ben'.startsWith(user.city)")), facts(Map.of(), null));

        assertThat(result.getDecision()).isEqualTo(Decision.BLOCKED);
        assertThat(result.getReason()).startsWith(RuleEngine.RULE_ERROR);
    }

    private static Rule saved(String expression) {
        Rule rule = rule("R", 1, expression, RuleAction.BLOCK);
        rule.setId(1L);
        rule.setVersion(0L);
        return rule;
    }

    private NotificationFacts facts(Map<String, Object> variables, String city) {
        User user = new User();
        user.setId(1L);
        user.setName("Aryant");
        user.setTimezone("Asia/Kolkata");
        user.setCity(city);
        MessageTemplate template = new MessageTemplate();
        template.setCode("NEW_ARRIVALS");
        template.setType(MessageType.MARKETING);
        return NotificationFacts.load(user, template, variables, Instant.parse("2026-01-15T06:30:00Z"), notifications);
    }
}
