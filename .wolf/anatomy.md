# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-06-17
> Files: 20 tracked

## ./

- `.gitignore` — Git ignore rules (~133 tok)
- `CLAUDE.md` — OpenWolf entry point (~57 tok)
- `pom.xml` — Maven/Spring Boot 3.3.5 project config with all dependencies (~185 tok)

## .claude/

- `settings.json` — Claude Code settings (~441 tok)

## .claude/rules/

- `openwolf.md` — OpenWolf rules (~313 tok)

## src/main/java/org/example/garant/

- `GarantApplication.java` — Spring Boot entry point with @EnableScheduling (~30 tok)

## src/main/java/org/example/garant/auth/

- `AuthController.java` — POST /api/auth/login, GET /api/auth/me (~60 tok)
- `AuthService.java` — Login logic + UserDetailsService implementation (~70 tok)
- `JwtTokenProvider.java` — JWT generate/validate/extract using jjwt 0.12.6 (~80 tok)

## src/main/java/org/example/garant/auth/dto/

- `LoginRequest.java` — Record: email + password with validation (~20 tok)
- `AuthResponse.java` — Record: token, type, userId, name, email, role (~30 tok)

## src/main/java/org/example/garant/config/

- `DataSourceConfig.java` — Creates DB if not exists (connects to postgres admin DB first), then returns DataSource (~80 tok)
- `SecurityConfig.java` — Spring Security 6 stateless JWT config, @EnableMethodSecurity (~90 tok)
- `JwtAuthenticationFilter.java` — OncePerRequestFilter that validates JWT per request (~70 tok)

## src/main/java/org/example/garant/common/exception/

- `ApiError.java` — Record: status, message, timestamp — standard error response (~25 tok)
- `GlobalExceptionHandler.java` — @RestControllerAdvice handles 400/401/403/500 (~70 tok)

## src/main/java/org/example/garant/user/

- `Role.java` — Enum: ADMIN, MANAGER, WORKER (~10 tok)
- `User.java` — JPA entity + UserDetails, UUID PK, Lombok @Builder (~100 tok)
- `UserRepository.java` — findByEmail, existsByEmail (~20 tok)

## src/main/java/org/example/ (legacy)

- `Main.java` — IntelliJ placeholder, unused (~50 tok)

## src/main/resources/

- `application.yml` — DB, JPA, Flyway, JWT, Mail config — all env-variable driven (~80 tok)

## frontend/app/[locale]/(manager)/verify/

- `page.tsx` — F5 orchestration: site select → sync → VerifyCamera; writes db.pending on onRecord, sonner toast (~180 tok)

## src/main/resources/db/migration/

- `V1__init.sql` — Full schema: users, sites, site_managers, site_workers, face_descriptors, attendance, push_subscriptions, notifications (~200 tok)
