# Java Spring Boot: Monolith to Microservices

A course-based flash-sale application reconstructed in three stages. Each branch is a runnable snapshot of one stage.

| Branch | Development stage |
| --- | --- |
| [step1](https://github.com/harrywutech/java-springboot-monolith-microservices/tree/step1) | Synchronous monolith: users, products, promotions, and orders; MySQL, Flyway, jOOQ, local transactions, and conditional stock updates. |
| [step2](https://github.com/harrywutech/java-springboot-monolith-microservices/tree/step2) | Redis cache and Lua stock reservations; RocketMQ asynchronous order creation, delayed expiry, idempotent stock operations, and a transactional outbox. |
| [step3](https://github.com/harrywutech/java-springboot-monolith-microservices/tree/step3) | Business services with separate schemas; Consul discovery, OpenFeign calls, Gateway routing, multiple order instances, and two order shards. |

## Development process

1. Built a working monolith and verified order creation, stock reservation, rollback, and simulated payment.
2. Moved stock admission to Redis and order persistence to message consumers. Added retry-safe processing, expiry handling, and outgoing-event persistence.
3. Split the business domains into services and introduced discovery, routing, and order sharding. Verified instance failover and service recovery.
4. Prepared English source snapshots, removed restoration notes and verification artifacts, and made startup scripts use standard Java/Maven environment settings.

The implementation follows the course architecture with additional reliability repairs, including durable pending reservations and an outbox. Payment is simulated. This is a local learning project; no production throughput claim is made.

## Requirements

JDK 11, Maven 3.8+, Docker with Compose v2, and Python 3 for verification scripts. Set `JAVA_HOME` to JDK 11 and put Maven on `PATH`, or set `MAVEN_HOME`. Spring Boot 2.7.3; Java source/target level 8.

## Current branch: step1

All business domains share one Spring Boot process and one MySQL schema. Inventory reservation and order creation commit in one local transaction.

```sh
./scripts/start.sh
# In another terminal, after startup:
python3 scripts/smoke.py
# MySQL must be running for integration tests:
./scripts/mvn.sh -B test
```

Open `http://127.0.0.1:8070/login.html`. Demo credentials: `test` / `test`.
Stop the application with Ctrl+C, then run `docker compose stop` to stop MySQL while preserving data.
