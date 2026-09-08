#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if grep -RniE 'firebase|crashlytics|google-services[.]json'   --include='*.java' --include='*.kt' --include='*.gradle' --include='*.xml'   --include='*.properties' --include='Jenkinsfile' --include='.gitignore'   --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build --exclude-dir=playstore .; then
  echo "FAIL: active Firebase/Crashlytics references remain" >&2
  exit 1
fi

echo "PASS: no active Firebase/Crashlytics/Google Services references found"
