# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-06-17

## User Preferences

- Communication in Bulgarian; code comments and documentation in English
- Always produce a written plan and wait for user approval before implementing
- Clean, professional code — no speculative features, no unnecessary abstractions

## Key Learnings

- **Project:** garant
- **Stack:** Spring Boot (backend) + Next.js (frontend) + PostgreSQL
- **Auth:** JWT + Spring Security, stateless login-based authentication
- **Deploy target:** AWS EC2 (later stage) — use env variables, no hardcoded hosts/ports

## Do-Not-Repeat

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->

## Decision Log

- **Biometric — face recognition:** face-api.js (TensorFlow.js) — 1:N matching against descriptors cached in IndexedDB. Chosen because it works on any phone with camera, supports offline, and identifies arbitrary workers on a single shared device. WebAuthn rejected (device-bound, 1:1 only). USB fingerprint reader planned as future phase via WebUSB API.

- **Offline-first architecture:** PWA with IndexedDB (via Dexie.js) as local DB on device. Service Worker + Workbox for asset/model caching. Background Sync API for uploading pending attendance records when internet returns. Two sync endpoints: GET /api/sync/site/{siteId} (download workers + face descriptors) and POST /api/attendance/sync (bulk upload pending records). Face descriptors ~4KB each — 100 workers ≈ 400KB, well within IndexedDB limits.
