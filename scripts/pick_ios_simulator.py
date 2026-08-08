#!/usr/bin/env python3
"""Pick an iPhone simulator from `xcrun simctl list -j` output (stdin).

Output (stdout, single line):
  udid <UDID>                        an available iPhone simulator already exists
  create <deviceType> <runtime>      no device, but an iOS runtime is installed:
                                     caller should `xcrun simctl create` one
  (nothing)                          no usable iOS runtime at all

Selection prefers the newest iOS runtime, then prefers plain "iPhone 16"
family names for determinism.
"""

import json
import re
import sys


def runtime_version(runtime_key: str):
    m = re.search(r"SimRuntime\.iOS-(\d+)-(\d+)", runtime_key)
    return (int(m.group(1)), int(m.group(2))) if m else None


def device_family(name: str):
    m = re.search(r"iPhone-?(\d+)", name.replace(" ", "-"))
    return int(m.group(1)) if m else 0


def main() -> None:
    data = json.load(sys.stdin)

    best_device = None  # (version_tuple, family, udid)
    for runtime_key, devices in (data.get("devices") or {}).items():
        ver = runtime_version(runtime_key)
        if ver is None:
            continue
        for dev in devices:
            if not dev.get("isAvailable"):
                continue
            if "iPhone" not in dev.get("deviceTypeIdentifier", ""):
                continue
            score = (ver, device_family(dev.get("name", "")), dev.get("name", ""))
            if best_device is None or score > best_device[0]:
                best_device = (score, dev["udid"])

    if best_device is not None:
        print(f"udid {best_device[1]}")
        return

    best_pair = None  # (version_tuple, family, deviceTypeIdentifier, runtimeIdentifier)
    runtimes = [
        r for r in (data.get("runtimes") or [])
        if r.get("isAvailable") and runtime_version(r.get("identifier", "")) is not None
    ]
    devicetypes = [
        t for t in (data.get("devicetypes") or [])
        if "iPhone" in t.get("identifier", "")
    ]
    for rt in runtimes:
        ver = runtime_version(rt["identifier"])
        for dt in devicetypes:
            score = (ver, device_family(dt.get("name", "")))
            if best_pair is None or score > best_pair[0]:
                best_pair = (score, dt["identifier"], rt["identifier"])

    if best_pair is not None:
        print(f"create {best_pair[1]} {best_pair[2]}")


if __name__ == "__main__":
    main()
