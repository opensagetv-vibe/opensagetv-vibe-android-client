# Public Source Bootstrap — Emergency Fallback Only

The exact user-supplied repository is already bundled and is the authoritative baseline for this project.

Do not run public bootstrap during normal setup.

If the bundled source trees are intentionally removed and no exact source ZIP is available, the fallback is:

```bash
./dev.sh bootstrap-both
```

Default public ref: `OpenSageTV/sagetv-miniclient` tag `v1.14.0`.

This is only a recovery/convenience path. Before player behavioral work, prefer restoring the exact source represented by `source/SOURCE_IMPORT.json`.
