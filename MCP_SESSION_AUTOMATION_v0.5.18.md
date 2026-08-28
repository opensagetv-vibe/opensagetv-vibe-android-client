# MCP Session Automation v0.5.18

## Goal

Run a full SageTV MiniClient Dev regression session without manually navigating the STV: configure the player, connect the client, start a named recording on that same MiniClient, verify real A/V playback, optionally run seek/comskip checks, and exit.

## Preferred MCP calls

1. `launch_dev_app()`
2. `dev_set_player_config(player=..., streaming=..., decoding=..., gsy_engine=...)`
3. `dev_connect_server()` (with no arguments this uses the last-connected server)
4. `dev_play_video(video_name="Known Test Recording")`
5. Optional: `dev_run_seek_check(...)` and `dev_run_comskip_check(...)`
6. `dev_exit_session(stop_app=false)`

`dev_play_video` takes only the video/recording name. It derives the server and Dev client identity from the Android snapshot, discovers Sagex, resolves the MiniClient UI context, finds a unique MediaFile, invokes `Watch`, verifies real A/V output, and checks the current MediaFile ID when available.

## Name matching

- Exact case-insensitive title match is preferred.
- If there is no exact match, a unique case-insensitive substring match is allowed.
- If multiple MediaFiles match, nothing is started; the MCP result returns candidate IDs/titles. Use a more specific name.

## End-to-end shell helper

```bash
./dev.sh mcp-session-test \
  --video-name "Known Test Recording" \
  --player media3 \
  --streaming pull \
  --decoding hardware \
  --run-seek-health \
  --run-comskip right \
  --exit session
```

Omit server arguments to use the MiniClient's last-connected server. `--exit session` disconnects and returns to the server list. `--exit stop` also force-stops the Dev app.

## Sagex discovery

Normal use requires no API URL in the MCP call. Discovery tries the connected SageTV server on ports 8080, 8081 and 80. For a custom web/API configuration use environment variables:

```text
SAGETV_SAGEX_BASE=http://192.168.10.175:8090/sagex/api
SAGETV_SAGEX_USER=optional-user
SAGETV_SAGEX_PASSWORD=optional-password
```

The Sagex Remote API supports UI-context-aware calls and media object references. v0.5.18 uses `GetUIContextNames`, `GetMediaFiles`, `Watch`, and `GetCurrentMediaFile`.
