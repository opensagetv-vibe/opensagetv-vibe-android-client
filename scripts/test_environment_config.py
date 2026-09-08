#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from sagetv_dev_mcp.config import load_test_environment


def resolve(environment, key: str):
    if key == "device.serial":
        return environment.device_serial()
    if key == "server.address":
        return environment.server_address()
    if key == "identity.automated_client_id":
        return environment.automated_client_id()
    if key.startswith("fixture."):
        return environment.fixture(key.split(".", 1)[1])
    if key.startswith("test_defaults."):
        return environment.test_default(key.split(".", 1)[1], "")
    if key.startswith("capture."):
        return environment.capture_value(key.split(".", 1)[1], "")
    raise ValueError(f"unsupported configuration key: {key}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate or inspect the shared local test-environment TOML")
    parser.add_argument("--config", type=Path)
    parser.add_argument("--device", help="select a configured device key/alias for this check")
    parser.add_argument("--server", help="select a configured server key/alias for this check")
    parser.add_argument("--get", metavar="KEY", help="print one non-secret workflow value")
    parser.add_argument("--summary", action="store_true", help="print a redacted JSON summary")
    args = parser.parse_args()

    if args.device:
        import os
        os.environ["SAGETV_TEST_DEVICE_ALIAS"] = args.device
    if args.server:
        import os
        os.environ["SAGETV_TEST_SERVER_ALIAS"] = args.server
    environment = load_test_environment(args.config.resolve() if args.config else None)
    errors = environment.validate(require_device=True)
    if errors:
        print("INVALID TEST ENVIRONMENT")
        for error in errors:
            print(f"- {error}")
        return 2
    if args.get:
        try:
            value = resolve(environment, args.get)
        except ValueError as exc:
            parser.error(str(exc))
        if isinstance(value, list):
            print(",".join(str(item) for item in value))
        else:
            print(value)
        return 0
    print("PASS: local test environment is valid")
    print(f"config: {environment.path or '<environment only>'}")
    print(f"active device: {environment.active_device} ({environment.device_serial()})")
    print(f"active server: {environment.active_server} ({environment.server_address()})")
    if args.summary:
        print(json.dumps(environment.redacted(), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
