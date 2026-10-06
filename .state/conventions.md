# Conventions

- **Tone:** Be extremely concise. Lead with answers, not reasoning.

## Project Topography

- Standalone Spring Boot 4.0.6 app, single Maven module. Java 25 (`pom.xml`, Docker `eclipse-temurin:25`).
- Package root `com.cluesday.scoreboard`, layered by type:
  - `config/` — security, password encoder + `ObjectMapper` bean, Thymeleaf SSE engine, admin seed (`DataInitializer`).
  - `controller/` — `AdminController` (`/quizmaster/**`, QM+ADMIN), `UserAdminController` (`/admin/**`, ADMIN), `PublicController` (`/`, `/live/{sessionNumber}`, SSE `/events`).
  - `service/` — `QuizService` (live quiz state), `SseService` (emitters, heartbeat), `UserService`, `JpaUserDetailsService`.
  - `entity/` + `repository/` — JPA: `AppUserEntity` (`app_user`), `QuizResultEntity` (`quiz_result`, leaderboard as JSON TEXT).
  - `model/` — immutable records: `QuizSession`, `Team`, `TeamResult`, `QuizSnapshot`.
  - `event/` — Spring `ApplicationEvent`s: `ScoreChangedEvent`, `QuizEndedEvent`.
- Views: Thymeleaf in `src/main/resources/templates/{admin,public,fragments,layout}`. HTMX 2 + htmx-sse from unpkg CDN.
- Persistence: PostgreSQL. Live quiz state is in-memory; only finished results and users are persisted.
- No tests, no CI. Deploy: Railway via `Dockerfile` (two-stage) + `railway.toml`.

## Naming Conventions

- JPA classes suffixed `Entity`; table names snake_case via `@Table(name = ...)`.
- Repositories `<Entity>Repository extends JpaRepository`; derived query names (`findAllByOrderByCompletedAtDesc`).
- Domain/view models are `record`s with no suffix.
- Events suffixed `Event`, extend `ApplicationEvent`, carry no payload.
- Templates: `admin/<page>.html`, `public/<page>.html`, fragments referenced as `"file :: selector"`.
- Roles stored as bare strings `"ADMIN"` / `"QM"`; Spring sees `ROLE_ADMIN` / `ROLE_QM`.
- Composite in-memory keys as strings: `"teamId:roundNum"`.

## Patterns & Idioms

- Constructor injection only, `private final` fields; no `@Autowired`. `@Value` only for env-backed config.
- Section dividers in large classes: `// ── Section ─────…`.
- Controllers: return template names; `redirect:` after POST; flash messages via `RedirectAttributes` (`error`/`success`).
- HTMX endpoints return fragments (`"admin/teams :: #team-list"`) or `@ResponseBody "ok"`; validation failures → `ResponseEntity.badRequest()`.
- Service methods that mutate scores publish `ScoreChangedEvent`; `SseService` listens. Score-change SSE push is currently disabled (see Field Notes).
- `@Transactional` on service write methods touching JPA.
- Styling: hand-rolled Tailwind-like utility classes + CSS variables in `layout/head.html` (no Tailwind build). Dark theme, Sora + DM Mono fonts.
- Code style: Spring Java Format (tabs, Spring brace/`catch` on new line).

## Tooling & Commands

- Run: `./mvnw spring-boot:run` (Windows: `mvnw.cmd`). Needs `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD`.
- Local DB: architect points `PG*` vars at the **Railway Postgres** instance.
- Build: `./mvnw package` (Docker uses `-DskipTests`).
- Format: `./mvnw spring-javaformat:apply` before committing. Not enforced in build.
- Admin seed: `ADMIN_USER` / `ADMIN_PASS` (defaults `admin`/`changeme`), only when `app_user` is empty.
- Tests: none. Verification is **manual** (run app, exercise flows). Do not add a test suite unless asked.
- Commits: conventional style, lowercase type: `feat:`, `fix:`, `refactor:`, `chore:`, `docs:`.

## Field Notes

- `ddl-auto: update` + local runs against Railway DB → entity changes alter the shared/prod schema immediately. Treat schema changes as deploy-level actions.
- `README.md` is stale: mentions `/quizSetup`, uuid live URLs, Tailwind, `RoundType`, joker, fully in-memory, Java 21. Code is the source of truth.
- Live URL is keyed by `sessionNumber`, not uuid. Only one active session at a time (`QuizService.activeSession`).
- `QuizSession.MAX_ROUNDS = 6` is fixed; scoring is per-round totals (not per-question).
- Tables 1–25 are "standard"; table 25 gets custom name `∞`. Extra teams can have any table number or a custom name.
- In-memory quiz state is lost on restart; only `endQuiz()` persists to `quiz_result`.
- `SseService.onScoreChanged` has `@EventListener` commented out pending prod verification of `ThymeleafConfig` `sseTemplateEngine` fix. Public board relies on page reload until re-enabled.
- CSRF disabled; auth is HTTP Basic. `/admin/**` ADMIN-only, `/quizmaster/**` ADMIN or QM.
- `ObjectMapper` bean is defined manually in `PasswordEncoderConfig` (Boot 4 / Jackson 3 default doesn't cover `com.fasterxml` 2.x).
- `.mvn/jvm.config` sets `--enable-native-access=ALL-UNNAMED`.
- Railway IaC: `.railway/railway.ts` (npm `railway` pkg) is authoritative on `railway config apply` — anything undeclared (vars, GitHub source) gets removed. Always `railway config plan` first; expect 0 destroys. `railway.toml` (CaC) is deprecated, stops working 2026-12-01.
- Railway CLI is at `~/scoop/shims` (not on Git Bash PATH); JDK 25 at `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`.
- OGNL is not on the classpath: any programmatic Thymeleaf engine must be `SpringTemplateEngine` (SpEL), not plain `TemplateEngine`.
- Template smoke test without DB: render templates via `SpringTemplateEngine` + `ClassLoaderTemplateResolver` on `target/classes` with an in-memory `QuizService(e -> {}, null, null)`.
