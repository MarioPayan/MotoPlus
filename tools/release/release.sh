#!/usr/bin/env bash
# Build the signed release APK and publish it as a GitHub release tagged v<versionName>.
# Signing keys stay on this machine (see app/build.gradle.kts); nothing secret is uploaded.
set -euo pipefail
cd "$(dirname "$0")/../.."
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit your changes first." >&2; exit 1; }
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/motoplus-tools/jdk-21.0.13+9}"
./gradlew --quiet test assembleRelease
version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
apk="app/build/outputs/apk/release/MotoPlus-$version.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"
apksigner_bin=$(ls "$HOME"/Android/Sdk/build-tools/*/apksigner | tail -1)
"$apksigner_bin" verify "$apk"
git push origin HEAD
gh release create "v$version" "$apk" --target "$(git rev-parse HEAD)" --title "MotoPlus $version" --generate-notes
