package org.pronto.pronto.service;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.repository.NotificationRepository;
import org.pronto.pronto.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.pronto.pronto.TestFixtures.DAY;
import static org.pronto.pronto.TestFixtures.KOLKATA;
import static org.pronto.pronto.TestFixtures.at;
import static org.pronto.pronto.TestFixtures.request;
import static org.pronto.pronto.TestFixtures.user;

/** Not @Transactional on purpose: each decide commits for real, so the per-user row lock is what's tested. */
@SpringBootTest
class DecisionConcurrencyTest {

    private static final int THREADS = 10;
    private static final int DAILY_CAP = 3;

    @Autowired
    private DecisionService decisionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @MockitoBean
    private Clock clock;

    @Test
    void parallelDecidesForOneUserNeverExceedTheDailyCap() throws Exception {
        User user = userRepository.save(user(KOLKATA));
        Instant noon = at(DAY, "12:00", KOLKATA);

        List<DecisionResponse> responses = inParallel(
                () -> decisionService.decide(request(user.getId(), "NEW_ARRIVALS", Map.of()), noon));

        assertCapHeld(user, decisionsOf(responses));
    }

    /** API path: every clock read is later than the previous one, as on a real server. */
    @Test
    void parallelDecidesThroughTheClockPathNeverExceedTheDailyCap() throws Exception {
        User user = userRepository.save(user(KOLKATA));
        Instant noon = at(DAY, "12:00", KOLKATA);
        AtomicLong ticks = new AtomicLong();
        when(clock.instant()).thenAnswer(invocation -> noon.plusMillis(ticks.incrementAndGet()));

        List<DecisionResponse> responses = inParallel(
                () -> decisionService.decide(request(user.getId(), "NEW_ARRIVALS", Map.of())));

        assertCapHeld(user, decisionsOf(responses));
        // "now" is read after the lock, so later-locked (higher id) decisions never carry an earlier time.
        assertThat(responses.stream().sorted(Comparator.comparing(DecisionResponse::getDecisionId))
                .map(DecisionResponse::getDecidedAt).toList()).isSorted();
    }

    private void assertCapHeld(User user, List<Decision> decisions) {
        assertThat(decisions).filteredOn(Decision.ALLOWED::equals).hasSize(DAILY_CAP);
        assertThat(decisions).filteredOn(Decision.BLOCKED::equals).hasSize(THREADS - DAILY_CAP);
        assertThat(notificationRepository.countByUser_Id(user.getId())).isEqualTo(DAILY_CAP);
    }

    private static List<Decision> decisionsOf(List<DecisionResponse> responses) {
        return responses.stream().map(DecisionResponse::getDecision).toList();
    }

    private static List<DecisionResponse> inParallel(Callable<DecisionResponse> decide) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<DecisionResponse>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return decide.call();
                }));
            }
            start.countDown();
            List<DecisionResponse> responses = new ArrayList<>();
            for (Future<DecisionResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
