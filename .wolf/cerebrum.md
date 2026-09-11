# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-06-17

## User Preferences

- Communication in Bulgarian; code comments and documentation in English
- Always produce a written plan and wait for user approval before implementing
- Clean, professional code — no speculative features, no unnecessary abstractions
- "Индексирай промените" = обнови само OpenWolf файловете (.wolf/anatomy.md, memory.md, cerebrum.md). НЕ прави git commit и НЕ push-вай към GitHub.

## Key Learnings

- **A session belongs to the WORKER, not to the site (2026-09-11).** The projection key is `(worker, day)` — `AttendanceService.DayKey` deliberately has no `siteId`, and `findForWorkerDay` loads every site. A person has one session at a time: someone who checks in at site A and scans at site B in the evening is *ending* that session. Pairing per site read it as a fresh check-in, opened a phantom session at B, and the nightly scheduler closed both — inflating the day and splitting the hours. The same rule has to hold in four places or the bug comes back through whichever one was missed: the projection, `findUnclosedCheckIns` (no `co.site.id = a.site.id`), the scheduler's dedup key, and **`getTodayStatus`** — that last one is what the terminal's green/red button reads, and it is the user-visible half of the fix.

- **The terminal's site is a CLAIM, not a fact (2026-09-11).** `client_site_id` / `site_id` mirrors `client_type` / `type`: store what the device said, derive the truth on the server. The device works from a cached site list that may be stale, incomplete, or evaluated without checkpoints, and it is offline exactly when it is most likely to be wrong. Never let a client's guess become a stored fact without the server re-deriving it from raw data — and never overwrite the guess, or a manual correction becomes invisible.

- **Geometry lives in two places and they must agree (2026-09-11).** `site/GeoResolver.java` and `frontend/lib/geo.ts` implement the same haversine + point-to-segment maths, because the terminal evaluates zones offline and the server re-evaluates them on sync. `distanceToZone` returns a SIGNED distance to the boundary (negative = inside); that one number both decides in-zone and ranks candidate sites, which is why "nearest site" and "is it inside" never disagree. Change one file, change the other.

- **Never silently fall back to an arbitrary item (2026-09-11).** The whole incident was `return entries[0].siteId` — the first assignment in sync order — when no zone matched. A fallback that picks by *position in a list* rather than by the property being decided will look fine in testing and file scans 163 km away in production. Fall back to the nearest/best candidate and record how good the match was (`distance_meters`) so the guess is auditable.

- **`@Transactional` works only on PUBLIC methods (2026-09-11).** `AnnotationTransactionAttributeSource` has `publicMethodsOnly = true`, so a package-private `@Transactional` method compiles, reads correctly, and runs with no transaction at all. It will usually appear to work, because each repository write commits on its own — the damage only shows when something fails part-way through. `AttendanceService.reresolveSites` had exactly this. `AutoCheckoutScheduler.insertMissingCheckouts` still does (and is additionally called via `this`, so it was never advised either); it has been in production since PR #6, which is precisely why the failure mode is easy to miss.

- **Mocked repositories hide everything that only exists at runtime (2026-09-11).** Every service test mocks `AttendanceRepository`, so JPQL is never parsed, `ddl-auto: validate` is never run against the Flyway schema, and proxying/transaction behaviour is never exercised. Two real defects (a dedup matching a derived column, and the non-transactional repair above) survived a green suite. Booting the app against a throwaway `postgres:16` and driving the real endpoints found both in minutes — do that before any deploy that touches migrations, entities or `@Query`.

- **Request/IP chain:** browser → nginx → Next.js API proxy (`frontend/app/api/[...path]/route.ts`, forwards all headers except host/origin/referer/connection) → Spring backend. So Spring's `getRemoteAddr()` is the Next container, NOT the client. To capture the real client IP, nginx must set `X-Real-IP $remote_addr` + `X-Forwarded-For $proxy_add_x_forwarded_for` (both nginx.conf and nginx.prod.conf), the Next proxy forwards them (already does), and the backend reads `X-Forwarded-For` first hop / `X-Real-IP`. There is NO nginx `/api` route — everything goes through Next.

- **Production topology:** `docker-compose.prod.yml` runs ONLY postgres + backend + frontend (frontend publishes :3000); there is NO nginx container. Nginx is installed ON THE HOST and reverse-proxies `https://tracker.garant-90.com` → `http://localhost:3000` using **`nginx.prod.conf`** (with the letsencrypt cert paths + the X-Real-IP/X-Forwarded-For headers). `nginx.conf` (proxy to `frontend:3000`, `*.sslip.io`) is the containerized/dev variant, NOT current prod. Backend reads env from `.env` (compose `env_file: .env`), not `.env.prod`. Reload prod nginx with `nginx -t && systemctl reload nginx` (NOT `docker compose restart nginx`). DEPLOY.md was rewritten 2026-09-08 to reflect this (previously described a stale sslip.io + containerized-nginx setup).

- **Project:** garant
- **Stack:** Spring Boot (backend) + Next.js (frontend) + PostgreSQL
- **Auth:** JWT + Spring Security, stateless login-based authentication
- **Deploy target:** AWS EC2 (later stage) — use env variables, no hardcoded hosts/ports

## Do-Not-Repeat

<!-- [2026-07-09] HTML input min/max attributes are NOT enforced when the field is controlled via useState instead of react-hook-form. Always add explicit validation in onSubmit and @Positive/@Min constraints on the backend DTO for numeric inputs outside react-hook-form. -->

<!-- [2026-07-09] @Modifying bulk JPQL DELETE must use clearAutomatically = true to avoid stale first-level cache when followed by inserts in the same transaction. -->

<!-- [2026-07-10] @Modifying(clearAutomatically = true) WITHOUT flushAutomatically = true is dangerous when other entities are dirty in the same transaction. Spring's FlushModeType.AUTO does NOT flush dirty entities from unrelated tables before a @Modifying query. This means pending UPDATEs on entity A can be evicted by clearAutomatically after a DELETE on entity B, silently discarding changes. Always pair clearAutomatically = true with flushAutomatically = true when other entities may be dirty in the same transaction. -->

<!-- [2026-07-09] All read methods in @Service classes that make multiple DB queries should have @Transactional(readOnly = true) — not just getAll() but also getById() and any helper that chains repository calls. -->

<!-- [2026-06-18] React Query cache invalidation: always invalidate the LIST query key [QK], not just the detail [QK, id]. The list and detail are separate cache entries. Invalidating [QK] covers both due to partial matching. -->

<!-- [2026-06-18] sessionLog in verify/page.tsx must merge BOTH /api/attendance/today (synced) AND db.pending (unsynced) records. Only using the API means offline/pending records are invisible. -->

<!-- [2026-06-21] Never hardcode UI strings (especially Bulgarian). All user-visible text must go through next-intl t() and have keys in both en.json and bg.json. -->

<!-- [2026-06-21] Docker multi-stage builds: gitignored files (WASM, MJS, ML models) are absent in CI. Re-run scripts/copy-wasm.js in Stage 2 and download ML models with wget during build. -->

<!-- [2026-06-21] ORT copies BOTH .wasm AND .mjs files to public/. Copy pattern: /ort-wasm-simd-threaded.*\.(wasm|mjs)$/. Content-Type header required for .mjs (text/javascript). PWA cache must match the same pattern. -->

<!-- [2026-06-21] Detection while-loops: always wrap the async inference call in try/catch. Without it, any ORT/MediaPipe internal error causes an unhandled rejection and silently kills the loop. -->

<!-- [2026-06-21] setInterval-based detection (FaceRegisterModal): guard with a ref (detectingRef) and try/catch/finally to prevent concurrent inference calls if inference takes longer than the interval. -->

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->

<!-- [2026-06-22] IndexedDB workers table must use compound primary key [id+siteId]. Using only 'id' causes BulkError when the same worker is synced for a second site — the catch block falls to offline cache which returns empty workers, breaking face detection. Always use bulkPut (not bulkAdd) for worker persistence. -->

<!-- [2026-06-22] DELETE /api/users/{id} is a SOFT-DELETE (deactivate, not real delete). Face descriptors are NOT cascade-deleted because the user row still exists. Always call faceDescriptorRepository.deleteByUserId() inside deactivate(). -->

<!-- [2026-07-31] useGeoLocation no longer accepts a site param. It only tracks raw position + permissionDenied. Per-worker geo validation (which site the worker belongs to, and whether the device is within that site's zone) is done in VerifyCamera via resolveWorkerSite(). This allows one-to-many: same worker in multiple sites → pick the site the device is currently within. -->

<!-- [2026-07-31] verify/page.tsx no longer has a 'select' phase. On mount it auto-syncs ALL assigned sites via syncAll() and goes directly to camera. Workers from all sites are flattened into one array; siteId is derived at record time from the matched worker. -->

<!-- [2026-07-31] When location permission is PERMISSION_DENIED (GeolocationPositionError.code === 1), show a full-screen overlay in VerifyCamera (not just the top bar text). The overlay includes numbered steps for enabling location in device settings. navigator.permissions.query({name:'geolocation'}) pre-detects the denied state before watchPosition fires its error. -->

<!-- [2026-07-31] Reports: siteId is optional for /api/reports/attendance and /api/reports/hours (required=false in controller, service branches on null → findAllInDateRange). The 'missing' tab still requires a specific siteId (logic depends on site worker list). The 'summary' tab never needed siteId. Frontend Select adds "Всички обекти" as first option (value="_all" → siteId='') mirroring the company filter pattern. -->

<!-- [2026-07-31] Reports: companyName is fetched via CompanyRepository.findWorkerCompanyPairs(workerIds) — a bulk query returning [workerId, companyId, companyName]. Use putIfAbsent to keep the first (alphabetically first) company per worker. Pass as Map<UUID,String> into buildWorkedHoursRows() and toRow(). AttendanceReportRow and WorkedHoursRow records both have companyName as second field after workerName. -->

<!-- [2026-07-31] Assign modals (SiteAssignModal, CompanySiteModal, WorkerSiteModal, CompanyWorkerModal): BOTH the "assigned" list AND the "available/unassigned" list must have max-h + overflow-y-auto. A common mistake is adding scroll only to the available list and forgetting the assigned list, which overflows when many items are assigned. Use max-h-60 for assigned, max-h-48 for available (smaller since it has a search field above it). -->

<!-- [2026-08-04] When adding @Modifying to a JPA repository method, always add the import: org.springframework.data.jpa.repository.Modifying. It is NOT auto-imported by Spring Data. -->

<!-- [2026-08-04] Attendance.lat and Attendance.lng are primitive double (not Double). Never write null checks like == null on them — it's a compile error. Fields are @Column(nullable=false) so they are always set. -->

<!-- [2026-08-04] JPA IN clause with empty collection produces invalid SQL and throws an exception at runtime. Always guard before calling findByXxxIn(): if the collection is empty, return early (e.g. if (records.isEmpty()) return 0). -->

<!-- [2026-08-04] In reports/page.tsx the needsSite flag controls whether the site Select renders. If a tab needs the site selector (e.g. missing), it must be included in the condition. Current: needsSite = tab !== 'summary'. -->

<!-- [2026-08-04] Assign modals (SiteAssignModal, WorkerSiteModal, CompanySiteModal, CompanyWorkerModal) use sm:max-w-lg for width. Do NOT revert to sm:max-w-md. -->

<!-- [2026-08-20] next-intl translation keys used in a component must exist in the SAME namespace the component reads (useTranslations('X')). A key that exists in namespace 'workers' is NOT available via useTranslations('verify'). Always verify the key is in the correct namespace, not just in any namespace. -->

<!-- [2026-08-20] Export endpoints (/attendance/export, /hours/export) must mirror the query endpoints: if the query accepts optional siteId (required=false), the export must too. Frontend handleExport must use `siteId: siteId || undefined` (not bare `siteId`) so empty string is not sent as a param. -->

<!-- [2026-09-08] Running mvn on this machine needs TWO things or it fails: (1) Lombok must be declared under maven-compiler-plugin <annotationProcessorPaths> (added to pom.xml, lombok 1.18.42) — without it javac either can't find Lombok symbols or crashes with `TypeTag :: UNKNOWN`; (2) JAVA_HOME must point to JDK 21, NOT the machine-default JDK 25 — on JDK 25 the project's Mockito (5.x via Spring Boot 3.3.5) cannot create inline mocks ("Mockito cannot mock this class"). Working command: `JAVA_HOME=/usr/local/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home mvn -o test`. Frontend: `npx tsc --noEmit` in frontend/. -->

## Decision Log

- **Biometric — face recognition v2:** MediaPipe FaceLandmarker (478-landmark detection) + MobileFaceNet ONNX (InsightFace w600k_mbf, 512-dim ArcFace embeddings) via onnxruntime-web. Replaced face-api.js. Module-level singletons with reference counting (consumerCount). GPU delegate with CPU fallback. All ONNX output tensors must be disposed explicitly.

- **Biometric — face recognition v1 (replaced):** face-api.js (TensorFlow.js) — 128-dim descriptors. Replaced by v2 (see above). V2__clear_face_descriptors.sql clears the old 128-dim data.

- **A de-duplication window is measured in SECONDS, never minutes (2026-09-10, PR #6):** the re-scan threshold started at 15 minutes, which silently destroyed real data — a worker checked in 08:00 and out 08:10 had the check-out swallowed as an "accidental re-scan", leaving the shift open for the auto-checkout to close, so ten minutes became a full day. The mistake was conflating two different things: an accidental double tap is physically a matter of SECONDS, while a short shift is legitimate data. Now `attendance.min-gap-seconds`, default 60 — every genuine re-scan observed in production was 1-18 seconds. Rule of thumb: when a rule discards user data, size it from measured reality, not intuition.

- **Every write path must re-derive the day (2026-09-10, PR #6):** `type` is a DERIVED field, so anything that writes, deletes or moves a record has to call `renormalizeDay` afterwards or the rest of the day keeps stale directions (this is one way to end up with two consecutive check-ins). Covered: sync, `deleteAttendance`, `manualRecord`, `changeSite` (both sites), `AutoCheckoutScheduler`. Always split into two transactions — the projection must read the DB AFTER the write commits, otherwise it rebuilds the day from the row being removed.

- **Never match on a derived column (2026-09-10, PR #6):** `existsDuplicate` compared `type`, which the projection rewrites. A terminal without client event ids re-sending a queued record therefore failed to match its own row (it reported CHECK_OUT, the server had stored CHECK_IN) and inserted a duplicate. Dedup/uniqueness must use immutable values — here `client_type`, which is why scheduler and admin records set it too.

- **Verify the deployed IMAGE before believing a code bug (reinforced 2026-09-10):** twice now, prod behaviour that "could not happen" was a stale hand-built `latest`. The decisive check is `docker inspect garant-backend --format '{{.Image}}' | xargs docker image inspect --format '{{.Created}}'` — compare that timestamp against `synced_at` of the suspect rows. Also note prod's host file named `docker-compose.yml` actually holds the *prod* compose content (pulls images; does NOT build).

- **Guards / 12-24h shifts are a ShiftType, NOT a Role (2026-09-09, PR #3):** `ShiftType` (`DAY` | `SHIFT_24H`) on the user. A `GUARD` *role* was proposed and rejected after checking the code: `FaceDescriptorService.save()` gates on `role == WORKER`, so guards could not have registered a face (unable to check in at all), plus three more `role == WORKER` branches in `UserService`. Rule of thumb for this repo: **`Role` is an authority; anything with identical permissions belongs on a different axis.** Effects of `SHIFT_24H`: excluded from `AutoCheckoutScheduler` (filtered in the query), the session projection is seeded from the previous day so it continues across midnight (max 26h — `MAX_OPEN_SHIFT_HOURS`; older = forgotten, left open), and `ReportService` dates their records by the session that opened them instead of the calendar day. `V12` defaults everyone to `DAY`; a null `shiftType` on update means "keep current" so an older client cannot reset a guard.

- **Never invent an end time for an unclosed shift:** guards may work 12h OR 24h, so any automatic checkout is wrong for some of them — and a wrong time is worse than a missing one because it looks like real data on the payroll report. `GuardShiftMonitor` therefore only *alerts* (daily 09:00 + a live dashboard list via `openGuardShifts`) and never closes. Same reasoning applies to any future "helpful" auto-fill of attendance times.

- **Report windows must be wider than the requested range:** a shift that opens before `from` or closes after the 06:00 night boundary is invisible otherwise — a finished overnight guard shift showed up in NEITHER day's report. `getWorkedHours`/`getWorkedHoursSummary` widen the fetch by a day on both sides and then `trimToRange` the emitted rows. Widening is safe because out-of-range groups are filtered after pairing.

- **Attendance direction is DERIVED, not reported (2026-09-09, PR #1):** The terminal no longer decides check-in vs check-out — it reports "worker seen at time T" and the server derives `type` by projecting the whole day's ordered events (`SessionProjector`, a pure dependency-free function; `AttendanceService.renormalizeDay()` re-runs it after every sync batch). The projection is **order-independent and idempotent** — that is precisely what makes offline sync correct, because a late batch from an offline terminal simply re-projects and heals the day. Rules: a day starts with CHECK_IN and alternates; two scans <15 min apart (`attendance.min-gap-minutes`) are an accidental re-scan; ADMIN_MANUAL rows are authoritative and never re-typed; a real check-out supersedes a SCHEDULER_AUTO one. Rows are NEVER deleted — excluded ones are flagged `ignored` + reason, and every report/terminal/scheduler query filters `ignored = false`. This REPLACED the earlier accept-and-flag anomaly design (anomalies just moved work to the admin); the anomaly columns/enum remain only so pre-change rows stay readable.

- **Docker Hub `latest` images are hand-built and DRIFT from the repo:** prod pulls `ludogoriesoft/attendtrack-*:latest` (docker-compose.prod.yml has no `build:`). A prod DB showed `source`/`SCHEDULER_AUTO` populated (new code) yet 0 anomalies across 3075 rows, while the same committed code flagged the identical case in a local end-to-end replay — i.e. the pushed image was built from a partial working tree. When prod behaviour contradicts the committed code, verify the deployed IMAGE before hunting for a code bug.

- **Attendance audit metadata (Phase 2, 2026-09-08):** Added explicit `AttendanceSource` enum (TERMINAL_FACE / TERMINAL_MANUAL / ADMIN_MANUAL / SCHEDULER_AUTO) — replaces the fragile `manualOverride && manager==null` heuristic (the report now prefers `source`, falling back to the heuristic only for pre-V10 NULL rows). Added audit columns `ip_address`, `user_agent`, `client_device_id`, `app_version` (V10, with best-effort source backfill). IP/user-agent captured server-side in `AttendanceController.sync` from the request (see request/IP chain in Key Learnings); device-id/app-version sent by the client (`frontend/lib/deviceId.ts`, localStorage UUID). New `GET /api/attendance/{id}/details` (ADMIN) + `AttendanceDetailsModal` (report "Details" button per session). nginx configs updated for X-Real-IP/X-Forwarded-For → require nginx reload on deploy.

- **Attendance state-machine reconciliation (Phase 1 Extended, 2026-09-08):** The device path (VerifyCamera + /api/attendance/sync) historically decided CHECK_IN/CHECK_OUT purely client-side from a stale per-device `sessionLog` and the server never validated it — root cause of duplicate check-ins / stuck open sessions across devices/offline. Fix keeps the server as the authority: `AttendanceService.sync` sorts the batch by recordedAt, tracks running last-type per (worker,site,day) seeded from DB, and flags illegal transitions with `anomaly` + `AnomalyReason` (ACCEPT-and-FLAG, chosen over reject so offline data is never lost). `clientEventId` (UUID per event) + a partial-unique index (V9) give atomic idempotent dedup. Client hardening: persistent `sessionStatus` IndexedDB table keyed `[workerId+siteId]` (reload-safe offline), `sessionLog` keyed `workerId:siteId` (was workerId — collapsed multi-site), local (not UTC) "today", and VerifyCamera shows BOTH buttons when offline + status not server-confirmed. NOT done (Phase 2): explicit AttendanceSource taxonomy and IP/user-agent/device audit columns.

- **Offline-first architecture:** PWA with IndexedDB (via Dexie.js) as local DB on device. Service Worker + Workbox for asset/model caching. Background Sync API for uploading pending attendance records when internet returns. Two sync endpoints: GET /api/sync/site/{siteId} (download workers + face descriptors) and POST /api/attendance/sync (bulk upload pending records). Face descriptors ~4KB each — 100 workers ≈ 400KB, well within IndexedDB limits.
