package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.engine.RuleTrace;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.MessageType;
import org.pronto.pronto.template.RenderedMessage;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class DecisionResponse {
    Long decisionId;
    Long userId;
    String templateCode;
    Decision decision;
    String reason;
    Long ruleId;
    String ruleName;
    MessageType messageType;
    Instant decidedAt;
    Long notificationId;
    RenderedMessage message;
    List<RuleTrace> trace;
}
