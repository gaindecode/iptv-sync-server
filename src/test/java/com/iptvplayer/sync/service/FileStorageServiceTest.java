package com.iptvplayer.sync.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileStorageService")
class FileStorageServiceTest {

    private FileStorageService service;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        service = new FileStorageService();
        tempDir = Files.createTempDirectory("iptv-test-uploads");
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    @DisplayName("store() sauvegarde le fichier et retourne un ID avec extension préservée")
    void store_shouldSaveFileAndReturnId() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "playlist.m3u", "application/x-mpegurl",
            "#EXTM3U\n#EXTINF:-1,Test\nhttp://example.com/stream".getBytes()
        );

        String fileId = service.store(file);

        assertThat(fileId).endsWith(".m3u");
        assertThat(service.exists(fileId)).isTrue();
    }

    @Test
    @DisplayName("store() rejette un fichier vide")
    void store_shouldReject_whenEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.m3u", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.store(emptyFile))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vide");
    }

    @Test
    @DisplayName("store() rejette un fichier trop volumineux")
    void store_shouldReject_whenTooLarge() {
        byte[] tooLarge = new byte[21 * 1024 * 1024]; // 21 Mo > limite 20 Mo
        MockMultipartFile bigFile = new MockMultipartFile("file", "big.m3u", "text/plain", tooLarge);

        assertThatThrownBy(() -> service.store(bigFile))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("volumineux");
    }

    @Test
    @DisplayName("resolve() rejette un fileId contenant une traversée de répertoire")
    void resolve_shouldReject_pathTraversal() {
        assertThatThrownBy(() -> service.resolve("../../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.resolve("sub/dir/file.m3u"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("exists() retourne false pour un fichier inexistant")
    void exists_shouldReturnFalse_whenNotFound() {
        assertThat(service.exists("inexistant-12345.m3u")).isFalse();
    }

    @Test
    @DisplayName("exists() retourne false (pas d'exception) pour un fileId invalide")
    void exists_shouldReturnFalse_whenInvalidId() {
        assertThat(service.exists("../etc/passwd")).isFalse();
    }
}
