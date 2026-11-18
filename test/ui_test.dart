// Pinned framework compatibility export.
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ai_photography_director/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('photography/director');
  late List<String> calls;
  setUp(() {
    calls = [];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'onboarded') {
        return true;
      }
      if (call.method == 'settings') {
        return <String, dynamic>{};
      }
      if (call.method == 'status') {
        return {'overlay': false, 'running': false, 'status': 'Stopped'};
      }
      return null;
    });
  });
  tearDown(() => TestDefaultBinaryMessengerBinding
      .instance.defaultBinaryMessenger
      .setMockMethodCallHandler(channel, null));
  testWidgets('phone dashboard and mode selection render', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const PhotographyApp());
    await tester.pumpAndSettle();
    expect(find.text('Start Stock Camera Assist'), findsOneWidget);
    await tester.tap(find.text('Open AI Camera'));
    await tester.pumpAndSettle();
    expect(calls, contains('aiCamera'));
    expect(calls, isNot(contains('start')));
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Modes'));
    await tester.pumpAndSettle();
    expect(find.text('Portrait'), findsOneWidget);
    await tester.tap(find.text('Portrait'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('permission flow and settings work on narrow screen',
      (tester) async {
    tester.view.physicalSize = const Size(320, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const PhotographyApp());
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Start Stock Camera Assist'), 220);
    await tester.tap(find.text('Start Stock Camera Assist'));
    await tester.pumpAndSettle();
    expect(find.text('Open overlay permission'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Overlay appearance'));
    await tester.pumpAndSettle();
    expect(find.text('Rule-of-thirds grid'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
}
