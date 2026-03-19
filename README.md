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

### CV File Storage Local Test

Phase 1 keeps `POST /proposals` unchanged. Create the proposal first, then upload the CV through the dedicated endpoint.

Example env for local mock storage:

```powershell
$env:ENABLE_FILE_STORAGE="true"
$env:STORAGE_PROVIDER="mock"
$env:MAX_CV_FILE_SIZE_MB="10"
$env:ALLOWED_CV_CONTENT_TYPES="application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
```

Optional env for real providers:

```powershell
$env:STORAGE_PROVIDER="s3"
$env:S3_BUCKET="your-bucket"
$env:AWS_REGION="ap-southeast-1"
$env:AWS_ACCESS_KEY_ID="your-access-key"
$env:AWS_SECRET_ACCESS_KEY="your-secret-key"
```

```powershell
$env:STORAGE_PROVIDER="minio"
$env:MINIO_ENDPOINT="http://localhost:9000"
$env:MINIO_ACCESS_KEY="minioadmin"
$env:MINIO_SECRET_KEY="minioadmin"
$env:MINIO_BUCKET="skillbridge-cv"
$env:MINIO_REGION="us-east-1"
$env:MINIO_SECURE="false"
```

Recommended verification flow:

1. Create a proposal with `POST /proposals`.
2. Upload the CV with `POST /proposals/{proposalId}/cv` using multipart field name `file`.
3. Verify `GET /proposals/{proposalId}/cv` returns metadata and a secure download endpoint.
4. Verify `GET /proposals/{proposalId}/cv/download` works for the proposal owner and the recruiter owning that job.
5. Re-upload another CV for the same proposal and confirm metadata is replaced.
6. Try an invalid file type or oversized file and confirm the API rejects it with `400`.

### Advanced Job Search Local Test

Phase 1 keeps the public `GET /jobs` API unchanged and routes search internally through DB or OpenSearch depending on env.

DB fallback mode:

```powershell
$env:ENABLE_ADVANCED_SEARCH="false"
$env:SEARCH_PROVIDER="db"
```

OpenSearch mode:

```powershell
$env:ENABLE_ADVANCED_SEARCH="true"
$env:SEARCH_PROVIDER="opensearch"
$env:OPENSEARCH_URL="http://localhost:9200"
$env:OPENSEARCH_USERNAME="admin"
$env:OPENSEARCH_PASSWORD="admin"
$env:OPENSEARCH_INDEX_JOBS="jobs"
$env:OPENSEARCH_INDEX_COMPANIES="companies"
$env:OPENSEARCH_CONNECT_TIMEOUT_MS="3000"
$env:OPENSEARCH_SOCKET_TIMEOUT_MS="5000"
```

Recommended verification flow:

1. Start `job-service` with DB fallback first and verify `GET /jobs` still works with existing query params.
2. Switch to OpenSearch mode and create or publish a few jobs through the existing job APIs.
3. Trigger `POST /jobs/internal/search/reindex` with `X-Internal-Api-Key` to backfill old data if needed.
4. Call `GET /jobs?keyword=backend&location=ho%20chi%20minh&sortBy=salary_high` and confirm the response contract is unchanged.
5. Stop OpenSearch or set an invalid `OPENSEARCH_URL`, then call `GET /jobs` again and confirm the service still returns DB-backed results.

### Google Calendar Local Test

Set the proposal-service calendar env vars before `bootRun`:

```powershell
$env:ENABLE_CALENDAR="true"
$env:GOOGLE_CLIENT_ID="your-client-id"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"
$env:GOOGLE_REDIRECT_URI="http://localhost:8080/oauth2/callback/google"
$env:GOOGLE_CALENDAR_ID="your-calendar-id"
$env:GOOGLE_SCOPES="https://www.googleapis.com/auth/calendar"
$env:GOOGLE_REFRESH_TOKEN="your-refresh-token"
```

Recommended local verification flow:

1. Start `proposal-service`, `job-service`, `notification-service`, and the gateway.
2. Use a recruiter account to call `POST /proposals/{proposalId}/interview` with future `interviewScheduledAt` and `interviewEndsAt`.
3. Confirm the response contains `googleEventId` when Google Calendar succeeds.
4. Confirm the proposal row stores `googleEventId` and `interviewEndsAt`.
5. Confirm the event appears in the configured Google Calendar with candidate and recruiter attendees.

To test failure handling without breaking the business flow:

1. Keep `ENABLE_CALENDAR=true`.
2. Temporarily set an invalid `GOOGLE_REFRESH_TOKEN` or `GOOGLE_CALENDAR_ID`.
3. Call the same interview scheduling API again.
4. Confirm the proposal is still moved to `INTERVIEW_SCHEDULED`, `googleEventId` stays empty, and `calendarWarning` is returned.

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
