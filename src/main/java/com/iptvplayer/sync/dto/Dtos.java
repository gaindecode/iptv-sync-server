package com.iptvplayer.sync.dto;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.playlist.Playlist;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ── Device ────────────────────────────────────────────────────────────────────

public class DeviceDto {

    public record RegisterRequest(
        @NotBlank @Size(max = 255) String deviceName,
        Device.DeviceType deviceType
    ) {}

    public record RegisterResponse(
        UUID deviceId,
        String pairCode,
        LocalDateTime pairCodeExpiresAt,
        String portalUrl
    ) {}

    public record StatusResponse(
        UUID deviceId,
        String deviceName,
        Device.DeviceStatus status,
        LocalDateTime lastSeen
    ) {}
}

// ── Playlist ──────────────────────────────────────────────────────────────────

class PlaylistDto {

    public record AddRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank String sourceType,   // url | xtream
        String source,                  // URL M3U
        String epgUrl,
        String xtreamServer,
        String xtreamUsername,
        String xtreamPassword
    ) {}

    public record PlaylistResponse(
        UUID id,
        String name,
        String sourceType,
        String source,
        String epgUrl,
        int channelCount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        public static PlaylistResponse from(Playlist p) {
            return new PlaylistResponse(
                p.getId(), p.getName(),
                p.getSourceType().name().toLowerCase(),
                p.getSource(), p.getEpgUrl(),
                p.getChannelCount(),
                p.getStatus().name().toLowerCase(),
                p.getCreatedAt(), p.getUpdatedAt()
            );
        }
    }

    public record DevicePlaylistsResponse(
        UUID deviceId,
        List<PlaylistResponse> playlists
    ) {}
}

// ── Sync Event (WebSocket payload) ────────────────────────────────────────────

class SyncEventDto {

    public record WsMessage(
        String type,      // PLAYLIST_ADDED | PLAYLIST_UPDATED | PLAYLIST_REMOVED | PING
        Object payload
    ) {}

    public record PairCodeResponse(
        String pairCode,
        LocalDateTime expiresAt
    ) {}
}
