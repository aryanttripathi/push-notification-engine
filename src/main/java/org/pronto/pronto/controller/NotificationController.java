package org.pronto.pronto.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.pronto.pronto.dto.DecideRequest;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.dto.SentNotificationResponse;
import org.pronto.pronto.service.DecisionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String DEFAULT_LIMIT = "50";
    private static final int MAX_LIMIT = 500;

    private final DecisionService decisionService;
    private final Clock clock;

    /** BLOCKED is still a 200: the request was valid, the answer is "no". */
    @PostMapping("/decide")
    public DecisionResponse decide(@Valid @RequestBody DecideRequest request) {
        return decisionService.decide(request, clock.instant());
    }

    @GetMapping("/decisions")
    public List<DecisionResponse> decisions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit) {
        return decisionService.decisions(userId, limit);
    }

    @GetMapping("/sent")
    public List<SentNotificationResponse> sent(
            @RequestParam Long userId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit) {
        return decisionService.sent(userId, limit);
    }
}
