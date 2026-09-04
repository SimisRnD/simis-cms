# Vendored Foundation 6.8.1

Four of the six files in this directory are never served. Two are. Knowing
which is which matters, because the unserved ones carry stock Foundation
colours that the served build no longer uses.

| File | Served? | What it is |
| --- | --- | --- |
| `foundation.tokens.min.css` | **yes** | The build the application loads. Generated — see below. |
| `motion-ui.min.css` | **yes** | Vendor original, loaded as-is. |
| `foundation.min.css` | no | Vendor original. **Input to the generator** — see below. |
| `foundation.css` | no | Vendor original, unminified. Reference reading only. |
| `foundation.css.map` | no | Source map for `foundation.css`. |
| `foundation.min.css.map` | no | Source map for `foundation.min.css`. |

`foundation.tokens.min.css` is linked from `WEB-INF/jsp/main.jsp`,
`error-404.jsp`, `error-500.jsp`, `cms/file-browser.jsp` and
`cms/video-browser.jsp`. Nothing links the other Foundation files.

## Colours here are not the colours that ship

`tools/route-foundation-tokens.py` reads `foundation.min.css` and rewrites
Foundation's base palette to `var(--sc-fnd-*)` references, keeping the original
value as each fallback. The result is `foundation.tokens.min.css`. The token
values that actually paint the interface live in `css/platform-tokens.css` and
differ by theme and by light/dark mode.

So a colour in `foundation.css` or `foundation.min.css` is stock Foundation as
vendored, not what renders. **Do not measure contrast against those two files.**
Doing so has already produced one false accessibility defect: `.label.alert`
reads as `#fefefe` on `#cc4b37` there, which is 4.498:1 and looks like a clean
AA failure, while the rule that ships resolves through `--sc-fnd-alert` — a
value deliberately darkened away from Foundation's own since issue 1527 — and
passes. The proposed "fix" would have hardcoded a colour into `platform.css`
and bypassed the token layer, undoing the real fix while appearing to make one.

To check a colour, read the token in `css/platform-tokens.css` for the mode you
care about, or measure the rendered page.

## Changing things

- **Token values** (what a colour actually is): `css/platform-tokens.css`.
  `tools/check-token-contrast.py` gates these.
- **Which colours get routed at all**: `tools/route-foundation-tokens.py`.
- **`foundation.tokens.min.css`**: never by hand. Re-run the generator;
  CI byte-compares it (`route-foundation-tokens.py --check`).
- **`foundation.min.css`**: only to re-vendor Foundation, then regenerate.
  Its bytes are consumed wholesale, so anything added to it — including a
  comment — lands in the served output, and any hex inside that comment gets
  routed to a `var()` like a real declaration. That is why this README exists
  instead of a banner in that file.
- **`foundation.css`**: carries a banner saying the above. It changes nothing
  at runtime; editing it fixes nothing.
