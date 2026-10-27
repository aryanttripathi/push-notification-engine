package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class UserResponse {
    Long id;
    String name;
    String timezone;
    String city;
    Long hubId;
    int orderCount;
    Instant createdAt;
}
