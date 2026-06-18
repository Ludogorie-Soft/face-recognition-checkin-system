# Memory

> Chronological action log. Hooks and AI append to this file automatically.
> Old sessions are consolidated by the daemon weekly.

| 17:00 | Responsive design audit — all pages/components reviewed | all | complete | ~4000 |
| 17:05 | Mobile nav: added hamburger + drawer to AdminLayout, onClose prop to AdminSidebar | AdminSidebar.tsx, layout.tsx | complete | ~200 |
| 17:06 | Table overflow: overflow-x-auto + min-w on sites, workers, reports tables | sites/page.tsx, workers/page.tsx, reports/page.tsx | complete | ~60 |
| 17:07 | Reports: removed double p-6, made site selector full-width on mobile | reports/page.tsx | complete | ~30 |
| 17:08 | Page headers: text-xl on mobile, button label hidden on mobile (sm:inline) | sites/page.tsx, workers/page.tsx | complete | ~30 |

| 2026-06-17 | F5 complete: wrote verify/page.tsx — site selection → sync → VerifyCamera orchestration | app/[locale]/(manager)/verify/page.tsx | success | ~300 tok |
| 2026-06-17 | F6 complete: wrote reports/page.tsx — attendance + missing tabs, filters, table, Excel export | app/[locale]/(admin)/reports/page.tsx | success | ~350 tok |
| 2026-06-17 | F7 complete: push notifications — worker/index.ts, usePushNotifications, NotificationBell | worker/index.ts, hooks/usePushNotifications.ts, components/layout/NotificationBell.tsx | success | ~300 tok |

| Time  | Description                          | File(s)                                                                                                                                       | Outcome   | ~Tokens |
|-------|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|-----------|---------|
| 09:00 | Project context established          | cerebrum.md                                                                                                                                   | completed | 200     |
| 09:10 | Phase 1 — Spring Boot foundation     | pom.xml, application.yml, V1__init.sql, GarantApplication.java, Role.java, User.java, UserRepository.java, JwtTokenProvider.java, JwtAuthenticationFilter.java, SecurityConfig.java, AuthService.java, AuthController.java, ApiError.java, GlobalExceptionHandler.java | completed | 1800    |
| 2026-06-17 | clickMapToSelect translation key added to en.json + bg.json | messages/en.json, messages/bg.json | success | ~50 tok |
| 2026-06-17 | Dashboard page implemented — StatCard grid, /api/dashboard/stats endpoint, useDashboard hook | dashboard/DashboardController.java, DashboardStats.java, hooks/useDashboard.ts, app/.../dashboard/page.tsx | success | ~300 tok |

## Session: 2026-06-18 11:15

| Time | Action | File(s) | Outcome | ~Tokens |
|------|--------|---------|---------|--------|
| 11:26 | Edited README.md | expanded (+37 lines) | ~187 |
| 11:26 | Session end: 1 writes across 1 files (README.md) | 1 reads | ~201 tok |
| 11:28 | Edited README.md | expanded (+8 lines) | ~163 |
| 11:29 | Session end: 2 writes across 1 files (README.md) | 1 reads | ~376 tok |
| 11:31 | Edited src/main/java/org/example/attendTrack/config/CorsConfig.java | added 1 condition(s) | ~101 |
| 11:31 | Edited src/main/resources/application.yml | 2→2 lines | ~15 |
| 11:31 | Edited README.md | 3→8 lines | ~100 |
| 11:31 | Session end: 5 writes across 3 files (README.md, CorsConfig.java, application.yml) | 3 reads | ~686 tok |
| 11:34 | Session end: 5 writes across 3 files (README.md, CorsConfig.java, application.yml) | 6 reads | ~686 tok |
| 11:36 | Session end: 5 writes across 3 files (README.md, CorsConfig.java, application.yml) | 16 reads | ~686 tok |
| 11:37 | Edited frontend/next.config.ts | added nullish coalescing | ~79 |
| 11:37 | Edited frontend/lib/axios.ts | 4→4 lines | ~28 |
| 11:37 | Session end: 7 writes across 5 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 17 reads | ~793 tok |
| 11:38 | Edited frontend/next.config.ts | reduced (-8 lines) | ~28 |
| 11:39 | Created frontend/app/api/[...path]/route.ts | — | ~250 |
| 11:39 | Session end: 9 writes across 6 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 18 reads | ~1071 tok |
| 11:41 | Edited frontend/package.json | inline fix | ~10 |
| 11:41 | Session end: 10 writes across 7 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 19 reads | ~1081 tok |
| 11:42 | Edited frontend/package.json | inline fix | ~6 |
| 11:42 | Edited frontend/package.json | inline fix | ~8 |
| 11:43 | Session end: 12 writes across 7 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 19 reads | ~1095 tok |
| 11:49 | Session end: 12 writes across 7 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 21 reads | ~1095 tok |
| 11:59 | Created frontend/app/[locale]/page.tsx | — | ~61 |
| 12:00 | Created frontend/app/[locale]/(auth)/login/page.tsx | — | ~942 |
| 12:00 | Session end: 14 writes across 8 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 21 reads | ~2098 tok |
| 12:01 | Session end: 14 writes across 8 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 21 reads | ~2098 tok |
| 12:06 | Edited frontend/app/api/[...path]/route.ts | modified arrayBuffer() | ~84 |
| 12:06 | Session end: 15 writes across 8 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 27 reads | ~2432 tok |
| 12:08 | Edited frontend/next.config.ts | added 1 condition(s) | ~88 |
| 12:08 | Session end: 16 writes across 8 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 27 reads | ~2689 tok |
| 12:09 | Session end: 16 writes across 8 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 27 reads | ~2689 tok |
| 12:12 | Session end: 16 writes across 8 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 27 reads | ~2689 tok |
| 12:16 | Edited frontend/hooks/useFaceApi.ts | 10→13 lines | ~130 |
| 12:16 | Session end: 17 writes across 9 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 28 reads | ~2819 tok |
| 12:17 | Session end: 17 writes across 9 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 28 reads | ~2819 tok |
| 12:17 | Session end: 17 writes across 9 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 28 reads | ~2819 tok |
| 12:20 | Edited frontend/app/[locale]/(admin)/workers/page.tsx | 23→25 lines | ~348 |
| 12:20 | Session end: 18 writes across 9 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 30 reads | ~3167 tok |
| 12:26 | Edited frontend/app/globals.css | CSS: --popover, --popover-foreground | ~56 |
| 12:26 | Edited frontend/app/globals.css | CSS: --popover, --popover-foreground | ~64 |
| 12:26 | Edited frontend/app/globals.css | CSS: --color-popover, --color-popover-foreground | ~66 |
| 12:26 | Session end: 21 writes across 10 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 34 reads | ~3353 tok |
| 12:27 | Edited frontend/components/sites/MapPicker.tsx | 16→16 lines | ~134 |
| 12:28 | Edited frontend/components/sites/MapPicker.tsx | added optional chaining | ~174 |
| 12:28 | Edited frontend/components/sites/MapPicker.tsx | modified Recenter() | ~71 |
| 12:28 | Edited frontend/components/sites/MapPicker.tsx | 12→12 lines | ~117 |
| 12:28 | Session end: 25 writes across 11 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 35 reads | ~3849 tok |
| 12:29 | Edited frontend/components/sites/MapPicker.tsx | 13 → 11 | ~5 |
| 12:29 | Session end: 26 writes across 11 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 35 reads | ~3854 tok |
| 12:34 | Edited README.md | 22→17 lines | ~158 |
| 12:34 | Session end: 27 writes across 11 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 35 reads | ~6222 tok |
| 12:34 | Edited frontend/app/[locale]/(auth)/login/page.tsx | inline fix | ~20 |
| 12:34 | Edited frontend/app/[locale]/(auth)/login/page.tsx | 1→2 lines | ~30 |
| 12:35 | Edited frontend/app/[locale]/(auth)/login/page.tsx | CSS: hover | ~268 |
| 12:35 | Session end: 30 writes across 11 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 35 reads | ~7482 tok |
| 12:37 | Edited frontend/components/layout/ThemeToggle.tsx | "p-2 rounded-lg text-muted" → "p-2 rounded-lg text-foreg" | ~31 |
| 12:37 | Session end: 31 writes across 12 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 36 reads | ~7513 tok |
| 12:38 | Edited frontend/app/[locale]/(auth)/login/page.tsx | 7→10 lines | ~94 |
| 12:39 | Edited frontend/app/[locale]/(auth)/login/page.tsx | 2→4 lines | ~42 |
| 12:39 | Session end: 33 writes across 12 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 36 reads | ~7649 tok |
| 12:40 | Edited frontend/app/[locale]/(auth)/login/page.tsx | added optional chaining | ~86 |
| 12:40 | Session end: 34 writes across 12 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 36 reads | ~7954 tok |
| 12:43 | Edited frontend/app/api/[...path]/route.ts | added error handling | ~256 |
| 12:43 | Session end: 35 writes across 12 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 36 reads | ~8216 tok |
| 12:46 | Edited frontend/app/api/[...path]/route.ts | 2→4 lines | ~36 |
| 12:46 | Session end: 36 writes across 12 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 38 reads | ~8252 tok |
| 12:48 | Session end: 36 writes across 12 files (README.md, CorsConfig.java, application.yml, next.config.ts, axios.ts) | 38 reads | ~8252 tok |

## Session: 2026-06-18 12:55

| Time | Action | File(s) | Outcome | ~Tokens |
|------|--------|---------|---------|--------|
| 13:03 | Edited src/main/resources/application.yml | 86400000 → 604800000 | ~12 |
| 13:03 | Edited frontend/next.config.ts | expanded (+10 lines) | ~137 |
| 13:03 | Edited frontend/app/[locale]/(manager)/verify/page.tsx | 13→13 lines | ~154 |
| 13:03 | Edited frontend/app/[locale]/(manager)/verify/page.tsx | added optional chaining | ~268 |
| 13:04 | Edited frontend/app/[locale]/(manager)/verify/page.tsx | expanded (+11 lines) | ~181 |
| 13:04 | Edited frontend/app/[locale]/(manager)/verify/page.tsx | added 1 import(s) | ~30 |
| 13:04 | Session end: 6 writes across 3 files (application.yml, next.config.ts, page.tsx) | 10 reads | ~3313 tok |
| 13:06 | Session end: 6 writes across 3 files (application.yml, next.config.ts, page.tsx) | 10 reads | ~3313 tok |
| 13:16 | Edited frontend/app/[locale]/(manager)/verify/page.tsx | modified if() | ~199 |
| 13:16 | Session end: 7 writes across 3 files (application.yml, next.config.ts, page.tsx) | 13 reads | ~3512 tok |
| 13:20 | Session end: 7 writes across 3 files (application.yml, next.config.ts, page.tsx) | 13 reads | ~3512 tok |
| 13:22 | Session end: 7 writes across 3 files (application.yml, next.config.ts, page.tsx) | 13 reads | ~3512 tok |
| 13:23 | Edited frontend/next.config.ts | 3→4 lines | ~34 |
| 13:23 | Created frontend/Dockerfile | — | ~206 |
| 13:23 | Created docker-compose.yml | — | ~320 |
| 13:23 | Created docker-compose.prod.yml | — | ~352 |
| 13:23 | Created nginx.conf | — | ~302 |
| 13:24 | Edited .gitignore | 2→6 lines | ~18 |
| 13:24 | Session end: 13 writes across 8 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 17 reads | ~5212 tok |
| 13:25 | Edited README.md | expanded (+51 lines) | ~428 |
| 13:25 | Session end: 14 writes across 9 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 18 reads | ~7870 tok |
| 13:26 | Session end: 14 writes across 9 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 18 reads | ~7870 tok |
| 13:27 | Session end: 14 writes across 9 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 18 reads | ~7870 tok |
| 13:28 | Session end: 14 writes across 9 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 18 reads | ~7870 tok |
| 13:30 | Created DEPLOY.md | — | ~1498 |
| 13:30 | Session end: 15 writes across 10 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 18 reads | ~9475 tok |
| 13:31 | Session end: 15 writes across 10 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 18 reads | ~9475 tok |
| 13:31 | Edited frontend/Dockerfile | 2→2 lines | ~10 |
| 13:31 | Session end: 16 writes across 10 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 19 reads | ~9692 tok |
| 16:21 | Session end: 16 writes across 10 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 20 reads | ~9692 tok |
| 16:22 | Session end: 16 writes across 10 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 20 reads | ~9692 tok |
| 16:24 | Edited frontend/app/[locale]/(admin)/reports/page.tsx | CSS: dark, dark | ~162 |
| 16:24 | Session end: 17 writes across 10 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 20 reads | ~9854 tok |
| 16:33 | Edited frontend/hooks/useSites.ts | modified useAssignWorker() | ~200 |
| 16:33 | Session end: 18 writes across 11 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 23 reads | ~10054 tok |
| 16:47 | Edited frontend/hooks/useSites.ts | modified useAssignManager() | ~347 |
| 16:47 | Session end: 19 writes across 11 files (application.yml, next.config.ts, page.tsx, Dockerfile, docker-compose.yml) | 24 reads | ~11129 tok |
