# Warehouse Reservation — Backend

Spring Boot 3 service. See the [top-level README](../README.md) for
architecture, design patterns, and trade-offs.

## Run locally

```bash
docker compose -f ../docker-compose.yml up -d   # Postgres on :5432
./mvnw spring-boot:run                          # API on :8080
```

Environment variables (all optional, sensible defaults provided):

| Var | Default | Meaning |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/warehouse` | JDBC URL |
| `DB_USER` | `warehouse` | DB user |
| `DB_PASSWORD` | `warehouse` | DB password |
| `SERVER_PORT` | `8080` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated origins |

## Run tests

```bash
./mvnw test
```

For a visual chart report (pass/fail/error distribution + suite breakdown), run:

```bash
chmod +x scripts/run-tests-with-report.sh
./scripts/run-tests-with-report.sh
```

This generates and opens:

`target/test-report/index.html`

25 tests across:

* `domain.inventory.InventoryTest` — aggregate invariants
* `domain.reservation.state.ReservationStateTest` — State pattern transitions
* `service.ReservationFactoryTest` — Factory validation (mocked deps)
* `service.ReservationServiceIntegrationTest` — full transactional stack
  including the **50-thread no-oversell concurrency proof**
* `api.ReservationControllerTest` — HTTP contract end-to-end

Tests use H2 in PostgreSQL-compatibility mode so the suite runs without
Docker.

## Quick smoke test

```bash
curl -s http://localhost:8080/inventory | jq

curl -s -X POST http://localhost:8080/reservations \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORD-1","items":[{"sku":"A100","quantity":3}]}' | jq

# Then take the returned id and:
curl -s -X POST http://localhost:8080/reservations/1/confirm | jq
```
