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
     * Enregistre un nouveau device (ou met à jour lastSeen).
     * Retourne le device + un PairCode frais.
     */
    @Transactional
    public DeviceDto.RegisterResponse register(DeviceDto.RegisterRequest request) {
        Device device = Device.builder()
            .deviceName(request.deviceName())
            .deviceType(request.deviceType() != null ? request.deviceType() : Device.DeviceType.ANDROID_TV)
            .platform("ANDROID")
            .status(Device.DeviceStatus.ONLINE)
            .build();
        device = deviceRepository.save(device);

        PairCode pairCode = generatePairCode(device);
        log.info("Device enregistré : {} — code : {}", device.getId(), pairCode.getCode());

        return new DeviceDto.RegisterResponse(
            device.getId(),
            pairCode.getCode(),
            pairCode.getExpiresAt(),
            "/link" // URL du portail web (relatif, à préfixer avec le domaine)
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
        pairCodeRepository.findActiveByDeviceId(deviceId, LocalDateTime.now())
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
        pairCodeRepository.deleteExpired(LocalDateTime.now());
        log.debug("Codes expirés nettoyés");
    }

    // ── Privé ──────────────────────────────────────────────────────────────────

    private PairCode generatePairCode(Device device) {
        String code = generateUniqueCode();
        PairCode pairCode = PairCode.builder()
            .device(device)
            .code(code)
            .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
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
