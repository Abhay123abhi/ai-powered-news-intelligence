# Render deployment and free-tier operation

## Updating an existing deployment

This change updates the news and AI request schema. Deploy backend and frontend together in a short maintenance window; an old frontend cannot call the new AI endpoints correctly. An open old browser tab must refresh after deployment.

1. Merge only after GitHub Actions passes. Configure branch protection to require the `backend` and `frontend` jobs if you want merge enforcement; a workflow file alone does not enable protection.
2. In Render Key Value, change eviction policy to **noeviction**. The updated Blueprint contains this setting, but verify it was applied to your existing service. Keep the plan **Free**.
3. In the API service, retain publisher keys and backend-only `GEMINI_API_KEY`. Set `AI_REQUESTS_PER_MINUTE=5`, `AI_REQUESTS_PER_DAY=20`, and lower either if your actual AI Studio quota is smaller. An existing value of 15 is not automatically replaced by an application default.
4. Keep `AI_ENABLED=true` only when you want AI enabled and the key's Google project is on the Free Tier. Retain `GEMINI_MODEL=gemini-3.6-flash`; no fallback model is configured.
5. Deploy the backend. Its Docker build now runs tests before packaging. Health check: `/actuator/health/liveness`.
6. Update the static site's build command to `npm ci && npm test && npm run build`, publish directory to **dist**, and Node version to **22**. Keep the `/api/*` rewrite before the SPA fallback. Deploy it immediately after the backend.
7. Refresh the browser. Check a search, Next/Previous, a one-click AI brief, light/dark themes, and mobile tabs. Check that error messages remain readable when AI is disabled.

If a Blueprint manages the services, synchronize `render.yaml` and verify the resulting settings in the dashboard. Existing manual service settings can otherwise retain the old `build` publish directory.

## New installation

Create a Render Blueprint from this repository. `render.yaml` defines the free backend, free Key Value cache, and static frontend. Supply publisher API keys and optionally a Gemini key. Do not place credentials in frontend environment variables or commit them.

The default AI budget is shared by all visitors and every key used by this app instance. It is intentionally small. Current limits and free-model availability should be checked in your own [AI Studio project](https://aistudio.google.com/) and [Google's documentation](https://ai.google.dev/gemini-api/docs/rate-limits).

## What prevents surprise paid AI usage?

Use a Google project that remains on the **Free Tier**, without paid billing enabled. Application limits are additional protection, not a substitute for that account setting. Gemini credentials inherit their project's billing tier. There is no automatic paid fallback in the code.

Render service plans must also remain Free; source code cannot inspect or guarantee your account's billing configuration. Free services sleep and may lose ephemeral state. This is a portfolio demo, not an availability guarantee.

## Verification and rollback

CI tests use fake providers and never need production API keys. `npm audit` runs against the installed dependency tree; findings can change as new advisories are published. The frontend migration was checked with zero reported findings at implementation time.

For rollback, deploy the prior backend and frontend commits together, restore the static publish directory to `build` for the old Create React App version, and retain conservative quota settings where supported. Redis uses versioned keys, so the new feed schema does not overwrite the old news cache. Do not flush Redis merely to roll back: doing so also resets allowance counters.

## Troubleshooting local AI requests

A 502 with `AI_UPSTREAM_ERROR` means the backend received an unsuccessful provider response; it does not identify the cause on its own. After updating, check the Spring Boot console for `Gemini request failed: upstreamStatus=..., category=...`. Only status and a fixed category are logged, never credentials or provider response bodies.

- `AI_REQUEST_REJECTED` (upstream 400): check the configured model and its supported generation settings; verify the key in Google AI Studio.
- `AI_ACCESS_DENIED` (401/403): check API key validity, project restrictions and Gemini access.
- `AI_MODEL_UNAVAILABLE` (404): check that `GEMINI_MODEL` is available to this project and supports `generateContent`.
- `AI_UPSTREAM_ERROR` (5xx): the provider failed; retry after the displayed delay.
- `AI_QUOTA_REACHED` (429): wait for quota to reset. Do not enable billing or a paid fallback for this free demo.

Restart the backend after changing environment variables. Do not paste API keys into issues, logs, or screenshots. A Ready badge indicates configuration availability, not a successful paid/quota-consuming test generation.
