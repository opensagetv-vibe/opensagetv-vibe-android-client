# Media3 Push/Dynamic vs Pull Comskip Matrix — v0.5.31

## Correct Comskip command path

Device testing proved the working Comskip action is the native SageTV command itself:

```text
command right
command left
```

These are the same commands accepted by `./dev.sh mcp-send-sequence`. The automation does **not** simulate an Android long press and does **not** resolve the normal `videoplaying_right` / `videoplaying_left` mapping. Those normal short-press mappings are FF/REW.

The instrumented Comskip helper wraps the direct SageTV `right` / `left` command in the existing Android `skip_check` output-health probe so it can still measure:

- landing timeline / jump size
- A/V recovery latency
- video/audio decoder identity
- video/audio continued advancement
- buffering/loading
- player/surface/error state

Comskip marker timestamps remain server/STV-owned and are not semantically exposed to the MiniClient, so output health is authoritative and the landing timeline is diagnostic unless an external expected target is supplied.

## Run

```bash
./dev.sh mcp-media3-comskip-matrix \
  --server 192.168.10.175 \
  --text "<recording search text>"
```

`--text` is required. The matrix starts a fresh Media3 Hardware session in Dynamic and Pull, then sends direct SageTV `right` followed by `left` while verifying real A/V recovery.

No APK rebuild is required for v0.5.31.
