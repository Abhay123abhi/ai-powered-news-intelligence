# Architecture and trade-offs

## Search and pagination

1. A normalized query and page size locate a short-lived feed session.
2. Guardian and NYT fetch ten stories concurrently using virtual threads.
3. Each new batch is normalized, ranked, URL-deduplicated, and appended to the session.
4. The requested page slices this accumulated buffer. Unshown stories remain for the next page.
5. Further provider batches are fetched only when needed. Exhausted/failed providers stop fetching.
6. Feed state is cached in Redis, with a maximum of 100 local sessions as a bounded fallback.

This avoids dropping the second publisher's overflow. It intentionally does not claim globally perfect sorting over a continuously changing publisher index. Already displayed pages stay stable; new upstream batches may still reflect a changing news corpus. Five pages per publisher cap work and memory for the free demo.

## AI flow

1. Validate request sizes and server-issued feed/page/story references.
2. Resolve only stories from a page that has been served.
3. Build a cache key from prompt version, operation, model, question, and article URL/title/description/source/publication time.
4. Return a cached answer, or join an identical generation already running in this process.
5. Acquire one of two generation slots.
6. Atomically reserve each upstream attempt against Redis minute/day counters.
7. Call Gemini with bounded excerpts and a structured JSON response instruction.
8. Validate the response shape and source indices, build citation metadata from backend-held stories, and cache the result.

No embeddings or vector database are used. This is context-grounded generation over retrieved excerpts, not full-document retrieval or independently verified news analysis.

## Failure and cost boundaries

- News can continue with one publisher and with the bounded local feed cache during a Redis outage.
- New AI generation fails closed when its Redis budget cannot be checked; unavailable caching must not become unlimited generation.
- 429 responses cause cooldown, using numeric Retry-After when provided. Transient 5xx/network failures may retry once; retries consume the same attempt budget.
- The application sends no paid fallback request. Its quota counters do not determine the Google account's billing tier.
- Configure Redis with `noeviction`: under memory pressure cache writes may fail, but counters must not be evicted to make room for articles.
- Render's free Redis tier is not a durable ledger. Data loss resets allowances. A strict monetary guarantee requires keeping billing disabled at the provider account level.
- Per-process feed locks, coalescing, concurrency, and ingress throttles are intentionally scoped to one free instance. Horizontal scaling needs distributed locking, fairness, and concurrency control.

## Frontend

Vite builds a static React app. A single stylesheet preserves the visual cascade from the previous design, with workspace controls and motion rules grouped at the end. Further visual refactoring should use screenshot comparisons rather than deleting overrides blindly.

Mobile tabs share a dynamically labelled tab panel and support arrow/Home/End navigation. AI output is tied to the feed and selection; stale requests are aborted/ignored. The server may still finish an already-started upstream call after browser cancellation, so duplicate-request coalescing and server budgets remain essential.
