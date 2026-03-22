# P8: No Build Step

**No bundler, no transpiler, no npm at runtime.**

## Rules

- All JS is vanilla ES2020+ (browser-native, no import/export modules unless `type="module"`)
- Third-party libraries loaded via local copies in `website/lib/` or CDN `<script>` fallback
- All CSS is vanilla (no Sass, no PostCSS, no Tailwind)
- The app works by opening `website/index.html` directly or via any static file server
- No `webpack`, `vite`, `esbuild`, or `rollup` in the build chain

## Third-Party Libraries in `/website/lib/`

```
website/lib/
  mind-elixir.js     ← MindElixir (downloaded, committed)
  d3.min.js          ← D3.js tree-shaken (optional, for alt renderer)
  saveSvgAsPng.js    ← SVG→PNG export helper (optional)
```

Loaded in `index.html`:
```html
<script src="lib/mind-elixir.js"></script>
```

## Agent-Side Tools Are Separate

Agent scripts in `/scripts` **can** use npm, pip, etc. — they run offline
on the developer machine. Only `/website` must be zero-build.

## Applied In

- 0047 (MindElixir loaded from `website/lib/`)
- 0053 (saveSvgAsPng from `website/lib/`)
- 0057 (D3.js from `website/lib/`)
