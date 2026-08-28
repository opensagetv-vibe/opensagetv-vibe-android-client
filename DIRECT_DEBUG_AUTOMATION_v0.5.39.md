# SageTV MiniClient Dev v0.5.39 — Direct Debug Automation

## Goal

Make automated playback tests use the MiniClient and active player directly instead of simulating Android remote keys whenever a direct API exists.

## Direct operations

| MCP tool | Android/MiniClient action | Android key injection |
| --- | --- | --- |
| `dev_input_text_direct(text)` | `MiniClientConnection.postKeyEvent(...)` per character | No |
| `dev_hide_ime()` | `InputMethodManager.hideSoftInputFromWindow(...)` | No |
| `dev_player_control(action)` | `MiniPlayerPlugin.play/pause/stop` | No |
| `dev_seek_time(target_ms)` | `MiniPlayerPlugin.seek(target_ms)` | No |
| `dev_seek_relative(delta_ms)` | compute target then `MiniPlayerPlugin.seek(...)` | No |
| `dev_skip_forward(skip_ms)` | direct positive relative seek | No |
| `dev_skip_backward(skip_ms)` | direct negative relative seek | No |
| `dev_comskip(direction)` | internal `EventRouter.postCommand(RIGHT/LEFT)` | No Android key |

## Why Comskip still needs a SageTV event

The MiniClient does not receive the SageTV/STV commercial-marker list. Android therefore cannot safely calculate a next/previous marker locally. The dedicated debug Comskip operation sends the SageTV RIGHT/LEFT event internally, bypassing Android key maps and long-press handling; SageTV chooses the marker and issues the resulting media seek normally.

## Standard recording-start automation

The default startup sequence now uses:

1. direct SageTV `search` event,
2. wait for text-input/IME debug status,
3. `directtext`,
4. direct `hideime`,
5. direct SageTV commands for the remaining UI navigation.

Legacy `sendkey` and `sendtext` remain available for diagnostic reproduction.

## Media3 seek matrix

The Media3 Push/Pull matrix now tests direct player seeking with explicit millisecond deltas. `--skip-forward-ms` and `--skip-backward-ms` control the requested skip size. Pause/resume is also direct player API control.
