#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 scripts/services.py assert-stopped
docker compose up -d --wait
./scripts/mvn.sh -B -f "$PWD/pom.xml" -DskipTests package
python3 scripts/services.py start
