package org.pronto.pronto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class DecideRequest {

    @NotNull
    private Long userId;

    @NotBlank
    @Size(max = 64)
    private String templateCode;

    @Size(max = 100)
    private String idempotencyKey;

    private Map<String, Object> variables;
}
