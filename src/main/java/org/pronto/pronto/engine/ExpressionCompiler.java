package org.pronto.pronto.engine;

import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.exception.InvalidRuleException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses and evaluates rule expressions in the SpEL sandbox. Rules are untrusted input, so evaluation always
 * uses {@link SimpleEvaluationContext} (no T(), new, bean refs, assignment or getClass()).
 * Never use StandardEvaluationContext here.
 */
@Component
public class ExpressionCompiler {

    public static final int MAX_EXPRESSION_LENGTH = 1000;

    private final ExpressionParser parser = new SpelExpressionParser();
    // ponytail: unbounded, one entry per (rule id, version) until restart; fine at dashboard edit rates.
    // Evict superseded versions if rules start changing often.
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    public Expression compile(Rule rule) {
        return cache.computeIfAbsent(rule.getId() + ":" + rule.getVersion(),
                key -> parser.parseExpression(rule.getExpression()));
    }

    /** Parses the expression and dry-runs it on stub facts; it must return a Boolean. */
    public void validate(String expression) {
        if (StringUtils.isBlank(expression)) {
            throw new InvalidRuleException("expression must not be blank");
        }
        if (StringUtils.length(expression) > MAX_EXPRESSION_LENGTH) {
            throw new InvalidRuleException("expression is longer than " + MAX_EXPRESSION_LENGTH + " characters");
        }
        Object result;
        try {
            result = evaluate(parser.parseExpression(expression), NotificationFacts.stub());
        } catch (RuntimeException e) {
            throw new InvalidRuleException(e.getMessage());
        }
        if (!(result instanceof Boolean)) {
            throw new InvalidRuleException("expression must return a boolean, got " + typeOf(result));
        }
    }

    /** Evaluates a saved rule. Throws if it fails or does not return a Boolean. */
    public boolean matches(Rule rule, NotificationFacts facts) {
        Object result = evaluate(compile(rule), facts);
        if (result instanceof Boolean matched) {
            return matched;
        }
        throw new IllegalStateException("expression must return a boolean, got " + typeOf(result));
    }

    private static Object evaluate(Expression expression, NotificationFacts facts) {
        EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withInstanceMethods()
                .withRootObject(facts)
                .build();
        return expression.getValue(context);
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
