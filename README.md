# IPTV Sync Server

Backend Spring Boot pour la synchronisation des playlists IPTV entre le portail web et l'application Android TV.

## Architecture

```
Portail Web (index.html)
        ↓  POST /api/device/code/{code}/playlist
Spring Boot Server
        ↓  WebSocket push ou polling fallback
Android TV (React Native)
```

## Prérequis

- Docker + Docker Compose
- Java 21 (pour le développement local)

## Démarrage rapide

```bash
# 1. Cloner + configurer
cp .env.example .env
# Éditer .env avec vos valeurs

# 2. Lancer avec Docker Compose
docker compose up -d

# 3. Vérifier que le serveur est actif
curl http://localhost:8080/actuator/health
```

## API REST

### Enregistrer un device (TV)
```
POST /api/device/register
{ "deviceName": "TV Samsung", "deviceType": "ANDROID_TV" }

→ { "deviceId": "uuid", "pairCode": "AB82JK", "pairCodeExpiresAt": "..." }
```

### Vérifier un code (portail web)
```
GET /api/device/resolve/{code}
→ { "deviceId": "uuid", "deviceName": "TV Samsung", "status": "ONLINE" }
```

### Ajouter une playlist (portail web)
```
POST /api/device/code/{pairCode}/playlist
{ "name": "Maison", "sourceType": "url", "source": "https://...", "epgUrl": "..." }
```

### Polling fallback (TV sans WS)
```
GET /api/device/{deviceId}/sync
→ [{ "type": "PLAYLIST_ADDED", "payload": {...} }]
```

### Regénérer un code
```
POST /api/device/{deviceId}/pair-code/refresh
```

## WebSocket

L'app TV se connecte à `ws://SERVEUR:8080/ws/device` et envoie :
```json
{ "type": "IDENTIFY", "deviceId": "uuid" }
```

Le serveur répond :
```json
{ "type": "CONNECTED", "deviceId": "uuid" }
```

Puis pousse les événements :
```json
{ "type": "PLAYLIST_ADDED", "payload": { "id": "...", "name": "...", "sourceType": "url", "source": "..." } }
```

## Lancer les tests

```bash
mvn test
```

## Déploiement VPS

```bash
# Sur le VPS
git clone <repo> iptv-sync
cd iptv-sync
cp .env.example .env
# Éditer .env

docker compose up -d --build

# Servir le portail web (nginx ou simplement avec le port 80)
# Le fichier portal/index.html peut être servi statiquement
```

## Structure du projet

```
src/main/java/com/iptvplayer/sync/
├── domain/
│   ├── device/     Device, SyncEvent
│   ├── pairing/    PairCode
│   └── playlist/   Playlist
├── repository/     Interfaces JPA
├── service/        DeviceService, PlaylistService
├── controller/     DeviceController, PlaylistController
├── websocket/      WebSocketHandler, SessionManager
├── config/         WebSocketConfig (CORS + WS)
├── dto/            Request/Response DTOs
└── exception/      GlobalExceptionHandler
```
