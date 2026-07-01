# ── Stage 1 : Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

# ── Stage 2 : Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/iptv-sync-server-*.jar app.jar

# Utilisateur non-root pour la sécurité
RUN addgroup -S iptv && adduser -S iptv -G iptv

# Créer le répertoire uploads AVANT de passer en user iptv,
# puis lui donner les permissions d'écriture — le volume Docker
# uploads_data sera monté ici au runtime, mais sans ce mkdir le
# répertoire appartient à root et l'utilisateur iptv ne peut pas écrire.
RUN mkdir -p /app/uploads && chown -R iptv:iptv /app/uploads

USER iptv

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
