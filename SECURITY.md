# Security policy

## Supported code

Security fixes are applied to the current `main` branch. Historical snapshots
and the frozen `source/existing` comparison tree are retained for review and
are not separately supported releases.

## Reporting a vulnerability

Use GitHub's private vulnerability-reporting feature for this repository. Do
not open a public issue containing credentials, device identifiers, server
paths, private media, or exploit details. Include the affected version,
reproduction steps, expected impact, and any proposed mitigation.

## Secrets and development builds

The checked-in project contains no production signing key. Debug builds use a
development certificate and expose bounded MCP/debug surfaces; they are for
commissioning and testing, not a hardened public deployment. Local Fire TV,
SMB, and SageTV credentials belong only in ignored configuration files.
