package com.iptvplayer.sync.repository;

import com.iptvplayer.sync.domain.playlist.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {
    List<Playlist> findByDeviceId(UUID deviceId);
    void deleteByIdAndDeviceId(UUID id, UUID deviceId);
}
