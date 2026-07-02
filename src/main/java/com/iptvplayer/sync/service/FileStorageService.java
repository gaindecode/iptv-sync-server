package com.iptvplayer.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * FileStorageService — stocke les fichiers M3U uploadés depuis le portail web.
 *
 * Permet à un utilisateur d'envoyer un fichier .m3u directement depuis son
 * téléphone/ordinateur plutôt que de fournir une URL — le serveur héberge
 * le fichier et expose une URL de téléchargement que la TV utilisera comme
 * `source` (même mécanisme qu'une playlist sourceType=url classique, voir
 * Playlist.source — aucune modification du schéma ou du type côté RN).
 *
 * Stockage sur disque dans un volume Docker monté (voir docker-compose.yml
 * uploads_data) — survit aux redéploiements du conteneur serveur.
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${app.storage.upload-dir:/app/uploads}")
    private String uploadDir;

    /** Taille max acceptée — alignée sur spring.servlet.multipart.max-file-size */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 Mo

    /**
     * Sauvegarde le fichier uploadé et retourne son identifiant unique
     * (utilisé ensuite dans l'URL de téléchargement /api/files/{fileId}).
     */
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Fichier trop volumineux (max 20 Mo)");
        }

        String originalName = file.getOriginalFilename();
        String extension = (originalName != null && originalName.contains("."))
            ? originalName.substring(originalName.lastIndexOf('.'))
            : ".m3u";

        String fileId = UUID.randomUUID().toString() + extension;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        Path target = dir.resolve(fileId);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        log.info("Fichier stocké : {} ({} octets)", fileId, file.getSize());
        return fileId;
    }

    /**
     * Résout le chemin disque d'un fichier stocké à partir de son ID.
     * Valide que l'ID ne contient pas de séquence de traversée de
     * répertoire (../ ) avant de construire le chemin — fileId vient
     * d'une requête HTTP publique (GET /api/files/{fileId}).
     */
    public Path resolve(String fileId) {
        if (fileId.contains("..") || fileId.contains("/") || fileId.contains("\\")) {
            throw new IllegalArgumentException("Identifiant de fichier invalide");
        }
        return Paths.get(uploadDir).resolve(fileId);
    }

    public boolean exists(String fileId) {
        try {
            return Files.exists(resolve(fileId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
