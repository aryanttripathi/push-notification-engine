package org.pronto.pronto.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pages are forwards to static files; MockMvc doesn't follow forwards, so each hop is fetched explicitly. */
@SpringBootTest
@AutoConfigureMockMvc
class PageTest {

    private static final Map<String, String> PAGES = Map.of(
            "/dashboard", "<title>Pronto Rules</title>",
            "/dashboard/new", "<title>Pronto Rule Editor</title>",
            "/dashboard/templates", "<title>Pronto Templates</title>",
            "/demo", "<title>Pronto Demo</title>");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pagesServeTheirHtml() throws Exception {
        for (Map.Entry<String, String> page : PAGES.entrySet()) {
            String target = mockMvc.perform(get(page.getKey()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getForwardedUrl();
            mockMvc.perform(get(target))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(page.getValue())));
        }
    }
}
