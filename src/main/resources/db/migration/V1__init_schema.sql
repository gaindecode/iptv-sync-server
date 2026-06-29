-- V1__init_schema.sql
-- Schéma initial IPTV Sync Server

-- ── Devices (téléviseurs) ─────────────────────────────────────────────────────
CREATE TABLE devices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_name VARCHAR(255) NOT NULL,
    device_type VARCHAR(50)  NOT NULL DEFAULT 'ANDROID_TV',  -- ANDROID_TV | PHONE | TABLET
    platform    VARCHAR(50)  NOT NULL DEFAULT 'ANDROID',
    last_seen   TIMESTAMP,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ONLINE',       -- ONLINE | OFFLINE
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_devices_status ON devices(status);

-- ── Pair Codes (association temporaire) ───────────────────────────────────────
-- Le code 6 caractères affiché sur la TV.
-- Jamais utilisé comme identifiant principal — expire après 10 min.
CREATE TABLE pair_codes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID         NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    code       VARCHAR(6)   NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pair_codes_code       ON pair_codes(code);
CREATE INDEX idx_pair_codes_device_id  ON pair_codes(device_id);
CREATE INDEX idx_pair_codes_expires_at ON pair_codes(expires_at);

-- ── Playlists ─────────────────────────────────────────────────────────────────
-- Aligné sur le type Playlist de l'app RN (sourceType: url | file | xtream)
CREATE TABLE playlists (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        UUID         NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    source_type      VARCHAR(20)  NOT NULL,  -- url | xtream
    source           TEXT,                   -- URL M3U (null si xtream)
    epg_url          TEXT,
    xtream_server    TEXT,                   -- serveur Xtream (null si url)
    xtream_username  TEXT,                   -- chiffré en AES
    xtream_password  TEXT,                   -- chiffré en AES
    channel_count    INTEGER      NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SYNCED | ERROR
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_playlists_device_id  ON playlists(device_id);
CREATE INDEX idx_playlists_status     ON playlists(status);

-- ── Sync Events (file d'événements) ──────────────────────────────────────────
-- Chaque modification génère un événement consommé par le device.
-- Permet la reprise après déconnexion sans perte de données.
CREATE TABLE sync_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID         NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_type VARCHAR(50)  NOT NULL,  -- PLAYLIST_ADDED | PLAYLIST_UPDATED | PLAYLIST_REMOVED
    payload    JSONB        NOT NULL,
    consumed   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sync_events_device_id ON sync_events(device_id);
CREATE INDEX idx_sync_events_consumed  ON sync_events(consumed);
CREATE INDEX idx_sync_events_created   ON sync_events(created_at);
