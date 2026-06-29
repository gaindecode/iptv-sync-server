package com.iptvplayer.sync.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iptvplayer.sync.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;

/**
 * Handler WebSocket natif (pas de STOMP).
 *
 * Protocole TV → Serveur :
 *   { "type": "IDENTIFY", "deviceId": "uuid" }  — à la connexion
 *   { "type": "PONG" }                           — réponse au PING serveur
 *
 * Protocole Serveur → TV :
 *   { "type": "PING" }                           — keepalive 30s
 *   { "type": "PLAYLIST_ADDED", "payload": {...} }
 *   { "type": "PLAYLIST_REMOVED", "payload": {...} }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IptvWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Le deviceId sera fourni via le message IDENTIFY
        // On stocke provisoirement avec l'ID de session
        log.debug("WS nouvelle connexion : {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) msg.get("type");

        switch (type) {
            case "IDENTIFY" -> {
                String deviceId = (String) msg.get("deviceId");
                if (deviceId != null) {
                    // Associer la session au deviceId
                    session.getAttributes().put("deviceId", deviceId);
                    sessionManager.register(deviceId, session);
                    deviceService.updateLastSeen(UUID.fromString(deviceId));

                    // Confirmer la connexion
                    String ack = objectMapper.writeValueAsString(Map.of(
                        "type", "CONNECTED",
                        "deviceId", deviceId
                    ));
                    session.sendMessage(new TextMessage(ack));
                }
            }
            case "PONG" -> log.trace("PONG reçu de {}", session.getAttributes().get("deviceId"));
            default -> log.warn("Message WS inconnu : type={}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceId = (String) session.getAttributes().get("deviceId");
        if (deviceId != null) {
            sessionManager.unregister(deviceId);
        }
        log.debug("WS fermé : {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String deviceId = (String) session.getAttributes().get("deviceId");
        log.error("WS erreur device={} : {}", deviceId, exception.getMessage());
        if (deviceId != null) sessionManager.unregister(deviceId);
    }
}
