package com.iptvplayer.sync.controller;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.pairing.PairCode;
import com.iptvplayer.sync.dto.DeviceDto;
import com.iptvplayer.sync.dto.SyncEventDto;
import com.iptvplayer.sync.repository.DeviceRepository;
import com.iptvplayer.sync.service.DeviceService;
import com.iptvplayer.sync.service.PlaylistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final PlaylistService playlistService;

    @PostMapping("/register")
    public ResponseEntity<DeviceDto.RegisterResponse> register(
        @Valid @RequestBody DeviceDto.RegisterRequest request
    ) {
        return ResponseEntity.ok(deviceService.register(request));
    }

    @PostMapping("/{deviceId}/pair-code/refresh")
    public ResponseEntity<SyncEventDto.PairCodeResponse> refreshCode(
        @PathVariable UUID deviceId
    ) {
        PairCode code = deviceService.refreshPairCode(deviceId);
        return ResponseEntity.ok(new SyncEventDto.PairCodeResponse(
            code.getCode(), code.getExpiresAt()
        ));
    }

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

    /**
     * GET /api/device/all
     * Liste tous les appareils avec leurs playlists — portail web.
     */
    @GetMapping("/all")
    public ResponseEntity<List<DeviceDto.DeviceWithPlaylistsResponse>> listDevices() {
        return ResponseEntity.ok(
            deviceRepository.findAllByOrderByLastSeenDesc().stream()
                .map(d -> new DeviceDto.DeviceWithPlaylistsResponse(
                    d.getId(),
                    d.getDeviceName(),
                    d.getStatus(),
                    d.getLastSeen(),
                    playlistService.getPlaylists(d.getId())
                ))
                .toList()
        );
    }

    /**
     * DELETE /api/device/{deviceId}
     * Supprime un appareil et toutes ses données associées.
     */
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID deviceId) {
        deviceService.deleteDevice(deviceId);
        return ResponseEntity.noContent().build();
    }
}
