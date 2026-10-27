package org.pronto.pronto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    /** IANA zone id, e.g. Asia/Kolkata. */
    @NotBlank
    @Size(max = 64)
    private String timezone;

    /** Required: templates use {{user_city}}, and a request can't supply it. */
    @NotBlank
    @Size(max = 64)
    private String city;

    @PositiveOrZero
    private Integer hubId;

    @PositiveOrZero
    private int orderCount;
}
