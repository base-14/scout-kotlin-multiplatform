#!/usr/bin/env bash
# Assembles the release Scout.xcframework, zips it, and prints the SPM checksum.
# For a release: run this, upload the zip to a release host, then set the binaryTarget
# `url` + `checksum` in Package.swift (remote) or point the podspec at the zip.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/scout-ios/build/XCFrameworks/release"
ZIP="$ROOT/scout-ios/build/Scout.xcframework.zip"

echo "==> Assembling release XCFramework"
"$ROOT/gradlew" -p "$ROOT" :scout-ios:assembleScoutReleaseXCFramework -Pscout.enableIos=true

echo "==> Zipping"
rm -f "$ZIP"
(cd "$OUT" && zip -r -q "$ZIP" Scout.xcframework)

echo "==> Checksum (put this in Package.swift's remote binaryTarget)"
swift package --package-path "$ROOT/scout-ios" compute-checksum "$ZIP"
echo "==> Zip: $ZIP"
