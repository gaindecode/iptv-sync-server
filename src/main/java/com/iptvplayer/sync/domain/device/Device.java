package com.iptvplayer.sync.domain.device;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Device {

    @Id
    // Pas de @UuidGenerator ici : l'ID peut être fourni explicitement par
    // le client (deviceId généré côté RN, stocké en MMKV) pour garantir
    // la stabilité entre les appels register() successifs. setIdIfAbsent()
    // dans @PrePersist génère un UUID seulement si aucun n'a été fourni.
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "device_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    @Column(nullable = false)
    private String platform;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
        if (status == null) status = DeviceStatus.ONLINE;
        if (deviceType == null) deviceType = DeviceType.ANDROID_TV;
        if (platform == null) platform = "ANDROID";
        lastSeen = LocalDateTime.now();
    }

    public enum DeviceType { ANDROID_TV, PHONE, TABLET }
    public enum DeviceStatus { ONLINE, OFFLINE }
}
