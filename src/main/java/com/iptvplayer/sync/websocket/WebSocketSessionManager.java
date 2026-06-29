package com.iptvplayer.sync.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les sessions WebSocket actives, indexées par deviceId.
 * Thread-safe via ConcurrentHashMap.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public void register(String deviceId, WebSocketSession session) {
        sessions.put(deviceId, session);
        log.info("WS connecté : device={} session={}", deviceId, session.getId());
    }

    public void unregister(String deviceId) {
        sessions.remove(deviceId);
        log.info("WS déconnecté : device={}", deviceId);
    }

    public boolean isConnected(String deviceId) {
        WebSocketSession session = sessions.get(deviceId);
        return session != null && session.isOpen();
    }

    /**
     * Envoie un message JSON au device.
     * Silencieux si le device est déconnecté (l'événement est persisté en DB).
     */
    public void sendToDevice(String deviceId, Object payload) {
        WebSocketSession session = sessions.get(deviceId);
        if (session == null || !session.isOpen()) {
            log.debug("WS send ignoré : device={} hors ligne", deviceId);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
            log.debug("WS envoyé à device={} : {}", deviceId, json);
        } catch (Exception e) {
            log.error("Erreur WS send device={} : {}", deviceId, e.getMessage());
            sessions.remove(deviceId);
        }
    }

    public int getActiveCount() {
        return (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }
}
