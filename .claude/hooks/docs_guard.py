# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Hook PreToolUse de Claude Code para el manual técnico.

1. Bloquea Edit/Write/MultiEdit sobre los derivados (docs/manual-tecnico.html y .pdf).
2. Bloquea `git push` hacia ramas protegidas si el manual está desactualizado:
   - hay cambios fuera de docs/ posteriores a la última /docs-sync, o
   - el HTML/PDF no corresponden al Markdown actual.

Exit 0 = permitir. Exit 2 = bloquear; stderr se entrega a Claude como motivo.
"""

from __future__ import annotations

import json
import os
import re
import shlex
import subprocess
import sys
from pathlib import Path

PROTECTED_BRANCHES = {"main", "master"}
DERIVED_FILES = ("docs/manual-tecnico.html", "docs/manual-tecnico.pdf")
MANIFEST = "docs/ALCANCE-DOCS.md"
SYNC_LINE = re.compile(r"docs-sync: commit=([0-9a-fA-F]+)")
PUSH = re.compile(r"\bgit\s+(?:-C\s+\S+\s+)?push\b(?P<args>[^;&|]*)")


def git(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["git", *args], capture_output=True, text=True)


def block(message: str) -> None:
    print(message, file=sys.stderr)
    sys.exit(2)


def guard_derived_edit(tool_input: dict) -> None:
    path = str(tool_input.get("file_path", "")).replace("\\", "/")
    if path.endswith(DERIVED_FILES):
        block(
            f"{path} es un archivo generado. Edita docs/manual-tecnico.md "
            "(o scripts/docs/manual-template.html) y regenera con "
            "`uv run scripts/docs/build_manual.py`."
        )


def push_targets(args: str, current_branch: str) -> set[str]:
    tokens = shlex.split(args, posix=True)
    if any(t in ("--all", "--mirror") for t in tokens):
        return set(PROTECTED_BRANCHES)
    positional = [t for t in tokens if not t.startswith("-")]
    refspecs = positional[1:]
    if not refspecs:
        return {current_branch}
    targets = set()
    for ref in refspecs:
        dest = ref.lstrip("+").split(":")[-1].removeprefix("refs/heads/")
        targets.add(current_branch if dest == "HEAD" else dest)
    return targets


def guard_push(command: str) -> None:
    match = PUSH.search(command)
    if not match:
        return
    current = git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip()
    if not push_targets(match.group("args"), current) & PROTECTED_BRANCHES:
        return

    manifest = Path(MANIFEST)
    if not manifest.exists():
        block(f"No existe {MANIFEST}; no se puede verificar la frescura del manual antes del push.")
    sync = SYNC_LINE.search(manifest.read_text(encoding="utf-8"))
    base = sync.group(1) if sync else "0000000"
    if set(base) == {"0"}:
        block("El manual técnico nunca se ha sincronizado. Corre /docs-sync <Parte> antes de hacer push.")
    if git("cat-file", "-e", f"{base}^{{commit}}").returncode != 0:
        block(f"El commit {base} registrado en {MANIFEST} no existe. Corre /docs-sync <Parte>.")

    changed = git("diff", "--name-only", base, "HEAD", "--", ".", ":!docs").stdout.split()
    if changed:
        listed = "\n  ".join(changed[:10]) + ("\n  ..." if len(changed) > 10 else "")
        block(
            f"Manual técnico desactualizado: {len(changed)} archivo(s) cambiaron desde la "
            f"última /docs-sync ({base}):\n  {listed}\nCorre /docs-sync <Parte> antes de hacer push."
        )

    check = subprocess.run(
        ["uv", "run", "--quiet", "--no-project", "--with", "pypdf>=5.0", "python", "scripts/docs/build_manual.py", "--check"],
        capture_output=True, text=True,
    )
    if check.returncode != 0:
        block((check.stdout + check.stderr).strip() or "build_manual.py --check falló.")


def main() -> None:
    event = json.load(sys.stdin)
    root = git("rev-parse", "--show-toplevel").stdout.strip()
    if not root:
        return
    os.chdir(root)

    tool = event.get("tool_name", "")
    tool_input = event.get("tool_input", {}) or {}
    if tool in ("Edit", "Write", "MultiEdit"):
        guard_derived_edit(tool_input)
    elif tool == "Bash":
        guard_push(str(tool_input.get("command", "")))


if __name__ == "__main__":
    main()