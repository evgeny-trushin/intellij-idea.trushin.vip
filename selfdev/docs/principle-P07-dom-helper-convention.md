# P7: DOM Helper Convention

**Use `$` and `$$` for DOM queries.**

## Convention

```javascript
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];
```

## For Separate Files

If `mindmap.js` is a separate IIFE, it must define its own `$`/`$$`:

```javascript
// website/mindmap.js
(function () {
  'use strict';
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => [...document.querySelectorAll(sel)];
  // ...
})();
```

Or access from shared scope if exposed by `app.js`.

## Rule

- Never use `document.getElementById()` — use `$('#id')` instead
- Never use `document.getElementsByClassName()` — use `$$('.class')` instead
- All IIFE modules that do DOM work must define or import these helpers

## Applied In

- All browser-side JS files in `/website`
