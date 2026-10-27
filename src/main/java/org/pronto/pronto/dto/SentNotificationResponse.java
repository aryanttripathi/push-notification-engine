package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.entity.MessageType;

import java.time.Instant;

@Value
@Builder
public class SentNotificationResponse {
    Long id;
    Long decisionId;
    Long userId;
    String templateCode;
    String campaignCode;
    MessageType messageType;
    String title;
    String body;
    String deepLink;
    Instant sentAt;
}
