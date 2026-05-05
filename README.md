# Cluesday Scoreboard

Real-time quiz scoring app for Cluesday quiz nights.  
Built with Spring Boot 4 · Thymeleaf · HTMX · Tailwind CSS · SSE · Docker.

---

## Quick Start (local)

```bash
# Requires Java 25 and Maven (or use the wrapper)
./mvnw spring-boot:run
```

Open `http://localhost:8080/admin` — you'll be prompted for credentials.

**Default dev credentials** (override with env vars before going live):

| Variable    | Default   |
|-------------|-----------|
| `ADMIN_USER` | `admin`   |
| `ADMIN_PASS` | `changeme` |

> **Security reminder:** Always set `ADMIN_USER` and `ADMIN_PASS` in your environment
> before deploying. The defaults are intentionally weak.

---

## Usage Guide

### 1 · Create a quiz (Admin)
1. Go to `/admin` and log in.
2. Enter **Cluesday #**, **Quizmaster name**, **date**, rounds, and default pts per question.
3. Click **Create Quiz**.

### 2 · Register tables
- Check the tables that are playing (1–25 grid).
- Click **Apply Table Selection** — teams are created as "Table N".
- Add extra teams (visiting groups, etc.) with the free-form input below.
- Click **Start Quiz** when done.

### 3 · Score during the quiz
- Use the **round tabs** on the dashboard.
- Tick a checkbox for a correct answer (= default pts) or click a preset for partial credit.
- **Round 5**: use the −1 / 0 / +1 buttons.
- **Round 6 (Long Connects)**: set the answer count first, then enter free-form scores.
- Toggle the **★ Joker** button to double a team's round total.
- Check **Round complete** when all scores are entered — this publishes that round to the live board.

### 4 · Share the live scoreboard
- Copy the **public URL** from the dashboard header and share via WhatsApp / message.
- Audience opens `/live/{uuid}` — the board updates in real-time as you mark rounds complete.

### 5 · End the quiz
- Click **End Quiz** — scores are saved to history, the live board shows "Quiz Ended".
- View past quizzes at `/admin/history`.

---

## Deploying to Railway

### Prerequisites
- [Railway account](https://railway.app)
- [Railway CLI](https://docs.railway.app/develop/cli) (optional but handy)

### Steps

**Option A — GitHub deploy (recommended)**
1. Push this repo to GitHub.
2. In Railway: **New Project → Deploy from GitHub repo** → select this repo.
3. Railway detects the `Dockerfile` and builds automatically.
4. Go to **Variables** and add:
   ```
   ADMIN_USER=<your-username>
   ADMIN_PASS=<your-strong-password>
   ```
5. Railway auto-assigns a public URL. Share `/live/{uuid}` with your audience.

**Option B — CLI deploy**
```bash
railway login
railway init          # link or create a project
railway up            # build + deploy from local directory
railway variables set ADMIN_USER=<user> ADMIN_PASS=<pass>
```

### Notes
- The app is **stateless between restarts** — scores live in memory only.
  History survives container restarts only as long as the process stays up.
- `PORT` is set automatically by Railway; no manual configuration needed.
- The Dockerfile uses a two-stage build (build → slim JRE image).

---

## Architecture overview

```
/admin/**          Spring Security HTTP Basic  →  Admin controller
/live/{uuid}       Public (no auth)             →  Public controller + SSE
/live/{uuid}/events                             →  SseService (broadcasts score fragments)
```
