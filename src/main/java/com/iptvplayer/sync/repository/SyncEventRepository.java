package com.iptvplayer.sync.repository;

import com.iptvplayer.sync.domain.device.SyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SyncEventRepository extends JpaRepository<SyncEvent, UUID> {

    /** Événements non consommés pour un device — polling fallback */
    List<SyncEvent> findByDeviceIdAndConsumedFalseOrderByCreatedAtAsc(UUID deviceId);

    @Modifying
    @Query("UPDATE SyncEvent e SET e.consumed = true WHERE e.id IN :ids")
    void markConsumed(List<UUID> ids);

    /** Nettoyage des anciens événements consommés (> 24h) */
    @Modifying
    @Query("DELETE FROM SyncEvent e WHERE e.consumed = true AND e.createdAt < :cutoff")
    void deleteOldConsumed(java.time.LocalDateTime cutoff);
}
