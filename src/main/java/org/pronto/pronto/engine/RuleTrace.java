package org.pronto.pronto.engine;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.pronto.pronto.entity.RuleAction;

/** One rule evaluation, stored as JSON on the decision for audit. */
@Value
@Builder
@Jacksonized
public class RuleTrace {
    Long ruleId;
    String ruleName;
    int priority;
    RuleAction action;
    boolean matched;
    String error;
}
