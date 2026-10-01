# Changelog

All notable changes to the Scout mobile RUM SDKs published from this repository:

- **scout-core** — shared Kotlin Multiplatform core (OpenTelemetry span/metric/log pipeline, batching, offline buffer, crash persistence)
- **scout-android** — Android SDK (auto-instrumentation: taps, screens, HTTP, ANR, crashes, vitals)
- **scout-ios** — iOS SDK (Kotlin/Native engine + `ScoutKit` Swift wrapper, distributed as an xcframework)
- **scout-kmp** — unified Kotlin Multiplatform facade that apps depend on

Each SDK is published to Maven Central independently via its own tag (`core-`, `android-`, `ios-`, `kmp-`). Releases are cut in coordinated waves; every entry below lists the per-SDK versions that shipped together. Versions are not aligned across SDKs — a wave bumps only the SDKs that changed, so version numbers can skip.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## Unreleased — scout-core · scout-android · scout-ios · scout-kmp

### Added
- **Cellular generation on every span: `network.connection.subtype`.** `network.connection.type` only ever distinguished transports (`wifi` / `cellular` / `ethernet`), so a 5G session and an EDGE session were both just `cellular`. The dynamic-attribute provider now also reports the radio access technology — `nr`, `nrnsa`, `lte_ca`, `lte`, `hspap`, `umts`, `edge`, … — using the OpenTelemetry `network.connection.subtype` value space, and only while the active transport is cellular.
  - **Android** reads `TelephonyCallback.DisplayInfoListener` on API 31+, which needs no permission and distinguishes 5G NSA (`nrnsa`) and 5G Advanced (`nr`) from the LTE anchor the base network type reports. Below API 31 it falls back to `TelephonyManager.getDataNetworkType()`, and only when the host app already holds `READ_PHONE_STATE` — the SDK never declares nor requests that permission, and reports no subtype instead.
  - **iOS** reads `CTTelephonyNetworkInfo.serviceCurrentRadioAccessTechnology`, which is unaffected by the iOS 16 `CTCarrier` deprecation.
  - Forwarded spans from bridged SDKs (scout-flutter, and web pages relayed through it) inherit the value, since the dynamic provider runs for every ingested span.
  - The API 31+ path is callback-driven, so the first spans of a launch — typically `app_startup` and the earliest vitals — can ship before the first `onDisplayInfoChanged` arrives and carry no subtype. Reading it synchronously at install time would require `READ_PHONE_STATE`, which is not worth a permission prompt.

## 2026-08-04 — scout-core 0.1.8 · scout-android 0.1.8 · scout-ios 0.1.10 · scout-kmp 0.1.10

### Added
- **MetricKit diagnostics in the Kotlin/Native iOS engine.** `MXCrashDiagnostic` and `MXHangDiagnostic` payloads are now subscribed and reported by the engine itself, so KMP and bridge consumers receive iOS crash/hang telemetry (previously only the Swift `ScoutKit` wrapper subscribed).

### Changed
- **`maxOfflineStorageMb` is now enforced.** The offline buffer prunes `cache/scout_offline` with FIFO (oldest-first) eviction once the directory exceeds the configured cap, on a periodic schedule and on `forceFlush()`.

### Removed
- **Unenforceable offline item-cap config knobs** `offlineMaxTraceItems`, `offlineMaxMetricItems`, and `offlineMaxLogItems` were dropped from `ScoutConfig` — the underlying persisting processor never honored per-signal item counts. Size-based capping via `maxOfflineStorageMb` replaces them.

## 2026-07-27 — scout-core 0.1.7 · scout-android 0.1.7 · scout-ios 0.1.9 · scout-kmp 0.1.9

### Fixed
- **Durable, cross-process crash de-duplication.** Replaced the per-launch counter with a durable filesystem ledger (`cache/scout_reported_crashes`) so a crash reported richly (with full stack trace) is never re-emitted as a thin `ApplicationExitInfo` duplicate on a later launch or from a second process.
- **Durable crash persistence.** `persistCrash` now `fsync`s the pending crash file before the atomic move and skips blank pending files on replay, so a crash's stack trace survives rapid successive crashes instead of being lost to a 0-byte file.

### Added
- **`forceFlush()` on app background** so buffered telemetry is exported before the process is suspended.

## 2026-07-24 — scout-core 0.1.6 · scout-android 0.1.6 · scout-ios 0.1.8 · scout-kmp 0.1.8

### Fixed
- **Honor the full `ScoutConfig`.** All configured batch and retry limits are now enforced end-to-end, and iOS emits a valid `traceparent` and `phys_footprint` memory metric.

## 2026-07-22 — scout-ios 0.1.5/0.1.6/0.1.7 · scout-kmp 0.1.7

### Added
- **Full config forwarding on iOS** through `configure`, `start`, and `startBridge`.

### Changed
- **Fully automated iOS publishing.** The `publish-ios` workflow is dispatch-triggered and builds the xcframework, pins the `Package.swift` checksum, publishes the klib, and creates the tag + GitHub Release in one step (no manual tagging).

## 2026-07-21 — scout-core 0.1.5 · scout-android 0.1.5

### Added
- **`reportError` on the common API** (plus a string-based `reportError` overload on Android) for manual error reporting.

### Changed
- **Unified batch/export configuration** with metric batching, an offline-buffer gate, and debug export logging in the core.
- **Vitals metrics are gated by config flags** and HTTP connections are reused on Android.
- **All iOS instrumentation moved into the Kotlin/Native engine** (crash, ANR, tap, HTTP, screen, jank, metrics), consolidating platform logic in shared code.

## 2026-07-13 — scout-core 0.1.3 · scout-android 0.1.3 · scout-ios 0.1.4 · scout-kmp 0.1.3

### Added
- **Per-component SDK version resource attributes** (`scout.core.version`, `scout.android.version`, `scout.ios.version`, `scout.kmp.version`) on all emitted telemetry.
- **`ScoutNative` product exposed for SPM consumers.**

### Fixed
- **`expect`/`actual` filesystem accessor** for metadata so the core compiles cleanly across all targets.

## 2026-07-12 — scout-ios 0.1.3

### Added
- **Bridge ingest API.** `startBridge` plus breadcrumb and metric bridge ingest, forwarded-metrics ingest, and breadcrumb replace ingest, enabling hybrid/delegating consumers (e.g. Flutter) to feed telemetry through the native SDK.

### Fixed
- **Session-rotation recursion** and a move to a **file-based pending crash store**; non-empty crash messages; `screen_load`/`view_session` durations emitted in seconds.

## 2026-07-07 — scout-core 0.1.2 · scout-android 0.1.2 · scout-ios 0.1.2 · scout-kmp 0.1.1/0.1.2

### Added
- **RUM attribute constants** in the core.
- **Device and network attributes** on Android and iOS.
- **Screen, tap, and lifecycle attributes** with delegation-aware screen tracking.
- **Dynamic framework + `ScoutNative` product** for iOS.
- **scout-kmp publishing** (publishes only the KMP facade, pinning core/android/ios).

## 2026-07-06 — scout-core 0.1.1 · scout-android 0.1.1 · scout-ios 0.1.1

### Added
- **Maven Central publishing.** Per-SDK publishing workflows so each artifact publishes only itself and pins its Scout dependencies; releases gated on CI (`make ci`).
- xcframework checksum pinning for the iOS release.

## 0.1.0 — Initial release

### Added
- **scout-android** — Android RUM SDK with auto-instrumentation, published to Maven Central (vanniktech), MIT licensed, `minSdk 26`.
- **scout-ios** — iOS SDK (Kotlin 2.3) with reportError, resource attributes, metrics, taps, jank, MetricKit, `URLSession.shared` HTTP coverage, `screen_view` root spans, breadcrumbs on error/crash/anr, and an `app_crash` span.
- **scout-kmp** — unified Kotlin Multiplatform module exposing the full manual API on iOS and KMP.
