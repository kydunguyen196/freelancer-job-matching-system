## SkillBridge Backend

Microservices backend for a freelance marketplace.

### Tech Stack

- Java 17
- Spring Boot 3.4.x
- Spring Security + JWT
- Spring Cloud Gateway
- PostgreSQL (database-per-service)
- RabbitMQ (domain events)
- SpringDoc OpenAPI (Swagger)
- Docker Compose

### Services

- `gateway-service` (port `8080`)
- `auth-service` (port `8081`)
- `user-service` (port `8082`)
- `job-service` (port `8083`)
- `proposal-service` (port `8084`)
- `contract-service` (port `8085`)
- `notification-service` (port `8086`)

### Domain Flow

1. Client creates a job.
2. Freelancer applies with a proposal.
3. Client accepts proposal.
4. Proposal service calls contract service internal endpoint.
5. Contract service creates contract + default milestone.
6. Events are published to RabbitMQ:
   - `proposal.created`
   - `proposal.accepted`
   - `milestone.completed`
7. Notification service consumes events and stores notifications.

### Day 13 Hardening Implemented

- Validation + global exception handling for body/query/path constraints.
- Pagination for:
  - `GET /jobs`
  - `GET /jobs/{jobId}/proposals`
- Correlation ID support (`X-Correlation-Id`) with propagation through gateway.
- OpenAPI/Swagger enabled for each service.
- Multi-stage Dockerfile for every service.
- Full backend `docker-compose.yml` runs all services.

### API Pagination

Both paginated endpoints return list body + paging headers:

- `X-Page`
- `X-Size`
- `X-Total-Elements`
- `X-Total-Pages`

Default params:

- `page=0`
- `size=20` (max `100`)

### Correlation ID

- Incoming header: `X-Correlation-Id`
- If missing, generated automatically.
- Returned in response header and included in log pattern.

### Run Locally (Gradle)

1. Start infra:

```powershell
docker compose up -d postgres rabbitmq
```

2. Start all services (separate shells or script):

```powershell
.\gradlew :services:auth-service:bootRun
.\gradlew :services:user-service:bootRun
.\gradlew :services:job-service:bootRun
.\gradlew :services:proposal-service:bootRun
.\gradlew :services:contract-service:bootRun
.\gradlew :services:notification-service:bootRun
.\gradlew :services:gateway-service:bootRun
```

3. Run tests:

```powershell
.\gradlew test --rerun-tasks
```

### Run Full Backend With Docker

```powershell
docker compose up --build
```

Gateway entrypoint: `http://localhost:8080`

### Swagger URLs

- Gateway: `http://localhost:8080/swagger-ui.html`
- Auth: `http://localhost:8081/swagger-ui.html`
- User: `http://localhost:8082/swagger-ui.html`
- Job: `http://localhost:8083/swagger-ui.html`
- Proposal: `http://localhost:8084/swagger-ui.html`
- Contract: `http://localhost:8085/swagger-ui.html`
- Notification: `http://localhost:8086/swagger-ui.html`

### Day 14 Demo Seed

Seed script creates demo users + profile + job + proposal + accepted contract:

```powershell
pwsh ./scripts/seed-demo.ps1
```

Default users after seed:

- `client.demo@skillbridge.local / Demo12345!`
- `freelancer.demo@skillbridge.local / Demo12345!`
