package org.pronto.pronto.engine;

import org.pronto.pronto.entity.Campaign;
import org.pronto.pronto.entity.MessageTemplate;
import org.pronto.pronto.entity.MessageType;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.repository.NotificationRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Root object of every rule expression. Public getters expose plain values only: no entity or repository
 * is reachable from SpEL. DB-backed facts are loaded on first read and memoized for this evaluation.
 */
public class NotificationFacts {

    /** Hours reported when there is no previous notification (or no campaign). */
    public static final double NO_PREVIOUS_HOURS = 1e9;
    private static final double MILLIS_PER_HOUR = 3_600_000d;
    private static final String STUB_TEXT = "stub";
    private static final String STUB_TEMPLATE_CODE = "STUB";

    private final UserFacts user;
    private final MessageFacts message;
    private final Map<String, Object> variables;
    private final ZonedDateTime localNow;
    private final LongSupplier marketingSentTodayLoader;
    private final Supplier<Instant> lastSameCampaignLoader;
    private final Supplier<Instant> lastMarketingLoader;
    private Long marketingSentToday;
    private Double hoursSinceLastSameCampaign;
    private Double hoursSinceLastMarketing;

    private NotificationFacts(UserFacts user, MessageFacts message, Map<String, Object> variables,
                              ZonedDateTime localNow, LongSupplier marketingSentTodayLoader,
                              Supplier<Instant> lastSameCampaignLoader, Supplier<Instant> lastMarketingLoader) {
        this.user = user;
        this.message = message;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        this.localNow = localNow;
        this.marketingSentTodayLoader = marketingSentTodayLoader;
        this.lastSameCampaignLoader = lastSameCampaignLoader;
        this.lastMarketingLoader = lastMarketingLoader;
    }

    /** Facts for a real decision. Every query is bounded by {@code sent_at <= now}. */
    public static NotificationFacts load(User user, MessageTemplate template, Map<String, Object> variables,
                                         Instant now, NotificationRepository notifications) {
        ZonedDateTime localNow = now.atZone(ZoneId.of(user.getTimezone()));
        Instant localMidnight = localNow.toLocalDate().atStartOfDay(localNow.getZone()).toInstant();
        Long userId = user.getId();
        Campaign campaign = template.getCampaign();
        return new NotificationFacts(UserFacts.from(user), MessageFacts.from(template), variables, localNow,
                () -> notifications.countByUser_IdAndMessageTypeAndSentAtBetween(
                        userId, MessageType.MARKETING, localMidnight, now),
                () -> campaign == null ? null : notifications.findLastSentAtByCampaign(userId, campaign.getId(), now),
                () -> notifications.findLastSentAtByType(userId, MessageType.MARKETING, now));
    }

    /** DB-free facts with empty variables, used to dry-run an expression before it is saved. */
    public static NotificationFacts stub() {
        UserFacts user = UserFacts.builder()
                .id(0L).name(STUB_TEXT).timezone(ZoneOffset.UTC.getId()).city(STUB_TEXT).hubId(0).orderCount(0)
                .build();
        MessageFacts message = MessageFacts.builder()
                .type(MessageType.MARKETING.name()).templateCode(STUB_TEMPLATE_CODE)
                .build();
        return new NotificationFacts(user, message, Map.of(), Instant.EPOCH.atZone(ZoneOffset.UTC),
                () -> 0L, () -> null, () -> null);
    }

    public UserFacts getUser() {
        return user;
    }

    public MessageFacts getMessage() {
        return message;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public int getLocalHour() {
        return localNow.getHour();
    }

    public String getLocalDayOfWeek() {
        return localNow.getDayOfWeek().name();
    }

    public long getMarketingSentToday() {
        if (marketingSentToday == null) {
            marketingSentToday = marketingSentTodayLoader.getAsLong();
        }
        return marketingSentToday;
    }

    public double getHoursSinceLastSameCampaign() {
        if (hoursSinceLastSameCampaign == null) {
            hoursSinceLastSameCampaign = hoursSince(lastSameCampaignLoader.get());
        }
        return hoursSinceLastSameCampaign;
    }

    public double getHoursSinceLastMarketing() {
        if (hoursSinceLastMarketing == null) {
            hoursSinceLastMarketing = hoursSince(lastMarketingLoader.get());
        }
        return hoursSinceLastMarketing;
    }

    private double hoursSince(Instant previous) {
        if (previous == null) {
            return NO_PREVIOUS_HOURS;
        }
        return Duration.between(previous, localNow.toInstant()).toMillis() / MILLIS_PER_HOUR;
    }
}
