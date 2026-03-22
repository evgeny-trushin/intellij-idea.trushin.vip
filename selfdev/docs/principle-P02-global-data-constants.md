# P2: Global Data Constants

**Data files export a single global `const` variable.**

- `quiz-data.js` → `const QUIZ_DATA = { ... };`
- `video-links.js` → `const VIDEO_LINKS = { ... };`
- New: `mindmap-data.js` → `const MINDMAP_DATA = { ... };`
- New: `quiz-concept-map.js` → `const QUIZ_CONCEPT_MAP = { ... };`

## Guard Pattern

```javascript
// In app.js or mindmap.js
if (typeof QUIZ_DATA === 'undefined') { /* error message */ return; }
if (typeof MINDMAP_DATA === 'undefined') { /* graceful fallback — hide mind map */ }
```

## Rule

Every `<script src="...">` data file in `/website` must:
1. Declare exactly one `const UPPER_SNAKE_NAME = { ... };`
2. Contain valid JSON assigned to that constant
3. Be loadable independently (no imports, no dependencies)

## Applied In

- 0041 (knowledge hierarchy schema → `MINDMAP_DATA`)
- 0043 (subtitle parsing → `SUBTITLE_DATA`)
- 0044 (semantic chunking → `CHUNKED_DATA`)
- 0046 (quiz-concept mapping → `QUIZ_CONCEPT_MAP`)
