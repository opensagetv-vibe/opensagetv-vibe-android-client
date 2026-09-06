#!/usr/bin/env python3
"""Generate the reviewed Android runtime dependency/license inventory.

The input is the plain Gradle dependency report produced by the unified build
environment.  Every selected Maven coordinate must match an explicit license
rule.  An unknown coordinate is a hard failure so dependency drift cannot be
silently published without review.
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from dataclasses import dataclass
from pathlib import Path


DEPENDENCY_RE = re.compile(
    r"(?:\+---|\\---)\s+([^:\s]+):([^:\s]+):([^\s]+)(?:\s+->\s+([^\s]+))?"
)


@dataclass(frozen=True)
class LicenseRule:
    license_expression: str
    license_files: str
    upstream: str


APACHE = LicenseRule(
    "Apache-2.0",
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0",
)
MIT = LicenseRule(
    "MIT",
    "third_party/licenses/MIT.txt",
    "https://spdx.org/licenses/MIT.html",
)
BSD3 = LicenseRule(
    "BSD-3-Clause",
    "third_party/licenses/BSD-3-Clause.txt",
    "https://spdx.org/licenses/BSD-3-Clause.html",
)


def license_for(group: str, artifact: str) -> LicenseRule | None:
    coordinate = f"{group}:{artifact}"
    if group.startswith("androidx."):
        return APACHE
    if group in {
        "com.google.android.exoplayer",
        "com.badlogicgames.gdx",
        "com.google.errorprone",
        "com.google.guava",
        "com.google.j2objc",
        "com.hierynomus",
        "io.github.carguo",
        "org.jetbrains",
        "org.jetbrains.kotlin",
        "org.jetbrains.kotlinx",
        "org.jspecify",
    }:
        return APACHE
    if coordinate == "com.github.tony19:apktool-lib":
        return APACHE
    if coordinate == "com.github.bumptech.glide:glide":
        return LicenseRule(
            "BSD-3-Clause AND bundled third-party terms",
            "third_party/licenses/Glide-3.8.0.txt",
            "https://github.com/bumptech/glide/blob/v3.8.0/LICENSE",
        )
    if coordinate == "com.github.rahatarmanahmed:circularprogressview":
        return MIT
    if group == "com.github.tony19" and artifact.startswith("logback-android-"):
        return LicenseRule(
            "EPL-1.0 OR LGPL-2.1-or-later",
            "third_party/licenses/EPL-1.0.txt;third_party/licenses/LGPL-2.1.txt",
            "https://github.com/tony19/logback-android",
        )
    if coordinate == "com.google.code.findbugs:jsr305":
        return BSD3
    if coordinate == "com.jcraft:jzlib":
        return LicenseRule(
            "BSD-style-JCraft",
            "third_party/licenses/JCraft-JZlib.txt",
            "http://www.jcraft.com/jzlib/LICENSE.txt",
        )
    if coordinate == "net.engio:mbassador":
        return MIT
    if group == "org.bouncycastle":
        return LicenseRule(
            "Bouncy-Castle",
            "third_party/licenses/Bouncy-Castle.txt",
            "https://www.bouncycastle.org/about/license.html",
        )
    if coordinate == "org.checkerframework:checker-qual":
        return MIT
    if coordinate == "org.nanohttpd:nanohttpd":
        return BSD3
    if coordinate == "org.slf4j:slf4j-api":
        return MIT
    return None


def parse_coordinates(report: str) -> list[tuple[str, str, str]]:
    coordinates: set[tuple[str, str, str]] = set()
    for line in report.splitlines():
        match = DEPENDENCY_RE.search(line)
        if not match:
            continue
        group, artifact, declared, selected = match.groups()
        if declared.startswith("{"):
            continue
        version = selected or declared
        if version in {"FAILED", "(*)"}:
            continue
        coordinates.add((group, artifact, version))
    return sorted(coordinates)


def render_csv(coordinates: list[tuple[str, str, str]]) -> str:
    rows: list[list[str]] = []
    unknown: list[str] = []
    for group, artifact, version in coordinates:
        rule = license_for(group, artifact)
        coordinate = f"{group}:{artifact}:{version}"
        if rule is None:
            unknown.append(coordinate)
            continue
        rows.append(
            [
                coordinate,
                rule.license_expression,
                rule.license_files,
                rule.upstream,
            ]
        )
    if unknown:
        raise ValueError("unreviewed runtime dependencies: " + ", ".join(unknown))

    from io import StringIO

    output = StringIO(newline="")
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(["coordinate", "license", "license_files", "upstream"])
    writer.writerows(rows)
    return output.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("artifacts/reports/gradle-runtime-dependencies.txt"),
    )
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    report = args.input.read_text(encoding="utf-8", errors="replace")
    coordinates = parse_coordinates(report)
    if not coordinates:
        raise ValueError(f"no runtime coordinates found in {args.input}")
    rendered = render_csv(coordinates)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8", newline="")
    else:
        sys.stdout.write(rendered)
    print(f"reviewed runtime coordinates: {len(coordinates)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
