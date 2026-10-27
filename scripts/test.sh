#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
docker compose up -d --wait mysql redis
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' <<'SQL'
CREATE DATABASE IF NOT EXISTS step3_promotion_test CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'step3_promotion_test'@'%' IDENTIFIED BY 'step3_local';
GRANT ALL ON step3_promotion_test.* TO 'step3_promotion_test'@'%';
SQL
./scripts/mvn.sh -B -f "$PWD/pom.xml" test
