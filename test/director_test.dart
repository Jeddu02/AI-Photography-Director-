import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ai_photography_director/director.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test/director');
  final calls = <MethodCall>[];
  var deny = false;
  var failSettings = false;
  setUp(() {
    calls.clear();
    deny = false;
    failSettings = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      switch (call.method) {
        case 'settings':
          return {'mode': 'Portrait', 'threshold': .7};
        case 'status':
          return {
            'overlay': true,
            'running': false,
            'status': 'Stopped',
            'frames': 3,
            'latencyMs': 57
          };
        case 'saveSettings':
          if (failSettings) {
            throw PlatformException(
                code: 'disk', message: 'Could not persist settings');
          }
          return null;
        case 'start':
          if (deny) {
            throw PlatformException(
                code: 'denied', message: 'Capture declined');
          }
          return null;
      }
      return null;
    });
  });
  tearDown(() => TestDefaultBinaryMessengerBinding
      .instance.defaultBinaryMessenger
      .setMockMethodCallHandler(channel, null));
  test('loads persisted settings and actual native state', () async {
    final d = Director(channel: channel);
    await d.load();
    expect(d.mode, 'Portrait');
    expect(d.overlay, isTrue);
    expect(d.running, isFalse);
    expect(d.frames, 3);
    expect(d.latency, 57);
    d.dispose();
  });
  test('settings save preserves other keys', () async {
    final d = Director(channel: channel);
    await d.load();
    await d.set('grid', false);
    final saved =
        calls.lastWhere((c) => c.method == 'saveSettings').arguments as Map;
    expect(saved['grid'], false);
    expect(saved['mode'], 'Portrait');
    expect(saved['threshold'], .7);
    d.dispose();
  });
  test('failed persistence restores prior selection', () async {
    final d = Director(channel: channel);
    failSettings = true;
    await d.set('mode', 'Food');
    expect(d.mode, 'Auto Director');
    expect(d.error, 'Could not persist settings');
    d.dispose();
  });
  test('consent denial returns idle and a clear error', () async {
    final d = Director(channel: channel);
    deny = true;
    await d.command('start');
    expect(d.busy, isFalse);
    expect(d.error, 'Capture declined');
    expect(d.running, isFalse);
    d.dispose();
  });
  test('duplicate start suppressed while consent pending', () async {
    final d = Director(channel: channel);
    final first = d.command('start');
    final second = d.command('start');
    await Future.wait([first, second]);
    expect(calls.where((c) => c.method == 'start').length, 1);
    d.dispose();
  });
}
