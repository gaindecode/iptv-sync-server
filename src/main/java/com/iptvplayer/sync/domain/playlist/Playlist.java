package com.iptvplayer.sync.domain.playlist;

import com.iptvplayer.sync.domain.device.Device;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "playlists")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Playlist {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false)
    private String name;

    /**
     * Aligné sur PlaylistSourceType de l'app RN : url | xtream
     * (file non supporté côté serveur — les fichiers locaux ne transitent pas)
     */
    @Column(name = "source_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PlaylistSourceType sourceType;

    /** URL M3U — null si sourceType = XTREAM */
    @Column(columnDefinition = "TEXT")
    private String source;

    @Column(name = "epg_url", columnDefinition = "TEXT")
    private String epgUrl;

    /** Serveur Xtream — null si sourceType = URL */
    @Column(name = "xtream_server", columnDefinition = "TEXT")
    private String xtreamServer;

    @Column(name = "xtream_username", columnDefinition = "TEXT")
    private String xtreamUsername;

    @Column(name = "xtream_password", columnDefinition = "TEXT")
    private String xtreamPassword;

    @Column(name = "channel_count")
    private int channelCount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlaylistStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = PlaylistStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PlaylistSourceType { URL, XTREAM }
    public enum PlaylistStatus { PENDING, SYNCED, ERROR }
}
