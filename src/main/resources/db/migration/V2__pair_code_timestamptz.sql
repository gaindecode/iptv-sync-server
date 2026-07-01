-- V2__pair_code_timestamptz.sql
-- Convertit expires_at en TIMESTAMPTZ pour que les timestamps soient
-- stockés avec timezone — corrige le décalage horaire entre le serveur
-- Spring Boot (UTC) et les clients JS qui parsent les dates ISO.
-- Sans cette migration, LocalDateTime (TIMESTAMP sans TZ) était retourné
-- sans suffixe Z, et new Date() côté client l'interprétait en heure locale.

ALTER TABLE pair_codes
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ
    USING expires_at AT TIME ZONE 'UTC';
