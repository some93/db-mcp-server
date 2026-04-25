package com.mcp.transport.http;

import com.mcp.infrastructure.config.AppConfig;
import com.mcp.transport.http.sse.SseSessionManager;
import com.mcp.transport.mcp.core.McpProtocolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(McpTransportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(McpTransportControllerRouteTest.TestConfig.class)
@TestPropertySource(properties = {
        "mcp.http.path=/custom-mcp",
        "mcp.http.ssePath=/custom-events",
        "spring.main.allow-bean-definition-overriding=true"
})
class McpTransportControllerRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private McpProtocolService protocolService;

    @Autowired
    private SseSessionManager sseSessionManager;

    @Test
    void customHttpPath_isMappedInsteadOfDefaultPath() throws Exception {
        when(protocolService.handleRequest(anyMap()))
                .thenReturn(Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of("ok", true)));
        when(protocolService.handleRequest(anyMap(), isNull()))
                .thenReturn(Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of("ok", true)));

        mockMvc.perform(post("/custom-mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"initialize\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.result.ok").value(true));

        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"initialize\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customSsePath_isMappedInsteadOfDefaultPath() throws Exception {
        when(sseSessionManager.createSession()).thenReturn(new SseEmitter());

        mockMvc.perform(get("/custom-events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        mockMvc.perform(get("/mcp/sse"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AppConfig appConfig() {
            AppConfig appConfig = new AppConfig();
            appConfig.getMcp().getHttp().setEnabled(true);
            return appConfig;
        }

        @Bean
        McpProtocolService protocolService() {
            return mock(McpProtocolService.class);
        }

        @Bean
        SseSessionManager sseSessionManager() {
            return mock(SseSessionManager.class);
        }
    }
}
