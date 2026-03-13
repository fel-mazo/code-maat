# ── Stage 1: Build ClojureScript frontend ────────────────────────────────────
FROM node:20-slim AS frontend-builder

WORKDIR /build

# Install npm deps first (layer cache — only invalidated when package.json changes)
COPY package.json ./
RUN npm install --no-audit --no-fund

# Copy ClojureScript source and shadow-cljs config
COPY shadow-cljs.edn ./
COPY src/ ./src/
COPY resources/ ./resources/

# Compile ClojureScript to resources/public/js/main.js
# Uses shadow-cljs in standalone mode (no JVM deps needed at this stage)
RUN npx shadow-cljs compile app


# ── Stage 2: Build Clojure uberjar ───────────────────────────────────────────
FROM clojure:temurin-21-tools-deps-alpine AS backend-builder

WORKDIR /build

# Cache Maven dependencies — only re-download when deps.edn changes
COPY deps.edn ./
RUN clojure -P && clojure -P -M:build

# Copy all source and compiled frontend assets
COPY --from=frontend-builder /build/resources/ ./resources/
COPY src/ ./src/
COPY build.clj ./

# Build the uberjar (compiles Clojure, bundles everything including frontend JS)
RUN clojure -T:build uber


# ── Stage 3: Runtime image ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL description="codescene-lite — self-hosted code analysis web UI"
LABEL maintainer="https://github.com/adamtornhill/code-maat"

# Create a non-root user for security
RUN addgroup -S codescene && adduser -S codescene -G codescene

WORKDIR /app

# Copy only the uberjar from the builder stage
COPY --from=backend-builder /build/target/codescene-lite-0.1.0-standalone.jar ./app.jar

# /data is where the EDN store writes repos.edn and results/
# Mount this as a volume to persist data across container restarts
VOLUME /data

# The server reads config.edn at startup; data-dir must be writable
USER codescene

EXPOSE 7777

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=85.0", \
  "-Djava.awt.headless=true", \
  "-Dcodescene.data-dir=/data", \
  "-jar", "app.jar"]
