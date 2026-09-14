# SMB server and folder setup

OpenSageTV Vibe uses one server-first SMB2/SMB3 workflow for media playback,
named configuration profiles, and diagnostic exports.

## Add a server

1. Open **Settings > SMB Direct Settings > Manage SMB servers**.
2. Choose **Add SMB server**.
3. Enter a friendly name, server/IP, and one share name.
4. Leave **Use authentication** unchecked for anonymous access. Check it to
   enable username, password, and optional domain fields.
5. Save the server, then select it and use **Test connection**.

Port 445 is used by default. A custom port can be entered with the server,
such as `nas.example:1445`. Bracketed IPv6 with a port is supported, such as
`[fe80::10]:1445`.

Credentials stay in device-local preferences. They are excluded from named
profiles, diagnostic bundles, telemetry, and UI summaries.

## Select folders

Each destination is configured independently:

- **Media path mappings** opens a mapping submenu. Add a mapping by entering
  the server folder SageTV reports, choosing a saved SMB server, and browsing
  to the corresponding SMB folder. All three values remain visible together
  in the Media Mapping dialog. **Test** verifies the selected share/folder
  before **Apply** creates the mapping. Existing mappings can be changed,
  tested, or removed.
- **Browse configuration folder** selects where named MiniClient profiles are
  stored.
- **Browse diagnostics folder** selects the independent destination used by
  On request or Always diagnostic export.

Folder rows use a folder icon. The first `..` row moves to the parent folder;
at the share root it returns to the saved-server chooser. **Select this
folder** accepts the folder currently named in the dialog title.

The client still materializes credential-free `smb://` URLs and historical
mapping properties internally, so existing SMB Direct playback, named-profile,
and diagnostic code remains compatible. Manually configured older settings
continue to load and are used as a fallback when no saved server profile
matches a media mapping.

## Installing development APK updates

`dev.cmd install` (or `./dev.sh install`) performs an in-place verified APK
update and preserves commissioned servers, the client identity, and all app
settings. Use the separately named `install-clean` command only when an
intentional full reset is required.
