package com.iptvplayer.sync.service;

import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.device.SyncEvent;
import com.iptvplayer.sync.domain.playlist.Playlist;
import com.iptvplayer.sync.dto.PlaylistDto;
import com.iptvplayer.sync.repository.DeviceRepository;
import com.iptvplayer.sync.repository.PlaylistRepository;
import com.iptvplayer.sync.repository.SyncEventRepository;
import com.iptvplayer.sync.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaylistService")
class PlaylistServiceTest {

    @Mock private PlaylistRepository playlistRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private SyncEventRepository syncEventRepository;
    @Mock private WebSocketSessionManager wsManager;
    @InjectMocks private PlaylistService playlistService;

    private Device buildDevice() {
        return Device.builder()
            .id(UUID.randomUUID())
            .deviceName("TV Test")
            .deviceType(Device.DeviceType.ANDROID_TV)
            .platform("ANDROID")
            .status(Device.DeviceStatus.ONLINE)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("addPlaylist() crée la playlist et émet un SyncEvent + WS")
    void addPlaylist_shouldCreateAndEmitEvent() {
        Device device = buildDevice();
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        Playlist savedPlaylist = Playlist.builder()
            .id(UUID.randomUUID())
            .device(device)
            .name("Maison")
            .sourceType(Playlist.PlaylistSourceType.URL)
            .source("https://example.com/playlist.m3u")
            .status(Playlist.PlaylistStatus.PENDING)
            .channelCount(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(playlistRepository.save(any())).thenReturn(savedPlaylist);

        PlaylistDto.AddRequest request = new PlaylistDto.AddRequest(
            "Maison", "url", "https://example.com/playlist.m3u",
            null, null, null, null
        );

        PlaylistDto.PlaylistResponse response = playlistService.addPlaylist(device.getId(), request);

        assertThat(response.name()).isEqualTo("Maison");
        assertThat(response.sourceType()).isEqualTo("url");

        // Vérifie que le SyncEvent a été persisté
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(SyncEvent.EventType.PLAYLIST_ADDED);

        // Vérifie que le WS a été notifié
        verify(wsManager).sendToDevice(eq(device.getId().toString()), any());
    }

    @Test
    @DisplayName("addPlaylist() lève une exception si le device n'existe pas")
    void addPlaylist_shouldThrow_whenDeviceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(deviceRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playlistService.addPlaylist(unknownId,
            new PlaylistDto.AddRequest("Test", "url", "https://x.com", null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Device introuvable");
    }

    @Test
    @DisplayName("removePlaylist() supprime et notifie le device")
    void removePlaylist_shouldDeleteAndNotify() {
        Device device = buildDevice();
        UUID playlistId = UUID.randomUUID();
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        playlistService.removePlaylist(device.getId(), playlistId);

        verify(playlistRepository).deleteByIdAndDeviceId(playlistId, device.getId());
        verify(syncEventRepository).save(argThat(e ->
            e.getEventType() == SyncEvent.EventType.PLAYLIST_REMOVED
        ));
        verify(wsManager).sendToDevice(eq(device.getId().toString()), any());
    }

    @Test
    @DisplayName("pollEvents() retourne les événements non consommés et les marque")
    void pollEvents_shouldReturnAndMarkConsumed() {
        Device device = buildDevice();
        SyncEvent event = SyncEvent.builder()
            .id(UUID.randomUUID())
            .device(device)
            .eventType(SyncEvent.EventType.PLAYLIST_ADDED)
            .payload(java.util.Map.of("id", "test"))
            .consumed(false)
            .createdAt(LocalDateTime.now())
            .build();

        when(syncEventRepository.findByDeviceIdAndConsumedFalseOrderByCreatedAtAsc(device.getId()))
            .thenReturn(List.of(event));

        List<SyncEvent> result = playlistService.pollEvents(device.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo(SyncEvent.EventType.PLAYLIST_ADDED);
        verify(syncEventRepository).markConsumed(List.of(event.getId()));
    }

    @Test
    @DisplayName("pollEvents() retourne liste vide s'il n'y a pas d'événements")
    void pollEvents_shouldReturnEmpty_whenNoEvents() {
        Device device = buildDevice();
        when(syncEventRepository.findByDeviceIdAndConsumedFalseOrderByCreatedAtAsc(device.getId()))
            .thenReturn(List.of());

        List<SyncEvent> result = playlistService.pollEvents(device.getId());

        assertThat(result).isEmpty();
        verify(syncEventRepository, never()).markConsumed(any());
    }

    @Test
    @DisplayName("getPlaylists() retourne toutes les playlists du device")
    void getPlaylists_shouldReturnDevicePlaylists() {
        Device device = buildDevice();
        Playlist p1 = Playlist.builder()
            .id(UUID.randomUUID()).device(device).name("Maison")
            .sourceType(Playlist.PlaylistSourceType.URL).source("https://a.com")
            .status(Playlist.PlaylistStatus.SYNCED).channelCount(1000)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        Playlist p2 = Playlist.builder()
            .id(UUID.randomUUID()).device(device).name("Bureau")
            .sourceType(Playlist.PlaylistSourceType.XTREAM).xtreamServer("http://xtream.com")
            .status(Playlist.PlaylistStatus.PENDING).channelCount(0)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(playlistRepository.findByDeviceId(device.getId())).thenReturn(List.of(p1, p2));

        List<PlaylistDto.PlaylistResponse> result = playlistService.getPlaylists(device.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlaylistDto.PlaylistResponse::name)
            .containsExactlyInAnyOrder("Maison", "Bureau");
    }
}
