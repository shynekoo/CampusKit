# SPDX-License-Identifier: MPL-2.0
"""Guard the diagnostics build configurations.

Run with: python scripts/test_build_config.py

Two switches, deliberately separate:

* ``DIAG_BASIC`` is always compiled in. It is the audited set - a fixed field whitelist, no free
  text, no SDK payloads, no network - and it stays off at runtime until the reader turns it on.
  Shipping it in the release is what lets a normal user report "课表加载失败" with evidence.
* ``DIAGNOSTICS`` (``--diagnostics``) adds the high-density detail events. A normal release and a
  water-control release both leave it out; internal triage opts in explicitly.

Runtime default-off is asserted by CoreTests against DiagPolicy.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_preview import diagnostics_enabled  # noqa: E402


def main():
    cases = [
        ([], False, "normal release: basic diagnostics only"),
        (["--field-water"], False, "water-control release: basic diagnostics only"),
        (["--diagnostics"], True, "explicit internal build: detail events compiled in"),
        (["--field-water", "--diagnostics"], True, "field build can opt in explicitly"),
        (["--field-water", "--no-diagnostics"], False, "older explicit-off flag stays off"),
    ]
    for argv, expected, label in cases:
        actual = diagnostics_enabled(argv)
        if actual != expected:
            raise AssertionError("%s: expected %s, got %s" % (label, expected, actual))
        print("PASS: %s" % label)
    # The generated source is the only place the two flags are decided. If DIAG_BASIC ever stops
    # being unconditional, a release would silently lose the page users are told to open.
    import build_preview
    source = build_preview.write_build_flags(False, False, False)
    text = source.read_text(encoding="utf-8")
    for needle in ("static final boolean DIAG_BASIC = true;",
                   "static final boolean DIAGNOSTICS = false;"):
        if needle not in text:
            raise AssertionError("generated BuildFlags is missing %r" % needle)
    detail = build_preview.write_build_flags(False, False, True).read_text(encoding="utf-8")
    if "static final boolean DIAGNOSTICS = true;" not in detail:
        raise AssertionError("--diagnostics did not reach the generated BuildFlags")
    print("PASS: generated flags keep basic diagnostics on and detail events opt-in")
    print("PASS: all %d diagnostics build configurations" % (len(cases) + 2))


if __name__ == "__main__":
    main()
