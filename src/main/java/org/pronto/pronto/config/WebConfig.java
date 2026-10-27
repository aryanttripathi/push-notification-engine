package org.pronto.pronto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Clean URLs for the static pages in src/main/resources/static. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
        registry.addViewController("/dashboard/new").setViewName("forward:/rule-form.html");
        registry.addViewController("/dashboard/templates").setViewName("forward:/templates.html");
        registry.addViewController("/demo").setViewName("forward:/demo.html");
    }
}
