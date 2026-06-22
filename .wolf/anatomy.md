# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-06-22T05:10:02.646Z
> Files: 59 tracked | Anatomy hits: 0 | Misses: 0

## ./

- `.gitignore` — Git ignore rules (~220 tok)
- `CLAUDE.md` — OpenWolf entry point (~57 tok)
- `DEPLOY.md` — Деплой на AWS EC2 с HTTPS (без домейн) (~1404 tok)
- `docker-compose.prod.yml` — Docker Compose: 5 services (~352 tok)
- `docker-compose.yml` — Docker Compose services (~320 tok)
- `FACE_RECOGNITION_V2.md` — Face Recognition — Вариант 2: MediaPipe + MobileFaceNet ONNX (~1394 tok)
- `nginx.conf` (~302 tok)
- `pom.xml` — Maven/Spring Boot 3.3.5 project config with all dependencies (~185 tok)
- `README.md` — Project documentation (~2597 tok)

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

## frontend/app/[locale]/

- `page.tsx` — LocalePage (~61 tok)

## frontend/app/[locale]/(admin)/reports/

- `page.tsx` — today — renders table (~3486 tok)

## frontend/app/[locale]/(admin)/workers/

- `page.tsx` — ROLE_VARIANT — renders table (~2517 tok)

## frontend/app/[locale]/(auth)/login/

- `page.tsx` — LoginPage — renders form (~1233 tok)

## frontend/app/[locale]/(manager)/

- `layout.tsx` — ManagerLayout (~596 tok)

## frontend/app/[locale]/(manager)/verify/

- `page.tsx` — VerifyPage (~2182 tok)

## frontend/app/api/[...path]/

- `route.ts` — Next.js API route (~354 tok)

## frontend/components/layout/

- `ThemeToggle.tsx` — ThemeToggle (~197 tok)

## frontend/components/sites/

- `MapPicker.tsx` — DEFAULT_CENTER (~756 tok)

## frontend/components/verify/

- `VerifyCamera.tsx` — drawFaceMesh (~4977 tok)

## frontend/components/workers/

- `FaceRegisterModal.tsx` — FaceRegisterModal — renders modal (~1759 tok)

## frontend/hooks/

- `useFaceApi.ts` — useFaceApi — MediaPipe FaceLandmarker + MobileFaceNet ONNX (~2359 tok)
- `useSites.ts` — API routes: GET, DELETE, POST (7 endpoints) (~718 tok)
- `useSiteSync.ts` — Exports SyncResult, SyncStatus, useSiteSync (~603 tok)

## frontend/lib/

- `axios.ts` — Declares api (~267 tok)
- `db.ts` — Exports WorkerRecord, SiteInfo, PendingAttendance, db (~495 tok)
- `faceAlignment.ts` — Face alignment: transforms a raw video frame into a normalized (~1112 tok)
- `faceMatcher.ts` — Cosine-similarity 1:N face matcher. (~613 tok)
- `prefetchModels.ts` — prefetchModels — silently warms the Service Worker cache with all face (~462 tok)

## frontend/messages/

- `bg.json` (~1443 tok)
- `en.json` (~1375 tok)

## frontend/scripts/

- `copy-wasm.js` — copy-wasm.js — runs automatically after `npm install` (postinstall). (~625 tok)

## src/main/java/org/example/ (legacy)

- `Main.java` — IntelliJ placeholder, unused (~50 tok)

## src/main/java/org/example/attendTrack/config/

- `CorsConfig.java` — ", config); (~342 tok)

## src/main/java/org/example/attendTrack/user/

- `FaceDescriptorService.java` — Service: FaceDescriptorService (~888 tok)
- `UserService.java` — Service: UserService (~964 tok)

## src/main/java/org/example/attendTrack/user/dto/

- `FaceDescriptorRequest.java` — Class: FaceDescriptorRequest (~84 tok)

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
