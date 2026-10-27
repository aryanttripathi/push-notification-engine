package org.pronto.pronto.service;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.dto.DemoDataResponse;
import org.pronto.pronto.dto.DemoRunResponse;
import org.pronto.pronto.dto.DemoStep;
import org.pronto.pronto.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Not @Transactional: the demo commits every step like real API calls. */
@SpringBootTest
class DemoServiceTest {

    @Autowired
    private DemoService demoService;
    @Autowired
    private RuleRepository ruleRepository;

    @Test
    void everyStepPassesAndTheScenarioIsReRunnable() {
        for (int run = 0; run < 2; run++) {
            DemoRunResponse result = demoService.run();

            assertThat(result.getSteps()).extracting(DemoStep::getNumber)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
            assertThat(result.getSteps()).allSatisfy(step ->
                    assertThat(step.isPassed()).as("step %d: %s", step.getNumber(), step.getActual()).isTrue());
            assertThat(result.isAllPassed()).isTrue();
            assertThat(ruleRepository.findByName("DEMO_SMALL_CART_" + result.getUserId())).isEmpty();
        }
    }

    @Test
    void snapshotCapsDecisionsAndNotificationsAt100() {
        demoService.run();
        DemoDataResponse data = demoService.snapshot();

        assertThat(data.getDecisions()).isNotEmpty().hasSizeLessThanOrEqualTo(100);
        assertThat(data.getNotifications()).isNotEmpty().hasSizeLessThanOrEqualTo(100);
        assertThat(data.getRules()).extracting("name").contains("QUIET_HOURS");
        assertThat(data.getTemplates()).isNotEmpty();
    }
}
