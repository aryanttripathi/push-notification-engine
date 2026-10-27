package org.pronto.pronto.engine;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.entity.Decision;

import java.util.List;

@Value
@Builder
public class EvaluationResult {
    Decision decision;
    String reason;
    Long ruleId;
    String ruleName;
    List<RuleTrace> trace;
}
