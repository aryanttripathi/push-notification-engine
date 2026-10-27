package org.pronto.pronto.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Evaluates rules in the given order. First match wins, no match allows, an error blocks (fail-closed). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngine {

    public static final String DEFAULT_ALLOW = "DEFAULT_ALLOW";
    public static final String RULE_ERROR = "RULE_ERROR";
    private static final int MAX_ERROR_LENGTH = 300;

    private final ExpressionCompiler compiler;

    public EvaluationResult evaluate(List<Rule> rules, NotificationFacts facts) {
        List<RuleTrace> trace = new ArrayList<>();
        for (Rule rule : rules) {
            boolean matched;
            try {
                matched = compiler.matches(rule, facts);
            } catch (RuntimeException e) {
                String error = StringUtils.abbreviate(
                        StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), MAX_ERROR_LENGTH);
                log.warn("rule failed, blocking ruleId={} rule={} error={}", rule.getId(), rule.getName(), error);
                trace.add(traceOf(rule, false, error));
                return result(Decision.BLOCKED, RULE_ERROR + ": " + rule.getName() + ": " + error, rule, trace);
            }
            log.debug("rule evaluated ruleId={} rule={} matched={}", rule.getId(), rule.getName(), matched);
            trace.add(traceOf(rule, matched, null));
            if (matched) {
                return result(decisionFor(rule), rule.getName(), rule, trace);
            }
        }
        return EvaluationResult.builder().decision(Decision.ALLOWED).reason(DEFAULT_ALLOW).trace(trace).build();
    }

    private static Decision decisionFor(Rule rule) {
        return switch (rule.getAction()) {
            case ALLOW -> Decision.ALLOWED;
            case BLOCK -> Decision.BLOCKED;
        };
    }

    private static EvaluationResult result(Decision decision, String reason, Rule rule, List<RuleTrace> trace) {
        return EvaluationResult.builder()
                .decision(decision)
                .reason(reason)
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .trace(trace)
                .build();
    }

    private static RuleTrace traceOf(Rule rule, boolean matched, String error) {
        return RuleTrace.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .priority(rule.getPriority())
                .action(rule.getAction())
                .matched(matched)
                .error(error)
                .build();
    }
}
