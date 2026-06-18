# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-06-18T13:47:25.329Z
> Files: 43 tracked | Anatomy hits: 0 | Misses: 0

## ./

- `.gitignore` — Git ignore rules (~152 tok)
- `CLAUDE.md` — OpenWolf entry point (~57 tok)
- `DEPLOY.md` — Деплой на AWS EC2 с HTTPS (без домейн) (~1404 tok)
- `docker-compose.prod.yml` — Docker Compose: 5 services (~352 tok)
- `docker-compose.yml` — Docker Compose services (~320 tok)
- `nginx.conf` (~302 tok)
- `pom.xml` — Maven/Spring Boot 3.3.5 project config with all dependencies (~185 tok)
- `README.md` — Project documentation (~2597 tok)

## .claude/

- `settings.json` — Claude Code settings (~441 tok)

## .claude/rules/

- `openwolf.md` — OpenWolf rules (~313 tok)

## frontend/

- `Dockerfile` — Docker container definition (~207 tok)
- `next.config.ts` — Declares withNextIntl (~305 tok)
- `package.json` — Node.js package manifest (~425 tok)

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

## frontend/app/[locale]/(manager)/verify/

- `page.tsx` — VerifyPage (~2182 tok)

## frontend/app/api/[...path]/

- `route.ts` — Next.js API route (~354 tok)

## frontend/components/layout/

- `ThemeToggle.tsx` — ThemeToggle (~197 tok)

## frontend/components/sites/

- `MapPicker.tsx` — DEFAULT_CENTER (~756 tok)

## frontend/hooks/

- `useFaceApi.ts` — Exports FaceApiState, useFaceApi (~556 tok)
- `useSites.ts` — API routes: GET, DELETE, POST (7 endpoints) (~718 tok)

## frontend/lib/

- `axios.ts` — Declares api (~267 tok)

## src/main/java/org/example/ (legacy)

- `Main.java` — IntelliJ placeholder, unused (~50 tok)

## src/main/java/org/example/attendTrack/config/

- `CorsConfig.java` — ", config); (~342 tok)

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
