# AI error handling

The existing page design, animations, build system, deployment configuration, and
model are unchanged. Frontend errors use fixed visitor-friendly messages; they do
not display the API's diagnostic `detail` field.

To investigate a failure, open browser Developer Tools → Network → the failed
`/api/ai/*` request → Response. For example:

```json
{
  "status": 429,
  "detail": "The AI provider returned a rate or quota limit (429).",
  "code": "AI_PROVIDER_QUOTA",
  "upstreamStatus": 429,
  "retryAfter": 60,
  "requestId": "unique-request-id"
}
```

`requestId` also appears in backend failure logs. `upstreamStatus` is included only
when known. These are safe diagnostic categories, not raw Gemini payloads: API
keys, article text, prompts, and provider bodies are not exposed or logged.

| Code | Meaning |
| --- | --- |
| AI_PROVIDER_QUOTA | Gemini returned 429, or its resulting cooldown is still active |
| AI_APP_RATE_LIMIT | The application's existing per-minute limit was reached |
| AI_BUSY | Another uncached generation is running on this server |
| AI_TIMEOUT | Provider connection failed or timed out |
| AI_ACCESS_DENIED | Provider returned 401 or 403 |
| AI_MODEL_UNAVAILABLE | Provider returned 404 |
| AI_REQUEST_REJECTED | Provider returned 400 |
| AI_UPSTREAM_ERROR | Another provider HTTP failure |
| AI_COOLDOWN | Circuit breaker paused calls after repeated transient failures |
| AI_OUTPUT_LIMIT | Provider output was truncated |
| AI_EMPTY_RESPONSE / AI_INVALID_RESPONSE | Missing or malformed provider output |
| AI_INVALID_REQUEST | Request failed input validation |
| AI_DISABLED / AI_NOT_CONFIGURED | Feature disabled or provider credential missing |
| AI_INTERRUPTED / AI_INTERNAL_ERROR | Interrupted retry or unexpected internal failure |

Where available, `retryAfter` and the `Retry-After` header give a delay in seconds.
For provider 429s without a usable header, 60 seconds is a conservative cooldown,
not a guarantee that provider quota will reset then. The provider's 429 alone does
not identify whether the exhausted quota was requests, tokens, or a daily limit.

There is no new daily cap. Existing `AI_REQUESTS_PER_MINUTE` remains unchanged.
429s are not automatically retried, and cooldown requests preserve the quota
reason. Transient 5xx/transport failures retain the existing bounded retry policy.
Cached answers are checked before the application limit and provider cooldown.

Concurrency and application rate limits are per server instance, not a distributed
project-wide quota. The application limit counts uncached generation requests,
not each provider retry. Multi-instance quota enforcement and pagination changes
are outside this focused PR. Aborting an obsolete browser request discards its
result; it does not guarantee cancellation of an already running provider call.
