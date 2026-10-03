# /// script
# requires-python = ">=3.11"
# dependencies = [
#     "playwright==1.56.0",
#     "pypdf>=5.0",
# ]
# ///
"""Genera docs/manual-tecnico.html y docs/manual-tecnico.pdf desde docs/manual-tecnico.md.

El Markdown es la unica fuente editable. El HTML y el PDF son derivados
deterministas: nunca se editan a mano.

Uso (desde la raiz del repo):
    uv run scripts/docs/build_manual.py            # genera HTML + PDF
    uv run scripts/docs/build_manual.py --check    # solo verifica frescura (exit 1 si hay deriva)
    uv run scripts/docs/build_manual.py --html-only

Primera vez en una maquina:
    uv run --with playwright==1.56.0 playwright install chromium
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_MD = REPO_ROOT / "docs" / "manual-tecnico.md"
OUTPUT_HTML = REPO_ROOT / "docs" / "manual-tecnico.html"
OUTPUT_PDF = REPO_ROOT / "docs" / "manual-tecnico.pdf"
TEMPLATE = Path(__file__).resolve().parent / "manual-template.html"

META_BLOCK = re.compile(r"\A\s*<!--\s*\nmanual-meta\s*\n(?P<body>.*?)-->", re.DOTALL)
SHA_META = re.compile(r'<meta name="manual-source-sha256" content="([0-9a-f]{64})">')
PDF_SHA_KEY = "/PdvManualSourceSha256"
REQUIRED_META = ("titulo", "lead", "actualizado", "chips")
RENDER_TIMEOUT_MS = 120_000


def read_source() -> tuple[str, str, dict[str, str]]:
    # Normaliza fin de línea: con core.autocrlf=true en Windows el hash sería distinto al de Linux/CI.
    text = SOURCE_MD.read_text(encoding="utf-8").replace("\r\n", "\n")
    sha = hashlib.sha256(text.encode("utf-8")).hexdigest()
    match = META_BLOCK.match(text)
    if not match:
        sys.exit(f"{SOURCE_MD}: falta el bloque <!-- manual-meta ... --> al inicio del archivo.")
    meta: dict[str, str] = {}
    for line in match.group("body").splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            meta[key.strip()] = value.strip()
    missing = [k for k in REQUIRED_META if not meta.get(k)]
    if missing:
        sys.exit(f"{SOURCE_MD}: manual-meta sin claves obligatorias: {', '.join(missing)}")
    return text, sha, meta


def render_html(text: str, sha: str, meta: dict[str, str]) -> str:
    chips = [f"Actualizado {meta['actualizado']}"] + [c.strip() for c in meta["chips"].split("|") if c.strip()]
    chips_html = "".join(f'<span class="chip">{html.escape(c)}</span>' for c in chips)
    # JSON dentro de <script>: escapar "</" y "<!--" para que el contenido nunca cierre la etiqueta.
    md_json = json.dumps(text, ensure_ascii=False).replace("</", "<\\/").replace("<!--", "<\\u0021--")
    replacements = {
        "{{SOURCE_SHA}}": sha,
        "{{SOURCE_SHA_SHORT}}": sha[:12],
        "{{TITULO}}": html.escape(meta["titulo"]),
        "{{LEAD}}": html.escape(meta["lead"]),
        "{{CHIPS}}": chips_html,
        "{{GENERADO}}": html.escape(meta["actualizado"]),
        "{{MD_JSON}}": md_json,
    }
    out = TEMPLATE.read_text(encoding="utf-8")
    for placeholder, value in replacements.items():
        out = out.replace(placeholder, value)
    leftover = re.findall(r"\{\{[A-Z_]+\}\}", out)
    if leftover:
        sys.exit(f"Plantilla con placeholders sin resolver: {sorted(set(leftover))}")
    return out


def render_pdf(sha: str, meta: dict[str, str], assets_dir: Path | None) -> None:
    from playwright.sync_api import sync_playwright
    from pypdf import PdfReader, PdfWriter

    footer = (
        '<div style="width:100%;font-size:8px;color:#5C574B;padding:0 16mm;'
        'display:flex;justify-content:space-between;font-family:sans-serif;">'
        f"<span>{html.escape(meta['titulo'])} &middot; {html.escape(meta['actualizado'])}</span>"
        '<span>Página <span class="pageNumber"></span> de <span class="totalPages"></span></span></div>'
    )
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(color_scheme="light")
        if assets_dir:
            # Modo sin red: sirve marked/mermaid desde archivos locales en vez del CDN.
            local = {"marked.min.js": assets_dir / "marked.min.js", "mermaid.min.js": assets_dir / "mermaid.min.js"}
            for name, path in local.items():
                page.route(f"**/{name}", lambda route, _request, path=path: route.fulfill(path=str(path)))
            page.route("https://fonts.*/**", lambda route, _request: route.abort())
        page.goto(OUTPUT_HTML.as_uri(), wait_until="load")
        page.wait_for_function("window.__manualReady === true", timeout=RENDER_TIMEOUT_MS)
        errors = page.evaluate(
            """() => Array.from(document.querySelectorAll('.diagram .mermaid')).filter(n => {
                   const svg = n.querySelector('svg');
                   return !svg || svg.getAttribute('aria-roledescription') === 'error';
               }).length"""
        )
        if errors:
            browser.close()
            sys.exit(f"{errors} diagrama(s) Mermaid no se renderizaron. Revisa la sintaxis en el Markdown.")
        page.emulate_media(media="print", color_scheme="light")
        tmp_pdf = OUTPUT_PDF.with_suffix(".tmp.pdf")
        page.pdf(
            path=str(tmp_pdf),
            format="Letter",
            print_background=True,
            prefer_css_page_size=True,
            display_header_footer=True,
            header_template="<span></span>",
            footer_template=footer,
            margin={"top": "18mm", "bottom": "20mm", "left": "16mm", "right": "16mm"},
            outline=True,
            tagged=True,
        )
        browser.close()

    writer = PdfWriter(clone_from=PdfReader(tmp_pdf))
    writer.add_metadata({
        "/Title": meta["titulo"],
        "/Subject": "Manual técnico del proyecto PDV / POS",
        "/Author": "Proyecto PDV",
        PDF_SHA_KEY: sha,
    })
    with OUTPUT_PDF.open("wb") as fh:
        writer.write(fh)
    tmp_pdf.unlink()


def check(sha: str) -> int:
    problems: list[str] = []
    if not OUTPUT_HTML.exists():
        problems.append(f"no existe {OUTPUT_HTML.relative_to(REPO_ROOT)}")
    else:
        m = SHA_META.search(OUTPUT_HTML.read_text(encoding="utf-8"))
        if not m or m.group(1) != sha:
            problems.append("el HTML no corresponde a la versión actual del Markdown")
    if not OUTPUT_PDF.exists():
        problems.append(f"no existe {OUTPUT_PDF.relative_to(REPO_ROOT)}")
    else:
        from pypdf import PdfReader

        pdf_sha = (PdfReader(OUTPUT_PDF).metadata or {}).get(PDF_SHA_KEY)
        if pdf_sha != sha:
            problems.append("el PDF no corresponde a la versión actual del Markdown")
    if problems:
        print("Manual desactualizado: " + "; ".join(problems) + ". Corre: uv run scripts/docs/build_manual.py")
        return 1
    print(f"Manual al día (sha256 {sha[:12]}).")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="solo verifica que HTML y PDF correspondan al Markdown")
    parser.add_argument("--html-only", action="store_true", help="genera solo el HTML")
    parser.add_argument("--assets-dir", type=Path, help="carpeta con marked.min.js y mermaid.min.js para generar sin red")
    args = parser.parse_args()

    text, sha, meta = read_source()
    if args.check:
        return check(sha)

    OUTPUT_HTML.write_text(render_html(text, sha, meta), encoding="utf-8", newline="\n")
    print(f"HTML: {OUTPUT_HTML.relative_to(REPO_ROOT)}")
    if not args.html_only:
        render_pdf(sha, meta, args.assets_dir)
        print(f"PDF:  {OUTPUT_PDF.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
    sys.exit(main())