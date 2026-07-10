# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-07-10T10:47:59.324Z
> Files: 136 tracked | Anatomy hits: 0 | Misses: 0

## ./

- `.gitignore` — Git ignore rules (~220 tok)
- `CLAUDE.md` — OpenWolf entry point (~57 tok)
- `DEPLOY.md` — Деплой на AttendTrack — AWS EC2 (Amazon Linux) + Docker + HTTPS (~2098 tok)
- `docker-compose.prod.yml` — Docker Compose: 5 services (~375 tok)
- `docker-compose.yml` — Docker Compose services (~320 tok)
- `FACE_RECOGNITION_V2.md` — Face Recognition — Вариант 2: MediaPipe + MobileFaceNet ONNX (~1394 tok)
- `GUIDE_BG.md` — AttendTrack — Ръководство за потребителя (~2385 tok)
- `GUIDE_EN.md` — AttendTrack — User Guide (~2311 tok)
- `nginx.conf` (~313 tok)
- `PLAN.md` — PLAN: Two-Role Refactor — ADMIN + WORKER only (~1591 tok)
- `pom.xml` — Maven/Spring Boot 3.3.5 project config with all dependencies (~185 tok)
- `README.md` — Project documentation (~2597 tok)
- `ROADMAP.md` — AttendTrack — Roadmap (~647 tok)

## .claude/

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

- `page.tsx` — StatCard (~3901 tok)

## frontend/app/[locale]/(admin)/reports/

- `page.tsx` — fmtLocal (~9361 tok)

## frontend/app/[locale]/(admin)/sites/

- `page.tsx` — formatTime — renders table (~2170 tok)

## frontend/app/[locale]/(admin)/workers/

- `page.tsx` — ROLE_VARIANT — renders table (~2916 tok)

## frontend/app/[locale]/(auth)/login/

- `page.tsx` — LoginPage — renders form (~1233 tok)

## frontend/app/[locale]/(manager)/

- `layout.tsx` — VerifyLayout (~855 tok)

## frontend/app/[locale]/(manager)/verify/

- `page.tsx` — VerifyPage (~2182 tok)

## frontend/app/api/[...path]/

- `route.ts` — Next.js API route (~362 tok)

## frontend/components/

- `providers.tsx` — Providers (~296 tok)

## frontend/components/companies/

- `CompanyDialog.tsx` — Req — renders form, modal (~1198 tok)
- `CompanySiteModal.tsx` — CompanySiteModal — renders modal (~1236 tok)
- `CompanyWorkerModal.tsx` — CompanyWorkerModal — renders modal (~1450 tok)

## frontend/components/layout/

- `AdminSidebar.tsx` — AdminSidebar (~937 tok)
- `ThemeToggle.tsx` — ThemeToggle (~197 tok)

## frontend/components/sites/

- `MapPicker.tsx` — DEFAULT_CENTER (~1205 tok)
- `SiteAssignModal.tsx` — SiteAssignModal — renders modal (~2024 tok)
- `SiteDialog.tsx` — MapPicker — renders form, modal (~3560 tok)

## frontend/components/ui/

- `textarea.tsx` — Textarea (~203 tok)

## frontend/components/verify/

- `VerifyCamera.tsx` — drawFaceMesh (~4928 tok)

## frontend/components/workers/

- `FaceRegisterModal.tsx` — FaceRegisterModal — renders modal (~1849 tok)
- `WorkerDialog.tsx` — ROLES — renders form, modal (~2332 tok)
- `WorkerSiteModal.tsx` — WorkerSiteModal — renders modal (~2458 tok)

## frontend/hooks/

- `useAuth.ts` — Exports useAuth (~278 tok)
- `useCompanies.ts` — API routes: GET, DELETE, POST (6 endpoints) (~693 tok)
- `useDashboard.ts` — API routes: GET (2 endpoints) (~382 tok)
- `useFaceApi.ts` — useFaceApi — MediaPipe FaceLandmarker + MobileFaceNet ONNX (~2359 tok)
- `useGeoLocation.ts` — Exports GeoState, useGeoLocation (~435 tok)
- `useSites.ts` — API routes: GET, DELETE, POST (5 endpoints) (~587 tok)
- `useSiteSync.ts` — Exports SyncResult, SyncStatus, useSiteSync (~670 tok)
- `useWorkers.ts` — API routes: GET, DELETE, POST (4 endpoints) (~574 tok)

## frontend/lib/

- `auth.ts` — Exports TOKEN_KEY, getToken, setToken, removeToken + 5 more (~400 tok)
- `axios.ts` — Declares api (~267 tok)
- `db.ts` — Exports WorkerRecord, CheckpointInfo, SiteInfo, PendingAttendance, db (~629 tok)
- `errors.ts` — Known error codes from the backend ErrorCode enum (~302 tok)
- `faceAlignment.ts` — Face alignment: transforms a raw video frame into a normalized (~1112 tok)
- `faceMatcher.ts` — Cosine-similarity 1:N face matcher. (~613 tok)
- `geo.ts` — Exports haversineDistance, isWithinRadius, isWithinAnyCheckpoint (~273 tok)
- `prefetchModels.ts` — prefetchModels — silently warms the Service Worker cache with all face (~462 tok)

## frontend/messages/

- `bg.json` (~2447 tok)
- `en.json` (~2346 tok)

## frontend/scripts/

- `copy-wasm.js` — copy-wasm.js — runs automatically after `npm install` (postinstall). (~625 tok)

## frontend/types/

- `company.ts` — Exports CompanyResponse, CompanyRequest (~133 tok)
- `site.ts` — Exports CheckpointResponse, CheckpointRequest, SiteResponse, SiteRequest (~264 tok)
- `user.ts` — Exports Role, CompanyRef, UserResponse, UserRequest (~127 tok)

## src/main/java/org/example/ (legacy)

- `Main.java` — IntelliJ placeholder, unused (~50 tok)

## src/main/java/org/example/attendTrack/attendance/

- `AttendanceController.java` — RestController: AttendanceController (3 endpoints) (~321 tok)
- `AttendanceRepository.java` — Class: AttendanceRepository (~1470 tok)
- `AttendanceService.java` — Service: AttendanceService (~1614 tok)
- `AutoCheckoutScheduler.java` — Runs at 00:01 every day. For each worker who checked in yesterday at a site (~1189 tok)

## src/main/java/org/example/attendTrack/common/exception/

- `ErrorCode.java` — Class: ErrorCode (~146 tok)

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

## src/main/java/org/example/attendTrack/dashboard/

- `AbsenteeRow.java` — Class: AbsenteeRow (~39 tok)
- `ActivityEntry.java` — Class: ActivityEntry (~67 tok)
- `DashboardController.java` — RestController: DashboardController (3 endpoints) (~1642 tok)
- `DashboardExtended.java` — Class: DashboardExtended (~101 tok)
- `DayAttendance.java` — Class: DayAttendance (~36 tok)
- `SiteAttendance.java` — Class: SiteAttendance (~45 tok)

## src/main/java/org/example/attendTrack/notification/

- `NotificationService.java` — Notifies all admins and managers of the site about missing workers. (~1068 tok)

## src/main/java/org/example/attendTrack/report/

- `HoursCorrection.java` — Entity: HoursCorrection (~403 tok)
- `HoursCorrectionRepository.java` — Class: HoursCorrectionRepository (~296 tok)
- `ReportController.java` — RestController: ReportController (9 endpoints) (~1498 tok)
- `ReportService.java` — Service: ReportService (~6594 tok)

## src/main/java/org/example/attendTrack/report/dto/

- `HoursCorrectionRequest.java` — Class: HoursCorrectionRequest (~108 tok)
- `WorkedHoursRow.java` — One row in the "by-site" worked hours report. (~394 tok)
- `WorkedHoursSummaryRow.java` — One row in the summary worked hours report, grouped by worker across all sites. (~131 tok)

## src/main/java/org/example/attendTrack/site/

- `SiteCheckpoint.java` — Entity: SiteCheckpoint (~232 tok)
- `SiteCheckpointRepository.java` — Class: SiteCheckpointRepository (~210 tok)
- `SiteController.java` — RestController: SiteController (11 endpoints) (~892 tok)
- `SiteService.java` — Service: SiteService (~2901 tok)
- `SiteWorkerRepository.java` — Class: SiteWorkerRepository (~464 tok)

## src/main/java/org/example/attendTrack/site/dto/

- `CheckpointDto.java` — CheckpointDto: from (~131 tok)
- `SiteRequest.java` — Required on create, ignored on update (company managed via CompanyService). (~184 tok)
- `SiteResponse.java` — SiteResponse: from, summary (~368 tok)

## src/main/java/org/example/attendTrack/sync/

- `SyncController.java` — RestController: SyncController (2 endpoints) (~160 tok)
- `SyncService.java` — Service: SyncService (~727 tok)

## src/main/java/org/example/attendTrack/sync/dto/

- `SiteSyncResponse.java` — SiteSyncResponse: CheckpointInfo, SiteInfo (~179 tok)

## src/main/java/org/example/attendTrack/user/

- `FaceDescriptorRepository.java` — Class: FaceDescriptorRepository (~271 tok)
- `FaceDescriptorService.java` — Service: FaceDescriptorService (~881 tok)
- `Role.java` — Class: Role (~22 tok)
- `User.java` — Entity: User (~560 tok)
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

- `application.yml` (~434 tok)

## src/main/resources/db/migration/

- `V1__init.sql` — Full schema: users, sites, site_managers, site_workers, face_descriptors, attendance, push_subscriptions, notifications (~200 tok)
- `V2__clear_face_descriptors.sql` — V2: Clear all 128-dim face descriptors (face-api.js). (~43 tok)
- `V3__remove_manager_role.sql` — Migrate existing MANAGER users to ADMIN role (~82 tok)
- `V4__hours_corrections.sql` — SQL: tables: hours_corrections (~162 tok)
- `V5__site_checkpoints.sql` — Creates site_checkpoints table; migrates existing site lat/lng/radius as first checkpoint (~80 tok)
- `V5__site_checkpoints.sql` — SQL: tables: site_checkpoints (~174 tok)
- `V6__add_company_to_users.sql` (~14 tok)
- `V7__companies.sql` — SQL: tables: companies, company_sites, company_workers (~252 tok)
