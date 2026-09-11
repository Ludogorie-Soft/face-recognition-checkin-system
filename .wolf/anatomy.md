# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-09-11T07:27:35.747Z
> Files: 181 tracked | Anatomy hits: 0 | Misses: 0

## ./

- `.gitignore` — Git ignore rules (~220 tok)
- `CLAUDE.md` — OpenWolf entry point (~57 tok)
- `DEPLOY.md` — Прод деплой: EC2 + Docker Compose (postgres/backend/frontend) + Nginx НА ХОСТА чрез nginx.prod.conf; домейн tracker.garant-90.com; env файл .env (~2251 tok)
- `docker-compose.prod.yml` — Docker Compose: 4 services (~306 tok)
- `docker-compose.yml` — Docker Compose services (~320 tok)
- `FACE_RECOGNITION_V2.md` — Face Recognition — Вариант 2: MediaPipe + MobileFaceNet ONNX (~1394 tok)
- `GUIDE_BG.md` — AttendTrack — Ръководство за потребителя (~2385 tok)
- `GUIDE_EN.md` — AttendTrack — User Guide (~2311 tok)
- `nginx.conf` (~358 tok)
- `nginx.prod.conf` (~271 tok)
- `OFFLINE_WORKFLOW_SIMPLE.md` — AttendTrack — Как работи без интернет (~736 tok)
- `OFFLINE_WORKFLOW.md` — AttendTrack — Офлайн режим (~1930 tok)
- `OFFLINE_WORKFLOW.md` — Офлайн архитектура: 4 фази, sync flow, race condition защита, ограничения (~800 tok)
- `PLAN.md` — PLAN: Two-Role Refactor — ADMIN + WORKER only (~1591 tok)
- `pom.xml` (~1366 tok)
- `README.md` — Project documentation (~2597 tok)
- `ROADMAP.md` — AttendTrack — Roadmap (~647 tok)

## .claude/

- `launch.json` (~85 tok)
- `settings.json` — Claude Code settings (~441 tok)

## .claude/rules/

- `openwolf.md` — OpenWolf rules (~313 tok)

## frontend/

- `Dockerfile` — Docker container definition (~445 tok)
- `next.config.ts` — Declares withNextIntl (~672 tok)
- `package.json` — Node.js package manifest (~452 tok)

## frontend/app/

- `globals.css` — Styles: 5 rules, 61 vars (~976 tok)
- `page.tsx` — RootPage (~31 tok)

## frontend/app/[locale]/

- `page.tsx` — LocalePage (~61 tok)

## frontend/app/[locale]/(admin)/companies/

- `page.tsx` — CompaniesPage — renders table (~2246 tok)

## frontend/app/[locale]/(admin)/dashboard/

- `page.tsx` — StatCard (~4101 tok)

## frontend/app/[locale]/(admin)/reports/

- `page.tsx` — fmtLocal (~16273 tok)

## frontend/app/[locale]/(admin)/settings/

- `page.tsx` — today (~1170 tok)

## frontend/app/[locale]/(admin)/sites/

- `page.tsx` — formatTime — renders table (~2170 tok)

## frontend/app/[locale]/(admin)/workers/

- `page.tsx` — ROLE_VARIANT — renders table (~2916 tok)

## frontend/app/[locale]/(auth)/

- `layout.tsx` — AuthLayout (~455 tok)

## frontend/app/[locale]/(auth)/login/

- `page.tsx` — LoginPage — renders form (~1233 tok)

## frontend/app/[locale]/(manager)/

- `layout.tsx` — VerifyLayout (~844 tok)

## frontend/app/[locale]/(manager)/verify/

- `page.tsx` — VerifyPage (~3305 tok)

## frontend/app/[locale]/admin/

- `page.tsx` — AdminPage (~61 tok)

## frontend/app/api/[...path]/

- `route.ts` — Next.js API route (~362 tok)

## frontend/components/

- `providers.tsx` — Providers (~296 tok)

## frontend/components/companies/

- `CompanyDialog.tsx` — Req — renders form, modal (~1198 tok)
- `CompanySiteModal.tsx` — CompanySiteModal — renders modal; assigned list max-h-60 overflow-y-auto; sm:max-w-lg (~1250 tok)
- `CompanyWorkerModal.tsx` — CompanyWorkerModal — renders modal; sm:max-w-lg (~1450 tok)

## frontend/components/dashboard/

- `ManualAttendanceModal.tsx` — todayStr — renders modal (~3497 tok)

## frontend/components/layout/

- `AdminSidebar.tsx` — AdminSidebar (~1148 tok)
- `ThemeToggle.tsx` — ThemeToggle (~197 tok)

## frontend/components/offline/

- `SyncBanner.tsx` — SyncBanner (~1336 tok)

## frontend/components/reports/

- `AttendanceDetailsModal.tsx` — Set only when the terminal claimed a different site than the one the server resolved. (~1370 tok)

## frontend/components/sites/

- `MapPicker.tsx` — DEFAULT_CENTER (~3599 tok)
- `SiteAssignModal.tsx` — SiteAssignModal — renders modal; sm:max-w-lg; assigned list max-h-60 overflow-y-auto (~2040 tok)
- `SiteDialog.tsx` — MapPicker — renders form, modal (~5390 tok)
- `TimePicker.tsx` — HOURS (~502 tok)

## frontend/components/ui/

- `textarea.tsx` — Textarea (~203 tok)

## frontend/components/verify/

- `ManualOverrideModal.tsx` — ManualOverrideModal — renders modal; workers sorted A-Z; search input filters by name; selected worker highlighted with checkmark (~1316 tok)
- `SyncLoader.tsx` — SVG comet-arc loader; indeterminate: pure CSS spin + feGaussianBlur glow; determinate: smooth strokeDashoffset fill. No JS state. (~620 tok)
- `VerifyCamera.tsx` — Called when a new worker is recognised, so the page can refresh the server status. (~6766 tok)

## frontend/components/workers/

- `FaceRegisterModal.tsx` — FaceRegisterModal — renders modal (~1849 tok)
- `WorkerDialog.tsx` — ROLES — renders form, modal (~2332 tok)
- `WorkerSiteModal.tsx` — WorkerSiteModal — renders modal; assigned list max-h-60 overflow-y-auto; sm:max-w-lg (~2470 tok)

## frontend/hooks/

- `useAuth.ts` — Exports useAuth (~278 tok)
- `useCompanies.ts` — API routes: GET, DELETE, POST (6 endpoints) (~693 tok)
- `useDashboard.ts` — API routes: GET (2 endpoints) (~369 tok)
- `useFaceApi.ts` — useFaceApi — MediaPipe FaceLandmarker + MobileFaceNet ONNX (~2359 tok)
- `useGeoLocation.ts` — Exports GeoState (no site param), useGeoLocation(); tracks raw position + permissionDenied flag (~684 tok)
- `useSites.ts` — API routes: GET, DELETE, POST (7 endpoints) (~772 tok)
- `useSiteSync.ts` — Exports SyncResult, SyncAllResult, SyncStatus, useSiteSync (~1146 tok)
- `useWorkers.ts` — API routes: GET, DELETE, POST (4 endpoints) (~574 tok)

## frontend/lib/

- `auth.ts` — Exports TOKEN_KEY, getToken, setToken, removeToken + 5 more (~400 tok)
- `axios.ts` — Declares api (~267 tok)
- `db.ts` — GPS accuracy radius in metres at scan time. Undefined for records queued before v7. (~1167 tok)
- `deviceId.ts` — Stable per-device identifier for attendance audit metadata. Generated once and (~201 tok)
- `errors.ts` — Known error codes from the backend ErrorCode enum (~302 tok)
- `faceAlignment.ts` — Face alignment: transforms a raw video frame into a normalized (~1112 tok)
- `faceMatcher.ts` — Cosine-similarity 1:N face matcher. (~613 tok)
- `geo.ts` — Minimum distance (metres) from point P to line segment AB. (~908 tok)
- `prefetchModels.ts` — prefetchModels — silently warms the Service Worker cache with all face (~462 tok)

## frontend/messages/

- `bg.json` (~3640 tok)
- `en.json` (~3503 tok)

## frontend/scripts/

- `copy-wasm.js` — copy-wasm.js — runs automatically after `npm install` (postinstall). (~625 tok)

## frontend/types/

- `company.ts` — Exports CompanyResponse, CompanyRequest (~133 tok)
- `site.ts` — Exports CheckpointResponse, CheckpointRequest, SiteResponse, SiteRequest (~310 tok)
- `user.ts` — Exports Role, CompanyRef, UserResponse, UserRequest (~127 tok)

## src/main/java/org/example/ (legacy)

- `Main.java` — IntelliJ placeholder, unused (~50 tok)

## src/main/java/org/example/attendTrack/attendance/

- `AnomalyReason.java` — Why the sync reconciliation flagged an attendance record as anomalous. (~154 tok)
- `Attendance.java` — Site the terminal claimed. {@link #site} is the server-resolved value; the two differ (~1231 tok)
- `AttendanceController.java` — Real client IP behind the nginx → Next.js proxy chain. (~1572 tok)
- `AttendanceRepository.java` — Fallback dedup for terminals that predate client event ids. Matches on {@code clientType} — (~3417 tok)
- `AttendanceService.java` — Identifies one (worker, day) whose session sequence must be re-projected. Deliberately not (~8004 tok)
- `AttendanceSource.java` — Where an attendance record originated. Replaces the fragile (~182 tok)
- `AutoCheckoutScheduler.java` — Runs at 00:01 every day. Searches the last 7 days for workers who checked in (~1459 tok)
- `GuardShiftMonitor.java` — Daily 09:00 alert for 12/24h shifts open past 26h. Deliberately does NOT close them — an invented end time would look like real payroll data. (~430 tok)
- `SessionProjector.java` — Pure projection of one worker's raw scan events (for a single site and day) into the correct (~1279 tok)

## src/main/java/org/example/attendTrack/attendance/dto/

- `AttendanceDetail.java` — Full audit view of a single attendance record, for the admin report details modal. (~680 tok)
- `AttendanceRecord.java` — GPS accuracy radius in metres at scan time; null for clients that predate this field. (~270 tok)
- `AttendanceSyncResponse.java` — Class: AttendanceSyncResponse (~50 tok)
- `ManualAttendanceRequest.java` — Class: ManualAttendanceRequest; fields: workerId, siteId, type, date, time (LocalTime, optional) (~145 tok)
- `WorkerDayStatus.java` — Status of one worker for a given site + day; fields: workerId, workerName, attendanceId, lastType, checkInTime, checkOutTime, calculatedHours (~220 tok)

## src/main/java/org/example/attendTrack/common/exception/

- `ErrorCode.java` — Class: ErrorCode (~153 tok)

## src/main/java/org/example/attendTrack/company/

- `Company.java` — Entity: Company (~547 tok)
- `CompanyController.java` — RestController: CompanyController (10 endpoints) (~763 tok)
- `CompanyRepository.java` — Class: CompanyRepository (~798 tok)
- `CompanyService.java` — Service: CompanyService (~1812 tok)

## src/main/java/org/example/attendTrack/company/dto/

- `CompanyRequest.java` — Class: CompanyRequest (~76 tok)
- `CompanyResponse.java` — CompanyResponse: SiteRef, WorkerRef, from (~381 tok)

## src/main/java/org/example/attendTrack/config/

- `CorsConfig.java` — ", config); (~342 tok)
- `SecurityConfig.java` — ").permitAll() (~955 tok)
- `TimeZoneConfig.java` — Pins the JVM default zone to app.timezone (Europe/Sofia). Device times are local wall clock; a UTC server made synced_at look earlier than recorded_at (~260 tok)

## src/main/java/org/example/attendTrack/dashboard/

- `AbsenteeRow.java` — Class: AbsenteeRow (~39 tok)
- `ActivityEntry.java` — Class: ActivityEntry (~67 tok)
- `DashboardController.java` — RestController: DashboardController (3 endpoints) (~1251 tok)
- `DashboardExtended.java` — Class: DashboardExtended (~92 tok)
- `DayAttendance.java` — Class: DayAttendance (~36 tok)
- `OpenShiftEntry.java` — Record: workerName, siteName, since — a guard shift left open (~55 tok)
- `OutOfZoneEntry.java` — Class: OutOfZoneEntry (~46 tok)
- `SiteAttendance.java` — Class: SiteAttendance (~45 tok)

## src/main/java/org/example/attendTrack/notification/

- `NotificationService.java` — Notifies all admins and managers of the site about missing workers. (~1068 tok)

## src/main/java/org/example/attendTrack/report/

- `HoursCorrection.java` — Entity: HoursCorrection (~403 tok)
- `HoursCorrectionRepository.java` — Class: HoursCorrectionRepository (~296 tok)
- `ReportController.java` — RestController: ReportController (9 endpoints) (~1518 tok)
- `ReportService.java` — Service: ReportService (~10716 tok)

## src/main/java/org/example/attendTrack/report/dto/

- `AttendanceReportRow.java` — Class: AttendanceReportRow (~198 tok)
- `HoursCorrectionRequest.java` — Class: HoursCorrectionRequest (~108 tok)
- `WorkedHoursRow.java` — One row in the "by-site" worked hours report. (~892 tok)
- `WorkedHoursSummaryRow.java` — One row in the summary worked hours report, grouped by worker across all sites. (~131 tok)

## src/main/java/org/example/attendTrack/site/

- `GeoResolver.java` — Decides which site a scan belongs to, from its coordinates. (~1920 tok)
- `SiteCheckpoint.java` — Second endpoint — non-null only for LINE checkpoints. (~341 tok)
- `SiteCheckpointRepository.java` — Class: SiteCheckpointRepository (~217 tok)
- `SiteController.java` — RestController: SiteController (11 endpoints) (~892 tok)
- `SiteService.java` — Service: SiteService (~3025 tok)
- `SiteWorkerRepository.java` — Class: SiteWorkerRepository (~464 tok)

## src/main/java/org/example/attendTrack/site/dto/

- `CheckpointDto.java` — Second endpoint — non-null only for LINE checkpoints. (~260 tok)
- `SiteRequest.java` — Required on create, ignored on update (company managed via CompanyService). (~184 tok)
- `SiteResponse.java` — SiteResponse: from, summary (~368 tok)

## src/main/java/org/example/attendTrack/sync/

- `SyncController.java` — RestController: SyncController (2 endpoints) (~160 tok)
- `SyncService.java` — Service: SyncService (~750 tok)

## src/main/java/org/example/attendTrack/sync/dto/

- `SiteSyncResponse.java` — Second endpoint — non-null for LINE checkpoints only. (~231 tok)

## src/main/java/org/example/attendTrack/user/

- `FaceDescriptorRepository.java` — Class: FaceDescriptorRepository (~271 tok)
- `FaceDescriptorService.java` — Service: FaceDescriptorService (~881 tok)
- `Role.java` — Class: Role (~22 tok)
- `ShiftType.java` — Enum: DAY, SHIFT_24H — scheduling attribute (guards), NOT a permission (~120 tok)
- `User.java` — Entity: User; has role + shiftType (~640 tok)
- `UserService.java` — Service: UserService (~2240 tok)

## src/main/java/org/example/attendTrack/user/dto/

- `FaceDescriptorRequest.java` — Class: FaceDescriptorRequest (~84 tok)
- `UserRequest.java` — Class: UserRequest (~190 tok)
- `UserResponse.java` — UserResponse: CompanyRef, from, from (~292 tok)

## src/main/java/org/example/garant/

- `GarantApplication.java` — Spring Boot entry point with @EnableScheduling (~30 tok)

## src/main/java/org/example/garant/auth/

- `AuthController.java` — POST /api/auth/login, GET /api/auth/me (~60 tok)
- `AuthService.java` — Login logic + UserDetailsService implementation (~70 tok)
- `JwtTokenProvider.java` — JWT generate/validate/extract using jjwt 0.12.6 (~80 tok)

## src/main/java/org/example/garant/auth/dto/

- `AuthResponse.java` — Record: token, type, userId, name, email, role (~30 tok)
- `LoginRequest.java` — Record: email + password with validation (~20 tok)

## src/main/java/org/example/garant/common/exception/

- `ApiError.java` — Record: status, message, timestamp — standard error response (~25 tok)
- `GlobalExceptionHandler.java` — @RestControllerAdvice handles 400/401/403/500 (~70 tok)

## src/main/java/org/example/garant/config/

- `DataSourceConfig.java` — Creates DB if not exists (connects to postgres admin DB first), then returns DataSource (~80 tok)
- `JwtAuthenticationFilter.java` — OncePerRequestFilter that validates JWT per request (~70 tok)
- `SecurityConfig.java` — Spring Security 6 stateless JWT config, @EnableMethodSecurity (~90 tok)

## src/main/java/org/example/garant/user/

- `Role.java` — Enum: ADMIN, MANAGER, WORKER (~10 tok)
- `User.java` — JPA entity + UserDetails, UUID PK, Lombok @Builder (~100 tok)
- `UserRepository.java` — findByEmail, existsByEmail (~20 tok)

## src/main/resources/

- `application.yml` (~701 tok)

## src/main/resources/db/migration/

- `V1__init.sql` — Full schema: users, sites, site_managers, site_workers, face_descriptors, attendance, push_subscriptions, notifications (~200 tok)
- `V10__attendance_audit_metadata.sql` — V10: Explicit record origin + audit metadata. (~354 tok)
- `V11__session_projection.sql` — V11: Server-side normalization of check-in / check-out direction. (~346 tok)
- `V12__user_shift_type.sql` — V12: users.shift_type (DAY default) — 12/24h shift workers (guards) (~180 tok)
- `V13__attendance_site_resolution.sql` — The terminal is no longer trusted to decide WHICH SITE a scan belongs to. (~338 tok)
- `V14__attendance_gps_accuracy.sql` — The terminal has always known how good its GPS fix was — useGeoLocation reads `accuracy` from (~221 tok)
- `V2__clear_face_descriptors.sql` — V2: Clear all 128-dim face descriptors (face-api.js). (~43 tok)
- `V3__remove_manager_role.sql` — Migrate existing MANAGER users to ADMIN role (~82 tok)
- `V4__hours_corrections.sql` — SQL: tables: hours_corrections (~162 tok)
- `V5__site_checkpoints.sql` — Creates site_checkpoints table; migrates existing site lat/lng/radius as first checkpoint (~80 tok)
- `V5__site_checkpoints.sql` — SQL: tables: site_checkpoints (~174 tok)
- `V6__add_company_to_users.sql` (~14 tok)
- `V7__companies.sql` — SQL: tables: companies, company_sites, company_workers (~252 tok)
- `V8__line_checkpoints.sql` — V8: Add line/corridor checkpoint support (~117 tok)
- `V9__attendance_reconciliation.sql` — V9: Server-side reconciliation of the check-in / check-out state machine. (~371 tok)

## src/test/java/org/example/attendTrack/attendance/

- `AttendanceRepositoryQueryTest.java` — Runs every attendance query against a real persistence context. (~2847 tok)
- `AttendanceServiceNormalizationTest.java` — Wiring tests for sync + day re-projection. (~3395 tok)
- `AttendanceServiceSiteResolutionTest.java` — The terminal's site is a claim, not a fact — these tests pin the server's re-resolution of it. (~2802 tok)
- `AutoCheckoutSchedulerTest.java` — The nightly auto-checkout, which invents the hours nobody scanned — so what it declines to create (~1348 tok)
- `SessionProjectorTest.java` — Exhaustive tests for the pure session projection — the core of server-side normalization. (~1779 tok)

## src/test/java/org/example/attendTrack/report/

- `WorkedHoursReportTest.java` — Worked-hours pairing: the layer that turns a day's scans into sessions and hours. (~3174 tok)

## src/test/java/org/example/attendTrack/site/

- `GeoResolverTest.java` — Geometry and site-resolution tests. (~2562 tok)
