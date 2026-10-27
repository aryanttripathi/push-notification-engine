package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.entity.RuleAction;

import java.time.Instant;

@Value
@Builder
public class RuleResponse {
    Long id;
    String name;
    String description;
    int priority;
    String expression;
    RuleAction action;
    boolean enabled;
    boolean systemRule;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
