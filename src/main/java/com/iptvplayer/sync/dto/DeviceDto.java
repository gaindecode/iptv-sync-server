package com.iptvplayer.sync.dto;

import com.iptvplayer.sync.domain.device.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public class DeviceDto {

    public record RegisterRequest(
        @NotBlank @Size(max = 255) String deviceName,
        Device.DeviceType deviceType,
        /** UUID généré côté client (MMKV) — si fourni et déjà connu,
         *  le serveur réutilise ce device au lieu d'en créer un nouveau. */
        UUID deviceId
    ) {}

    public record RegisterResponse(
        UUID deviceId,
        String pairCode,
        OffsetDateTime pairCodeExpiresAt,
        String portalUrl
    ) {}

    public record StatusResponse(
        UUID deviceId,
        String deviceName,
        Device.DeviceStatus status,
        LocalDateTime lastSeen
    ) {}
}
