#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -n "${MAVEN_HOME:-}" ]; then
  exec "$MAVEN_HOME/bin/mvn" "$@"
fi
exec mvn "$@"
