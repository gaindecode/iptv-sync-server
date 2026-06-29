package com.iptvplayer.sync.repository;

import com.iptvplayer.sync.domain.pairing.PairCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PairCodeRepository extends JpaRepository<PairCode, UUID> {

    Optional<PairCode> findByCodeAndUsedFalse(String code);

    @Query("SELECT p FROM PairCode p WHERE p.device.id = :deviceId AND p.used = false AND p.expiresAt > :now")
    Optional<PairCode> findActiveByDeviceId(UUID deviceId, LocalDateTime now);

    /** Nettoyage planifié des codes expirés */
    @Modifying
    @Query("DELETE FROM PairCode p WHERE p.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}
