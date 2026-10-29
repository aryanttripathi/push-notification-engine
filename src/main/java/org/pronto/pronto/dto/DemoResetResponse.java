package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;

/** Row counts right after a reset. */
@Value
@Builder
public class DemoResetResponse {
    int rules;
    int users;
    int campaigns;
    int templates;
    int decisions;
    int notifications;
}
