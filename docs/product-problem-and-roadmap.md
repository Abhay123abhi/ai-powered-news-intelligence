# Product definition and validation plan

## Core problem

Readers need to understand a topic from multiple publishers **without reading every article or trusting an unsupported AI summary**. A successful session gives them a concise account of events and working links to the underlying reporting.

## First audience

1. Busy professionals who want a brief overview of a topic in about a minute.
2. Students and topic researchers who need citations to follow up.

Analysts comparing publisher framing are a secondary audience until comparable event coverage is consistently available.

## Jobs to be done

- Search for a topic, see timely reporting and its publisher, and recognize when a source is missing.
- Summarize **only articles in the current feed** and open the corresponding source.
- Compare publisher descriptions **only where they discuss the same event**.
- Recover from upstream provider and AI quota failures without losing the core news workflow.

## Release acceptance criteria

### Milestone 1 — Reliability and honest coverage (PR #44)
- [x] A failed news provider is reported as partial coverage when another succeeds.
- [x] Switching searches cannot allow a canceled request to replace a newer result.
- [x] Previous articles are removed from AI context when beginning another search.
- [x] Backend Gradle tests run on pull requests.

### Milestone 2 — Genuine unified pagination
- [ ] Define a bounded merged search snapshot or cursor that **preserves** all fetched articles across pages.
- [ ] Return an explicit completeness window rather than summing provider totals as unique total results.
- [ ] Test mismatched Guardian/NYT page sizes, overlap, provider dropouts, pagination stability and cache expiry.
- [ ] Label the feed scope honestly if upstream APIs limit accessible results.

### Milestone 3 — Better comparison relevance
- [ ] Group only likely same-event articles using title similarity, entities and published time.
- [ ] Require at least two distinct publishers and a meaningful overlap before enabling a comparison.
- [ ] Provide a clear no-match explanation otherwise.
- [ ] Evaluate sample story pairs manually for false matches before introducing embeddings.

### Milestone 4 — Stronger AI provenance
- [ ] Avoid treating arbitrary browser-supplied article metadata as verified provider content.
- [ ] Use a backend-bound search snapshot/article ID or a controlled source lookup.
- [ ] Evaluate claim-to-source support; valid source indices alone do not guarantee factual support.
- [ ] Separate configured, usable, quota-limited and disabled AI states.

### Milestone 5 — Retention and usability
- [ ] Offer local preferred topics/bookmarks only after observed repeat-use demand.
- [ ] Test keyboard navigation, 200% text zoom, reduced motion and 320–1024 px viewport layouts.
- [ ] Measure first-search success, briefing success, source click-through and repeat use with privacy-conscious analytics.

## Principles

- Start with the modular monolith. Do not introduce microservices or a vector database solely for appearances.
- Fail gracefully and tell the reader **what** content is missing.
- Do not market excerpts as full-article verification.
- Keep Gemini optional. Search and source links must stand on their own.
- Make real-user sessions and measured errors drive the next milestone.
