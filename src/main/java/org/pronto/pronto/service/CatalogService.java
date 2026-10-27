package org.pronto.pronto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.dto.CampaignRequest;
import org.pronto.pronto.dto.CampaignResponse;
import org.pronto.pronto.dto.TemplateRequest;
import org.pronto.pronto.dto.TemplateResponse;
import org.pronto.pronto.dto.UserRequest;
import org.pronto.pronto.dto.UserResponse;
import org.pronto.pronto.entity.Campaign;
import org.pronto.pronto.entity.MessageTemplate;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.exception.BadRequestException;
import org.pronto.pronto.exception.ConflictException;
import org.pronto.pronto.exception.NotFoundException;
import org.pronto.pronto.repository.CampaignRepository;
import org.pronto.pronto.repository.MessageTemplateRepository;
import org.pronto.pronto.repository.UserRepository;
import org.pronto.pronto.template.TemplateRenderer;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

/** Users, campaigns and templates: the setup data decisions run against. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    public static final String INVALID_TIMEZONE = "INVALID_TIMEZONE";
    private static final Sort BY_ID = Sort.by("id");

    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final MessageTemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;

    // ponytail: unpaged lists; add paging once these tables outgrow a single page.
    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        return userRepository.findAll(BY_ID).stream().map(CatalogService::toResponse).toList();
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setTimezone(validZone(request.getTimezone()));
        user.setCity(StringUtils.trim(request.getCity()));
        user.setHubId(request.getHubId() == null ? null : request.getHubId().longValue());
        user.setOrderCount(request.getOrderCount());
        User saved = userRepository.save(user);
        log.info("user created id={} timezone={}", saved.getId(), saved.getTimezone());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> campaigns() {
        return campaignRepository.findAll(BY_ID).stream().map(CatalogService::toResponse).toList();
    }

    @Transactional
    public CampaignResponse createCampaign(CampaignRequest request) {
        if (campaignRepository.existsByCode(request.getCode())) {
            throw new ConflictException("campaign " + request.getCode() + " already exists");
        }
        Campaign campaign = new Campaign();
        campaign.setCode(request.getCode());
        campaign.setName(request.getName());
        campaign.setCity(StringUtils.trimToNull(request.getCity()));
        campaign.setActive(request.isActive());
        Campaign saved = campaignRepository.save(campaign);
        log.info("campaign created id={} code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> templates() {
        return templateRepository.findAll(BY_ID).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TemplateResponse createTemplate(TemplateRequest request) {
        if (templateRepository.existsByCode(request.getCode())) {
            throw new ConflictException("template " + request.getCode() + " already exists");
        }
        MessageTemplate template = new MessageTemplate();
        template.setCode(request.getCode());
        template.setType(request.getType());
        template.setCampaign(campaignOf(request.getCampaignCode()));
        template.setTitle(request.getTitle());
        template.setBody(request.getBody());
        template.setDeepLink(StringUtils.trimToNull(request.getDeepLink()));
        MessageTemplate saved = templateRepository.save(template);
        log.info("template created id={} code={} type={}", saved.getId(), saved.getCode(), saved.getType());
        return toResponse(saved);
    }

    private Campaign campaignOf(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return campaignRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("campaign " + code + " not found"));
    }

    /** IANA region ids only (Asia/Kolkata); offsets and abbreviations like +05:30 or IST are rejected. */
    private static String validZone(String timezone) {
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new BadRequestException(INVALID_TIMEZONE,
                    "unknown timezone " + timezone + ", use an IANA region id such as Asia/Kolkata");
        }
        return timezone;
    }

    private static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .timezone(user.getTimezone())
                .city(user.getCity())
                .hubId(user.getHubId())
                .orderCount(user.getOrderCount())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static CampaignResponse toResponse(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .code(campaign.getCode())
                .name(campaign.getName())
                .city(campaign.getCity())
                .active(campaign.isActive())
                .createdAt(campaign.getCreatedAt())
                .build();
    }

    private TemplateResponse toResponse(MessageTemplate template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .code(template.getCode())
                .type(template.getType())
                .campaignCode(template.getCampaign() == null ? null : template.getCampaign().getCode())
                .title(template.getTitle())
                .body(template.getBody())
                .deepLink(template.getDeepLink())
                .requiredVariables(templateRenderer.variablesOf(template).stream()
                        .filter(name -> !TemplateRenderer.SYSTEM_VARIABLES.contains(name))
                        .toList())
                .build();
    }
}
