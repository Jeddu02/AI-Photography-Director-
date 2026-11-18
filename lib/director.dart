import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const modes = [
  'Auto Director',
  'Portrait',
  'Group Photo',
  'Landscape',
  'Food',
  'Street',
  'Manual Composition'
];
const modeDescriptions = [
  'Automatically choose a subject and adapt framing to people, food, or the wider scene.',
  'Face visibility, headroom, placement and background edge detail.',
  'Frame all detected people and keep the group away from edges.',
  'Suggest scenery/detail frames even without recognized object labels.',
  'Frame recognizable food classes with balanced placement and lighting.',
  'Subject placement and stable framing. No event prediction.',
  'Choose framing guides yourself. No AI capture recommendation.',
];

class Director extends ChangeNotifier {
  Director({MethodChannel? channel})
      : channel = channel ?? const MethodChannel('photography/director');
  final MethodChannel channel;
  bool _disposed = false;
  Future<void> _saveQueue = Future.value();
  Map<String, dynamic> settings = {
    'mode': 'Auto Director',
    'grid': false,
    'opacity': .5,
    'threshold': .55,
    'preset': 'Balanced',
    'preference': 'Nearest third',
    'top': .14,
    'bottom': .28,
    'left': .02,
    'right': .02,
    'autoPreview': true,
  };
  bool overlay = false, running = false, busy = false;
  String status = 'Stopped';
  int latency = 0, frames = 0;
  String? error;
  String get mode => settings['mode'] as String;
  void _emit() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  Future<void> load() async {
    await _saveQueue;
    try {
      final result = await channel.invokeMapMethod<String, dynamic>('settings');
      if (result != null) {
        settings.addAll(result);
      }
    } on PlatformException catch (e) {
      error = e.message;
    } on MissingPluginException {
      error = 'Android integration is available on Android builds.';
    }
    await refresh();
  }

  Future<void> refresh() async {
    try {
      final s = await channel.invokeMapMethod<String, dynamic>('status');
      if (s != null) {
        overlay = s['overlay'] == true;
        running = s['running'] == true;
        status = s['status'] as String? ?? 'Stopped';
        latency = (s['latencyMs'] as num?)?.toInt() ?? 0;
        frames = (s['frames'] as num?)?.toInt() ?? 0;
      }
    } on PlatformException catch (e) {
      error = e.message;
    } on MissingPluginException {
      error = 'Android integration is available on Android builds.';
    }
    _emit();
  }

  Future<void> set(String key, Object value) async {
    final old = settings[key];
    settings = {...settings, key: value};
    final snapshot = Map<String, dynamic>.from(settings);
    error = null;
    _emit();
    final save = _saveQueue.then((_) async {
      try {
        await channel.invokeMethod<void>('saveSettings', snapshot);
      } on PlatformException catch (e) {
        if (settings[key] == value) {
          settings = {...settings, key: old};
        }
        error = e.message;
        _emit();
      } on MissingPluginException {
        error = 'Android settings are unavailable.';
        _emit();
      }
    });
    _saveQueue = save;
    await save;
  }

  Future<void> command(String method) async {
    if (busy) {
      return;
    }
    busy = true;
    error = null;
    _emit();
    try {
      if (method == 'start' || method == 'manual') {
        await channel.invokeMethod<Object?>('notifications');
      }
      await channel.invokeMethod<Object?>(method);
    } on PlatformException catch (e) {
      error = e.message ?? e.code;
    } on MissingPluginException {
      error = 'Android integration is unavailable.';
    } finally {
      busy = false;
      await refresh();
    }
  }
}
