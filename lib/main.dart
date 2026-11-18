import 'dart:async';
// Compatibility re-export remains supported by the pinned stable SDK.
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'director.dart';

const yellow = Color(0xFFF4D34E);
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const PhotographyApp());
}

class PhotographyApp extends StatelessWidget {
  const PhotographyApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
      title: 'AI Photography Director',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          colorScheme: ColorScheme.fromSeed(
              seedColor: yellow,
              brightness: Brightness.dark,
              primary: yellow,
              surface: const Color(0xFF17191D)),
          scaffoldBackgroundColor: const Color(0xFF0B0D10),
          appBarTheme: const AppBarTheme(
              backgroundColor: Color(0xFF0B0D10), centerTitle: false),
          cardTheme: const CardThemeData(
              margin: EdgeInsets.zero, color: Color(0xFF17191D)),
          filledButtonTheme: FilledButtonThemeData(
              style: FilledButton.styleFrom(
                  minimumSize: const Size(48, 54),
                  foregroundColor: const Color(0xFF141510),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(18))))),
      home: const DirectorShell());
}

class DirectorShell extends StatefulWidget {
  const DirectorShell({super.key});
  @override
  State<DirectorShell> createState() => _DirectorShellState();
}

class _DirectorShellState extends State<DirectorShell>
    with WidgetsBindingObserver {
  final director = Director();
  Timer? timer;
  int tab = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    initialize();
    timer =
        Timer.periodic(const Duration(seconds: 2), (_) => director.refresh());
  }

  Future<void> initialize() async {
    await director.load();
    try {
      final done =
          await director.channel.invokeMethod<bool>('onboarded') ?? false;
      if (mounted && !done) {
        welcome();
      }
    } catch (_) {/* Dashboard already reports an unavailable Android bridge. */}
  }

  @override
  void dispose() {
    timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    director.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      director.load();
      timer ??=
          Timer.periodic(const Duration(seconds: 2), (_) => director.refresh());
    } else {
      timer?.cancel();
      timer = null;
    }
  }

  void page(String title, Widget Function() content) {
    Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (_) => AnimatedBuilder(
            animation: director,
            builder: (context, _) => Scaffold(
                appBar: AppBar(title: Text(title)),
                body: SafeArea(
                    child: ListView(
                        padding: const EdgeInsets.all(22),
                        children: [content()]))))));
  }

  Future<void> startAssistant() async {
    if (!director.overlay) {
      permissions();
      return;
    }
    if (director.mode == 'Manual Composition') {
      await director.command('manual');
      return;
    }
    final approved = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
                title: const Text('Analyze your camera screen'),
                content: const Text(
                    'Android may ask to enable stop notifications, then ask to share your screen. Select the entire screen so guides line up. The assistant then opens your stock camera. Anything in the preview area can be analyzed, including other apps if you switch. Frames are transient and never saved. Stop before private content.'),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('Cancel')),
                  FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      child: const Text('Continue'))
                ]));
    if (approved == true) {
      await director.command('start');
    }
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
      animation: director,
      builder: (context, _) => Scaffold(
          appBar: AppBar(
              title: Row(children: [
                const Icon(Icons.center_focus_strong, color: yellow),
                const SizedBox(width: 10),
                Text('DIRECTOR',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        letterSpacing: 3, fontWeight: FontWeight.w700))
              ]),
              actions: [
                IconButton(
                    tooltip: 'Getting started',
                    onPressed: welcome,
                    icon: const Icon(Icons.help_outline))
              ]),
          body: SafeArea(
              child: ListView(
                  key: ValueKey(tab),
                  padding: const EdgeInsets.fromLTRB(22, 12, 22, 28),
                  children: [
                if (director.error != null)
                  Padding(
                      padding: const EdgeInsets.only(bottom: 16),
                      child: notice(director.error!, Icons.info_outline)),
                if (tab == 0) ...home(),
                if (tab == 1) ...modeList(),
                if (tab == 2) ...settingsList(),
              ])),
          bottomNavigationBar: NavigationBar(
              selectedIndex: tab,
              onDestinationSelected: (value) => setState(() => tab = value),
              destinations: const [
                NavigationDestination(
                    icon: Icon(Icons.center_focus_strong), label: 'Director'),
                NavigationDestination(
                    icon: Icon(Icons.grid_view_rounded), label: 'Modes'),
                NavigationDestination(icon: Icon(Icons.tune), label: 'Settings')
              ])));
  List<Widget> home() => [
        const Text('SEE THE SHOT',
            style: TextStyle(
                color: yellow,
                fontSize: 11,
                letterSpacing: 2.7,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 12),
        const Text('Find the shot.\nMake it yours.',
            style: TextStyle(
                fontSize: 35,
                height: 1.12,
                fontWeight: FontWeight.w600,
                letterSpacing: -1.4)),
        const SizedBox(height: 12),
        const Text(
            'Find a promising frame. Follow the target. Zoom and capture.',
            style: TextStyle(color: Color(0xFFA5A9B1), height: 1.5)),
        const SizedBox(height: 24),
        AspectRatio(
            aspectRatio: 1.34,
            child: DecoratedBox(
                decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(24),
                    gradient: const LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFF24282B), Color(0xFF151917)])),
                child: Stack(children: [
                  const Positioned.fill(
                      child: CustomPaint(painter: FramingIllustration())),
                  const Positioned(
                      top: 18,
                      left: 18,
                      child: Text('COMPOSITION GUIDE',
                          style: TextStyle(
                              fontSize: 10, letterSpacing: 2, color: yellow))),
                  const Positioned(
                      top: 18,
                      right: 16,
                      child: Text('ILLUSTRATION',
                          style:
                              TextStyle(fontSize: 9, color: Colors.white54))),
                  Positioned(
                      bottom: 16,
                      left: 18,
                      right: 18,
                      child: Text(director.mode,
                          style: const TextStyle(
                              fontSize: 18, fontWeight: FontWeight.w500)))
                ]))),
        const SizedBox(height: 18),
        Card(
            child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Icon(
                            director.running
                                ? Icons.radio_button_checked
                                : Icons.radio_button_off,
                            color: director.running
                                ? const Color(0xFF91D9A5)
                                : Colors.white38,
                            size: 15),
                        const SizedBox(width: 8),
                        Expanded(
                            child: Text(
                                director.running
                                    ? 'ASSISTANT ACTIVE'
                                    : 'READY WHEN YOU ARE',
                                style: const TextStyle(
                                    fontSize: 10, letterSpacing: 1.7)))
                      ]),
                      const SizedBox(height: 10),
                      Text(director.status,
                          style: const TextStyle(fontSize: 16)),
                      if (director.frames > 0)
                        Padding(
                            padding: const EdgeInsets.only(top: 10),
                            child: Text(
                                '${director.frames} frames analyzed · last inference ${director.latency} ms',
                                style: const TextStyle(
                                    fontSize: 12, color: Colors.white54)))
                    ]))),
        const SizedBox(height: 18),
        FilledButton.icon(
            onPressed: director.busy ? null : () => director.command('aiCamera'),
            icon: const Icon(Icons.photo_camera_outlined),
            label: const Text('Open AI Camera')),
        const SizedBox(height: 10),
        if (director.running) ...[
          FilledButton.icon(
              onPressed:
                  director.busy ? null : () => director.command('camera'),
              icon: const Icon(Icons.camera_alt_outlined),
              label: const Text('Open stock camera')),
          const SizedBox(height: 10),
          OutlinedButton.icon(
              onPressed: () => director.command('stop'),
              icon: const Icon(Icons.stop_circle_outlined),
              label: const Text('Stop assistant'))
        ] else ...[
          FilledButton.icon(
              onPressed: director.busy ? null : startAssistant,
              icon: director.busy
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.auto_awesome_outlined),
              label: const Text('Start Stock Camera Assist')),
          const SizedBox(height: 10),
          TextButton.icon(
              onPressed: director.busy
                  ? null
                  : () {
                      if (!director.overlay) {
                        permissions();
                      } else {
                        director.command('manual');
                      }
                    },
              icon: const Icon(Icons.crop_free),
              label: const Text('Use manual guides'))
        ],
        const SizedBox(height: 18),
        const Text('ON DEVICE  ·  NO ACCOUNT  ·  NO API FEES',
            textAlign: TextAlign.center,
            style: TextStyle(
                fontSize: 10, letterSpacing: 1.1, color: Colors.white54)),
        const SizedBox(height: 24),
        row(Icons.layers_outlined, 'Permissions & compatibility',
            'Set up display-over-apps access', permissions),
        row(Icons.crop, 'Calibrate preview area',
            'Exclude your camera’s buttons', previewSettings),
      ];
  List<Widget> modeList() => [
        const Text('Find your direction.',
            style: TextStyle(fontSize: 30, fontWeight: FontWeight.w600)),
        const SizedBox(height: 10),
        const Text('Choose the scene. Guidance uses only supported signals.',
            style: TextStyle(color: Colors.white60, height: 1.5)),
        const SizedBox(height: 24),
        for (var i = 0; i < modes.length; i++)
          Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Card(
                  child: InkWell(
                      borderRadius: BorderRadius.circular(16),
                      onTap: () => director.set('mode', modes[i]),
                      child: Padding(
                          padding: const EdgeInsets.all(18),
                          child: Row(children: [
                            Icon(
                                [
                                  Icons.auto_awesome,
                                  Icons.person_outline,
                                  Icons.groups_outlined,
                                  Icons.landscape_outlined,
                                  Icons.restaurant,
                                  Icons.location_city,
                                  Icons.crop
                                ][i],
                                color: director.mode == modes[i]
                                    ? yellow
                                    : Colors.white54),
                            const SizedBox(width: 16),
                            Expanded(
                                child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                  Text(modes[i],
                                      style: const TextStyle(
                                          fontSize: 17,
                                          fontWeight: FontWeight.w500)),
                                  const SizedBox(height: 6),
                                  Text(modeDescriptions[i],
                                      style: const TextStyle(
                                          color: Colors.white54,
                                          fontSize: 12,
                                          height: 1.5))
                                ])),
                            if (director.mode == modes[i])
                              const Padding(
                                  padding: EdgeInsets.only(left: 8),
                                  child: Icon(Icons.check_circle,
                                      color: yellow, size: 20))
                          ]))))),
      ];
  List<Widget> settingsList() => [
        const Text('Make it yours.',
            style: TextStyle(fontSize: 30, fontWeight: FontWeight.w600)),
        const SizedBox(height: 24),
        row(Icons.layers_outlined, 'Overlay appearance',
            'Grid, transparency and subject placement', appearance),
        row(Icons.crop, 'Preview calibration',
            'Detect the camera preview automatically', previewSettings),
        row(Icons.auto_awesome_outlined, 'AI sensitivity',
            'Minimum subject confidence', sensitivity),
        row(Icons.battery_saver_outlined, 'Performance',
            '${director.settings['preset']}', performance),
        row(Icons.privacy_tip_outlined, 'Privacy & permissions',
            'Local frames, official consent and easy stop', permissions),
        row(Icons.build_outlined, 'Troubleshooting',
            'Black previews, rotation and touch controls', troubleshooting),
        row(Icons.info_outline, 'About', 'Capabilities, limits and licenses',
            about),
      ];
  Widget row(
          IconData icon, String title, String subtitle, VoidCallback action) =>
      Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Card(
              child: ListTile(
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  leading: Icon(icon, color: yellow),
                  title: Text(title, style: const TextStyle(fontSize: 15)),
                  subtitle: Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(subtitle,
                          style: const TextStyle(
                              fontSize: 12, color: Colors.white54))),
                  trailing: const Icon(Icons.chevron_right, size: 18),
                  onTap: action)));
  Widget notice(String text, IconData icon) => Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
          color: yellow.withValues(alpha: .09),
          borderRadius: BorderRadius.circular(16)),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: yellow, size: 20),
        const SizedBox(width: 12),
        Expanded(
            child:
                Text(text, style: const TextStyle(height: 1.5, fontSize: 13)))
      ]));
  Widget paragraphs(List<String> content) =>
      Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        for (final p in content)
          Padding(
              padding: const EdgeInsets.only(bottom: 18),
              child: Text(p,
                  style: const TextStyle(
                      fontSize: 15, height: 1.65, color: Color(0xFFBCC0C8))))
      ]);
  Widget slider(String key, String label, double min, double max) {
    final value = (director.settings[key] as num).toDouble().clamp(min, max);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('$label  ${(value * 100).round()}%'),
      Slider(
          value: value,
          min: min,
          max: max,
          divisions: ((max - min) * 100).round(),
          onChanged: (v) => director.set(key, v)),
      const SizedBox(height: 12)
    ]);
  }

  Widget choice(String key, String title, List<String> values) => Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: DropdownButtonFormField<String>(
          isExpanded: true,
          key: ValueKey('$key-${director.settings[key]}'),
          initialValue: director.settings[key] as String,
          decoration: InputDecoration(
              labelText: title, border: const OutlineInputBorder()),
          items: [
            for (final value in values)
              DropdownMenuItem(value: value, child: Text(value, overflow: TextOverflow.ellipsis))
          ],
          onChanged: (value) {
            if (value != null) {
              director.set(key, value);
            }
          }));
  void welcome() => page(
      'Your eye. A little guidance.',
      () => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const Icon(Icons.center_focus_strong, size: 80, color: yellow),
            const SizedBox(height: 28),
            paragraphs([
              'Open AI Camera for integrated guidance, exposure controls, and offline photo review. Stock Camera Assist adds visual guidance over a compatible camera app.',
              '1. Allow display over other apps.\n2. Start the assistant and authorize sharing of the entire screen.\n3. Open your stock camera.\n4. Center the highlighted frame, then follow its zoom cue.\n5. Hold steady and press the camera shutter when ready.',
              'Drag ◉ out of the way; tap to expand. Choose a detected subject, select an area on the preview, or choose Whole scene, find another shot, pause, change mode, show a grid, adjust transparency or stop.',
              'The highlighted rectangle is a suggested photograph within the current scene. Zoom cues are relative to your current view, because the assistant cannot read your stock camera’s lens setting. Some cameras may still block capture or overlays.'
            ]),
            FilledButton(
                onPressed: () {
                  director.command('completeOnboarding');
                  Navigator.pop(context);
                  permissions();
                },
                child: const Text('Set up permissions')),
            TextButton(
                onPressed: () {
                  director.command('completeOnboarding');
                  Navigator.pop(context);
                },
                child: const Text('Explore first'))
          ]));
  void permissions() => page(
      'Privacy & permissions',
      () => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            notice(
                director.overlay
                    ? 'Display over other apps is allowed.'
                    : 'Allow display over other apps to show the assistant.',
                Icons.layers_outlined),
            const SizedBox(height: 18),
            FilledButton(
                onPressed: () => director.command('overlayPermission'),
                child: const Text('Open overlay permission')),
            const SizedBox(height: 22),
            paragraphs([
              'Screen analysis requires fresh Android authorization every time you start. Choose the entire screen so positions line up. Device policy can still alter or block capture.',
              'Only the calibrated preview rectangle is analyzed. If you switch apps, that rectangle can contain their content. Stop before private apps. Automatic foreground-app identification is unsupported.',
              'Frames live briefly in memory. No saved screen recordings, automatic image uploads, account, advertising or paid API. AI Camera requests camera access when opened. The app has no internet, microphone, external-storage, or accessibility permission.',
              'Android 13+ may ask to allow notifications. The foreground notification offers Stop when allowed; otherwise use the floating Stop control or Android’s Active apps controls. Locking ends capture; start and authorize again after unlocking.',
              'ML Kit processes input images on device under Google’s published terms and documents SDK metrics. This app removes internet permission to block direct network access. See the source notices for details.'
            ])
          ]));
  void appearance() => page(
      'Overlay appearance',
      () => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Rule-of-thirds grid'),
                subtitle: const Text('Shows thirds inside the suggested shot'),
                value: director.settings['grid'] == true,
                onChanged: (value) => director.set('grid', value)),
            const SizedBox(height: 18),
            slider('opacity', 'Guide opacity', .2, .65),
            choice('preference', 'Composition placement',
                ['Nearest third', 'Center', 'Left third', 'Right third', 'Upper third', 'Lower third', 'Nearest intersection']),
            notice(
                'The full-screen guide stays below Android’s touch-obscuring opacity limit. The small movable control receives taps only inside its own bounds.',
                Icons.touch_app_outlined)
          ]));
  void previewSettings() => page(
      'Preview calibration',
      () => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Automatic preview detection'),
                subtitle: const Text('Find and update the preview as you reframe or rotate.'),
                value: director.settings['autoPreview'] != false,
                onChanged: (value) => director.set('autoPreview', value)),
            if (director.settings['autoPreview'] == false) ...[
              notice('Match the outer border to the camera image and leave controls outside.', Icons.crop),
              slider('top', 'Exclude from top', 0, .3),
              slider('bottom', 'Exclude from bottom', 0, .4),
              slider('left', 'Exclude from left', 0, .4),
              slider('right', 'Exclude from right', 0, .4),
            ] else
              notice('Open your stock camera. Guidance starts when the preview edges are clear. If your camera layout cannot be detected, you can set its edges here.', Icons.crop)
          ]));
  void sensitivity() => page(
      'AI sensitivity',
      () => Column(children: [
            slider('threshold', 'Minimum subject confidence', .4, .9),
            notice(
                'Higher confidence reduces uncertain detections. The score is not a probability that a photograph is good. A checkmark appears as soon as consecutive live observations confirm steady framing.',
                Icons.auto_awesome_outlined)
          ]));
  void performance() => page(
      'Performance',
      () => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            choice('preset', 'Processing preset',
                ['Battery Saver', 'Balanced', 'High Responsiveness']),
            paragraphs([
              'Battery Saver requests at most one inference per second. Balanced uses a minimum 450 ms interval. High Responsiveness uses 220 ms. These are scheduling limits, not measured frame rates.',
              'Indicators interpolate on the display clock between analysis results, with a gentle pulse and colour transition. Battery Saver limits that animation to about 30 scheduled redraws per second; actual phone frame rate is unmeasured. Only one captured frame is analyzed at a time. New frames are dropped while busy. Capture’s longest edge is limited to 960 pixels. Severe thermal conditions slow analysis on supported versions.',
              'Last inference: ${director.latency} ms\nAnalyzed this session: ${director.frames} frames\nBattery, thermal and sustained-performance baselines have not been measured on your phone.'
            ])
          ]));
  void troubleshooting() => page(
      'Troubleshooting',
      () => paragraphs([
            'Black or unavailable preview\nThe camera may protect its preview, or the scene may simply be dark. The assistant cannot distinguish every cause. Stop AI and start manual guides. No security bypass is attempted.',
            'Guides do not line up\nStop and authorize the entire screen. The assistant checks the preview again after layout changes. Use Preview calibration if it cannot find your camera layout. If Android forces app-only capture, manual guides may be necessary.',
            'Camera controls do not respond\nCollapse the control panel and drag it away. Some cameras reject all overlays. Stop the assistant to restore access.',
            'Application switching\nScreen analysis follows the authorized display. Automatic app-name recognition is unsupported. Stop before leaving the camera for sensitive content.',
            'Interrupted session\nLocking, revoked consent, another sharing session or Android stopping the service ends guidance. Return here and authorize a fresh session. An old permission token is never reused.',
            'Missed subjects\nSmall faces, low light, occlusion, unusual objects and camera UI can reduce accuracy. The general model has a fixed COCO vocabulary. Manual Composition never claims AI readiness.',
            'No visual guidance\nExpand the floating button and select Show visual guidance. Check overlay permission. Device vendors may restrict background operation. Physical-camera compatibility remains unverified.'
          ]));
  void about() => page(
      'About Director',
      () => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const Text('AI Photography Director',
                style: TextStyle(fontSize: 26, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            const Text('0.14.0 · styles, portrait lighting, and adjustments',
                style: TextStyle(color: yellow)),
            const SizedBox(height: 22),
            paragraphs([
              'Offline visual assistance, with no subscription, account, advertising, paid API or automatic shutter. You choose the photograph.',
              'Choose a detected subject from the floating panel or select Whole scene to keep a wide view. The director protects detected bodies, asks for clearer separation when boxes overlap, and recommends tighter framing only when it earns a meaningful improvement. Green indicates aligned, steady framing; it is not a photograph quality score.',
              'Limits: face/eye estimates do not guarantee lack of obstruction. Horizon candidates are image edges, not semantic horizons. Background edges are not depth segmentation. Strong mirrored patterns and converging edges can influence framing. Visual-interest scores do not understand beauty or unseen surroundings. Suggestions are alternatives for you to judge. No separate semantic scene classifier, smile readiness, GPU/NPU qualification or perfect-moment guarantee.',
              'EfficientDet-Lite0 COCO float32 is bundled under Apache 2.0; MediaPipe Tasks Vision 0.10.35 uses Apache 2.0. Bundled ML Kit Face Detection 16.1.7 is governed by Google ML Kit and API terms. Flutter uses BSD-3-Clause; Kotlin/AndroidX use Apache 2.0. See source THIRD_PARTY_NOTICES.md.',
              'Build success and host tests do not prove behavior over your stock camera. See RELEASE_STATUS.md and the hardware checklist.'
            ]),
            TextButton(
                onPressed: () => showLicensePage(
                    context: context,
                    applicationName: 'AI Photography Director',
                    applicationVersion: '0.14.0'),
                child: const Text('Flutter open-source licenses'))
          ]));
}

class FramingIllustration extends CustomPainter {
  const FramingIllustration();
  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = Colors.white.withValues(alpha: .09);
    for (var i = 1; i < 3; i++) {
      canvas.drawLine(Offset(size.width * i / 3, 0),
          Offset(size.width * i / 3, size.height), p);
      canvas.drawLine(Offset(0, size.height * i / 3),
          Offset(size.width, size.height * i / 3), p);
    }
    final x = size.width * .66;
    final y = size.height * .43;
    p
      ..color = yellow.withValues(alpha: .7)
      ..strokeWidth = 1.5;
    canvas.drawRRect(
        RRect.fromRectAndRadius(
            Rect.fromCenter(
                center: Offset(x, y),
                width: size.width * .2,
                height: size.height * .37),
            const Radius.circular(10)),
        p);
    canvas.drawCircle(Offset(x, y - 16), 13, p);
    canvas.drawArc(
        Rect.fromCenter(center: Offset(x, y + 27), width: 43, height: 43),
        3.14,
        3.14,
        false,
        p);
    p.color = yellow.withValues(alpha: .35);
    canvas.drawCircle(Offset(size.width * .33, size.height * .33), 9, p);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
