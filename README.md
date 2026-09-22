# AI-Powered News Intelligence

[Live demo](https://abhay123abhi-news-web.onrender.com/) · [Architecture and trade-offs](docs/architecture.md) · [API and configuration](docs/api.md) · [Render deployment](docs/deployment.md)

[![Build and test](https://github.com/Abhay123abhi/ai-powered-news-intelligence/actions/workflows/ci.yml/badge.svg)](https://github.com/Abhay123abhi/ai-powered-news-intelligence/actions/workflows/ci.yml)

Newsroom brings reporting from The Guardian and The New York Times into one reading workspace. Select relevant stories, create a brief, ask questions, or compare coverage without switching between publishers.

Built with **Java 21, Spring Boot, React, Vite, Redis, and Google Gemini** as a public learning project. The demo uses free hosting and an optional free-tier Gemini API project. The first request can take about a minute while Render wakes up.

## What you can try

1. Search a topic or choose a quick topic.
2. Browse the editorial feed; pagination keeps articles that did not fit on earlier pages.
3. In **AI workspace**, choose up to eight stories. Select both publishers covering the same topic for a comparison.
4. Create a brief or ask a question. Follow the citations to check the original reporting.
5. If AI is unavailable, browse news or open the clearly labelled example without making an AI request.

The violet/cyan design includes light/dark themes, separate mobile Discover/AI views, keyboard-accessible tabs, readable AI controls, and reduced-motion support.

## Engineering decisions

| Decision | Reason |
| --- | --- |
| Provider interfaces + adapters | Keep publisher payloads outside the application model |
| Virtual threads + concurrent fan-out | Fetch both publishers without serial network waits |
| Bounded feed sessions | Retain overflow and freeze pages already shown |
| Redis + bounded local feed fallback | Reuse news and preserve sessions across ordinary app restarts |
| Server-held AI context | Callers send story references, not arbitrary article text |
| Structured responses + citation validation | Render predictable output and remove unsupported citation references |
| Shared attempt budget + concurrency limit | Protect a small public demo's free AI allowance |
| Cached and coalesced AI requests | Repeated identical requests reuse one generated insight |
| Explicit failure states | Keep news useful when AI, Redis, or one publisher is unavailable |

![Application architecture](docs/architecture.svg)

## Honest limits

- AI receives **headlines and excerpts**, not full articles. A valid citation ID is not proof that a generated claim is true. Verify original sources.
- The shared AI budget defaults to **5 upstream attempts/minute and 20/day**; these are conservative application settings, not Google's advertised quota. Every retry counts. Set them at or below your actual AI Studio allowance.
- There is no paid model fallback. Keeping the Google project on its Free Tier is an account setting; source code cannot enforce its billing tier.
- Search sessions last 15 minutes and fetch at most five ten-story pages per publisher. Batches are ranked as fetched; previously displayed pages are not globally reranked as new articles arrive.
- A session can end with fewer stories if providers return duplicates, no results, or fail. The UI identifies partial coverage.
- Feed locks, request throttles, and the two-generation concurrency limit target **one backend instance**. Redis stores feeds and shared AI counters. Multiple instances would need distributed feed locks and a distributed concurrency limit.
- Redis failure permits bounded local news browsing and pauses new AI generation. Already cached AI responses can be reused only while their cache is available.
- Free Redis data loss or a manual flush can reset counters. Quota controls reduce abuse; they are not a durable financial spending cap.
- Questions and selected excerpts are sent to Gemini. Do not submit sensitive information. Review [Google's free-tier data-use terms](https://ai.google.dev/gemini-api/terms).

## Run locally

Requires Java 21, Node.js 22.12+, Redis, and at least one publisher API key. Redis should use `noeviction` so cache pressure cannot evict AI allowance counters.

```bash
# Optional disposable local Redis container
docker run --name news-cache -p 127.0.0.1:6379:6379 -d redis:7-alpine redis-server --maxmemory 128mb --maxmemory-policy noeviction

export GUARDIAN_API_KEY="your-key"
export NYT_API_KEY="your-key"          # optional if Guardian is configured
export REDIS_URL="redis://localhost:6379"
export AI_ENABLED="false"             # news works without Gemini

cd backend
bash gradlew bootRun
```

On Windows PowerShell, use `$env:GUARDIAN_API_KEY="your-key"` and the same form for other variables, then `cd backend` and `.\gradlew.bat bootRun`.

In a second terminal:

```bash
cd frontend
npm ci
npm start
```

Open http://localhost:3000. Vite proxies `/api` to http://localhost:8080.

To enable AI, set `AI_ENABLED=true`, `GEMINI_API_KEY`, and `GEMINI_MODEL=gemini-3.6-flash` in the backend environment and restart it. Confirm your project is on the Free Tier in AI Studio. API keys stay server-side; never put them in frontend variables.

## Tests and builds

```bash
cd backend
bash gradlew test bootJar

cd ../frontend
npm ci
npm test
npm run build
npm audit --audit-level=high
```

Tests cover merged pagination, context ownership, provider failures, input validation, quota errors, retries, duplicate submissions, stale answers, reduced motion, and mobile tab navigation. They use mocks; no real API key or paid request is required.

GitHub Actions runs the checks for pull requests and `main`. The badge reports actual workflow state. The Docker build runs backend tests; the Render static build runs frontend tests. See [deployment instructions](docs/deployment.md) for branch protection and deployment sequencing.

## Features exposed by the API

The UI exposes **Daily brief**, **Ask the news**, and **Compare coverage**. Single-story **summary** and **why-it-matters** endpoints are available for API exploration but do not have separate UI buttons.

See [API examples and settings](docs/api.md) for request schemas, errors, limits, and health checks.

## License

No open-source license has been selected. Public visibility does not grant permission to reuse or redistribute the code. A license should be chosen by the repository owner before offering reuse rights.
