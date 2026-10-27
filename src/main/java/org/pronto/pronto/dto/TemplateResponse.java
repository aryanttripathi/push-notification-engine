package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.entity.MessageType;

import java.util.List;

@Value
@Builder
public class TemplateResponse {
    Long id;
    String code;
    MessageType type;
    String campaignCode;
    String title;
    String body;
    String deepLink;
    List<String> requiredVariables;
}
