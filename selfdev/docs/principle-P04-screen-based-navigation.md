# P4: Screen-Based Navigation

**The app uses `<main class="screen">` sections, toggled by `showScreen(name)`.**

## Existing Screens

```javascript
const screens = {
  home: $('#screen-home'),
  quiz: $('#screen-quiz'),
  results: $('#screen-results'),
  progress: $('#screen-progress'),
};
```

## Adding a New Screen

1. Add `<main class="screen hidden" id="screen-mindmap">` to `website/index.html`
2. Register: `screens.mindmap = $('#screen-mindmap');`
3. Navigate via `showScreen('mindmap')` + `history.pushState`
4. Add an action card on the home screen (e.g., "🧠 Mind Map")

## Pattern

```html
<!-- website/index.html -->
<main class="screen hidden" id="screen-mindmap">
  <div class="container">
    <div id="mindmap-container"></div>
  </div>
</main>
```

## Applied In

- 0047 (mind map screen added to `website/index.html`)
- 0058 (progressive disclosure controls within the mind map screen)
