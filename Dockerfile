# ── Stage 1: Build (ClojureScript + Clojure uberjar) ─────────────────────────
# shadow-cljs always requires a JVM, so we use the Clojure image and add Node.
FROM clojure:temurin-21-tools-deps-alpine AS builder

# Install Node.js + npm for shadow-cljs
RUN apk add --no-cache nodejs npm

WORKDIR /build

# Cache npm deps (layer invalidated only when package.json changes)
COPY package.json ./
RUN npm install --no-audit --no-fund

# Cache Maven/Clojure deps (layer invalidated only when deps.edn changes)
COPY deps.edn ./
RUN clojure -P && clojure -P -M:build

# Copy all source files and static resources
COPY shadow-cljs.edn ./
COPY src/ ./src/
COPY resources/ ./resources/
COPY build.clj ./

# Compile ClojureScript → resources/public/js/main.js
RUN npx shadow-cljs compile app

# Build the uberjar (bundles Clojure + compiled frontend JS)
RUN clojure -T:build uber


# ── Stage 2: Runtime image ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL description="codescene-lite — self-hosted code analysis web UI"
LABEL maintainer="https://github.com/adamtornhill/code-maat"

# Create a non-root user for security
RUN addgroup -S codescene && adduser -S codescene -G codescene

WORKDIR /app

# Copy only the uberjar from the builder stage
COPY --from=builder /build/target/codescene-lite-0.1.0-standalone.jar ./app.jar

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
