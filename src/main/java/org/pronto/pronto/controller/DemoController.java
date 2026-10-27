package org.pronto.pronto.controller;

import lombok.RequiredArgsConstructor;
import org.pronto.pronto.dto.DemoDataResponse;
import org.pronto.pronto.dto.DemoRunResponse;
import org.pronto.pronto.service.DemoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pronto.demo.enabled", havingValue = "true")
public class DemoController {

    private final DemoService demoService;

    @PostMapping("/run")
    public DemoRunResponse run() {
        return demoService.run();
    }

    @GetMapping("/data")
    public DemoDataResponse data() {
        return demoService.snapshot();
    }
}
