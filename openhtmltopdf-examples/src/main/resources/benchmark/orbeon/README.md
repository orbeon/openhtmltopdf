# Orbeon Forms PDF benchmark fixture

Benchmark fixture for https://github.com/orbeon/orbeon-forms/issues/7681
("Improve PDF performance"). Used by
`com.openhtmltopdf.performance.OrbeonFixtureRunner` and
`com.openhtmltopdf.benchmark.OrbeonFixtureBenchmark`.

## Files

- `controls.xhtml` — main fixture: the XHTML that Orbeon Form Runner feeds to
  openhtmltopdf for the `orbeon-features/controls` sample form, with the form
  sections block repeated 4x (mimicking the issue's "form which repeats 10x
  some fairly large content", ~9400 lines). Stylesheet links rewritten to the
  local CSS files below.
- `controls-1x.xhtml` — the original, un-inflated capture (for reference and
  for regenerating `controls.xhtml`).
- `orbeon-1.css`, `orbeon-2.css` — the two aggregated CSS files served by the
  same Orbeon instance for that form (~6600 rules total). Captured *without*
  Orbeon's CSS-variable injection (`OrbeonPdfBoxUserAgent`), which only
  affects property values, not the selector workload being optimized.
- `Inter-Medium.ttf` — the default Form Runner PDF font, so text measurement
  is representative.
- `inflate-fixture.py` — regenerates `controls.xhtml` from `controls-1x.xhtml`
  (repeats the sections block with rewritten `id`/`for`/`aria-*`/`name`
  attributes, mimicking distinct Orbeon repeat iterations).

## Capture recipe

1. In a Orbeon Forms dev instance, set the `oxf.fr.pdf.dump.xhtml` property to
   a directory path. Each PDF render then dumps its exact input DOM (as fed to
   `PdfRendererBuilder.withW3cDocument`) to `<dir>/orbeon-pdf-*.xhtml`
   (pretty-printed, indent 2).
2. Generate a PDF, e.g. `GET /orbeon/fr/orbeon-features/controls/pdf`.
3. Copy the dump here as `controls-1x.xhtml`; `curl` the `<link>` stylesheet
   URLs from the dump into `orbeon-1.css` / `orbeon-2.css` (order as in the
   document).
4. Adjust `BLOCK_START`/`BLOCK_END` in `inflate-fixture.py` to the line range
   of the `xbl-fr-section` divs and run it.

## Known approximations

- The dump is pretty-printed, so it contains extra whitespace text nodes the
  live pipeline does not have.
- CSS custom properties (`var(...)`) are not resolved, unlike in Orbeon where
  `OrbeonPdfBoxUserAgent` injects the values at CSS load time.
- Renderer settings mirrored from Orbeon's `XHTMLToPDFProcessor`: page size
  8.5x11in, 14 dots per pixel, Inter as fallback font.
