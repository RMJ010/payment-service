# 💳 Payment Processing Microservice

A production-grade payment transaction processing system built with **Java 17 + Spring Boot 3**, featuring:

- ✅ ACID-compliant double-entry accounting
- ✅ Idempotent payment processing (no double charges)
- ✅ ML + rule-based fraud detection
- ✅ Kafka event streaming
- ✅ Redis caching for idempotency
- ✅ PostgreSQL with audit ledger
- ✅ Docker Compose for local dev
- ✅ Free cloud deployment on Render.com

---

## Architecture

```
Client → PaymentController
           → IdempotencyService (Redis)
           → AccountService (PostgreSQL)
           → FraudDetectionService (ML + rules)
           → PaymentService (double-entry, SERIALIZABLE tx)
           → KafkaEventProducer
```

---

## Quick Start (Local — 5 Minutes)

### Prerequisites
- Docker Desktop installed and running
- Java 17+ (for running without Docker)
- Git

### Option A: Docker Compose (Recommended)

```bash
# 1. Clone the repo
git clone <your-repo-url>
cd payment-processing-system

# 2. Start all services (Postgres, Redis, Kafka, ML service, Spring Boot API)
docker compose up --build

# 3. Wait ~2 minutes for all services to be healthy, then test:
curl http://localhost:8080/actuator/health
```

That's it! The API is live at `http://localhost:8080`.

### Option B: Run Spring Boot locally (faster iteration)

```bash
# 1. Start only infrastructure
docker compose up postgres redis kafka -d

# 2. Run the Spring Boot app
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# API available at http://localhost:8080
```

---

## API Usage

### Create Accounts

```bash
# Create Alice's account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"holderName":"Alice Johnson","initialBalance":10000,"currency":"USD"}'

# Create Bob's account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"holderName":"Bob Smith","initialBalance":5000,"currency":"USD"}'
```

> 💡 Alternatively, use the pre-seeded accounts: `acc-alice-001`, `acc-bob-002`, `acc-carol-003`

### Process a Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "acc-alice-001",
    "destinationAccountId": "acc-bob-002",
    "amount": 250.00,
    "currency": "USD",
    "idempotencyKey": "payment-001",
    "description": "Rent payment"
  }'
```

**Response:**
```json
{
  "transactionId": "3fa85f64-...",
  "status": "COMPLETED",
  "amount": 250.00,
  "currency": "USD",
  "success": true,
  "message": "Payment processed successfully",
  "createdAt": "2024-01-15T10:30:00",
  "completedAt": "2024-01-15T10:30:00.123"
}
```

### Get Transaction

```bash
curl http://localhost:8080/api/v1/payments/{transactionId}
```

### Get Account Transactions

```bash
curl http://localhost:8080/api/v1/payments/account/acc-alice-001
```

### Get Account Ledger (Audit Trail)

```bash
curl http://localhost:8080/api/v1/accounts/acc-alice-001/ledger
```

### Test Fraud Detection

```bash
# This will trigger HIGH_VELOCITY rule (>5 txns/hour)
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/v1/payments \
    -H "Content-Type: application/json" \
    -d "{
      \"sourceAccountId\": \"acc-alice-001\",
      \"destinationAccountId\": \"acc-bob-002\",
      \"amount\": 100,
      \"currency\": \"USD\",
      \"idempotencyKey\": \"fraud-test-$i\"
    }"
done
```

---

## FREE Cloud Deployment

### Option 1: Render.com (Easiest — 100% Free)

Render has a free tier with PostgreSQL, Redis, and web services.

#### Step 1: Sign up
Go to [render.com](https://render.com) → Sign up with GitHub (free).

#### Step 2: Create PostgreSQL Database (Free)
1. Dashboard → **New** → **PostgreSQL**
2. Name: `payments-db`
3. Plan: **Free**
4. Click **Create Database**
5. Copy the **Internal Database URL** (starts with `postgresql://`)

#### Step 3: Create Redis (via Upstash — Free)
1. Go to [upstash.com](https://upstash.com) → Sign up free
2. **Create Database** → **Redis**
3. Name: `payments-redis`, Region: `us-east-1`
4. Copy the **Redis URL** → extract host and password

#### Step 4: Deploy the Spring Boot Service
1. Render Dashboard → **New** → **Web Service**
2. Connect your GitHub repo
3. Settings:
   - **Name:** `payment-service`
   - **Environment:** `Docker`
   - **Dockerfile Path:** `./Dockerfile`
   - **Plan:** Free
4. Add Environment Variables:
   ```
   SPRING_PROFILES_ACTIVE=production
   SPRING_DATASOURCE_URL=<your Render postgres Internal URL>
   SPRING_DATASOURCE_USERNAME=<from Render>
   SPRING_DATASOURCE_PASSWORD=<from Render>
   REDIS_HOST=<Upstash Redis host>
   REDIS_PORT=6379
   REDIS_PASSWORD=<Upstash Redis password>
   ML_SERVICE_ENABLED=false
   SECURITY_ENABLED=false
   KAFKA_BOOTSTRAP_SERVERS=localhost:9092
   ```
5. Click **Create Web Service**

#### Step 5: Deploy ML Service (Optional)
1. Render → **New** → **Web Service**
2. Connect same repo, **Root Directory:** `ml-fraud-service`
3. **Dockerfile Path:** `./Dockerfile`
4. Plan: Free
5. Name: `ml-fraud-service`
6. After deploy, update `payment-service` env vars:
   ```
   ML_SERVICE_URL=https://ml-fraud-service.onrender.com/predict
   ML_SERVICE_ENABLED=true
   ```

#### ⚠️ Free Tier Notes
- Services **spin down after 15 min** of inactivity (cold start ~30s)
- PostgreSQL free tier: 1GB storage, 90-day expiry
- Redis (Upstash): 10,000 commands/day free
- Kafka: Not available on free tier — app works without it (events are logged)

---

### Option 2: Railway.app (Also Free)

#### Step 1: Sign up
Go to [railway.app](https://railway.app) → Sign up with GitHub.

#### Step 2: Deploy
```bash
# Install Railway CLI
npm install -g @railway/cli

# Login
railway login

# Initialize project
railway init

# Add PostgreSQL
railway add --plugin postgresql

# Add Redis  
railway add --plugin redis

# Deploy
railway up
```

#### Step 3: Set Environment Variables
```bash
railway variables set SPRING_PROFILES_ACTIVE=production
railway variables set ML_SERVICE_ENABLED=false
railway variables set SECURITY_ENABLED=false
# Railway auto-injects DATABASE_URL, REDIS_URL
```

> Railway gives $5/month free credit — enough for this service.

---

### Option 3: Fly.io (Free tier)

```bash
# Install flyctl
curl -L https://fly.io/install.sh | sh

# Sign up / login
fly auth signup   # or: fly auth login

# Launch app (from project root)
fly launch --dockerfile Dockerfile

# Add PostgreSQL
fly postgres create --name payments-db

# Attach DB
fly postgres attach payments-db

# Add Redis
fly redis create

# Set secrets
fly secrets set SPRING_PROFILES_ACTIVE=production
fly secrets set ML_SERVICE_ENABLED=false
fly secrets set SECURITY_ENABLED=false

# Deploy
fly deploy
```

---

## Running Tests

```bash
# Unit tests only (no Docker needed)
./mvnw test

# All tests
./mvnw verify

# With coverage report
./mvnw verify jacoco:report
# Open: target/site/jacoco/index.html
```

---

## Monitoring

### Actuator Endpoints
```bash
# Health
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Prometheus (for Grafana)
curl http://localhost:8080/actuator/prometheus
```

### Kafka UI (local only)
```bash
docker compose --profile monitoring up
# Open: http://localhost:8090
```

---

## Project Structure

```
payment-processing-system/
├── src/main/java/com/fintech/payments/
│   ├── PaymentApplication.java          # Entry point
│   ├── config/                          # Spring configs
│   │   ├── DatabaseConfig.java
│   │   ├── KafkaConfig.java
│   │   └── SecurityConfig.java
│   ├── controller/                      # REST endpoints
│   │   ├── PaymentController.java       # POST /api/v1/payments
│   │   └── AccountController.java      # POST /api/v1/accounts
│   ├── model/                           # Domain models
│   │   ├── Account.java
│   │   ├── Transaction.java
│   │   ├── PaymentRequest.java
│   │   ├── PaymentResponse.java
│   │   └── FraudScore.java
│   ├── repository/                      # JPA repositories
│   ├── service/                         # Business logic
│   │   ├── PaymentService.java          # Core payment processing
│   │   ├── AccountService.java          # Account management
│   │   ├── FraudDetectionService.java   # ML + rules
│   │   └── IdempotencyService.java      # Redis-backed dedup
│   ├── kafka/
│   │   └── PaymentEventProducer.java    # Kafka publishing
│   └── exception/                       # Error handling
├── src/main/resources/
│   ├── application.yml                  # Main config
│   ├── application-dev.yml              # Dev overrides
│   └── db/migration/V1__initial_schema.sql
├── ml-fraud-service/                    # Python ML service
│   ├── app.py                           # Flask API
│   ├── model.py                         # RF classifier
│   └── Dockerfile
├── docker-compose.yml                   # Local dev setup
├── Dockerfile                           # Spring Boot image
├── render.yaml                          # Render.com deploy
├── kubernetes/deployment.yaml          # K8s manifests
└── .github/workflows/ci-cd.yml         # GitHub Actions
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `SERIALIZABLE` isolation | Prevents phantom reads in concurrent payments |
| Idempotency via Redis | Prevents double-charges on retries |
| Double-entry ledger | Immutable audit trail for every balance change |
| Kafka events | Decouples downstream processing (notifications, analytics) |
| Heuristic fallback | ML service outage doesn't block payments |
| Flyway migrations | Reproducible DB schema across all environments |

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| API | Java 17, Spring Boot 3.2 |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Messaging | Apache Kafka |
| ML Service | Python 3.11, scikit-learn, Flask |
| Containerization | Docker, Docker Compose |
| CI/CD | GitHub Actions |
| Cloud | Render.com / Railway / Fly.io |
