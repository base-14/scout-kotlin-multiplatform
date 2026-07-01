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

Optional API: `Scout.setUser`, `clearUser`, `logEvent`, `addBreadcrumb`, `reportError`.

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
| Sampling | deterministic FNV-1a, error-class bypass | ✅ |
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
