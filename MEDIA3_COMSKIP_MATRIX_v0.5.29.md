# Media3 Push/Dynamic vs Pull Comskip Matrix — v0.5.29

> **Superseded by v0.5.31:** the original v0.5.29 implementation incorrectly used the normal video-playing RIGHT/LEFT mappings. Device testing proved Comskip must send direct SageTV `right` / `left` commands. See `MEDIA3_COMSKIP_MATRIX_v0.5.31.md`.

## Why this is next

The v0.5.28 Fire TV matrix passed normal Media3 seek recovery in both modes. Pull did not show a >2 s seek-recovery regression, so no Pull DataSource/load-control change is justified. Pause/resume was similarly slow in both modes and is tracked separately.

## Run

```bash
./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>"
```

`--text` is required; there is no default recording title. Other defaults: Media3, Hardware decoding, Dynamic then Pull, configured video RIGHT then LEFT arrow mapping.

## Verdict

A direction passes only when real decoded video and audio recover and continue advancing with a valid player/surface and no player error. The SageTV STV/server does not expose semantic Comskip marker start/end metadata to the MiniClient, so the harness records the actual landing timeline and jump size but does not invent an expected marker target.

Recovery over 2000 ms is a warning and diagnostic checkpoint, not an automatic failure when output is healthy.

## Expected output artifact

`artifacts/firetv/<timestamp>_media3_comskip_matrix.json`

Send the console output plus this JSON report for the next review.
