package com.iptvplayer.sync.service;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.pairing.PairCode;
import com.iptvplayer.sync.dto.DeviceDto;
import com.iptvplayer.sync.repository.DeviceRepository;
import com.iptvplayer.sync.repository.PairCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService")
class DeviceServiceTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private PairCodeRepository pairCodeRepository;
    @InjectMocks private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceService, "pairCodeLength", 6);
        ReflectionTestUtils.setField(deviceService, "expiryMinutes", 10);
    }

    @Test
    @DisplayName("register() crée un device et retourne un PairCode valide")
    void register_shouldCreateDeviceAndPairCode() {
        Device saved = Device.builder()
            .id(UUID.randomUUID())
            .deviceName("TV Samsung")
            .deviceType(Device.DeviceType.ANDROID_TV)
            .platform("ANDROID")
            .status(Device.DeviceStatus.ONLINE)
            .createdAt(LocalDateTime.now())
            .build();

        PairCode savedCode = PairCode.builder()
            .id(UUID.randomUUID())
            .device(saved)
            .code("AB82JK")
            .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
            .used(false)
            .build();

        when(deviceRepository.save(any())).thenReturn(saved);
        when(pairCodeRepository.findByCodeAndUsedFalse(anyString())).thenReturn(Optional.empty());
        when(pairCodeRepository.save(any())).thenReturn(savedCode);

        DeviceDto.RegisterResponse response = deviceService.register(
            new DeviceDto.RegisterRequest("TV Samsung", Device.DeviceType.ANDROID_TV, null)
        );

        assertThat(response.deviceId()).isEqualTo(saved.getId());
        assertThat(response.pairCode()).hasSize(6);
        assertThat(response.pairCodeExpiresAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        verify(deviceRepository).save(any(Device.class));
        verify(pairCodeRepository).save(any(PairCode.class));
    }

    @Test
    @DisplayName("resolveCode() retourne le device si le code est valide")
    void resolveCode_shouldReturnDevice_whenCodeValid() {
        Device device = Device.builder().id(UUID.randomUUID()).deviceName("TV").build();
        PairCode code = PairCode.builder()
            .code("ABCDEF")
            .device(device)
            .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5))
            .used(false)
            .build();

        when(pairCodeRepository.findByCodeAndUsedFalse("ABCDEF")).thenReturn(Optional.of(code));

        Optional<Device> result = deviceService.resolveCode("ABCDEF");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(device.getId());
    }

    @Test
    @DisplayName("resolveCode() retourne empty si le code est expiré")
    void resolveCode_shouldReturnEmpty_whenCodeExpired() {
        Device device = Device.builder().id(UUID.randomUUID()).deviceName("TV").build();
        PairCode expiredCode = PairCode.builder()
            .code("EXPIRY")
            .device(device)
            .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)) // expiré
            .used(false)
            .build();

        when(pairCodeRepository.findByCodeAndUsedFalse("EXPIRY")).thenReturn(Optional.of(expiredCode));

        Optional<Device> result = deviceService.resolveCode("EXPIRY");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("resolveCode() retourne empty si le code est déjà utilisé")
    void resolveCode_shouldReturnEmpty_whenCodeUsed() {
        when(pairCodeRepository.findByCodeAndUsedFalse("USEDCD")).thenReturn(Optional.empty());

        Optional<Device> result = deviceService.resolveCode("USEDCD");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("refreshPairCode() invalide l'ancien code et crée un nouveau")
    void refreshPairCode_shouldInvalidateOldAndCreateNew() {
        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder().id(deviceId).deviceName("TV").build();
        PairCode oldCode = PairCode.builder()
            .device(device).code("OLDCOD")
            .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)).used(false).build();
        PairCode newCode = PairCode.builder()
            .device(device).code("NEWCOD")
            .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10)).used(false).build();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(pairCodeRepository.findActiveByDeviceId(eq(deviceId), any())).thenReturn(Optional.of(oldCode));
        when(pairCodeRepository.findByCodeAndUsedFalse(anyString())).thenReturn(Optional.empty());
        when(pairCodeRepository.save(any())).thenReturn(newCode);

        PairCode result = deviceService.refreshPairCode(deviceId);

        assertThat(result.getCode()).isEqualTo("NEWCOD");
        assertThat(oldCode.isUsed()).isTrue(); // ancien code invalidé
    }

    @Test
    @DisplayName("cleanExpiredCodes() supprime les codes expirés")
    void cleanExpiredCodes_shouldDeleteExpired() {
        deviceService.cleanExpiredCodes();
        verify(pairCodeRepository).deleteExpired(any(OffsetDateTime.class));
    }
}
