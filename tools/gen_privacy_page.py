#!/usr/bin/env python3
"""Renders PRIVACY.md to docs/privacy.html for GitHub Pages (tiny Markdown subset: headings, lists, links, bold)."""
import html
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..")
md = open(os.path.join(ROOT, "PRIVACY.md")).read()


def inline(t):
    t = html.escape(t, quote=False)
    t = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r'<a href="\2">\1</a>', t)
    t = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", t)
    return re.sub(r"\b([\w.+-]+@[\w-]+\.[\w.]+\w)", r'<a href="mailto:\1">\1</a>', t)


out, in_list = [], False
for block in md.split("\n\n"):
    lines = [l for l in block.strip().splitlines() if l.strip()]
    if not lines:
        continue
    if lines[0].startswith("# "):
        out.append(f"<h1>{inline(lines[0][2:])}</h1>")
    elif lines[0].startswith("## "):
        out.append(f"<h2>{inline(lines[0][3:])}</h2>")
    elif all(l.lstrip().startswith(("* ", "- ")) for l in lines):
        out.append("<ul>" + "".join(f"<li>{inline(l.lstrip()[2:])}</li>" for l in lines) + "</ul>")
    else:
        out.append(f"<p>{inline(' '.join(lines))}</p>")

page = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Arrow Escape – Privacy Policy</title>
<style>
  body {{ font: 16px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #1B2133;
         max-width: 720px; margin: 0 auto; padding: 32px 20px 64px; background: #fff; }}
  h1 {{ font-size: 28px; margin-bottom: 4px; }}
  h2 {{ font-size: 19px; margin-top: 32px; }}
  a {{ color: #2F5BEA; }}
  li {{ margin: 4px 0; }}
</style>
</head>
<body>
{chr(10).join(out)}
</body>
</html>
"""
open(os.path.join(ROOT, "docs", "privacy.html"), "w").write(page)
print("ok")
