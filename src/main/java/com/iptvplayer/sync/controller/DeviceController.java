package com.iptvplayer.sync.controller;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.pairing.PairCode;
import com.iptvplayer.sync.dto.DeviceDto;
import com.iptvplayer.sync.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * POST /api/device/register
     * Enregistre un nouveau device Android TV.
     * Retourne le deviceId permanent + le PairCode temporaire.
     *
     * Body : { "deviceName": "TV Samsung", "deviceType": "ANDROID_TV" }
     */
    @PostMapping("/register")
    public ResponseEntity<DeviceDto.RegisterResponse> register(
        @Valid @RequestBody DeviceDto.RegisterRequest request
    ) {
        return ResponseEntity.ok(deviceService.register(request));
    }

    /**
     * POST /api/device/{deviceId}/pair-code/refresh
     * Regénère un PairCode pour un device (code expiré ou utilisé).
     */
    @PostMapping("/{deviceId}/pair-code/refresh")
    public ResponseEntity<SyncEventDto.PairCodeResponse> refreshCode(
        @PathVariable UUID deviceId
    ) {
        PairCode code = deviceService.refreshPairCode(deviceId);
        return ResponseEntity.ok(new SyncEventDto.PairCodeResponse(
            code.getCode(), code.getExpiresAt()
        ));
    }

    /**
     * GET /api/device/resolve/{code}
     * Résout un PairCode en deviceId — utilisé par le portail web.
     */
    @GetMapping("/resolve/{code}")
    public ResponseEntity<DeviceDto.StatusResponse> resolveCode(
        @PathVariable String code
    ) {
        return deviceService.resolveCode(code)
            .map(device -> ResponseEntity.ok(new DeviceDto.StatusResponse(
                device.getId(),
                device.getDeviceName(),
                device.getStatus(),
                device.getLastSeen()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
}
