package com.iptvplayer.sync.repository;

import com.iptvplayer.sync.domain.device.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findAllByOrderByLastSeenDesc();

    @Modifying
    @Query("UPDATE Device d SET d.lastSeen = :now, d.status = 'ONLINE' WHERE d.id = :id")
    void updateLastSeen(UUID id, LocalDateTime now);
}
