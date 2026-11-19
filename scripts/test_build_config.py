"""Guard the three diagnostics build configurations.

Run with: python scripts/test_build_config.py

The rule under test: diagnostics are compiled in only when explicitly requested. A normal
release and a water-control release both ship without the logging code; an internal build opts
in with --diagnostics. Runtime default-off is asserted by CoreTests against DiagPolicy.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_preview import diagnostics_enabled  # noqa: E402


def main():
    cases = [
        ([], False, "normal release: no diagnostics"),
        (["--field-water"], False, "water-control release: no diagnostics by default"),
        (["--diagnostics"], True, "explicit internal build: diagnostics compiled in"),
        (["--field-water", "--diagnostics"], True, "field build can opt in explicitly"),
        (["--field-water", "--no-diagnostics"], False, "older explicit-off flag stays off"),
    ]
    for argv, expected, label in cases:
        actual = diagnostics_enabled(argv)
        if actual != expected:
            raise AssertionError("%s: expected %s, got %s" % (label, expected, actual))
        print("PASS: %s" % label)
    print("PASS: all %d diagnostics build configurations" % len(cases))


if __name__ == "__main__":
    main()
