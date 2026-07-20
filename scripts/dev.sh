#!/usr/bin/env bash
# Local dev loop: starts Postgres (if not already up) and runs the app with
# Spring DevTools hot-reload (profile "dev") so code changes auto-restart it.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Starting Postgres (docker compose)..."
docker compose up -d db

echo "Waiting for Postgres to be healthy..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' database-postgress 2>/dev/null)" = "healthy" ]; do
  sleep 1
done

echo "Starting application (profile: dev, hot-reload enabled)..."
SPRING_PROFILES_ACTIVE=dev \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/system \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
SERVER_PORT=8081 \
mvn spring-boot:run
