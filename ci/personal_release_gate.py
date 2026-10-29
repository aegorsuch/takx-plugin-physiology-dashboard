"""Fail closed until TAKX distributable and rolling-release safety are verified."""

import sys


SOURCE = "https://git.tak.gov/raptor/takx-plugins/takx-plugin-physiology-dashboard"
DESTINATION = "aegorsuch/takx-plugin-physiology-dashboard"
BLOCKERS = (
    "Run clean check assemble on canonical Forge main with authorized TAK "
    "repository credentials. Local configuration failed: "
    "No username configured for repo null/tak.",
    "Verify the exact native jar task output, TAKX manifest, bundled dependencies "
    "and installation compatibility; do not substitute an APK or arbitrary JAR.",
    "Record SHA-256, canonical commit, pipeline and producing build job for that "
    "exact package, then verify the downloaded artifact against that record.",
    "Confirm inherited CI/package policies with a Forge maintainer; policy and "
    "variable inspection was unavailable to this account.",
    "Implement and test the established fixed-tag rolling-release publisher "
    "with destination allowlisting, managed-release ownership, protected-main "
    "checks, concurrency/stale-pipeline guards and refusal to overwrite unknown "
    "releases, tags or refs.",
)


def main():
    print("PERSONAL GITHUB PUBLICATION BLOCKED", file=sys.stderr)
    print(f"Source: {SOURCE} (project 6850, main)", file=sys.stderr)
    print(f"Only permitted destination: {DESTINATION}", file=sys.stderr)
    for blocker in BLOCKERS:
        print(f"- {blocker}", file=sys.stderr)
    print(
        "No publisher is enabled. No credentials, uploads or remote ref writes "
        "are attempted. CI variables cannot bypass this gate.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
