package io.base14.scout.android.instrumentation

/**
 * Exposes metadata about the SDK's native library. `buildId()` returns the ELF `NT_GNU_BUILD_ID`
 * (hex) of `libscout_crash.so` — surfaced as the `ndk.build_id` resource attribute so native
 * crash frames can be symbolicated against the matching build. Best-effort: null if the native
 * library is unavailable (e.g. an unsupported ABI).
 */
internal object NativeLibInfo {
    private val loaded: Boolean =
        runCatching {
            System.loadLibrary("scout_crash")
            true
        }.getOrDefault(false)

    fun buildId(): String? = if (!loaded) null else runCatching { nativeBuildId() }.getOrNull()

    private external fun nativeBuildId(): String?
}
