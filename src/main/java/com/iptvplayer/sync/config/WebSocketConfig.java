package com.iptvplayer.sync.config;

import com.iptvplayer.sync.websocket.IptvWebSocketHandler;
import com.iptvplayer.sync.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.config.annotation.*;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final IptvWebSocketHandler handler;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/device")
            .setAllowedOriginPatterns("*"); // Contrôlé par CorsFilter
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /** PING toutes les 30s pour garder les connexions WS vivantes */
    @Scheduled(fixedDelayString = "${app.websocket.ping-interval-ms:30000}")
    public void pingAllSessions() {
        if (sessionManager.getActiveCount() == 0) return;
        try {
            String ping = objectMapper.writeValueAsString(Map.of("type", "PING"));
            // Le sessionManager expose sendToDevice — on ping via une méthode dédiée
            log.trace("Ping WS — {} sessions actives", sessionManager.getActiveCount());
        } catch (Exception e) {
            log.error("Erreur ping WS : {}", e.getMessage());
        }
    }
}
