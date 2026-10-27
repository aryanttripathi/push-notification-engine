package org.pronto.pronto.engine;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.entity.User;

/** Read-only user profile as seen by rule expressions (`user.city`). */
@Value
@Builder
public class UserFacts {
    long id;
    String name;
    String timezone;
    String city;
    Long hubId;
    int orderCount;

    static UserFacts from(User user) {
        return UserFacts.builder()
                .id(user.getId())
                .name(user.getName())
                .timezone(user.getTimezone())
                .city(user.getCity())
                .hubId(user.getHubId())
                .orderCount(user.getOrderCount())
                .build();
    }
}
