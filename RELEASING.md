# Releasing the Scout mobile SDKs

This is the release process for scout-core, scout-android, scout-ios and scout-kmp (this repo), and for
[scout-flutter](https://github.com/base-14/scout-flutter), which pins scout-android and scout-ios.
Follow it in order. Do not skip steps or run them in parallel.

## Why the order matters

Release builds (`-Prelease`) don't build the other modules from source. Each one depends on the
**published** artifact named in `gradle.properties`:

```properties
scout.coreVersion=…     # scout-android, scout-ios and scout-kmp pin this scout-core
scout.androidVersion=…  # scout-kmp pins this scout-android
scout.iosVersion=…      # scout-kmp pins this scout-ios
```

So a module can only be released after the version it pins is **live on Maven Central**. If you tag
too early, the publish job fails to resolve the dependency. Worse, it can ship an artifact whose POM
points at a version that doesn't exist. scout-flutter is one more step down the same chain:
`android/build.gradle` pins `io.base14:scout-android`, and `ios/scout_flutter/Package.swift` pins
the `ios-<version>` tag.

```
scout-core ─▶ scout-android ─┐
           └▶ scout-ios ─────┴▶ scout-kmp
                  scout-android + scout-ios ─▶ scout-flutter
```

## Rules for agents

- **Get explicit human confirmation before every tag push or workflow dispatch.** A publish to
  Maven Central or pub.dev can't be undone, and a version number can never be reused.
- **Push tags one at a time.** GitHub sends no push events when more than three tags are pushed
  together, so the publish workflows would never start.
- Every tag goes on a `main` commit whose CI is green.
- Release only what changed. A wave bumps only the SDKs it touches, so versions skip and are not
  aligned across SDKs.
- Don't move on to the next step until the previous artifact is live on Maven Central. Check it as
  shown below; a successful workflow run alone is not proof.

### Checking Maven Central

```bash
v=0.1.11
curl -sfI "https://repo1.maven.org/maven2/io/base14/scout-core/$v/scout-core-$v.pom" >/dev/null && echo live || echo "not yet"
# same for scout-android, scout-ios, scout-kmp
```

The publish jobs call `publishAndReleaseToMavenCentral`. After the job succeeds, Central still takes
from about 10 minutes to an hour to sync. Poll every few minutes and don't re-tag.

## Steps

Replace `X`, `Y` and `Z` below with the new versions. Use the `Unreleased` heading in `CHANGELOG.md`
to see which SDKs a wave includes.

### 1. Release scout-core

```bash
git tag core-X && git push origin core-X     # runs .github/workflows/publish-core.yml
```

Wait for the workflow, then wait until `scout-core` X is **live on Maven Central**.

### 2. Point scout-android and scout-ios at the new core

In `gradle.properties`, set `scout.coreVersion=X`. Commit it to `main`
(`release: pin scout-core X for the android and ios releases`), push, and wait for CI.

### 3. Release scout-android and scout-ios

```bash
git tag android-Y && git push origin android-Y      # publish-android.yml
gh workflow run publish-ios.yml -f version=Y         # publish-ios.yml
```

`publish-ios.yml` is dispatched, not tagged. It builds the xcframework, commits the checksum pin to
`Package.swift` on `main`, publishes the `scout-ios` klib, then creates the `ios-Y` tag and GitHub
Release itself. Pull `main` afterwards, because the workflow pushed a commit.

Wait until `scout-android` Y and `scout-ios` Y are both **live on Maven Central**.

### 4. Point scout-kmp at the new android and ios, then release it

In `gradle.properties`, set `scout.androidVersion=Y` and `scout.iosVersion=Y`. Commit it to `main`
(`release: pin scout-android Y and scout-ios Y for the kmp release`), push, and wait for CI. Then:

```bash
git tag kmp-Z && git push origin kmp-Z               # publish-kmp.yml
```

Wait until `scout-kmp` Z is **live on Maven Central**. Then replace the `Unreleased` heading in
`CHANGELOG.md` with the wave's versions and date, and commit it (`release: changelog for the … wave`).

### 5. Point scout-flutter at the new native SDKs, then release it

This happens in the scout-flutter repo, after `scout-android` Y is live on Maven Central and the
`ios-Y` tag exists:

1. Make one release commit on `main` (`release <version>: pin scout-android Y and scout-ios Y`):
   - `android/build.gradle`: `implementation 'io.base14:scout-android:Y'`
   - `ios/scout_flutter/Package.swift`: `revision: "ios-Y"`
   - `pubspec.yaml` `version:` and `lib/src/scope.dart` `kScopeVersion`: the new Flutter version.
     They must match, which `scope_version_sync_test.dart` enforces.
   - `CHANGELOG.md`: turn `## Unreleased` into `## <version>`.
2. Run `make ci`, push, and wait for CI.
3. Run `make release`. It tags `v<version>` from `pubspec.yaml`, and `publish.yml` publishes to
   pub.dev.

A scout-flutter change that depends on unreleased native APIs must not merge before step 5 is
possible.

## Partial waves

- **Only scout-android changed:** do step 3 (android only), the android half of step 4, then step 5
  if Flutter needs it.
- **Only scout-ios changed:** do step 3 (iOS dispatch only), the iOS half of step 4, then step 5 if
  Flutter needs it.
- **Only scout-core changed:** do every step. Each downstream SDK has to be re-published to pick
  up the new core.
