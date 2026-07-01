import 'package:flutter/services.dart';

/// Thin wrapper over the `hybrid_demo/native_triggers` MethodChannel handled by
/// the native [MainActivity]. Lets the Flutter profile screen invoke native
/// trigger code (the channel and handlers live on the Kotlin side).
class NativeBridge {
  NativeBridge._();

  static const MethodChannel _channel =
      MethodChannel('hybrid_demo/native_triggers');

  /// Blocks the native main thread for [durationMs] (native ANR demo).
  static Future<void> simulateNativeAnr({int durationMs = 5000}) {
    return _channel.invokeMethod<void>('simulateAnr', {'durationMs': durationMs});
  }

  /// Triggers an uncaught native exception (native crash demo).
  static Future<void> simulateNativeCrash() {
    return _channel.invokeMethod<void>('simulateCrash');
  }
}
