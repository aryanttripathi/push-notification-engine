package org.pronto.pronto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pronto.pronto.dto.RuleRequest;
import org.pronto.pronto.dto.RuleResponse;
import org.pronto.pronto.dto.ValidateResponse;
import org.pronto.pronto.engine.ExpressionCompiler;
import org.pronto.pronto.entity.Rule;
import org.pronto.pronto.exception.ConflictException;
import org.pronto.pronto.exception.InvalidRuleException;
import org.pronto.pronto.exception.NotFoundException;
import org.pronto.pronto.repository.RuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Rule CRUD for the dashboard. Every saved expression has passed the sandbox dry-run first. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleService {

    public static final String SYSTEM_RULE_CANNOT_BE_DISABLED = "SYSTEM_RULE_CANNOT_BE_DISABLED";
    public static final String SYSTEM_RULE_CANNOT_BE_DELETED = "SYSTEM_RULE_CANNOT_BE_DELETED";

    private final RuleRepository ruleRepository;
    private final ExpressionCompiler expressionCompiler;

    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map(RuleService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        expressionCompiler.validate(request.getExpression());
        requireUniqueName(request.getName(), null);
        Rule rule = new Rule();
        apply(request, rule);
        Rule saved = ruleRepository.saveAndFlush(rule);
        log.info("rule created id={} name={} priority={} action={}", saved.getId(), saved.getName(),
                saved.getPriority(), saved.getAction());
        return toResponse(saved);
    }

    @Transactional
    public RuleResponse update(Long id, RuleRequest request) {
        Rule rule = load(id);
        if (!Objects.equals(request.getVersion(), rule.getVersion())) {
            throw new ConflictException("rule " + id + " is at version " + rule.getVersion()
                    + " but the request has version " + request.getVersion() + "; reload and retry");
        }
        if (rule.isSystemRule() && !request.isEnabled()) {
            throw new ConflictException(SYSTEM_RULE_CANNOT_BE_DISABLED,
                    "rule " + rule.getName() + " is a system rule: its expression and priority can change, "
                            + "but it can't be disabled");
        }
        expressionCompiler.validate(request.getExpression());
        requireUniqueName(request.getName(), id);
        apply(request, rule);
        Rule saved = ruleRepository.saveAndFlush(rule);
        log.info("rule updated id={} name={} version={} enabled={}", saved.getId(), saved.getName(),
                saved.getVersion(), saved.isEnabled());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Rule rule = load(id);
        if (rule.isSystemRule()) {
            throw new ConflictException(SYSTEM_RULE_CANNOT_BE_DELETED,
                    "rule " + rule.getName() + " is a system rule: edit it instead");
        }
        ruleRepository.delete(rule);
        log.info("rule deleted id={} name={}", id, rule.getName());
    }

    public ValidateResponse validate(String expression) {
        try {
            expressionCompiler.validate(expression);
            return ValidateResponse.builder().valid(true).build();
        } catch (InvalidRuleException e) {
            return ValidateResponse.builder().valid(false).error(e.getMessage()).build();
        }
    }

    private Rule load(Long id) {
        return ruleRepository.findById(id).orElseThrow(() -> new NotFoundException("rule " + id + " not found"));
    }

    private void requireUniqueName(String name, Long ownId) {
        ruleRepository.findByName(name)
                .filter(other -> !Objects.equals(other.getId(), ownId))
                .ifPresent(other -> {
                    throw new ConflictException("a rule named " + name + " already exists");
                });
    }

    private static void apply(RuleRequest request, Rule rule) {
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setPriority(request.getPriority());
        rule.setExpression(request.getExpression());
        rule.setAction(request.getAction());
        rule.setEnabled(request.isEnabled());
    }

    private static RuleResponse toResponse(Rule rule) {
        return RuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .priority(rule.getPriority())
                .expression(rule.getExpression())
                .action(rule.getAction())
                .enabled(rule.isEnabled())
                .systemRule(rule.isSystemRule())
                .version(rule.getVersion())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
