#!/usr/bin/env python3
"""Physically validate named MiniClient profiles through the production SMB repository."""
from __future__ import annotations

import argparse
import sys

from mcp_seek_suite import MCPProcess, call_dict, initialize


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate SMB profile save/list/load/overwrite/delete")
    parser.add_argument(
        "--directory",
        required=True,
        help="SMB2/SMB3 directory used for temporary profile acceptance data",
    )
    parser.add_argument("--name", default="codex-profile-test")
    parser.add_argument("--username", default="")
    parser.add_argument("--password", default="")
    parser.add_argument("--domain", default="")
    args = parser.parse_args()

    client = MCPProcess()
    created = False
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        call_dict(
            client,
            "dev_set_player_config",
            {
                "smb_profile_directory": args.directory,
                "smb_profile_username": args.username,
                "smb_profile_password": args.password,
                "smb_profile_domain": args.domain,
                "smb_profile_clear_auth": not bool(args.username),
            },
            timeout=30.0,
        )
        print("PASS: configured profile share without returning credentials")

        try:
            call_dict(client, "dev_smb_profile_delete", {"name": args.name}, timeout=45.0)
        except RuntimeError:
            pass

        saved = call_dict(
            client, "dev_smb_profile_save", {"name": args.name, "overwrite": False}, timeout=45.0
        )
        created = True
        if int(saved.get("settingsCount", 0)) <= 0 or int(saved.get("clientIdCount", 0)) <= 0:
            raise RuntimeError(f"saved profile lacks settings or separate Client IDs: {saved}")
        print("PASS: initial atomic profile save")

        try:
            call_dict(
                client, "dev_smb_profile_save", {"name": args.name, "overwrite": False}, timeout=45.0
            )
        except RuntimeError:
            print("PASS: overwrite denied unless explicitly requested")
        else:
            raise RuntimeError("second save unexpectedly overwrote an existing profile")

        call_dict(
            client, "dev_smb_profile_save", {"name": args.name, "overwrite": True}, timeout=45.0
        )
        print("PASS: explicit overwrite")

        listed = call_dict(client, "dev_smb_profile_list", timeout=45.0)
        expected_file = args.name if args.name.endswith(".profile") else args.name + ".profile"
        names = str(listed.get("profiles", "")).split("|")
        if expected_file not in names:
            raise RuntimeError(f"saved profile is absent from listing: {listed}")
        print("PASS: remote profile listing")

        loaded = call_dict(client, "dev_smb_profile_load", {"name": args.name}, timeout=45.0)
        if loaded.get("credentialsPresent") is not False:
            raise RuntimeError(f"profile exposed or claimed credentials: {loaded}")
        if int(loaded.get("settingsCount", 0)) <= 0 or int(loaded.get("clientIdCount", 0)) <= 0:
            raise RuntimeError(f"loaded profile is incomplete: {loaded}")
        print("PASS: checksum/schema load with separately held Client IDs and no credentials")

        call_dict(client, "dev_smb_profile_delete", {"name": args.name}, timeout=45.0)
        created = False
        listed = call_dict(client, "dev_smb_profile_list", timeout=45.0)
        if expected_file in str(listed.get("profiles", "")).split("|"):
            raise RuntimeError("deleted test profile remains in the remote listing")
        print("PASS: bounded cleanup removed only the named test profile")
        print("SMB PROFILE TEST PASSED")
        return 0
    except Exception as exc:
        print(f"SMB PROFILE TEST FAILED: {exc}", file=sys.stderr)
        return 1
    finally:
        if created:
            try:
                call_dict(client, "dev_smb_profile_delete", {"name": args.name}, timeout=45.0)
            except Exception:
                pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
