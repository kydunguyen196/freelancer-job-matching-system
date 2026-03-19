# SkillBridge Postman Datatest Guide

## 1. Full API list (through gateway `http://localhost:8080`)

### Auth service
- `POST /auth/register` (public)
- `POST /auth/login` (public)
- `POST /auth/refresh` (public)
- `GET /auth/health` (public)

### User service
- `GET /users/me` (auth required)
- `PUT /users/me` (auth required)
- `POST /users/me/cv` (role `FREELANCER`, multipart file upload)

### Job service
- `GET /jobs` (public)
- `GET /jobs/{jobId}` (public)
- `POST /jobs` (role `CLIENT`)
- `PATCH /jobs/{jobId}/close` (role `CLIENT`)

### Proposal service
- `POST /proposals` (role `FREELANCER`)
- `GET /jobs/{jobId}/proposals` (role `CLIENT`)
- `PATCH /proposals/{proposalId}/accept` (role `CLIENT`)

### Contract service
- `GET /contracts/me` (auth required)
- `POST /contracts/{contractId}/milestones` (role `CLIENT`)
- `PATCH /milestones/{milestoneId}/complete` (auth required, must be contract participant)
- `POST /contracts/internal/from-proposal` (internal endpoint, requires header `X-Internal-Api-Key`)

### Notification service
- `GET /notifications/me` (auth required)
- `PATCH /notifications/{notificationId}/read` (auth required)

## 2. Import API into Postman (3 ways)

### Way A: Import directly from OpenAPI URLs
Postman -> `Import` -> `Link`, import each URL:
- `http://localhost:8080/v3/api-docs/auth-service`
- `http://localhost:8080/v3/api-docs/user-service`
- `http://localhost:8080/v3/api-docs/job-service`
- `http://localhost:8080/v3/api-docs/proposal-service`
- `http://localhost:8080/v3/api-docs/contract-service`
- `http://localhost:8080/v3/api-docs/notification-service`

### Way B: Use Swagger UI then export/copy request examples
- Open `http://localhost:8080/swagger-ui.html`
- Select each service in the top dropdown
- Copy request payloads / execute examples into a Postman collection

### Way B2: Import ready-to-run files in this repo
Postman -> `Import` -> `File`:
- Collection: `postman/SkillBridge-Datatest.postman_collection.json`
- Environment: `postman/SkillBridge-Local.postman_environment.json`

### Way C: Data-driven run with Collection Runner
- Build one Postman collection for the E2E flow
- Use `Runner` -> select collection -> attach data file:
  - JSON: `postman/data/datatest.json`
  - CSV: `postman/data/datatest.csv`

## 3. Environment variables for Postman

Create environment `SkillBridge Local` with:
- `baseUrl = http://localhost:8080`
- `clientEmail = client.demo@skillbridge.local`
- `freelancerEmail = freelancer.demo@skillbridge.local`
- `password = Demo12345!`
- `clientAccessToken` (empty)
- `freelancerAccessToken` (empty)
- `clientRefreshToken` (empty)
- `freelancerRefreshToken` (empty)
- `clientUserId` (empty)
- `freelancerUserId` (empty)
- `jobId` (empty)
- `proposalId` (empty)
- `contractId` (empty)
- `milestoneId` (empty)
- `notificationId` (empty)
- `internalApiKey = change-this-contract-internal-api-key`
- `futureDate` (empty)

## 4. Recommended end-to-end datatest order

1. `POST /auth/register` (CLIENT)
2. `POST /auth/register` (FREELANCER)
3. `POST /auth/login` (CLIENT) -> save tokens + userId
4. `POST /auth/login` (FREELANCER) -> save tokens + userId
5. `PUT /users/me` (CLIENT token)
6. `PUT /users/me` (FREELANCER token)
7. `POST /jobs` (CLIENT token) -> save `jobId`
8. `POST /proposals` (FREELANCER token, use `jobId`) -> save `proposalId`
9. `PATCH /proposals/{proposalId}/accept` (CLIENT token)
10. `GET /contracts/me` (FREELANCER token) -> save `contractId`, `milestoneId`
11. `PATCH /milestones/{milestoneId}/complete` (FREELANCER token)
12. `GET /notifications/me` (FREELANCER token) -> save `notificationId`
13. `PATCH /notifications/{notificationId}/read` (FREELANCER token)
14. `PATCH /jobs/{jobId}/close` (CLIENT token, optional)

## 5. Useful Postman test scripts

### 5.1 Login CLIENT (Tests tab)
```javascript
pm.test("200 OK", function () {
  pm.response.to.have.status(200);
});
const data = pm.response.json();
pm.environment.set("clientAccessToken", data.accessToken);
pm.environment.set("clientRefreshToken", data.refreshToken);
pm.environment.set("clientUserId", data.userId);
```

### 5.2 Login FREELANCER (Tests tab)
```javascript
pm.test("200 OK", function () {
  pm.response.to.have.status(200);
});
const data = pm.response.json();
pm.environment.set("freelancerAccessToken", data.accessToken);
pm.environment.set("freelancerRefreshToken", data.refreshToken);
pm.environment.set("freelancerUserId", data.userId);
```

### 5.3 Create Job (Tests tab)
```javascript
pm.test("201 Created", function () {
  pm.response.to.have.status(201);
});
const data = pm.response.json();
pm.environment.set("jobId", data.id);
```

### 5.4 Create Proposal (Tests tab)
```javascript
pm.test("201 Created", function () {
  pm.response.to.have.status(201);
});
const data = pm.response.json();
pm.environment.set("proposalId", data.id);
```

### 5.5 Get Contracts Me (Tests tab)
```javascript
pm.test("200 OK", function () {
  pm.response.to.have.status(200);
});
const arr = pm.response.json();
if (Array.isArray(arr) && arr.length > 0) {
  pm.environment.set("contractId", arr[0].id);
  if (arr[0].milestones && arr[0].milestones.length > 0) {
    pm.environment.set("milestoneId", arr[0].milestones[0].id);
  }
}
```

### 5.6 Get Notifications Me (Tests tab)
```javascript
pm.test("200 OK", function () {
  pm.response.to.have.status(200);
});
const arr = pm.response.json();
if (Array.isArray(arr) && arr.length > 0) {
  pm.environment.set("notificationId", arr[0].id);
}
```

### 5.7 Pre-request script for future date
```javascript
const d = new Date();
d.setDate(d.getDate() + 7);
pm.environment.set("futureDate", d.toISOString().slice(0, 10));
```

## 6. Minimal request body samples

### Register
```json
{
  "email": "{{clientEmail}}",
  "password": "{{password}}",
  "role": "CLIENT"
}
```

### Login
```json
{
  "email": "{{clientEmail}}",
  "password": "{{password}}"
}
```

### Update profile (freelancer)
```json
{
  "skills": ["java", "spring", "postgresql"],
  "hourlyRate": 45,
  "overview": "Demo freelancer account",
  "companyName": null
}
```

### Create job
```json
{
  "title": "Demo Backend Job {{$timestamp}}",
  "description": "Implement REST endpoint hardening and event notifications.",
  "budgetMin": 300,
  "budgetMax": 600,
  "tags": ["java", "spring-boot", "rabbitmq"]
}
```

### Create proposal
```json
{
  "jobId": {{jobId}},
  "coverLetter": "I can deliver this in 7 days with tests and docs.",
  "price": 450,
  "durationDays": 7
}
```

### Add milestone
```json
{
  "title": "Phase 2 milestone",
  "amount": 250,
  "dueDate": "{{futureDate}}"
}
```

### Internal create contract (optional/manual)
Header:
- `X-Internal-Api-Key: {{internalApiKey}}`

Body:
```json
{
  "proposalId": {{proposalId}},
  "jobId": {{jobId}},
  "clientId": {{clientUserId}},
  "freelancerId": {{freelancerUserId}},
  "milestoneAmount": 450,
  "durationDays": 7
}
```
