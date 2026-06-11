#!/usr/bin/env python3
"""Build controls.xhtml from controls-1x.xhtml by repeating the form sections
block N times (mimicking the "form which repeats 10x some fairly large
content" case of https://github.com/orbeon/orbeon-forms/issues/7681) and
rewriting the stylesheet links to the local CSS files.

The duplicated copies get unique id/for/aria-labelledby/name attribute values
so that ID-based matching behaves as it would with real Orbeon repeats.
"""

import re
import sys

SRC = "controls-1x.xhtml"
DST = "controls.xhtml"
# 1-based inclusive line range of the eight xbl-fr-section divs (children of
# span#fr-view-component) in the pretty-printed dump.
BLOCK_START = 250
BLOCK_END = 2517
COPIES = 4  # total occurrences of the block

ID_ATTRS = re.compile(r'\b(id|for|aria-labelledby|aria-describedby|name)="([^"]*)"')

def rewrite_ids(text: str, n: int) -> str:
    return ID_ATTRS.sub(lambda m: f'{m.group(1)}="{m.group(2)}-rep{n}"', text)

def main() -> None:
    lines = open(SRC, encoding="utf-8").readlines()
    block = "".join(lines[BLOCK_START - 1 : BLOCK_END])
    copies = [block] + [rewrite_ids(block, n) for n in range(2, COPIES + 1)]
    out = "".join(lines[: BLOCK_START - 1]) + "".join(copies) + "".join(lines[BLOCK_END:])

    # Point the two aggregated stylesheets to the local copies, in order.
    css_iter = iter(["orbeon-1.css", "orbeon-2.css"])
    out = re.sub(
        r'href="/orbeon/xforms-server/[^"]*\.css"',
        lambda m: f'href="{next(css_iter)}"',
        out,
    )

    with open(DST, "w", encoding="utf-8") as f:
        f.write(out)
    print(f"wrote {DST}: {out.count(chr(10))} lines, {COPIES} copies of block")

if __name__ == "__main__":
    sys.exit(main())
