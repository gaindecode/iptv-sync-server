package com.iptvplayer.sync.service;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.device.SyncEvent;
import com.iptvplayer.sync.domain.playlist.Playlist;
import com.iptvplayer.sync.dto.DeviceDto;
import com.iptvplayer.sync.dto.PlaylistDto;
import com.iptvplayer.sync.repository.DeviceRepository;
import com.iptvplayer.sync.repository.PlaylistRepository;
import com.iptvplayer.sync.repository.SyncEventRepository;
import com.iptvplayer.sync.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final DeviceRepository deviceRepository;
    private final SyncEventRepository syncEventRepository;
    private final WebSocketSessionManager wsManager;

    /**
     * Ajoute une playlist pour un device identifié par son PairCode (côté portail).
     * Émet un événement PLAYLIST_ADDED au device en temps réel via WebSocket.
     */
    @Transactional
    public PlaylistDto.PlaylistResponse addPlaylist(UUID deviceId, PlaylistDto.AddRequest request) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device introuvable : " + deviceId));

        Playlist.PlaylistSourceType sourceType = Playlist.PlaylistSourceType.valueOf(
            request.sourceType().toUpperCase()
        );

        Playlist playlist = Playlist.builder()
            .device(device)
            .name(request.name())
            .sourceType(sourceType)
            .source(request.source())
            .epgUrl(request.epgUrl())
            .xtreamServer(request.xtreamServer())
            .xtreamUsername(request.xtreamUsername())
            .xtreamPassword(request.xtreamPassword())
            .channelCount(0)
            .status(Playlist.PlaylistStatus.PENDING)
            .build();

        playlist = playlistRepository.save(playlist);
        log.info("Playlist ajoutée pour device {} : {}", deviceId, playlist.getId());

        // Persister l'événement (fallback polling si WS déconnecté)
        persistEvent(device, SyncEvent.EventType.PLAYLIST_ADDED, buildPayload(playlist));

        // Push WebSocket temps réel
        wsManager.sendToDevice(deviceId.toString(), Map.of(
            "type", "PLAYLIST_ADDED",
            "payload", buildPayload(playlist)
        ));

        return PlaylistDto.PlaylistResponse.from(playlist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistDto.PlaylistResponse> getPlaylists(UUID deviceId) {
        return playlistRepository.findByDeviceId(deviceId)
            .stream()
            .map(PlaylistDto.PlaylistResponse::from)
            .toList();
    }

    @Transactional
    public void removePlaylist(UUID deviceId, UUID playlistId) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device introuvable"));

        playlistRepository.deleteByIdAndDeviceId(playlistId, deviceId);

        persistEvent(device, SyncEvent.EventType.PLAYLIST_REMOVED,
            Map.of("playlistId", playlistId.toString()));

        wsManager.sendToDevice(deviceId.toString(), Map.of(
            "type", "PLAYLIST_REMOVED",
            "payload", Map.of("playlistId", playlistId.toString())
        ));

        log.info("Playlist {} supprimée pour device {}", playlistId, deviceId);
    }

    @Transactional
    public void mergeAll(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device introuvable"));

        persistEvent(device, SyncEvent.EventType.MERGE_ALL, Map.of());

        wsManager.sendToDevice(deviceId.toString(), Map.of(
            "type", "MERGE_ALL",
            "payload", Map.of()
        ));

        log.info("MERGE_ALL envoyé pour device {}", deviceId);
    }

    /**
     * Polling fallback — retourne les événements non consommés.
     * Utilisé quand le WebSocket n'est pas disponible.
     */
    @Transactional
    public List<SyncEvent> pollEvents(UUID deviceId) {
        List<SyncEvent> events = syncEventRepository
            .findByDeviceIdAndConsumedFalseOrderByCreatedAtAsc(deviceId);

        if (!events.isEmpty()) {
            syncEventRepository.markConsumed(events.stream().map(SyncEvent::getId).toList());
        }

        return events;
    }

    /** Nettoyage quotidien des anciens événements consommés */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanOldEvents() {
        syncEventRepository.deleteOldConsumed(LocalDateTime.now().minusHours(24));
        log.info("Anciens SyncEvents nettoyés");
    }

    // ── Privé ──────────────────────────────────────────────────────────────────

    private void persistEvent(Device device, SyncEvent.EventType type, Map<String, Object> payload) {
        SyncEvent event = SyncEvent.builder()
            .device(device)
            .eventType(type)
            .payload(payload)
            .consumed(false)
            .build();
        syncEventRepository.save(event);
    }

    private Map<String, Object> buildPayload(Playlist p) {
        return Map.of(
            "id", p.getId().toString(),
            "name", p.getName(),
            "sourceType", p.getSourceType().name().toLowerCase(),
            "source", p.getSource() != null ? p.getSource() : "",
            "epgUrl", p.getEpgUrl() != null ? p.getEpgUrl() : "",
            "xtreamServer", p.getXtreamServer() != null ? p.getXtreamServer() : "",
            "xtreamUsername", p.getXtreamUsername() != null ? p.getXtreamUsername() : "",
            "xtreamPassword", p.getXtreamPassword() != null ? p.getXtreamPassword() : ""
        );
    }
}
