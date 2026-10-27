package org.pronto.pronto.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pronto.pronto.dto.RuleRequest;
import org.pronto.pronto.dto.RuleResponse;
import org.pronto.pronto.dto.ValidateRequest;
import org.pronto.pronto.dto.ValidateResponse;
import org.pronto.pronto.service.RuleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @GetMapping
    public List<RuleResponse> list() {
        return ruleService.list();
    }

    @GetMapping("/{id}")
    public RuleResponse get(@PathVariable Long id) {
        return ruleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@Valid @RequestBody RuleRequest request) {
        return ruleService.create(request);
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        return ruleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ruleService.delete(id);
    }

    /** Always 200: the answer is {valid, error?}. */
    @PostMapping("/validate")
    public ValidateResponse validate(@RequestBody ValidateRequest request) {
        return ruleService.validate(request.getExpression());
    }
}
