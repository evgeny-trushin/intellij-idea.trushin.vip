# P3: Single IIFE Module

**All application logic lives in a single IIFE per JS file.**

```javascript
(function () {
  'use strict';
  // ... all code
  init();
})();
```

## For New Mind Map Code

New mind map code uses a **separate `mindmap.js` IIFE** that shares globals
via `window._mindmap`:

```javascript
// website/mindmap.js
(function () {
  'use strict';

  // Expose API for integration with app.js
  window._mindmap = {
    init: initMindMap,
    show: showMindMap,
    hide: hideMindMap,
  };

  function initMindMap() { /* ... */ }
  // ...
})();
```

## Rationale

- Keeps `app.js` under ~1500 lines (currently ~1400)
- Avoids polluting global scope
- Connected via a thin integration layer in `app.js`

## Applied In

- 0047 (MindElixir renderer → `website/mindmap.js`)
- 0051 (search/filter → part of `mindmap.js`)
- 0057 (D3.js renderer → `website/mindmap-d3.js`)
