#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
docker compose -f compose.step2.yaml up -d --wait
./scripts/mvn.sh -B -DskipTests package
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar target/ecomm-monolith-1.0-SNAPSHOT.jar --spring.profiles.active=step2
