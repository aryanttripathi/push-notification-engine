package org.pronto.pronto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.pronto.pronto.entity.MessageType;

@Data
public class TemplateRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotNull
    private MessageType type;

    /** Optional. Notifications of the same campaign are deduplicated. */
    @Size(max = 64)
    private String campaignCode;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String body;

    @Size(max = 500)
    private String deepLink;
}
