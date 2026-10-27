package org.pronto.pronto.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DemoRunResponse {
    Long userId;
    String userName;
    int total;
    int passed;
    boolean allPassed;
    List<DemoStep> steps;
}
