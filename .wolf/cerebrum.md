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

<!-- [2026-07-09] HTML input min/max attributes are NOT enforced when the field is controlled via useState instead of react-hook-form. Always add explicit validation in onSubmit and @Positive/@Min constraints on the backend DTO for numeric inputs outside react-hook-form. -->

<!-- [2026-07-09] @Modifying bulk JPQL DELETE must use clearAutomatically = true to avoid stale first-level cache when followed by inserts in the same transaction. -->

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

## Decision Log

- **Biometric — face recognition v2:** MediaPipe FaceLandmarker (478-landmark detection) + MobileFaceNet ONNX (InsightFace w600k_mbf, 512-dim ArcFace embeddings) via onnxruntime-web. Replaced face-api.js. Module-level singletons with reference counting (consumerCount). GPU delegate with CPU fallback. All ONNX output tensors must be disposed explicitly.

- **Biometric — face recognition v1 (replaced):** face-api.js (TensorFlow.js) — 128-dim descriptors. Replaced by v2 (see above). V2__clear_face_descriptors.sql clears the old 128-dim data.

- **Offline-first architecture:** PWA with IndexedDB (via Dexie.js) as local DB on device. Service Worker + Workbox for asset/model caching. Background Sync API for uploading pending attendance records when internet returns. Two sync endpoints: GET /api/sync/site/{siteId} (download workers + face descriptors) and POST /api/attendance/sync (bulk upload pending records). Face descriptors ~4KB each — 100 workers ≈ 400KB, well within IndexedDB limits.
