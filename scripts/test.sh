#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Separate schemas and Redis DB 15 keep tests away from the running demo and its relay.
for compose_file in compose.yaml compose.step2.yaml; do
  docker compose -f "$compose_file" up -d --wait mysql
  docker compose -f "$compose_file" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE IF NOT EXISTS skillup_test CHARACTER SET utf8mb4; GRANT ALL ON skillup_test.* TO '\''skillup'\''@'\''%'\'';"'
done
docker compose -f compose.step2.yaml up -d --wait redis
exec ./scripts/mvn.sh -B test "$@"
