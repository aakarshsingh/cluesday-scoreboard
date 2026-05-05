# Cluesday Scoreboard

Real-time quiz scoring app for Cluesday quiz nights.  
Built with Spring Boot · Thymeleaf · HTMX · Tailwind CSS · SSE.

---

## Quick Start (local)

```bash
# Requires Java 21+ and Maven (or use the wrapper)
./mvnw spring-boot:run
```

- **Home page:** `http://localhost:8080/` — public, shows active quiz link if any
- **Admin panel:** `http://localhost:8080/quizSetup` — requires credentials (not linked publicly)
- **Live scoreboard:** `http://localhost:8080/live/{uuid}` — public, real-time

**Default dev credentials** (override with env vars before going live):

| Variable      | Default      |
|---------------|--------------|
| `ADMIN_USER`  | `admin`      |
| `ADMIN_PASS`  | `changeme`   |

> **Security:** Always set strong credentials via environment variables before deploying.

---

## Usage Guide

### 1 · Create a quiz

1. Go to `/quizSetup` and log in.
2. Enter **Cluesday #**, **Quizmaster name**, and **date**.
3. Set **Rounds** (default 6) and **default pts per question** (default 1).
4. Optionally expand **Configure rounds** to set question counts and types per round:
   - **Normal** — checkbox + ¼/½/1/1½/2 preset buttons
   - **Speed (−1/0/+1)** — three-button scoring for buzzer rounds
   - **Connects** — free-form number input for Long Connects style rounds
5. Click **Create Quiz**.

### 2 · Register tables

- Check the tables that are playing (1–25 grid).
- Click **Apply Selection** — each selected table number becomes a team.
- Add extra tables (odd numbers, visiting groups) via the **Add Extra Table** input.
- Click **Start Quiz** when ready.

### 3 · Score during the quiz

- Use the **round tabs** along the top of the dashboard.
- For **Normal** rounds: tick the checkbox for a full-point correct answer, or press a preset button (¼, ½, 1, 1½, 2) for partial credit.
- For **Speed** rounds: press −1, 0, or +1 per question.
- For **Connects** rounds: type a score directly into the number input (debounced, saves automatically).
- Toggle **★** to double a team's round total (Joker).
- Tick **Mark round complete** when all scores are entered — this publishes the round to the live scoreboard.

### 4 · Share the live scoreboard

- Copy the public URL from the dashboard header.
- Share it via WhatsApp, Slack, or the projector — no login required.
- The board updates live via SSE as rounds are marked complete.

### 5 · End the quiz

- Click **End Quiz** — scores are saved to history, the live board shows "Quiz Ended".
- View past results at `/quizSetup/history`.

---

## Deploying to Railway

### Prerequisites
- [Railway account](https://railway.app)
- [Railway CLI](https://docs.railway.app/develop/cli) (optional)

### Option A — GitHub deploy (recommended)

1. Push this repo to GitHub.
2. In Railway: **New Project → Deploy from GitHub repo** → select this repo.
3. Railway detects the `Dockerfile` and builds automatically.
4. Under **Variables**, set:
   ```
   ADMIN_USER=<your-username>
   ADMIN_PASS=<your-strong-password>
   ```
5. Railway assigns a public URL. Share `/live/{uuid}` with your audience.

### Option B — CLI deploy

```bash
railway login
railway init
railway up
railway variables set ADMIN_USER=<user> ADMIN_PASS=<pass>
```

### Notes

- Scores live **in memory** — they are lost on container restart.
- `PORT` is set automatically by Railway; no manual config needed.
- The Dockerfile uses a two-stage build (Maven build → slim JRE image).

---

## Architecture

```
GET  /                    Public  →  Home page (shows active quiz link)
GET  /live/{uuid}         Public  →  Live scoreboard (SSE-driven)
GET  /live/{uuid}/events  Public  →  SSE stream of score fragments

GET  /quizSetup/**        Auth    →  Admin controller (HTTP Basic)
POST /quizSetup/score     Auth    →  Set a question score
POST /quizSetup/joker     Auth    →  Toggle joker for a team/round
POST /quizSetup/round/*/complete  →  Mark round visible on scoreboard
```

**Data model**
- `QuizSession` — session metadata (number, date, QM name, round count, default pts)
- `Team(id, tableNumber)` — identified by table number only, no custom names
- `RoundType` — `NORMAL`, `SPEED`, or `CONNECTS`; configured per round at setup
- All state is in-memory (`ConcurrentHashMap`); SSE broadcasts Thymeleaf-rendered HTML fragments

---

## Security notes

- Admin routes (`/quizSetup/**`) are protected by HTTP Basic Auth via Spring Security.
- The admin path is intentionally undiscoverable — it is not linked from the public home page.
- CSRF is disabled because admin operations are protected by Basic Auth and public routes are read-only.
- Set `ADMIN_USER` and `ADMIN_PASS` environment variables in production. The defaults (`admin` / `changeme`) are intentionally weak.
