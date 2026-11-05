# CLAUDE.md

Guidance for agents working in this repo. It publishes scout-core, scout-android, scout-ios and
scout-kmp. scout-flutter (a separate repo) pins scout-android and scout-ios from here.

## Releasing

**Read and follow [`RELEASING.md`](RELEASING.md) for any release, version pin or publish.** In short:

1. Tag `core-X`, then wait until scout-core X is live on Maven Central.
2. Set `scout.coreVersion=X` in `gradle.properties` and commit it to `main`.
3. Tag `android-Y` and dispatch `publish-ios.yml` with version Y. Wait until both are live on
   Maven Central.
4. Set `scout.androidVersion` and `scout.iosVersion` to Y, commit, tag `kmp-Z`, then update
   `CHANGELOG.md`.
5. In scout-flutter, pin `scout-android:Y` and `ios-Y`, bump the version, run `make release`.

Never tag a module before the version it pins is on Maven Central. Get explicit human confirmation
before every tag push or workflow dispatch, and push tags one at a time.

## Local build

- Set `ANDROID_HOME` (e.g. `/opt/homebrew/share/android-commandlinetools`), then run `make ci`.
- `make ci` skips iOS. To compile iOS, run
  `./gradlew -Pscout.enableIos=true :scout-ios:linkDebugFrameworkIosSimulatorArm64 :scout-kmp:compileKotlinIosSimulatorArm64`.
- To test unreleased changes in an app, use
  `./gradlew :scout-core:publishToMavenLocal :scout-android:publishToMavenLocal -PreleaseVersion=<v>-local`.
  Use a `-local` suffix: a real version number in `~/.m2` shadows the eventual release.
