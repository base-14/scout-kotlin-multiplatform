// Widget test for the Flutter profile screen of the hybrid demo.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hybrid_demo/profile_screen.dart';

void main() {
  testWidgets('ProfileScreen renders its trigger buttons', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: ProfileScreen()));

    // Section header and a representative button from each category.
    expect(find.text('FLUTTER TEST TRIGGERS'), findsOneWidget);
    expect(find.text('Trigger Jank (2s)'), findsOneWidget);
    expect(find.text('Throw Exception'), findsOneWidget);
    expect(find.text('Flutter Error'), findsOneWidget);
    expect(find.text('HTTP Call'), findsOneWidget);
    expect(find.text('Native ANR (5s)'), findsOneWidget);
    expect(find.text('Native Crash'), findsOneWidget);
    expect(find.text('Back to Native'), findsOneWidget);
  });
}
