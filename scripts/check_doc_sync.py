"""Remind which docs may need an update after a code change, based on Git diff.

 Advisory only: never modifies files, never fails the build by default. The point is to make
 "I forgot the docs" visible, not to block a legitimate format-only change. An explicit waiver
 is honoured (see --help). Used by AGENTS.md workflow and the docs CI workflow.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# path fragment (relative to repo root) -> docs that describe that area
DOC_MAP = (
    ("client/core/", ("docs/architecture.md", "docs/modules.md", "docs/data-interfaces.md")),
    ("client/android/", ("docs/modules.md", "docs/ui-navigation.md", "docs/data-interfaces.md")),
    ("client/fieldtest/", ("docs/modules.md", "docs/security.md")),
    ("client/android/AndroidManifest.xml", ("docs/security.md", "docs/modules.md")),
    ("scripts/build_preview.py", ("docs/build-and-release.md", "docs/known-issues.md")),
    ("scripts/", ("docs/build-and-release.md",)),
    (".github/workflows/", ("docs/build-and-release.md",)),
    ("delivery/update/", ("docs/build-and-release.md", "CHANGELOG.md")),
    ("client/THIRD_PARTY.md", ("docs/build-and-release.md",)),
)

DOC_SUFFIXES = (".md",)
WAIVER_FILE = ROOT / ".doc-sync-waiver"
TRAILER = re.compile(r"^Docs-Not-Needed:\s*\S+", re.MULTILINE)


def git(*args):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, errors="replace")
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout


def changed_paths(base):
    if base:
        names = git("diff", "--name-only", f"{base}...HEAD").splitlines()
    else:
        names = git("diff", "--name-only").splitlines()
        names += git("ls-files", "--others", "--exclude-standard").splitlines()
    return sorted({n.strip() for n in names if n.strip()})


def waiver_reason(base):
    if WAIVER_FILE.exists():
        text = WAIVER_FILE.read_text(encoding="utf-8", errors="replace").strip()
        if text and not text.startswith("#"):
            return f".doc-sync-waiver: {text.splitlines()[0].strip()}"
    commit_range = f"{base}..HEAD" if base else "-1"
    log = git("log", commit_range, "--format=%B%x00")
    for message in log.split("\x00"):
        found = TRAILER.search(message.strip())
        if found:
            return found.group(0).strip()
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="compare this ref...HEAD; default: working tree incl. untracked")
    parser.add_argument("--strict", action="store_true", help="exit 1 when docs may need an update")
    args = parser.parse_args()

    paths = changed_paths(args.base)
    docs = [p for p in paths if p.endswith(DOC_SUFFIXES)]
    code = [p for p in paths if not p.endswith(DOC_SUFFIXES)]

    if not code:
        print("DOC-SYNC: no code changes detected; nothing to check.")
        return 0

    if docs:
        print("DOC-SYNC: code and docs both changed in this diff:")
        for d in docs:
            print(f"  doc   {d}")
        return 0

    reason = waiver_reason(args.base)
    if reason:
        print(f"DOC-SYNC: explicitly declared no doc update needed -> {reason}")
        return 0

    suggested = set()
    for path in code:
        for fragment, targets in DOC_MAP:
            if path == fragment or path.startswith(fragment):
                suggested.update(targets)

    print("DOC-SYNC: code changed, no docs changed in this diff.")
    print("  Check whether these docs need an incremental update (no rewrite):")
    for doc in sorted(suggested) or ("docs/architecture.md",):
        print(f"    - {doc}")
    print("  If behaviour/architecture/interface/UI/security is unchanged, declare it with")
    print('  a commit trailer "Docs-Not-Needed: <specific reason>" or a one-line .doc-sync-waiver.')
    print("  Advisory only; see AGENTS.md section 2.")
    return 1 if args.strict else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # keep advisory behaviour even when git is unavailable
        print(f"DOC-SYNC: skipped ({exc})", file=sys.stderr)
        sys.exit(0)
