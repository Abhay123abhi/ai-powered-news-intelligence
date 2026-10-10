# Frontend code conventions

- Components, API clients and component-specific CSS stay under `src/features/<feature>/`.
- Cross-feature layout and theme styling stays in `src/` (existing import order is intentional for CSS overrides).
- Avoid dead selectors and duplicate HTML entrypoints; Vite loads `frontend/index.html`.
- JavaScript/JSX: 2-space indentation, double quotes, semicolons and LF line endings.
- Java: 4-space indentation. Keep behavior changes separate from styling refactors.
- The design includes **both dark and light themes**; keep original animations and respect `prefers-reduced-motion`.
- For code style, use Prettier with `frontend/.prettierrc.json` in your editor. Avoid autoformatting unrelated source files in UI-only PRs.
- Before submitting UI changes, run `npm test` and `npm run build` from `frontend/`.
