import 'dart:io';

import 'package:flutter/material.dart';
import 'package:scout_flutter/scout_flutter.dart';

/// Extra Flutter screens for the hybrid demo. Each is pushed with a named route
/// so the ScoutFlutter navigator observer records a clean `screen_view`; the
/// action buttons exercise the public ScoutFlutter API (all under the
/// base14.scout.flutter scope).

Route<void> demoRoute(Widget screen, String name) => MaterialPageRoute<void>(
      builder: (_) => screen,
      settings: RouteSettings(name: name),
    );

class FeedScreen extends StatelessWidget {
  const FeedScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return _DemoScaffold(
      title: 'Feed',
      subtitle: 'Flutter screen. Emits a custom event span when you tap below.',
      actions: [
        _actionButton('Log Event', Colors.indigo, () {
          ScoutFlutter.logEvent('feed_opened', attributes: {'source': 'flutter'});
        }),
      ],
      navButtons: [
        ('Cart', () => Navigator.push(context, demoRoute(const CartScreen(), '/cart'))),
        ('Back', () => Navigator.pop(context)),
      ],
    );
  }
}

class CartScreen extends StatelessWidget {
  const CartScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return _DemoScaffold(
      title: 'Cart',
      subtitle: 'Report a handled Dart error — an `error` span with stack trace, not a crash.',
      actions: [
        _actionButton('Report Handled Error', Colors.pink, () {
          ScoutFlutter.reportError(
            StateError('Handled error from Cart screen'),
            StackTrace.current,
          );
        }),
      ],
      navButtons: [
        ('Search', () => Navigator.push(context, demoRoute(const SearchScreen(), '/search'))),
        ('Back', () => Navigator.pop(context)),
      ],
    );
  }
}

class SearchScreen extends StatelessWidget {
  const SearchScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return _DemoScaffold(
      title: 'Search',
      subtitle: 'Drop a breadcrumb into the session timeline and emit an info log.',
      actions: [
        _actionButton('Add Breadcrumb', Colors.brown, () {
          ScoutFlutter.addBreadcrumb('action', 'Search breadcrumb tapped');
        }),
        _actionButton('Log Info', Colors.blueGrey, () {
          ScoutFlutter.logInfo('Search screen info log');
        }),
      ],
      navButtons: [
        ('Account', () => Navigator.push(context, demoRoute(const AccountScreen(), '/account'))),
        ('Back', () => Navigator.pop(context)),
      ],
    );
  }
}

class AccountScreen extends StatelessWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return _DemoScaffold(
      title: 'Account',
      subtitle: 'Set the RUM user identity and fire an outbound HTTP request.',
      actions: [
        _actionButton('Set User', Colors.teal, () {
          ScoutFlutter.setUser(id: 'flutter-user-7', attributes: {'plan': 'gold'});
        }),
        _actionButton('HTTP Call', Colors.indigo, () async {
          final client = HttpClient();
          try {
            final req = await client.getUrl(Uri.parse('https://httpbin.org/get'));
            final resp = await req.close();
            await resp.drain<void>();
          } catch (_) {
          } finally {
            client.close();
          }
        }),
      ],
      navButtons: [
        ('Back', () => Navigator.pop(context)),
      ],
    );
  }
}

Widget _actionButton(String label, Color color, VoidCallback onPressed) {
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

class _DemoScaffold extends StatelessWidget {
  final String title;
  final String subtitle;
  final List<Widget> actions;
  final List<(String, VoidCallback)> navButtons;

  const _DemoScaffold({
    required this.title,
    required this.subtitle,
    required this.actions,
    required this.navButtons,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(subtitle, style: const TextStyle(color: Colors.grey)),
              const SizedBox(height: 24),
              _sectionLabel('ACTIONS'),
              const SizedBox(height: 8),
              Wrap(spacing: 8, runSpacing: 8, children: actions),
              const SizedBox(height: 24),
              _sectionLabel('GO TO'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final (label, onTap) in navButtons)
                    OutlinedButton(onPressed: onTap, child: Text(label)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _sectionLabel(String text) => Text(
        text,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Colors.grey[600],
        ),
      );
}
