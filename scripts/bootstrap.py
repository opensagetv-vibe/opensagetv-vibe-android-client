#!/usr/bin/env python3
"""Clone the pinned upstream baseline and apply the Dev development refactor."""
from __future__ import annotations
import argparse
import subprocess
import sys
from pathlib import Path

DEFAULT_REPO = "https://github.com/OpenSageTV/sagetv-miniclient.git"
DEFAULT_REF = "v1.14.0"


def run(cmd: list[str], cwd: Path | None = None) -> None:
    print("+", " ".join(cmd))
    subprocess.run(cmd, cwd=cwd, check=True)


def main() -> int:
    here = Path(__file__).resolve().parents[1]
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=DEFAULT_REPO)
    ap.add_argument("--ref", default=DEFAULT_REF)
    ap.add_argument("--dest", type=Path, default=here / "miniclient-src")
    ap.add_argument("--skip-refactor", action="store_true")
    args = ap.parse_args()
    dest = args.dest.resolve()

    if dest.exists() and any(dest.iterdir()):
        if not (dest / ".git").is_dir():
            raise SystemExit(f"Destination is not empty and is not a git checkout: {dest}")
        print(f"Using existing checkout: {dest}")
        run(["git", "fetch", "--tags", "origin"], cwd=dest)
    else:
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists():
            dest.rmdir()
        run(["git", "clone", args.repo, str(dest)])

    # Do not hard-reset user work. Checkout only succeeds cleanly when safe.
    run(["git", "checkout", args.ref], cwd=dest)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=dest, text=True).strip()
    (dest / "UPSTREAM_BASELINE.properties").write_text(
        f"repository={args.repo}\nref={args.ref}\ncommit={head}\n", encoding="utf-8"
    )

    if not args.skip_refactor:
        run([sys.executable, str(here / "scripts" / "apply_dev_refactor.py"), str(dest)])
    print("Bootstrap complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
