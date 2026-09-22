# Newsroom 3D design experiment

This experiment branches from `feat/public-demo-reliability`. It changes presentation only; it does not change Gemini models, quotas, or backend behavior.

## Reference review

Reviewed public repository descriptions and README material on 2026-09-22. These are popular relevant references, not an exhaustive ranking or a hands-on usability study. Approximate stars at review time:

| Project | Stars | Relevant pattern | Application here |
| --- | ---: | --- | --- |
| [Folo](https://github.com/RSSNext/Folo) | 39k | Organized reading with integrated AI summaries | Keep browsing prominent; avoid adding extra setup to the AI workflow |
| [Vane, formerly Perplexica](https://github.com/ItzCrazyKns/Vane) | 36.9k | AI answering engine | Keep questions and answers in a focused workspace |
| [NewsBlur](https://github.com/samuelclay/NewsBlur) | 7.6k | News reading, Ask AI, daily briefing | Preserve the brief/ask distinction and access to original reporting |

The design choices are our interpretation of these product patterns, not claims that the projects use this 3D treatment. No project assets or code were copied.

## Visual decisions

- Preserve violet/cyan accents and editorial type; use glass primarily around controls.
- Replace the source strip with a compact, interactive Reporting → News feed → AI insight flow.
- CSS perspective and layered shadows provide depth without a WebGL dependency.
- Each stage is a real keyboard-accessible button with an expandable explanation.
- Entrance and hover motion illustrate depth; the reading and input surfaces stay still.
- On phones, reduce perspective; at narrow widths, stack the stages. Respect reduced-motion preferences.
- The diagram describes the workflow; it is not live system telemetry or proof of AI availability.

## Verification

Frontend tests and production build pass. Browser screenshot verification remains outstanding because the browser binary download failed in the execution environment. Review at 1440, 1280, 768, 390 and 360 CSS-pixel widths, with both themes and reduced motion enabled, before merging the experiment.
