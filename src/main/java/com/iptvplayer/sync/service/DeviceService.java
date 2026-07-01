package com.iptvplayer.sync.service;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.pairing.PairCode;
import com.iptvplayer.sync.dto.DeviceDto;
import com.iptvplayer.sync.repository.DeviceRepository;
import com.iptvplayer.sync.repository.PairCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final PairCodeRepository pairCodeRepository;

    @Value("${app.pair-code.length:6}")
    private int pairCodeLength;

    @Value("${app.pair-code.expiry-minutes:10}")
    private int expiryMinutes;

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O/1/I
    private final SecureRandom random = new SecureRandom();

    /**
     * Enregistre un device, ou réutilise celui existant si request.deviceId()
     * correspond à un device déjà connu (idempotence basée sur l'UUID
     * stable généré côté client et stocké en MMKV — voir syncService.ts
     * getOrCreateDeviceId()). Sans cette réutilisation, chaque appel créait
     * un nouveau device en base, désynchronisé de la connexion WebSocket
     * qui utilise toujours le même deviceId local côté TV.
     */
    @Transactional
    public DeviceDto.RegisterResponse register(DeviceDto.RegisterRequest request) {
        Device device = (request.deviceId() != null)
            ? deviceRepository.findById(request.deviceId()).orElse(null)
            : null;

        if (device != null) {
            device.setDeviceName(request.deviceName());
            device.setStatus(Device.DeviceStatus.ONLINE);
            device.setLastSeen(LocalDateTime.now());
            device = deviceRepository.save(device);
            log.info("Device réutilisé : {}", device.getId());
        } else {
            device = Device.builder()
                .id(request.deviceId()) // peut être null → généré dans @PrePersist
                .deviceName(request.deviceName())
                .deviceType(request.deviceType() != null ? request.deviceType() : Device.DeviceType.ANDROID_TV)
                .platform("ANDROID")
                .status(Device.DeviceStatus.ONLINE)
                .build();
            device = deviceRepository.save(device);
            log.info("Device créé : {}", device.getId());
        }

        PairCode pairCode = generatePairCode(device);
        log.info("PairCode généré pour {} : {}", device.getId(), pairCode.getCode());

        return new DeviceDto.RegisterResponse(
            device.getId(),
            pairCode.getCode(),
            pairCode.getExpiresAt(),
            "/portal"
        );
    }

    /**
     * Génère un nouveau PairCode pour un device existant.
     * Invalide l'ancien code s'il en existe un.
     */
    @Transactional
    public PairCode refreshPairCode(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device introuvable : " + deviceId));

        // Invalider le code précédent
        pairCodeRepository.findActiveByDeviceId(deviceId, OffsetDateTime.now(ZoneOffset.UTC))
            .ifPresent(old -> {
                old.setUsed(true);
                pairCodeRepository.save(old);
            });

        return generatePairCode(device);
    }

    /**
     * Résout un PairCode en Device — utilisé par le portail web.
     */
    @Transactional(readOnly = true)
    public Optional<Device> resolveCode(String code) {
        return pairCodeRepository.findByCodeAndUsedFalse(code.toUpperCase())
            .filter(PairCode::isValid)
            .map(PairCode::getDevice);
    }

    /**
     * Marque un PairCode comme utilisé après association.
     */
    @Transactional
    public void markCodeUsed(String code) {
        pairCodeRepository.findByCodeAndUsedFalse(code.toUpperCase())
            .ifPresent(pc -> {
                pc.setUsed(true);
                pairCodeRepository.save(pc);
            });
    }

    @Transactional
    public void updateLastSeen(UUID deviceId) {
        deviceRepository.updateLastSeen(deviceId, LocalDateTime.now());
    }

    /** Nettoyage horaire des codes expirés */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanExpiredCodes() {
        pairCodeRepository.deleteExpired(OffsetDateTime.now(ZoneOffset.UTC));
        log.debug("Codes expirés nettoyés");
    }

    // ── Privé ──────────────────────────────────────────────────────────────────

    private PairCode generatePairCode(Device device) {
        String code = generateUniqueCode();
        PairCode pairCode = PairCode.builder()
            .device(device)
            .code(code)
            .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(expiryMinutes))
            .used(false)
            .build();
        return pairCodeRepository.save(pairCode);
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = randomCode();
            attempts++;
            if (attempts > 100) throw new IllegalStateException("Impossible de générer un code unique");
        } while (pairCodeRepository.findByCodeAndUsedFalse(code).isPresent());
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(pairCodeLength);
        for (int i = 0; i < pairCodeLength; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
