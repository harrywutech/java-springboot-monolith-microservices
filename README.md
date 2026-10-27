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

## Current branch: step3

| Module | Responsibility |
| --- | --- |
| `common` | Shared contracts, sharding rule, and messaging adapter |
| `user-service` | Accounts and login |
| `commodity-service` | Product catalog |
| `promotion-service` | Promotions, Redis reservations, and stock events |
| `order-service` | Orders, simulated payment, expiry, and outgoing events |
| `frontend-service` | Store pages and static assets |
| `gateway-service` | Same-origin entry point and service routing |

```sh
./scripts/start.sh
# In another terminal, after all services are healthy:
python3 scripts/smoke.py
python3 scripts/failover.py
./scripts/test.sh
# Stop managed application processes and infrastructure, preserving data:
./scripts/stop.sh
```

Open `http://127.0.0.1:8090/login.html`; Consul UI: `http://127.0.0.1:18500`.
Demo users: `test` / `test` routes to shard 1, and `test2` / `test` routes to shard 2.

Eight JVMs run six service types: user, commodity, promotion, frontend, gateway, and three order instances. Two order instances share shard 1; one serves shard 2. Business services have separate schema accounts on one local MySQL server. The `mark` header selects an order shard and is only a teaching routing mechanism. Real authentication, payment-provider integration, and production high availability remain future work. RocketMQ uses amd64 emulation on Apple Silicon.
