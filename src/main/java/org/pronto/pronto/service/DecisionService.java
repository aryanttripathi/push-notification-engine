package org.pronto.pronto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.dto.DecideRequest;
import org.pronto.pronto.dto.DecisionResponse;
import org.pronto.pronto.dto.SentNotificationResponse;
import org.pronto.pronto.engine.EvaluationResult;
import org.pronto.pronto.engine.NotificationFacts;
import org.pronto.pronto.engine.RuleEngine;
import org.pronto.pronto.engine.RuleTrace;
import org.pronto.pronto.entity.Decision;
import org.pronto.pronto.entity.MessageTemplate;
import org.pronto.pronto.entity.MessageType;
import org.pronto.pronto.entity.Notification;
import org.pronto.pronto.entity.NotificationDecision;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.exception.BadRequestException;
import org.pronto.pronto.exception.ConflictException;
import org.pronto.pronto.exception.NotFoundException;
import org.pronto.pronto.repository.MessageTemplateRepository;
import org.pronto.pronto.repository.NotificationDecisionRepository;
import org.pronto.pronto.repository.NotificationRepository;
import org.pronto.pronto.repository.RuleRepository;
import org.pronto.pronto.repository.UserRepository;
import org.pronto.pronto.template.RenderedMessage;
import org.pronto.pronto.template.TemplateRenderer;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Decides whether a templated push may be sent to a user, and records the decision and the sent message. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionService {

    public static final String TRANSACTIONAL_BYPASS = "TRANSACTIONAL_BYPASS";
    public static final String VARIABLES_TOO_LONG = "VARIABLES_TOO_LONG";
    private static final int MAX_VARIABLES_JSON = 2000;
    private static final TypeReference<List<RuleTrace>> TRACE_TYPE = new TypeReference<>() { };

    private final UserRepository userRepository;
    private final MessageTemplateRepository templateRepository;
    private final RuleRepository ruleRepository;
    private final NotificationDecisionRepository decisionRepository;
    private final NotificationRepository notificationRepository;
    private final TemplateRenderer templateRenderer;
    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;

    @Transactional
    public DecisionResponse decide(DecideRequest request, Instant now) {
        Map<String, Object> variables = request.getVariables() == null ? Map.of() : request.getVariables();
        String variablesJson = variablesJson(variables);
        User user = lockUser(request.getUserId());
        Optional<DecisionResponse> replay = findReplay(request, user);
        if (replay.isPresent()) {
            return replay.get();
        }
        MessageTemplate template = loadTemplate(request.getTemplateCode());
        RenderedMessage message = templateRenderer.render(template, user, variables);
        EvaluationResult result = evaluate(user, template, variables, now);
        NotificationDecision decision = persistDecision(request, user, template, result, variablesJson, now);
        Notification notification = result.getDecision() == Decision.ALLOWED
                ? persistNotification(decision, message)
                : null;
        log.info("decision userId={} template={} result={} rule={} decisionId={} notificationId={}",
                user.getId(), template.getCode(), result.getDecision(), result.getRuleName(), decision.getId(),
                notification == null ? null : notification.getId());
        return toResponse(decision, notification);
    }

    @Transactional(readOnly = true)
    public List<DecisionResponse> decisions(Long userId, int limit) {
        List<NotificationDecision> decisions =
                decisionRepository.findByUser_IdOrderByDecidedAtDescIdDesc(userId, Limit.of(limit));
        if (decisions.isEmpty()) {
            return List.of();
        }
        Map<Long, Notification> byDecisionId = notificationRepository
                .findByDecision_IdIn(decisions.stream().map(NotificationDecision::getId).toList())
                .stream()
                .collect(Collectors.toMap(n -> n.getDecision().getId(), Function.identity()));
        return decisions.stream().map(d -> toResponse(d, byDecisionId.get(d.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<SentNotificationResponse> sent(Long userId, int limit) {
        return notificationRepository.findByUser_IdOrderBySentAtDescIdDesc(userId, Limit.of(limit)).stream()
                .map(DecisionService::toSentResponse)
                .toList();
    }

    /** First SQL of the transaction: serializes decisions per user, so caps can't be raced past. */
    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("user " + userId + " not found"));
    }

    /** Runs after the lock, so a concurrent retry with the same key sees the first attempt's rows. */
    private Optional<DecisionResponse> findReplay(DecideRequest request, User user) {
        if (StringUtils.isBlank(request.getIdempotencyKey())) {
            return Optional.empty();
        }
        return decisionRepository.findByIdempotencyKey(request.getIdempotencyKey()).map(stored -> {
            boolean sameRequest = Objects.equals(stored.getUser().getId(), user.getId())
                    && StringUtils.equals(stored.getTemplate().getCode(), request.getTemplateCode());
            if (!sameRequest) {
                throw new ConflictException("idempotencyKey was already used for a different user or template");
            }
            log.info("decision replayed userId={} decisionId={}", user.getId(), stored.getId());
            return toResponse(stored, notificationRepository.findByDecision_Id(stored.getId()).orElse(null));
        });
    }

    private MessageTemplate loadTemplate(String code) {
        return templateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("template " + code + " not found"));
    }

    /** Transactional messages always pass: an invariant in code that no dashboard rule can override. */
    private EvaluationResult evaluate(User user, MessageTemplate template, Map<String, Object> variables,
                                      Instant now) {
        if (template.getType() == MessageType.TRANSACTIONAL) {
            return EvaluationResult.builder()
                    .decision(Decision.ALLOWED)
                    .reason(TRANSACTIONAL_BYPASS)
                    .trace(List.of())
                    .build();
        }
        NotificationFacts facts = NotificationFacts.load(user, template, variables, now, notificationRepository);
        return ruleEngine.evaluate(ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc(), facts);
    }

    private NotificationDecision persistDecision(DecideRequest request, User user, MessageTemplate template,
                                                 EvaluationResult result, String variablesJson, Instant now) {
        NotificationDecision decision = new NotificationDecision();
        decision.setIdempotencyKey(StringUtils.defaultIfBlank(request.getIdempotencyKey(), null));
        decision.setUser(user);
        decision.setTemplate(template);
        decision.setMessageType(template.getType());
        decision.setDecision(result.getDecision());
        decision.setRuleId(result.getRuleId());
        decision.setRuleName(result.getRuleName());
        decision.setReason(result.getReason());
        decision.setVariables(variablesJson);
        // ponytail: trace column is 8000 chars (~80 rules); widen it if the rule table grows past that.
        decision.setTrace(toJson(result.getTrace()));
        decision.setDecidedAt(now);
        return decisionRepository.save(decision);
    }

    private Notification persistNotification(NotificationDecision decision, RenderedMessage message) {
        Notification notification = new Notification();
        notification.setDecision(decision);
        notification.setUser(decision.getUser());
        notification.setTemplate(decision.getTemplate());
        notification.setCampaign(decision.getTemplate().getCampaign());
        notification.setMessageType(decision.getMessageType());
        notification.setTitle(message.getTitle());
        notification.setBody(message.getBody());
        notification.setDeepLink(message.getDeepLink());
        notification.setSentAt(decision.getDecidedAt());
        return notificationRepository.save(notification);
    }

    private String variablesJson(Map<String, Object> variables) {
        String json = toJson(variables);
        if (StringUtils.length(json) > MAX_VARIABLES_JSON) {
            throw new BadRequestException(VARIABLES_TOO_LONG,
                    "variables are " + json.length() + " characters as JSON, max " + MAX_VARIABLES_JSON);
        }
        return json;
    }

    private DecisionResponse toResponse(NotificationDecision decision, Notification notification) {
        return DecisionResponse.builder()
                .decisionId(decision.getId())
                .userId(decision.getUser().getId())
                .templateCode(decision.getTemplate().getCode())
                .decision(decision.getDecision())
                .reason(decision.getReason())
                .ruleId(decision.getRuleId())
                .ruleName(decision.getRuleName())
                .messageType(decision.getMessageType())
                .decidedAt(decision.getDecidedAt())
                .notificationId(notification == null ? null : notification.getId())
                .message(notification == null ? null : RenderedMessage.builder()
                        .title(notification.getTitle())
                        .body(notification.getBody())
                        .deepLink(notification.getDeepLink())
                        .build())
                .trace(readTrace(decision.getTrace()))
                .build();
    }

    private static SentNotificationResponse toSentResponse(Notification notification) {
        return SentNotificationResponse.builder()
                .id(notification.getId())
                .decisionId(notification.getDecision().getId())
                .userId(notification.getUser().getId())
                .templateCode(notification.getTemplate().getCode())
                .campaignCode(notification.getCampaign() == null ? null : notification.getCampaign().getCode())
                .messageType(notification.getMessageType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .deepLink(notification.getDeepLink())
                .sentAt(notification.getSentAt())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize to JSON", e);
        }
    }

    private List<RuleTrace> readTrace(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, TRACE_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored trace is not valid JSON", e);
        }
    }
}
