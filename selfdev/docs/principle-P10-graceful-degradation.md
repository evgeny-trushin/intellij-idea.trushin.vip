# P10: Graceful Degradation

**New features must not break the existing quiz trainer.**

## Rules

- If `mindmap-data.js` is missing, the Mind Map button is **hidden** (not an error)
- If `quiz-concept-map.js` is missing, quiz overlay on mind map is disabled
- The existing quiz flow (home → quiz → results → progress) works **unchanged**
- Mind map is an **additive** feature — never a dependency for existing screens
- All new `<script>` tags use guard checks before accessing their globals

## Guard Pattern

```javascript
// In mindmap.js
if (typeof MINDMAP_DATA === 'undefined') {
  console.warn('mindmap-data.js not loaded — mind map disabled');
  return;
}
```

```javascript
// In app.js — hide button if data not available
if (typeof MINDMAP_DATA === 'undefined') {
  $('#btn-start-mindmap')?.classList.add('hidden');
}
```

## Error Boundary

Mind map errors must not crash the quiz app:
```javascript
try {
  window._mindmap?.init();
} catch (err) {
  console.error('Mind map init failed:', err);
  // Quiz app continues working normally
}
```

## Applied In

- 0047 (mind map renderer — graceful if data missing)
- 0049 (click-to-play — graceful if video links missing)
- 0050 (quiz overlay — graceful if mapping missing)
- All integration points between mind map and existing app
