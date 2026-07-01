package com.iptvplayer.sync.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class SyncEventDto {

    public record WsMessage(
        String type,      // PLAYLIST_ADDED | PLAYLIST_UPDATED | PLAYLIST_REMOVED | PING
        Object payload
    ) {}

    public record PairCodeResponse(
        String pairCode,
        OffsetDateTime expiresAt
    ) {}
}
