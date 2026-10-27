package org.pronto.pronto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.pronto.pronto.entity.RuleAction;

@Data
public class RuleRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull
    private Integer priority;

    @NotBlank
    @Size(max = 1000)
    private String expression;

    @NotNull
    private RuleAction action;

    private boolean enabled = true;

    /** Required on update: the version the editor loaded. A stale one is rejected with 409. */
    private Long version;
}
