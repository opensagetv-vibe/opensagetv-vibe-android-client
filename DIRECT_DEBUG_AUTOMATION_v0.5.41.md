# Direct Debug Automation v0.5.41

## Search text entry

The SageTV Search field is rendered by the MiniClient and is not a native Android `EditText`. Do not inject text by synchronously dispatching synthetic `KeyEvent`s from the debug BroadcastReceiver; that path can stall the receiver on Fire TV.

Preferred automated text sequence:

1. Send the SageTV Search command.
2. Wait for `hasTextInput=true`.
3. Wait for `imeVisibleKnown=true` and `imeVisible=true`.
4. Use `keyboardtext <text>` / `dev_input_text_keyboard(text)`.
5. The MCP host uses Android's OS `input text` service, which delivers input to the focused MiniClient view through Android's normal input system.
6. Hide the IME directly and verify `imeVisible=false` before continuing.

`sendtext` remains available as the low-level diagnostic primitive. `directtext` remains a compatibility alias to `keyboardtext`.

v0.5.41 is host/MCP-only and does not require rebuilding the v0.5.40 APK.
