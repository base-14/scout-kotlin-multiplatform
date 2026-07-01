import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'native_bridge.dart';

/// The Flutter half of the hybrid demo. Mirrors the `_SdkTestButtons` panel in
/// the platform_design sample: a reflowing grid of severity-colored trigger
/// buttons. Some buttons run pure-Dart effects; two delegate to native code over
/// [NativeBridge]. "Back to Native" finishes this FlutterActivity to return to
/// the Kotlin/Compose host.
class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Flutter Screen')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Center(
                child: Text('🐦', style: TextStyle(fontSize: 72)),
              ),
              const SizedBox(height: 8),
              const Text(
                'This screen runs inside a FlutterActivity, launched by the '
                'native host in the same process.',
                style: TextStyle(color: Colors.grey),
              ),
              const SizedBox(height: 24),
              Text(
                'FLUTTER TEST TRIGGERS',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey[600],
                ),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _testButton(
                    label: 'Trigger Jank (2s)',
                    color: Colors.amber,
                    onPressed: () {
                      // Busy-wait blocks the Dart isolate for ~2s.
                      final end = DateTime.now().add(const Duration(seconds: 2));
                      while (DateTime.now().isBefore(end)) {}
                    },
                  ),
                  _testButton(
                    label: 'Throw Exception',
                    color: Colors.red,
                    onPressed: () {
                      // Unhandled async exception -> PlatformDispatcher.onError.
                      Future<void>.microtask(() {
                        throw StateError('Test unhandled exception');
                      });
                    },
                  ),
                  _testButton(
                    label: 'Flutter Error',
                    color: Colors.deepOrange,
                    onPressed: () {
                      // Framework-reported error -> FlutterError.onError.
                      FlutterError.reportError(
                        FlutterErrorDetails(
                          exception: Exception('Test Flutter error'),
                          library: 'profile_screen',
                          context: ErrorDescription('testing error capture'),
                        ),
                      );
                    },
                  ),
                  _testButton(
                    label: 'HTTP Call',
                    color: Colors.indigo,
                    onPressed: () async {
                      // Network GET from the Dart isolate.
                      final client = HttpClient();
                      try {
                        final req = await client
                            .getUrl(Uri.parse('https://httpbin.org/get'));
                        final resp = await req.close();
                        await resp.drain<void>();
                      } catch (_) {
                      } finally {
                        client.close();
                      }
                    },
                  ),
                  _testButton(
                    label: 'Native ANR (5s)',
                    color: Colors.orange,
                    onPressed: () {
                      // Crosses the channel to block the native main thread.
                      NativeBridge.simulateNativeAnr();
                    },
                  ),
                  _testButton(
                    label: 'Native Crash',
                    color: Colors.black,
                    onPressed: () {
                      // Crosses the channel to trigger a native JVM crash.
                      NativeBridge.simulateNativeCrash();
                    },
                  ),
                ],
              ),
              const SizedBox(height: 32),
              OutlinedButton.icon(
                icon: const Icon(Icons.arrow_back),
                label: const Text('Back to Native'),
                onPressed: () {
                  // Finish the FlutterActivity, returning to HostActivity.
                  SystemNavigator.pop();
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _testButton({
    required String label,
    required Color color,
    required VoidCallback onPressed,
  }) {
    return ElevatedButton(
      style: ElevatedButton.styleFrom(
        backgroundColor: color,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      onPressed: onPressed,
      child: Text(label, style: const TextStyle(fontSize: 12)),
    );
  }
}
