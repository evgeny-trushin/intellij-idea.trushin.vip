# P5: CSS Variable Theming

**All colors use CSS custom properties with `[data-theme="dark"]` overrides.**

## Existing Pattern

```css
:root { --bg: #f8f9fb; --primary: #ff9900; ... }
[data-theme="dark"] { --bg: #0d1117; --primary: #ff9900; ... }
```

## AWS Category Colors

New AWS category colors are added as CSS custom properties:

```css
:root {
  --aws-compute: #ED7100;
  --aws-storage: #7AA116;
  --aws-database: #01A88D;
  --aws-networking: #8C4FFF;
  --aws-security: #DD344C;
  --aws-ml: #C925D1;
  --aws-management: #E7157B;
}
```

These do **not** need dark/light variants — AWS brand colors are consistent
across themes. Only background/surface colors adapt to theme.

## Rule

- Never use hardcoded hex colors in CSS rules — always use `var(--name)`
- New mind map styles go in `/website/styles.css` (same file, new section)
- AWS colors are referenced by mind map node templates

## Applied In

- 0042 (AWS color palette definition)
- 0048 (mind map node styling)
