package com.iptvplayer.sync.controller;

import com.iptvplayer.sync.domain.device.SyncEvent;
import com.iptvplayer.sync.dto.PlaylistDto;
import com.iptvplayer.sync.service.DeviceService;
import com.iptvplayer.sync.service.PlaylistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;
    private final DeviceService deviceService;

    /**
     * POST /api/device/code/{pairCode}/playlist
     * Ajoute une playlist via le PairCode (portail web).
     * Le code est marqué comme utilisé après succès.
     *
     * Body : { "name": "Maison", "sourceType": "url", "source": "https://..." }
     */
    @PostMapping("/device/code/{pairCode}/playlist")
    public ResponseEntity<Object> addPlaylistByCode(
        @PathVariable String pairCode,
        @Valid @RequestBody PlaylistDto.AddRequest request
    ) {
        return deviceService.resolveCode(pairCode)
            .<ResponseEntity<Object>>map(device -> {
                PlaylistDto.PlaylistResponse response =
                    playlistService.addPlaylist(device.getId(), request);
                deviceService.markCodeUsed(pairCode);
                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.badRequest()
                .body(Map.of("error", "Code invalide ou expiré")));
    }

    /**
     * GET /api/device/{deviceId}/playlists
     * Liste les playlists d'un device.
     */
    @GetMapping("/device/{deviceId}/playlists")
    public ResponseEntity<List<PlaylistDto.PlaylistResponse>> getPlaylists(
        @PathVariable UUID deviceId
    ) {
        return ResponseEntity.ok(playlistService.getPlaylists(deviceId));
    }

    /**
     * DELETE /api/device/{deviceId}/playlists/{playlistId}
     * Supprime une playlist et notifie le device.
     */
    @DeleteMapping("/device/{deviceId}/playlists/{playlistId}")
    public ResponseEntity<Void> removePlaylist(
        @PathVariable UUID deviceId,
        @PathVariable UUID playlistId
    ) {
        playlistService.removePlaylist(deviceId, playlistId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/device/{deviceId}/sync
     * Polling fallback : retourne les événements non consommés.
     * Utilisé quand WebSocket n'est pas disponible.
     */
    @GetMapping("/device/{deviceId}/sync")
    public ResponseEntity<List<Map<String, Object>>> pollSync(
        @PathVariable UUID deviceId
    ) {
        List<SyncEvent> events = playlistService.pollEvents(deviceId);
        List<Map<String, Object>> response = events.stream()
            .map(e -> Map.<String, Object>of(
                "type", e.getEventType().name(),
                "payload", e.getPayload(),
                "createdAt", e.getCreatedAt().toString()
            ))
            .toList();
        return ResponseEntity.ok(response);
    }
}
