package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class CampaignResponse {
    Long id;
    String code;
    String name;
    String city;
    boolean active;
    Instant createdAt;
}
