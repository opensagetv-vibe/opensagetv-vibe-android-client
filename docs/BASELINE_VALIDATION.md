# v0.5.75 migration baseline validation

Date: 2026-08-28

Host workspace: Windows, `C:\TMP_SAGETV_DOCKER`

The retired pre-migration container was used read-only for this one baseline
comparison. Its identity is intentionally not part of the current workflow;
current Docker evidence is in `UNIFIED_BUILD_VALIDATION.md`.

## Original project (read-only)

| Check | Result |
|---|---|
| Intended directory exists | PASS |
| `VERSION` | `0.5.75` |
| Git repository/status | No `.git` repository was present |
| Existing project manifest | PASS, 694 entries, 0 failures |
| Scaffold tests | PASS, 145 tests |
| MCP tests | PASS, 35 tests |
| Shell syntax | PASS |
| Full project validator | PASS |

The original was mounted read-only. Python bytecode was redirected to `/tmp`;
no APK, cache, key, local configuration, or test product was written there.

## Copy equivalence

Only packaging-rule products were excluded: build/cache directories, APK and
package outputs, ADB state, logs, captures, local configuration, and signing
secrets. Dotfiles and empty `.gitkeep` markers were retained.

| Content class | Original | Copy | Result |
|---|---:|---:|---|
| All included files | 1,201 | 1,201 | PASS |
| `source/dev` | 507 | 507 | PASS |
| `source/existing` | 472 | 472 | PASS |
| MCP | 12 | 12 | PASS |
| Tests | 18 | 18 | PASS |
| Scripts | 35 | 35 | PASS |
| Docker/config support | 8 | 8 | PASS |
| Required checked-in binary dependencies | 40 | 40 | PASS |

The relative-path comparison found 0 missing and 0 extra included files.
SHA-256 comparison of all 1,201 files found 0 mismatches. This includes the 14
native `libgdx.so` files hidden by nested legacy ignore rules.

## Copied project before restructuring

| Check | Result |
|---|---|
| `VERSION` | `0.5.75` |
| Existing project manifest | PASS, 694 entries, 0 failures |
| Scaffold tests | PASS, 145 tests |
| MCP tests | PASS, 35 tests |
| Shell syntax | PASS |
| Full project validator | PASS |
| Clean Gradle debug APK | PASS, 60 tasks executed |
| Build duration | 1 minute 6 seconds |
| APK SHA-256 | `839113f460fed5e6f37ec244ea6a2fbc574c32e5f9b131085c95a349bb364a69` |

The first direct Gradle attempt exposed a missing debug keystore because that
invocation had bypassed the host wrapper. The existing key generator was then
run inside Docker and the clean build passed. Phase 1 moves that safety into the
in-container build path so all callers receive identical behavior.

No device install, production-package command, or live playback test was run
during the structural baseline. Those require an explicitly commissioned
Android target.

## Initial migration equivalence

After repository identity/orchestration changes, 150 scaffold/static tests, 35
MCP tests, the full validator, and all 60 Gradle tasks passed. The resulting APK
retained SHA-256
`839113f460fed5e6f37ec244ea6a2fbc574c32e5f9b131085c95a349bb364a69`,
identical to the copied-project baseline. That proved the structural migration
had not changed the application before later API 36 work began.
