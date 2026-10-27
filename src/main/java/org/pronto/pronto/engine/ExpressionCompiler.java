package org.pronto.pronto.engine;

import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.exception.InvalidRuleException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingMethodResolver;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses and evaluates rule expressions in the SpEL sandbox. Rules are untrusted input, so evaluation always
 * uses {@link SimpleEvaluationContext} (no T(), new, bean refs, assignment or getClass()), and only the
 * allowlisted instance methods resolve, so a rule can't burn memory or CPU (repeat, replaceAll, ...).
 * Never use StandardEvaluationContext here.
 */
@Component
public class ExpressionCompiler {

    public static final int MAX_EXPRESSION_LENGTH = 1000;
    public static final Set<String> ALLOWED_METHODS = Set.of("contains", "containsKey", "startsWith", "endsWith",
            "equals", "equalsIgnoreCase", "isEmpty", "size", "length", "toLowerCase", "toUpperCase", "trim");

    private static final MethodResolver DATA_BINDING_METHODS = DataBindingMethodResolver.forInstanceMethodInvocation();
    /** Unknown names return null, which SpEL reports as "method cannot be found". */
    private static final MethodResolver ALLOWLISTED_METHODS = (context, target, name, argumentTypes) ->
            ALLOWED_METHODS.contains(name) ? DATA_BINDING_METHODS.resolve(context, target, name, argumentTypes) : null;

    private final ExpressionParser parser = new SpelExpressionParser();
    // ponytail: unbounded, one entry per distinct expression text; add a bound only if rules churn heavily.
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /** Keyed by expression text, so an edit (or a rolled-back edit) can never serve a stale expression. */
    public Expression compile(Rule rule) {
        return cache.computeIfAbsent(rule.getExpression(), parser::parseExpression);
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
        throw new EvaluationException("expression must return a boolean, got " + typeOf(result));
    }

    private static Object evaluate(Expression expression, NotificationFacts facts) {
        EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withMethodResolvers(ALLOWLISTED_METHODS)
                .withRootObject(facts)
                .build();
        return expression.getValue(context);
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
