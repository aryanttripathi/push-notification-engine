package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DemoDataResponse {
    List<UserResponse> users;
    List<CampaignResponse> campaigns;
    List<TemplateResponse> templates;
    List<RuleResponse> rules;
    List<DecisionResponse> decisions;
    List<SentNotificationResponse> notifications;
}
