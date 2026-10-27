package org.pronto.pronto.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pronto.pronto.dto.CampaignRequest;
import org.pronto.pronto.dto.CampaignResponse;
import org.pronto.pronto.dto.TemplateRequest;
import org.pronto.pronto.dto.TemplateResponse;
import org.pronto.pronto.dto.UserRequest;
import org.pronto.pronto.dto.UserResponse;
import org.pronto.pronto.service.CatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/users")
    public List<UserResponse> users() {
        return catalogService.users();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        return catalogService.createUser(request);
    }

    @GetMapping("/campaigns")
    public List<CampaignResponse> campaigns() {
        return catalogService.campaigns();
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse createCampaign(@Valid @RequestBody CampaignRequest request) {
        return catalogService.createCampaign(request);
    }

    @GetMapping("/templates")
    public List<TemplateResponse> templates() {
        return catalogService.templates();
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse createTemplate(@Valid @RequestBody TemplateRequest request) {
        return catalogService.createTemplate(request);
    }
}
