package org.pronto.pronto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampaignRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 64)
    private String city;

    private boolean active = true;
}
