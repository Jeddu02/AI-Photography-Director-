#!/usr/bin/env python3
"""Check the APK's version and match its signers to the last verified release."""

import argparse
from pathlib import Path
import re


def signing_certificates(evidence: Path) -> list[str]:
    signature_report = (evidence / "apk-signature.txt").read_text()
    certificates = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$",
        signature_report,
        re.IGNORECASE | re.MULTILINE,
    )
    if not certificates:
        raise SystemExit(f"No signer SHA-256 certificates found in {evidence}")
    return sorted(digest.lower() for digest in certificates)


def verify(pubspec: Path, candidate: Path, baseline: Path) -> None:
    expected = re.search(
        r"^version:[ \t]*([0-9]+\.[0-9]+\.[0-9]+)\+([0-9]+)[ \t]*$",
        pubspec.read_text(),
        re.MULTILINE,
    )
    if expected is None:
        raise SystemExit("Expected a Flutter version and Android build number")

    badging = (candidate / "apk-metadata.txt").read_text()
    actual = re.search(
        r"^package: .*?\bversionCode='([0-9]+)'\s+versionName='([^']+)'",
        badging,
        re.MULTILINE,
    )
    if actual is None or actual.groups() != (expected[2], expected[1]):
        raise SystemExit(f"APK version does not match {expected[1]}+{expected[2]}")

    if signing_certificates(candidate) != signing_certificates(baseline):
        raise SystemExit("APK signer differs from the v0.13.0 release signer")
    print(f"APK version {expected[1]}+{expected[2]} and upgrade signing certificate verified")


if __name__ == "__main__":
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("pubspec", type=Path)
    args.add_argument("candidate_evidence", type=Path)
    args.add_argument("baseline_evidence", type=Path)
    options = args.parse_args()
    verify(options.pubspec, options.candidate_evidence, options.baseline_evidence)
