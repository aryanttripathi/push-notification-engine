package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.template.RenderedMessage;

import java.util.Map;

/** One scripted demo step: what was sent, what we expected, what happened. */
@Value
@Builder
public class DemoStep {
    int number;
    /** Simulated local time (Asia/Kolkata), null for setup steps. */
    String localTime;
    String action;
    Map<String, Object> variables;
    String expected;
    String actual;
    boolean passed;
    String note;
    String rule;
    RenderedMessage message;
    Long decisionId;
    Long notificationId;
    /** Row created by a setup step (user id, rule id). */
    Long rowId;
}
