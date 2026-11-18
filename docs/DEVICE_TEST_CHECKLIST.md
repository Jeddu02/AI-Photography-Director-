# Hardware acceptance — pending

Record phone, Android version, camera version, lens, preview aspect ratio and orientation. Host tests or compilation do not establish these results.

1. Fresh install; deny/allow overlay, notifications and projection. Verify denial leaves no unwanted service. Manual guides require overlay but no capture consent.
2. Share the entire display; open stock camera; calibrate all four edges. Verify tracking positions, draggable/collapsible controls, opacity, modes and grid.
3. Verify shutter, zoom, exposure and lens controls remain usable outside the control window. Some cameras reject all overlays; document incompatibility.
4. Check body/face placement, group edge warnings, motion, low light, closed-eye estimates and stable readiness. Verify no automatic shutter.
5. Rotate and change camera layout/resolution; verify positions and state reset; recalibrate the rectangle. Test front-camera mirroring separately.
6. Cover the lens or test a protected preview. Expect cautious dark/unavailable warnings, no bypass. Stop and start manual guides.
7. Pause/resume, hide/show, stop, revoke overlay/capture, lock/unlock, kill process and begin another projection. No silent restart/token reuse. Verify cleanup and new consent.
8. Switch nonsensitive apps deliberately. Whole-display capture can follow their content; stop before private apps. App-name recognition is unsupported.
9. Run 30 minutes per preset. Measure actual inference latency, frames, dumpsys memory, battery and thermal state; check for growing memory, starvation and leaked windows.
10. Airplane mode and fresh process: models must work without download. Verify no app-created screenshots/recordings.
11. Test large font and TalkBack, including the floating control, and same-key update with preserved preferences.

Production/hardware compatibility remains unqualified until the critical capture, offline inference, touch, lifecycle and extended-operation checks pass on the target phone.


## v0.2 shot finder — all pending on device

- Install over v0.1 without uninstalling; verify existing calibration/mode/opacity remain and grid defaults off once.
- Compare Auto/Landscape on an unlabelled scene: a scene-dependent suggested frame appears. Test Find another shot, pause/resume and switching modes.
- Aim at the reticle, then zoom by the relative multiplier. Confirm remaining zoom decreases and the target does not repeatedly demand more zoom. Lens switches/large pans should reacquire rather than show stale green readiness.
- Watch position, size, color, readiness arc and pulse at all presets; report jitter and device-specific animation performance. No hardware FPS claim yet.
- Confirm a stable aligned scene can turn green; movement, dark frames, cropped people or people missing in Portrait must revoke/prevent it. Verify shutter remains fully manual.
- Check compact/manual/paused/stop states end shot animation; verify the grid toggle and four-edge calibration still work.
- Repeat existing rotation, lock/unlock, projection-stop, protected-preview, thermal and extended-operation checks below/above.


## v0.3 Subject Director — pending on phone

- Update over v0.2 without uninstalling; verify calibration/permissions/settings remain.
- Expand ◉ → Subject: Automatic. Choose a bottle/cat/person when detected; confirm selected class and framing remain while another object/person appears. Missing objects must not be invented.
- Select a moving person, allow a brief detector dropout, and verify subject-lost clears readiness; reopen the selector if a new tracker ID is assigned.
- Leave the menu open while the scene changes; stale ID/class choices must be rejected. Reopen to refresh. Distinguish same-class choices by image position.
- Select Whole scene in Auto/Landscape/Portrait: retain full framing, no zoom cue or alternative-shot button. Green means aligned and steady only.
- Test bodies near edges and tighter crops near other people; require breathing room and avoid cutting detected bodies. Try intersecting objects and verify overlap warning while acknowledging box false positives.
- Confirm marginal scenery does not automatically demand more zoom, but a clearly concentrated detail or small selected subject can earn a tighter frame.
- Watch reticle jitter and green-state pulse on the real camera at every preset. Repeat rotation, pause/resume, mode/settings change, lock, projection stop and extended-operation tests. Selection should clear when the detector intentionally resets.

## v0.5 capture guidance

- Confirm the overlay gives one instruction at a time. After consecutive live framing checks pass, look for the green **Ready to capture** checkmark. No countdown should appear.
- Move the phone or obscure the target. The checkmark should clear when subsequent analysis no longer qualifies. Confirm pausing or stopping also clears it.
- In **Subject → Select on preview**, tap a watch-sized object, then try dragging around a cat. Check that the overlay follows a static target during a slow reframe. Verify you can cancel the selection and still tap the stock camera.
- Try a tighter frame around the cat with a bright window or fan beside it. Compare with the wide frame through **Find another shot** or **Whole scene**. Verify that the subject stays inside both frames.

## v0.6 automatic preview

- Keep **Automatic preview detection** on and start AI assistance. Approve Android screen sharing. Verify that the stock camera opens where supported.
- Switch between portrait and landscape and between camera aspect ratios. Confirm that the guides and **Select on preview** follow the visible live preview. Camera controls should remain outside the detected crop on supported layouts.
- Aim at a dark, low-detail surface, then back at a detailed scene. Check that ambiguous edges clear capture readiness and the overlay resumes when the preview becomes detectable.
- For a layout with no clear boundary, turn off automatic detection and verify that the four saved edge settings still work.

## v0.7 AI Camera

- Open **AI Camera** with screen sharing and draw-over-apps access disabled. Confirm it requests Android camera permission and starts the in-app viewfinder. Deny permission and confirm the app stays usable.
- Select 4:3, 16:9, 1:1, and 3:2. Rotate the phone and check the portrait equivalents. Put a distinct object near each edge, capture, and compare the displayed JPEG with what you framed. Account for EXIF rotation and rounding when checking dimensions.
- Test tap-to-focus, pinch zoom, grid, lens switching, flash on a flash-capable lens, and self-facing preview versus saved mirroring. Review recent photos.
- Compare v0.7 in-app output with the manufacturer's Camera app in the same lighting. Check that the live checkmark clears after a ratio change, rotation, and a move that breaks alignment.
- Confirm **Stock Camera Assist** still starts separately after screen sharing consent.

## v0.8 camera tools

- Aim at a scene with dark overhangs. Try **Next frame** and compare the tight and full views when available.
- Turn on **Level**, tilt the phone, and rotate the screen. Check that the two lines align when the phone is level. Point up or down and confirm the line hides when tilt can no longer be estimated.
- Test **Timer off**, **Timer 3s**, and **Timer 10s**. Tap **Cancel timer** during a countdown. Confirm a countdown also cancels when leaving the camera or losing the live preview.
