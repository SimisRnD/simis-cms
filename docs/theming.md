---
id: theming
title: Theming and Color Scheme
# prettier-ignore
description: SimIS CMS design tokens, dark mode, and the theme.ui.mode site property
---

## Color scheme

The site property **Theme > Color Scheme** (`theme.ui.mode`) selects how the site renders:

| Value  | Behavior                                                                 |
| ------ | ------------------------------------------------------------------------ |
| `light` | Forced light. No toggle. **This is the default.**                        |
| `dark`  | Forced dark. No toggle.                                                  |
| `auto`  | Follows the visitor's operating system setting.                          |
| `user`  | Follows the operating system, and the visitor can override it.           |

The default is `light` on purpose: upgrading an existing site never changes how it looks. Dark mode is opt-in.

In `user` mode, place the **colorSchemeToggle** widget where the control should appear (the utility bar is the usual spot). The widget renders nothing in any other mode, because a toggle that contradicts a forced setting would be an inert control.

```xml
<widget name="colorSchemeToggle"/>
```

The visitor's choice is kept in `localStorage`, not a cookie — it is a display preference the server never needs, so it stays out of the request and out of the analytics surface. A small inline script in the document head applies the stored choice before the first paint, so switching schemes does not flash.

## Design tokens

`css/platform-tokens.css` defines the semantic tokens the platform styles against. Use these instead of hard-coded values in new components:

| Group    | Tokens                                                                           |
| -------- | -------------------------------------------------------------------------------- |
| Surfaces | `--sc-surface`, `--sc-surface-raised`, `--sc-surface-sunken`, `--sc-surface-overlay` |
| Text     | `--sc-text`, `--sc-text-muted`, `--sc-text-inverse`, `--sc-link`, `--sc-link-hover` |
| Lines    | `--sc-border` (decorative), `--sc-border-control` (form controls)                 |
| Fields   | `--sc-field-bg`, `--sc-field-text`, `--sc-field-placeholder`, `--sc-field-disabled-bg` |
| Focus    | `--sc-focus-ring`, `--sc-focus-ring-width`, `--sc-focus-ring-offset`             |
| Elevation| `--sc-shadow-sm`, `--sc-shadow-md`, `--sc-shadow-lg`                             |
| Radius   | `--sc-radius-sm`, `--sc-radius-md`, `--sc-radius-lg`, `--sc-radius-pill`          |
| Spacing  | `--sc-space-1` … `--sc-space-6`                                                   |
| Motion   | `--sc-motion-fast`, `--sc-motion-base`, `--sc-motion-ease`                        |

Every site theme color is also published as a token, named after the site property it comes from — `theme.button.primary.backgroundColor` becomes `--sc-button-primary-background-color`. These are emitted per request into the page's inline style block, so a widget or page stylesheet can reference the site's own colors:

```css
.my-component {
  background-color: var(--sc-surface-raised);
  color: var(--sc-text);
  border: 1px solid var(--sc-border-control);
  border-radius: var(--sc-radius-md);
  padding: var(--sc-space-4);
}
```

### Accessibility

Token pairings are chosen against WCAG 2.2: text meets 1.4.3 at 4.5:1 or better, and `--sc-border-control` meets 1.4.11 at 3:1 against both the page and raised surfaces. `--sc-border` is for decorative separators, which 1.4.11 exempts — do not use it to outline a control.

Two rules in `platform-tokens.css` apply in **both** schemes:

- A `:focus-visible` outline, so keyboard focus is visible without showing a ring to mouse users (2.4.11, 2.4.13).
- A `prefers-reduced-motion` block that collapses animation and transition durations (2.3.3).

### Dark mode and site colors

Dark mode repaints neutral chrome — page, cards, tables, form fields, overlays. It deliberately leaves the site's configured theme colors alone: buttons, semantic callouts, the top bar and the footer keep the colors an administrator chose, because those are brand decisions and lightening them can break their own contrast. A site turning dark mode on should review those theme colors against the dark page.

Add `platform-invert-on-dark` to an image that is authored for a light background (a logo on a white plate, say) to have it inverted in dark mode.
