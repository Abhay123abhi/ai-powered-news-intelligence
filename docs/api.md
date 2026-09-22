# API and configuration

## News and trusted AI context

```http
GET /api/news?keyword=technology&page=1&pageSize=12
```

The response includes `feedId`, `page`, `pageSize`, `articles`, `prevPage`, `nextPage`, `fetchedAt`, `partial`, `unavailableSources`, and `limited`. Counts refer to the returned articles, not an advertised exhaustive publisher result count.

For later pages, pass the same `feedId`, keyword, and page size. Traverse forward in order; previously served pages can be revisited. Start a new search when changing keyword or page size. A session expires after 15 minutes; HTTP 410 tells the client to restart from page one.

```http
GET /api/news?keyword=technology&page=2&pageSize=12&feedId=<returned-feed-id>
```

AI endpoints accept zero-based story indices from a **served page**. They load the actual text from the backend's feed store. The request does not accept caller-defined article content.

```http
POST /api/ai/brief
Content-Type: application/json

{"feedId":"<returned-feed-id>","page":1,"articleIds":[0,2,4]}
```

`POST /api/ai/compare` uses the same payload; select articles from at least two publishers. The model is instructed to decline comparisons when the selected excerpts do not cover the same event.

```http
POST /api/ai/ask
Content-Type: application/json

{
  "question":"What changed in the selected reporting?",
  "selection":{"feedId":"<returned-feed-id>","page":1,"articleIds":[0,2,4]}
}
```

`POST /api/ai/summary` and `/api/ai/why-it-matters` require exactly one story index.

AI output contains structured `content.sections[].items[]`, `citations`, a compatibility `text` field, `model`, and `cached`. Invalid source IDs are removed; uncited items are replaced with a standard insufficient-evidence message. This is structural validation, not semantic fact verification.

`GET /api/ai/status` reports `enabled` and `state`. Ready means configured to accept a request, not a successful live Gemini health probe or guaranteed remaining quota. The UI updates its status after each request.

## Failure behavior

| Status | Meaning | Visitor action |
| --- | --- | --- |
| 400 | Invalid input, selection, or comparison | Correct the selection or question |
| 410 | Feed expired or selected page is unavailable | Refresh news |
| 413 | JSON body exceeds 32 KiB | Reduce the request |
| 429 | Shared quota, concurrency, or request throttle | Follow `Retry-After`; browse news or try an identical cached insight |
| 502 | Upstream error or unusable AI output | Try another selection later |
| 503 | News sources unavailable, AI disabled/cooling down, or budget store unavailable | Continue with available features and retry later |
| 504 | AI network timeout | Retry later |

Errors use Problem Details; controlled AI errors add `code` and `retryAfter`. API keys and raw provider errors are not returned to visitors. Validation failures may use Spring's standard error response.

## Settings

| Variable | Default | Meaning |
| --- | --- | --- |
| `GUARDIAN_API_KEY`, `NYT_API_KEY` | empty | At least one configured enabled publisher is required |
| `REDIS_URL` | `redis://localhost:6379` | Feed storage, AI cache, shared AI counters |
| `AI_ENABLED` | `true` | Configuration switch; missing Gemini key also disables AI |
| `GEMINI_API_KEY` | empty | Server-only credential |
| `GEMINI_MODEL` | `gemini-3.6-flash` | Explicit model; no automatic paid fallback |
| `AI_REQUESTS_PER_MINUTE` | `5` | Shared upstream attempt allowance, including retries |
| `AI_REQUESTS_PER_DAY` | `20` | Shared daily attempts; midnight America/Los_Angeles reset |
| `AI_CACHE_TTL` | `30m` | AI result cache lifetime |
| `GEMINI_CONNECT_TIMEOUT` | `5s` | Connection timeout |
| `GEMINI_READ_TIMEOUT` | `15s` | Read timeout per attempt |
| `GEMINI_MAX_RETRIES` | `1` | Retries for 5xx/network errors; 429 is not immediately retried |
| `GEMINI_RETRY_BACKOFF` | `400ms` | Exponential backoff plus jitter |
| `GEMINI_CIRCUIT_FAILURE_THRESHOLD` | `4` | Failed logical requests before opening the circuit |
| `GEMINI_CIRCUIT_OPEN_DURATION` | `30s` | Circuit cooldown |
| `NEWS_FEED_TTL` | `15m` | Fixed feed lifetime, not extended by pagination |
| `NEWS_MAX_PROVIDER_PAGES` | `5` | Bounded fetch depth per publisher; ten stories per batch |
| `NEWS_CONNECT_TIMEOUT_MS` | `10000` | Publisher connection timeout |
| `NEWS_READ_TIMEOUT_MS` | `20000` | Publisher read timeout |
| `NEWS_PROVIDER_TIMEOUT` | `25s` | Task deadline; HTTP timeouts still bound underlying calls |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins; CORS is not authentication |
| `SWAGGER_ENABLED` | `false` | Swagger UI switch |

Public API throttles are local to the single backend: 30 news requests/minute per observed client address and 60 globally; 8 AI POST requests/minute per address and 30 globally. AI cache hits do not consume the separate Gemini attempt budget. Two distinct generations may run concurrently. These limits protect a small demo, not an unlimited public service.

Forwarded headers are deliberately not trusted. Behind a proxy, visitors may share the observed address and therefore a throttle bucket. Configure an independently verified trusted proxy boundary before using forwarded client IPs; do not blindly trust caller-supplied `X-Forwarded-For`.

## Health

- `/actuator/health/liveness`: process health; Render uses this so a Redis outage does not cause a restart loop.
- `/actuator/health`: includes dependency health.
- AI availability is separate from process liveness.
