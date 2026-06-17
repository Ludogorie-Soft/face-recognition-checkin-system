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

## Local Setup

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
