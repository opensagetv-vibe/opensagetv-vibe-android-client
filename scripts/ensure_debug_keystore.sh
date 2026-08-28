#!/usr/bin/env bash
set -euo pipefail

ANDROID_DIR="${ANDROID_USER_HOME:-${HOME:-/root}/.android}"
KEYSTORE="$ANDROID_DIR/debug.keystore"
# The SageTV MiniClient Gradle signing config overrides Android's usual
# androiddebugkey alias and expects this alias for BOTH debug and release blocks.
ALIAS="client"
STOREPASS="android"
KEYPASS="android"

mkdir -p "$ANDROID_DIR"

command -v keytool >/dev/null 2>&1 || {
  echo "ERROR: keytool is missing from the development image." >&2
  exit 3
}

alias_exists() {
  keytool -list -keystore "$KEYSTORE" -storepass "$STOREPASS" -alias "$ALIAS" >/dev/null 2>&1
}

create_client_alias() {
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass "$STOREPASS" \
    -alias "$ALIAS" \
    -keypass "$KEYPASS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=SageTV MiniClient Dev Debug,O=SageTV Dev,C=US" \
    -noprompt >/dev/null
}

if [[ -f "$KEYSTORE" ]]; then
  if alias_exists; then
    echo "SageTV debug signing key already present: $KEYSTORE (alias '$ALIAS')"
    exit 0
  fi

  # Do not destroy an existing ADB/Android signing identity. If the keystore is
  # readable with the normal Android debug password, add SageTV's required alias.
  if ! keytool -list -keystore "$KEYSTORE" -storepass "$STOREPASS" >/dev/null 2>&1; then
    echo "ERROR: Existing $KEYSTORE cannot be opened with the Android debug password." >&2
    echo "Refusing to replace it automatically. Back it up/remove it manually if appropriate." >&2
    exit 2
  fi

  echo "Existing Android debug keystore found but alias '$ALIAS' is missing."
  echo "Adding SageTV MiniClient debug signing alias '$ALIAS' without replacing the keystore..."
  create_client_alias
else
  echo "Creating persistent SageTV MiniClient debug keystore: $KEYSTORE (alias '$ALIAS')"
  create_client_alias
fi

chmod 600 "$KEYSTORE" 2>/dev/null || true

if ! alias_exists; then
  echo "ERROR: Failed to create/verify alias '$ALIAS' in $KEYSTORE." >&2
  exit 4
fi

echo "PASS: SageTV debug keystore is ready: $KEYSTORE (alias '$ALIAS')."
