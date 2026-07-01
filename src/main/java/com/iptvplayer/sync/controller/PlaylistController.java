package com.iptvplayer.sync.controller;

import com.iptvplayer.sync.domain.device.SyncEvent;
import com.iptvplayer.sync.dto.PlaylistDto;
import com.iptvplayer.sync.service.DeviceService;
import com.iptvplayer.sync.service.FileStorageService;
import com.iptvplayer.sync.service.PlaylistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PlaylistController {

    private final PlaylistService playlistService;
    private final DeviceService deviceService;
    private final FileStorageService fileStorageService;

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
     * POST /api/device/code/{pairCode}/playlist/upload
     * Ajoute une playlist à partir d'un fichier M3U uploadé (portail web).
     * Le fichier est stocké sur le serveur, son URL de téléchargement
     * devient le `source` de la playlist (sourceType=url, comme une
     * playlist distante classique — la TV ne sait pas faire la différence).
     *
     * Multipart : name (texte), file (fichier .m3u/.m3u8)
     */
    @PostMapping(value = "/device/code/{pairCode}/playlist/upload", consumes = "multipart/form-data")
    public ResponseEntity<Object> addPlaylistFromFile(
        @PathVariable String pairCode,
        @RequestParam("name") String name,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "epgUrl", required = false) String epgUrl,
        HttpServletRequest httpRequest
    ) {
        return deviceService.resolveCode(pairCode)
            .<ResponseEntity<Object>>map(device -> {
                try {
                    String fileId = fileStorageService.store(file);
                    String fileUrl = buildBaseUrl(httpRequest) + "/api/files/" + fileId;

                    PlaylistDto.AddRequest request = new PlaylistDto.AddRequest(
                        name, "url", fileUrl, epgUrl, null, null, null
                    );
                    PlaylistDto.PlaylistResponse response =
                        playlistService.addPlaylist(device.getId(), request);
                    deviceService.markCodeUsed(pairCode);
                    return ResponseEntity.ok(response);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                        .body(Map.<String, Object>of("error", e.getMessage()));
                } catch (IOException e) {
                    log.error("Erreur stockage fichier : {}", e.getMessage());
                    return ResponseEntity.internalServerError()
                        .body(Map.<String, Object>of("error", "Erreur lors du stockage du fichier"));
                }
            })
            .orElse(ResponseEntity.badRequest()
                .body(Map.of("error", "Code invalide ou expiré")));
    }

    /**
     * GET /api/files/{fileId}
     * Sert un fichier M3U uploadé — appelé par la TV comme n'importe
     * quelle URL M3U distante (loadM3UFromUrl côté RN).
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        if (!fileStorageService.exists(fileId)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(fileStorageService.resolve(fileId));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileId + "\"")
            .body(resource);
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean isDefaultPort = (scheme.equals("http") && port == 80)
            || (scheme.equals("https") && port == 443);
        return scheme + "://" + host + (isDefaultPort ? "" : ":" + port);
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
