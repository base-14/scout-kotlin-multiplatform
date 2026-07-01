import 'package:flutter/material.dart';
import 'package:scout_flutter/scout_flutter.dart';

import 'profile_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Scout RUM — Flutter side. Step 1 runs it standalone (its own session); Step 2
  // attaches it to the native owner so both halves share one session.
  await ScoutFlutter.initialize(
    config: ScoutFlutterConfig(
      serviceName: 'hybrid-demo',
      serviceVersion: '1.0.0',
      endpoint: 'https://your-collector.example/otlp',
      headers: {
        'Authorization': 'Bearer <YOUR_SCOUT_INGEST_TOKEN>',
      },
      secure: true,
      environment: 'production',
      sessionSampleRate: 100.0,
    ),
  );

  runApp(const HybridDemoApp());
}

class HybridDemoApp extends StatelessWidget {
  const HybridDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Hybrid Demo (Flutter)',
      navigatorObservers: [ScoutFlutter.navigatorObserver],
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: const ProfileScreen(),
    );
  }
}
