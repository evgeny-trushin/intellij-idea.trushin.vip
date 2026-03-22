# P1: Static-First Architecture

**Everything is a static file served from the `/website` folder.**

- No backend, no server, no runtime API calls
- Data files (`.js`) are generated **offline** by agent scripts in `/scripts`
- Generated files are committed to the repo and loaded via `<script>` tags
- The browser only **reads** pre-built static files — zero processing at load time
- Agent scripts (Python/Node.js) run on developer machine or CI, not in browser

## Existing Pattern

```html
<!-- website/index.html -->
<script src="quiz-data.js"></script>
<script src="video-links.js"></script>
<script src="app.js"></script>
```

## New Files Follow the Same Pattern

```html
<script src="mindmap-data.js"></script>
<script src="quiz-concept-map.js"></script>
<script src="mindmap.js"></script>
```

## Applied In

- All requirements 0041–0061
- Every data file and user-facing script lives in `/website`
- Every offline agent script lives in `/scripts`
