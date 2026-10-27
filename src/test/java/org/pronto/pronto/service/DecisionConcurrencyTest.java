package org.pronto.pronto.service;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.repository.NotificationRepository;
import org.pronto.pronto.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.pronto.pronto.TestFixtures.DAY;
import static org.pronto.pronto.TestFixtures.KOLKATA;
import static org.pronto.pronto.TestFixtures.at;
import static org.pronto.pronto.TestFixtures.request;
import static org.pronto.pronto.TestFixtures.user;

/** Not @Transactional on purpose: each decide commits for real, so the per-user row lock is what's tested. */
@SpringBootTest
class DecisionConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    private DecisionService decisionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void parallelDecidesForOneUserNeverExceedTheDailyCap() throws Exception {
        User user = userRepository.save(user(KOLKATA));
        Instant noon = at(DAY, "12:00", KOLKATA);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<DecisionResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return decisionService.decide(request(user.getId(), "NEW_ARRIVALS", Map.of()), noon);
                }));
            }
            start.countDown();
            List<Decision> decisions = new ArrayList<>();
            for (Future<DecisionResponse> future : futures) {
                decisions.add(future.get(30, TimeUnit.SECONDS).getDecision());
            }

            assertThat(decisions).filteredOn(Decision.ALLOWED::equals).hasSize(3);
            assertThat(decisions).filteredOn(Decision.BLOCKED::equals).hasSize(THREADS - 3);
            assertThat(notificationRepository.countByUser_Id(user.getId())).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }
}
