# Cluesday Scoreboard

Real-time scoring app for Cluesday quiz nights: quizmasters enter round scores on a phone or laptop, and a public scoreboard updates live for players.

Built with Spring Boot 4 · Java 25 · Thymeleaf · HTMX · SSE · PostgreSQL.

---

## Quick Start (local)

Requires **Java 25** and a reachable **PostgreSQL** database.

```bash
export PGHOST=localhost PGPORT=5432 PGDATABASE=cluesday PGUSER=postgres PGPASSWORD=...
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

| Page | URL | Access |
|------|-----|--------|
| Home | `http://localhost:8080/` | Public; links to the active quiz, if any |
| Quizmaster | `http://localhost:8080/quizmaster` | `QM` or `ADMIN` (not linked publicly) |
| User admin | `http://localhost:8080/admin` | `ADMIN` only |
| Live scoreboard | `http://localhost:8080/live/{cluesday#}` | Public |

### Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `PGHOST` / `PGPORT` / `PGDATABASE` | `localhost` / `5432` / `cluesday` | Database location |
| `PGUSER` / `PGPASSWORD` | `postgres` / *(empty)* | Database credentials |
| `ADMIN_USER` / `ADMIN_PASS` | `admin` / `changeme` | Seed admin account |
| `PORT` | `8080` | HTTP port (set automatically on Railway) |

The seed admin is created **only when the `app_user` table is empty**. Changing `ADMIN_USER`/`ADMIN_PASS` later has no effect, so manage accounts from `/admin` after first boot.

> **Schema:** Hibernate runs with `ddl-auto: update`. If your local `PG*` vars point at the production database, entity changes alter the production schema as soon as the app starts.

---

## Usage Guide

### 1 · Create a quiz

1. Go to `/quizmaster` and log in.
2. Enter the **Cluesday #**, **date** and **quizmaster** name, then click **Next → Teams**.

Only one quiz can be active at a time. Every quiz has 6 rounds.

### 2 · Register teams

- Tick the playing tables in the 1–25 grid and click **Apply Selection**. Table 25 is named `∞`.
- Use **Add Extra Team** for any other table number and/or a custom team name.
- Continue to the dashboard.

### 3 · Score during the quiz

- Pick a round from the **1–6** tabs (or **All**) and type each team's **round total**. Scores save automatically; half and quarter points are allowed.
- Click **Mark done** to publish a round. Only rounds marked done are shown on, and counted by, the public scoreboard.
- **+ Team** adds a late team mid-quiz. It gets 0 for rounds already marked done.
- Use ✎ on a team to rename or delete it.
- **Multiple devices** can score the same quiz. Each open dashboard re-syncs every 10 s. If someone else changed a score after your page loaded, your save is rejected and the box shows their value, so stale pages can't overwrite newer scores.

### 4 · Share the live scoreboard

- Click **Copy link** in the dashboard header (`/live/{cluesday#}`), then share it or put it on the projector. No login needed.
- The board updates over SSE whenever scores, teams or completed rounds change.

### 5 · End the quiz

- **End Quiz → Save & End** saves the final leaderboard to history. The live board then shows "Quiz has ended".
- **Discard** throws away the session without saving it.
- Past quizzes are at `/quizmaster/history`.

### Managing users (admin only)

`/admin` (also linked as **Users** on the setup page) lets admins create `QM` or `ADMIN` accounts and delete them.

---

## Deploying to Railway

The app deploys from GitHub (`main`) using the `Dockerfile`, which does a two-stage build: a Maven build, then a slim JRE 25 image. It needs a **Postgres** service in the same Railway project.

Infrastructure is declared in **`.railway/railway.ts`**. It wires the service's `PG*` variables to the `Postgres` service and leaves `ADMIN_USER`/`ADMIN_PASS` as they are set in the dashboard.

```bash
npm install                 # installs the `railway` IaC package
railway config plan         # always review first — expect 0 destroys
railway config apply
```

`railway config apply` is authoritative: it **removes** anything not declared in `railway.ts`, including variables and the GitHub source. `railway.toml` is the legacy config-as-code file. It is deprecated and stops working on 2026-12-01.

### Persistence

| Data | Stored in | Survives restart? |
|------|-----------|-------------------|
| Users | Postgres `app_user` | Yes |
| Finished quiz results | Postgres `quiz_result` (leaderboard as JSON) | Yes |
| The active quiz (teams, scores, completed rounds) | Server memory | **No** |

A restart or redeploy during a quiz loses that quiz's scores.

---

## Architecture

```
Public
GET  /                                   Home page
GET  /live/{sessionNumber}               Live scoreboard
GET  /live/{sessionNumber}/events        SSE stream (score-update / quiz-ended)

Quizmaster  (ROLE_QM or ROLE_ADMIN)
GET  /quizmaster                         Setup page
POST /quizmaster/setup | reset           Create / reset session
GET  /quizmaster/teams                   Team registration
POST /quizmaster/teams/{set-tables,add,add-during-quiz,rename,delete}
GET  /quizmaster/dashboard               Scoring dashboard
GET  /quizmaster/state                   JSON snapshot polled by open dashboards
POST /quizmaster/round-score             Set a round total (409 if changed elsewhere)
POST /quizmaster/round/{n}/complete      Publish / unpublish a round
POST /quizmaster/end | discard           Save to history / drop session
GET  /quizmaster/history[/{uuid}]        Past results

Admin  (ROLE_ADMIN)
GET  /admin                              User list
POST /admin/users                        Create user
POST /admin/users/{id}/delete            Delete user
```

**How it fits together**

- `QuizService` holds the active quiz in memory: the `QuizSession`, its teams, and round scores keyed `"teamId:round"`.
- Changes to scores publish a `ScoreChangedEvent`. `SseService` re-renders `fragments/scoreboard-table` and pushes it to every connected board, and sends a heartbeat comment every 20 s to keep proxies from closing the connection.
- `endQuiz()` writes the full leaderboard to `quiz_result` and publishes `QuizEndedEvent`, which closes all SSE streams.

**Security**

- HTTP Basic auth. Users are stored in Postgres with BCrypt-hashed passwords.
- CSRF is disabled. Admin operations sit behind Basic auth, and public routes are read-only.
- The quizmaster path is not linked from the public site. Set strong `ADMIN_*` credentials before the first deploy.

---

## Development

- Format before committing: `./mvnw spring-javaformat:apply`
- There is no automated test suite. Verify changes by running the app and exercising the flows above.
