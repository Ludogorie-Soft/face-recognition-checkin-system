# AttendTrack — Backend

A REST API for worker attendance tracking with facial recognition and GPS geofencing.

Built with **Spring Boot 3.3**, **Java 21**, **PostgreSQL**, and **Flyway**.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 |
| Maven | 3.9+ (or use the included `./mvnw` wrapper) |
| PostgreSQL | 14+ |

---

## Docker Setup (Frontend + Backend)

Both projects build automatically from a single command — no manual steps needed.

**What gets built:**
- `Dockerfile` — Spring Boot multi-stage build (Maven compiles the `.jar`, final image is JRE only, ~200 MB)
- `frontend/Dockerfile` — Next.js multi-stage build (`npm ci` + `next build`, final image is Node.js + compiled output, ~150 MB)

### Local

```bash
# 1. Create your env file
cp .env.prod.example .env
# Edit .env and fill in DB_USERNAME, DB_PASSWORD, JWT_SECRET, etc.

# 2. Build and start everything
docker compose up --build

# Frontend: http://localhost:3000
# Backend:  http://localhost:8080
```

### Production (AWS EC2)

```bash
# 1. Create your production env file
cp .env.prod.example .env.prod
# Edit .env.prod — use strong passwords and a real JWT_SECRET

# 2. Obtain an SSL certificate (required for camera access on mobile)
mkdir certs
certbot certonly --standalone -d your-domain.com
cp /etc/letsencrypt/live/your-domain.com/fullchain.pem certs/
cp /etc/letsencrypt/live/your-domain.com/privkey.pem certs/

# 3. Build and start in detached mode
docker compose -f docker-compose.prod.yml up --build -d
```

> **AWS ALB alternative:** If you use an Application Load Balancer for SSL termination, skip the `certs/` step.
> In `nginx.conf` change `listen 443 ssl` → `listen 80` and remove the `ssl_*` lines.

**Traffic flow in production:**
```
Internet → nginx (443) → frontend:3000
                       → backend:8080  (internal only, not exposed)
postgres               (internal only, not exposed)
```

---

## Local Setup (without Docker)

### 1. Create the database

```bash
psql -U postgres
CREATE DATABASE garant;
\q
```

### 2. Configure environment variables (optional)

All settings have defaults for local development. Override them via environment variables if needed:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/garant` | Database URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | *(built-in dev key)* | JWT signing secret — **change in production** |
| `JWT_EXPIRATION` | `86400000` | Token expiry in milliseconds (24h) |
| `SERVER_PORT` | `8080` | HTTP port |
| `ADMIN_EMAIL` | `admin@example.com` | Initial admin email |
| `ADMIN_PASSWORD` | `Admin@123!` | Initial admin password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed CORS origins |

### 3. Start the application

```bash
# From the project root
JAVA_HOME=/usr/local/Cellar/openjdk@21/21.0.7/libexec/openjdk.jdk/Contents/Home \
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

---

## Running on a Mobile Device (Local Network)

To access the app from your phone, both devices must be on the **same Wi-Fi network**.

### 1. Find your computer's local IP address

**macOS / Linux:**
```bash
ipconfig getifaddr en0
```

**Windows:**
```bash
ipconfig
# Look for "IPv4 Address" under your Wi-Fi adapter
```

The IP will look something like `192.168.x.x`.

### 2. Start the backend

```bash
./mvnw spring-boot:run
```

### 3. Start the frontend

```bash
cd frontend
npm run build && npm run start -- -H 0.0.0.0
```

This builds the app and starts the production server, accessible from all network interfaces.

### 4. Open on your phone

Open `http://192.168.x.x:3000` in your phone's browser (replace with your actual IP from step 1).

> **Note:** By default, the backend allows requests from any origin (`*`), which is suitable for local development. In production, set `CORS_ALLOWED_ORIGINS` to your actual frontend URL:
> ```bash
> CORS_ALLOWED_ORIGINS=https://your-domain.com ./mvnw spring-boot:run
> ```

On first startup:
- Flyway runs all database migrations automatically
- An initial admin account is created if no admin exists yet

---

## Database Migrations

Managed by **Flyway**. Migration scripts are located in:

```
src/main/resources/db/migration/
```

Migrations run automatically on startup. Do not modify existing migration files — add new versioned files instead (`V2__description.sql`, etc.).

---

## API Overview

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login with email + password, returns JWT |
| GET | `/api/auth/me` | Returns the current authenticated user |

All other endpoints require a `Bearer <token>` header.

---

### Users & Workers

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/users` | ADMIN | List all users (filterable by role) |
| POST | `/api/users` | ADMIN | Create a new user |
| PUT | `/api/users/{id}` | ADMIN | Update a user |
| DELETE | `/api/users/{id}` | ADMIN | Deactivate a user |
| POST | `/api/users/{id}/face` | ADMIN | Save face descriptor for a worker |
| DELETE | `/api/users/{id}/face` | ADMIN | Delete face descriptor |

---

### Sites

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/sites` | ADMIN, MANAGER | List sites (managers see only assigned sites) |
| GET | `/api/sites/{id}` | ADMIN, MANAGER | Get site details |
| POST | `/api/sites` | ADMIN | Create a site |
| PUT | `/api/sites/{id}` | ADMIN | Update a site |
| DELETE | `/api/sites/{id}` | ADMIN | Deactivate a site |
| POST | `/api/sites/{id}/managers/{userId}` | ADMIN | Assign a manager to a site |
| DELETE | `/api/sites/{id}/managers/{userId}` | ADMIN | Remove a manager from a site |
| POST | `/api/sites/{id}/workers/{userId}` | ADMIN | Assign a worker to a site |
| DELETE | `/api/sites/{id}/workers/{userId}` | ADMIN | Remove a worker from a site |

---

### Sync (Manager — offline support)

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/sync/{siteId}` | ADMIN, MANAGER | Returns site info + all workers with face descriptors for offline use |

Managers can only sync sites they are assigned to.

---

### Attendance

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/attendance` | ADMIN, MANAGER | Record a batch of attendance events |

Each attendance record includes: worker ID, site ID, type (CHECK_IN / CHECK_OUT), GPS coordinates, location validity, face confidence score, manual override flag, and recorded timestamp.

---

### Reports

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/reports/attendance` | ADMIN | Attendance records for a site in a date range |
| GET | `/api/reports/missing` | ADMIN | Workers who have not checked in on a given date |
| GET | `/api/reports/attendance/export` | ADMIN | Download attendance report as Excel (.xlsx) |

---

### Dashboard

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/dashboard/stats` | ADMIN | Returns: total sites, total workers, present today, missing today |

---

## Features

### JWT Authentication

- Stateless authentication using JWT (jjwt 0.12.6)
- Tokens are validated on every request via `JwtAuthenticationFilter`
- Role-based access control enforced via Spring Security's `@PreAuthorize` and method security

---

### Face Descriptor Storage

- Workers' faces are stored as 128-dimensional float arrays (`double[]`) in a JSONB column in PostgreSQL
- No images are stored — only the neural network descriptor computed by face-api.js on the client
- Before saving a new descriptor, the system computes the Euclidean distance against all existing descriptors. If any is below the threshold (0.45), the request is rejected with `FACE_ALREADY_REGISTERED` to prevent one person from being registered under multiple worker accounts

---

### Geofence Validation

- Each site has a GPS coordinate (lat/lng) and a radius in meters
- When attendance is recorded, the backend stores the worker's coordinates and whether they fall within the site's radius
- Geofence validation is performed on the frontend using the Haversine formula; the backend stores the result as `location_valid`

---

### Attendance Recording

- Accepts batches of attendance events (offline sync support)
- Each event is validated: worker must exist, site must exist, and the worker must be assigned to the site
- Duplicate detection: re-sending the same event (same worker, site, type, and timestamp) is handled gracefully

---

### Excel Export

- Attendance reports can be exported to `.xlsx` using Apache POI
- Columns: Worker ID, Worker Name, Site, Date, Type, Recorded At, Lat, Lng, Location Valid, Face Confidence, Manual Override
- All columns are auto-sized

---

### Error Handling

All API errors return a consistent JSON structure:

```json
{
  "status": 404,
  "code": "WORKER_NOT_FOUND",
  "message": "Worker not found: ...",
  "timestamp": "2026-06-17T10:00:00"
}
```

The `code` field is a machine-readable `ErrorCode` enum value that the frontend maps to localized user-facing messages.

---

## Project Structure

```
src/main/java/org/example/garant/
├── auth/               # Login endpoint, JWT provider, auth filter
├── attendance/         # Attendance entity, repository, service, controller
├── common/
│   └── exception/      # ApiException, ErrorCode enum, GlobalExceptionHandler
├── config/             # SecurityConfig, CorsConfig, DataSourceConfig
├── dashboard/          # Dashboard stats endpoint
├── report/             # Report service, Excel export, DTOs
├── site/               # Site entity, managers/workers assignment
├── sync/               # Sync endpoint for offline PWA support
└── user/               # User entity, FaceDescriptor, UserService
```

---

## Tech Stack

| Category | Technology |
|----------|-----------|
| Framework | Spring Boot 3.3.5 |
| Language | Java 21 |
| Security | Spring Security 6, JWT (jjwt 0.12.6) |
| Database | PostgreSQL 14+ |
| ORM | Spring Data JPA (Hibernate 6) |
| Migrations | Flyway |
| Excel export | Apache POI |
| Build tool | Maven |
