# Scout Kotlin Multiplatform

OpenTelemetry-based Real User Monitoring SDK for Kotlin/Android (and, in progress, iOS),
conforming to the Base14 Scout `semantics.md` contract. This is the **monorepo** for the
Scout mobile SDK family: a shared Kotlin Multiplatform core (`scout-core`) plus thin
per-platform bindings (`scout-android` today, `scout-ios` next), built on top of
[`opentelemetry-kotlin`](https://github.com/open-telemetry/opentelemetry-kotlin) with a custom
OTLP/JSON exporter.

## Development

All CI checks run behind a single `make` target — this is exactly what the GitHub Actions
workflow runs:

```bash
make ci          # fmt-check + lint + test + build (the full CI gate)
make fmt         # auto-format all Kotlin (Spotless + ktlint)
make test        # KMP core unit + semantics-conformance tests
make build       # assemble the library AAR + core jar
make help        # list all targets
```

Toolchain: JDK 17, Android SDK 35 + NDK 27.1.12297006 + CMake 3.22.1.

## Quick start

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Scout.initialize(
            this,
            ScoutConfig(
                serviceName = "my-app",
                endpoint = "https://collector.example",  // /v1/traces is appended
                headers = mapOf("Authorization" to "Bearer …"),  // optional auth
            ),
        )
    }
}
```

That single call wires up transport, session management, resource attributes, and **all
auto-instrumentation**. For HTTP, add the interceptor to your OkHttp client:

```kotlin
OkHttpClient.Builder().addInterceptor(ScoutOkHttpInterceptor()).build()
```

Optional API: `Scout.setUser`, `clearUser`, `setUserSampling`, `setEnabled`, `logEvent`, `addBreadcrumb`, `reportError`.

## Sampling

By default each session is sampled independently at `sessionSampleRate` (a percentage, default
`1.0`). Error-class spans bypass sampling while `alwaysCaptureErrors` is on.

**User-based sampling.** To decide per user, for example from your own remote config or feature
flags, pass the value your app already has:

```kotlin
// At startup: the last-fetched remote-config value, read synchronously from its cache.
ScoutConfig(serviceName = "my-app", endpoint = "…", userSampling = remoteConfig.cachedBoolean("scout_user_sampling"))

// When the value arrives or changes (fetch completes, user logs in):
Scout.setUserSampling(true)   // keep this user's sessions
Scout.setUserSampling(false)  // drop this user's spans; logs + metrics still flow
Scout.setUserSampling(null)   // no opinion: fall back to sessionSampleRate
```

```swift
Scout.start(serviceName: "my-app", endpoint: "…", userSampling: cachedValue)
Scout.setUserSampling(false)
```

- The decision is made **once per session**. A new value applies at the next session start (idle
  timeout, max duration, next launch, or user switch), never mid-session.
- The latest value is persisted. A non-null init value wins over the stored one, and `clearUser()`
  resets it.
- On an account switch, call `setUserSampling(newValue)` **before** `setUser(newId)`. The user change
  then starts a new session with the new user's value. Going from anonymous to the first login keeps
  the current session.
- Every span and log carries `session.sampling_source` = `user` | `fallback`.

## Turning RUM off at runtime

`enabled` is a kill switch your app controls. Use it when RUM should only run at certain times,
for example during working hours, including when Android's WorkManager wakes your process at night.
`setUserSampling(false)` is not a substitute: it waits for the next session and still sends logs
and metrics.

```kotlin
// Your app owns the schedule. Read it synchronously from your own cache at startup.
fun inWorkWindow(now: Instant = Instant.now()): Boolean {
    val shift = scheduleCache.nextOrCurrentShift() ?: return false
    return now >= shift.start.minus(Duration.ofHours(1)) && now < shift.end
}

// Application.onCreate (also runs when WorkManager wakes the process at 03:00):
Scout.initialize(this, ScoutConfig(serviceName = "my-app", endpoint = "…", enabled = inWorkWindow()))

// When the window opens or closes while the process is alive (alarm, worker, schedule refresh):
Scout.setEnabled(inWorkWindow())
```

```swift
Scout.start(serviceName: "my-app", endpoint: "…", enabled: inWorkWindow())
Scout.setEnabled(false)
```

From `commonMain` (scout-kmp): `ScoutConfig(enabled = …)`, `Scout.setEnabled(…)`, `Scout.isEnabled`.

- **While disabled nothing leaves the device and nothing is kept for later.** No spans, logs,
  metrics, errors, crashes or ANRs, even with `alwaysCaptureErrors`. No OTLP request is made, and
  nothing is written to the offline buffer.
- **No session is counted.** A launch with `enabled = false` does not create, resume or extend a
  session, and emits no app-start, cold-start or lifecycle telemetry. `Scout.sessionId` is `null`.
- **Turning off** flushes what was already recorded, ends the session, then drops everything.
  Spans still open at that moment are dropped.
- **Turning on** starts a fresh session (with `session.previous_id` linked), sampled like any new
  session. Startup metrics are not re-emitted for that process.
- **Crashes while disabled are never reported**, not on a later launch either. A crash from an
  earlier enabled run is reported on the next launch that is enabled; if that launch starts disabled,
  the report is kept until `setEnabled(true)`. (Android: JVM crashes and native crash files are kept;
  `ApplicationExitInfo` records are read at that point. iOS: KSCrash reports are kept; MetricKit
  diagnostics delivered while disabled are dropped.)
- **Not persisted.** Pass `enabled` on every launch. A `setEnabled` call before `initialize` is held
  and wins over the config value. Repeating the current value is a no-op.
- `setUser`, user attributes and `setUserSampling` calls while disabled are kept and apply once
  enabled.
- With `debugLogging`, the init state and each transition are logged (`SCOUTDBG enabled=…`).
- Hybrid apps: the owner's `bridgeContext()` carries `scout.enabled`, and attached layers forward
  their calls with `ScoutBridge.setEnabled` (Android) or `ScoutEngine.setEnabled` (iOS). Each Android
  process takes its own `enabled` from its own config; it is not propagated across processes yet.
  The WebView bridge does not follow it yet either.

## Modules

| Module | What |
|---|---|
| `scout-core` | KMP library (android/jvm/iosArm64/iosSimulatorArm64): config, session authority, FNV-1a sampler, identity, breadcrumbs, semantics vocab, OTLP/JSON serializer + exporter, OTel pipeline on `opentelemetry-kotlin`. |
| `scout-android` | Android library: the `Scout` facade + `initialize()` + all instrumentation + OkHttp interceptor. |
| `examples/` | Runnable integration examples (see `examples/flutter-android-example` — a hybrid Flutter + native Android app). |

## Implemented (v1)

| Signal | span / behavior | status |
|---|---|---|
| Screens | `screen_view`/`screen_load`/`view_session`; Activity + **Compose-Nav** (`trackScoutScreens` / `setScreen`) | ✅ |
| One trace per screen | `screen_view` is a long-lived **root span**; taps/HTTP/vitals/jank nest under it | ✅ |
| Span snapshots | screen root persisted + **resurrected on next launch** if the process dies mid-screen | ✅ |
| Startup | `app_startup` (cold + warm) | ✅ |
| Lifecycle | `app_lifecycle.changed` + session fg/bg | ✅ |
| Taps | `user_interaction`; **Compose semantics labels** (contentDescription/text/testTag) + View tree | ✅ |
| HTTP | `http.request` (OkHttp interceptor, spec keys) + **W3C `traceparent`** on first-party hosts | ✅ |
| Errors / crash | `error`, `app_crash`; **synchronous persist + replay-on-launch**; `error.fingerprint` | ✅ |
| Native crash | in-process **NDK signal handler** → signal/registers/PC frames + **binary images w/ build-ids**; also `ApplicationExitInfo` | ✅ |
| ANR | live main-thread watchdog + `ApplicationExitInfo` | ✅ |
| Jank | `long_task`, `frozen_frame` (JankStats) | ✅ |
| Vitals | `app_vital` (`fbc` startup, `inv` interaction-to-next-view) | ✅ |
| Metrics | `android.memory.usage`/`cpu.usage`/`frame.build_time` gauges → `/v1/metrics` | ✅ |
| Logs | `logDebug/Info/Warning/Error` → OTLP logs `/v1/logs` with trace context | ✅ |
| Facade | `setUser`/`setAccount`/`setFeatureFlag`/`setSessionAttributes`/`addTiming`/`startVital`+`endVital`/`recordOperationStep`/`logEvent`/`addBreadcrumb`/`reportError` | ✅ |
| Session | UUID, persistence, idle/max rotation, `session.previous_id` | ✅ |
| Sampling | deterministic FNV-1a, error-class bypass, per-user `userSampling` (see [Sampling](#sampling)) | ✅ |
| Kill switch | `enabled` / `setEnabled`: nothing exported, no session counted (see [Turning RUM off at runtime](#turning-rum-off-at-runtime)) | ✅ |
| Transport | OTLP/JSON over Ktor; batch + **crash-durable offline buffering** (persistingSpanProcessor) | ✅ |
| Resource attrs | os/device/app/arch/locale/tz/network per semantics.md §2; `service.version` auto-detected | ✅ |
| Hooks | `beforeSend` (PII/drop), feature toggles per signal | ✅ |

## Build & test

```bash
./gradlew :scout-core:jvmTest                       # core unit + conformance tests
./gradlew :scout-android:connectedDebugAndroidTest  # on-device instrumented tests (needs emulator/device)
./gradlew :scout-android:assembleRelease            # produce the library AAR
```

**Tests:** `scout-core` commonTest covers the sampler, session manager, identity, breadcrumbs, the
OTLP/JSON serializer (golden), and a **`semantics.md` conformance harness** (`SemanticsConformanceTest`)
that freezes the scope, span names, error-class set, and every attribute/resource key against the spec.
`scout-android` androidTest (`ScoutInstrumentedTest`) runs the real on-device pipeline against a local
`MockWebServer`: device-resource conformance, init idempotency, session persistence, and an end-to-end
emit asserting the exported OTLP/JSON payload is spec-conformant (scope `base14.scout.android`, common
session/identity attrs, `os.name=Android` resource, CLIENT span kind for HTTP).

**Build toolchain:** JDK 17+, Android SDK 35 + NDK 27.1 + CMake 3.22.1, Kotlin 2.1.10, AGP 8.7.3, Gradle 8.11.1.
**Consumer compatibility:** the published artifact targets **minSdk 21+, AGP 8.0+, Gradle 7+, Kotlin 2.1+** (`languageVersion = 2.0`), so stock apps consume it without forcing a newer toolchain. Consume via `mavenLocal()` + `implementation("io.base14:scout-android:<version>")`.

## Deliberate divergences from upstream OTel

- Wire encoding is **OTLP/JSON** (the library only ships protobuf; we serialize JSON ourselves).
- Instrumentation scope is **`base14.scout.android`** (semantics.md §8).

## Roadmap

- **iOS** — `scout-ios` bindings over the shared `scout-core` (in progress).
- **Hybrid bridge** — first-class Flutter/native session sharing (see `examples/flutter-android-example`).
- **Symbolication pipeline** — build-time mapping/symbol upload for obfuscated native stacks.
