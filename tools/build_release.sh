#!/usr/bin/env bash
set -euo pipefail
: "${APD_KEYSTORE:?Absolute path to your release keystore}"
: "${APD_STORE_PASSWORD:?Keystore password}"
: "${APD_KEY_ALIAS:?Signing alias}"
: "${APD_KEY_PASSWORD:?Key password}"
flutter pub get
flutter analyze
flutter test
(cd android && ./gradlew :app:testDebugUnitTest :app:lintRelease)
flutter build apk --release
