# Shared Development Principles — Mind Map Pipeline

Reference index for `/selfdev` analysis. Each principle is in a separate file
for targeted referencing from requirements.

---

## Principle Index

| ID | File | Summary |
|---|---|---|
| P1 | [principle-P01-static-first-architecture.md](principle-P01-static-first-architecture.md) | Everything is a static file in `/website` — no backend |
| P2 | [principle-P02-global-data-constants.md](principle-P02-global-data-constants.md) | Data files export `const UPPER_SNAKE = {...}` |
| P3 | [principle-P03-single-iife-module.md](principle-P03-single-iife-module.md) | App logic in IIFE; mind map in separate `mindmap.js` |
| P4 | [principle-P04-screen-based-navigation.md](principle-P04-screen-based-navigation.md) | `<main class="screen">` toggled by `showScreen()` |
| P5 | [principle-P05-css-variable-theming.md](principle-P05-css-variable-theming.md) | CSS custom properties, dark/light, AWS brand colors |
| P6 | [principle-P06-localstorage-namespacing.md](principle-P06-localstorage-namespacing.md) | All keys prefixed `aws-sa-pro-` |
| P7 | [principle-P07-dom-helper-convention.md](principle-P07-dom-helper-convention.md) | `$()` and `$$()` for DOM queries |
| P8 | [principle-P08-no-build-step.md](principle-P08-no-build-step.md) | Vanilla JS/CSS, libs in `/website/lib/`, zero build |
| P9 | [principle-P09-agent-scripts-output-website.md](principle-P09-agent-scripts-output-website.md) | Scripts in `/scripts`, output to `/website` |
| P10 | [principle-P10-graceful-degradation.md](principle-P10-graceful-degradation.md) | New features never break existing quiz app |

## Applies To

All requirements 0041–0061 (Mind Map Pipeline). Each requirement references
the specific principles it depends on.
