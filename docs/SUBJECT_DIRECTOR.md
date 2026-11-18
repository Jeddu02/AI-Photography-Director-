# Subject Director v0.3

This contract supersedes overlapping v0.2 behavior in SHOT_DIRECTOR.md. All processing remains on device with the existing bundled models. No new SDK, permission, account, API or network dependency was added.

## Subject selection

Expand the floating ◉ and open **Subject: Automatic**. Choose automatic routing, **Whole scene · stay wide**, or one of up to four currently detected subjects. Entries identify class and image position. People sort first, followed by area×reported confidence; detections below the sensitivity threshold are omitted. A face mostly inside a detected person (>=80% of face box area) is hidden as a duplicate menu choice. Faces can still be selected when no body is detected. The stock-camera image never becomes an interactive touch overlay; choices are buttons within the existing movable control panel.

The menu uses transient tracker IDs, not real-world identities. It is a bounded snapshot refreshed by reopening; clicking verifies the same ID/class remains in the latest results and that those results are no older than 2500 ms. Stale choices are rejected. Missing/unsupported classes cannot be selected: a wristwatch, for example, is not promised as a separate detectable category.

A detected choice overrides automatic mode routing. A selected bottle can remain the focus even while a person is visible. Whole scene uses only the full preview rectangle and does not suggest zoom; it may qualify geometrically even for a plain scene because it is an explicit framing choice, not a quality judgment. Automatic Portrait/Group still require detected people. Manual Composition does not use the selector or claim AI readiness.

A selected ID that disappears immediately produces a red subject-lost message, clears the goal and revokes readiness. The engine will not silently substitute another same-class instance. The user must reopen Subject and choose again if the tracker assigns a new ID. This can happen after detector dropout, fast movement or lens switches. The tracker itself uses class/box overlap and can confuse nearby similar subjects; selection is not identity recognition. Changing settings, rotation/capture resizing, pause/resume or restarting the service clears selection to Automatic when detector tracking resets. A simple >2000 ms observation gap resets the goal/readiness while preserving the explicit choice, which still must match its ID/class to qualify.

## Crop protection

CropProtection applies before ranking:

- Retain >=99.5% of the selected box/group union.
- Tighter frames require clearance >=6% of crop width for a person/group containing a person, or >=4.5% for other subjects/faces. The full view remains available when a tighter safe frame cannot fit.
- Reject a tighter crop that includes >8% but <99.5% of another detected person's box. This permits excluding an unrelated person completely, while avoiding crops through detected bodies.
- While directing, a selected person within 3.5% of a preview edge (other selected objects 2%) triggers a widen-framing warning and clears the previous goal before capture readiness.

These checks protect detected rectangular bounds, not anatomical keypoints. They cannot recover already cropped feet, infer hidden body parts, or identify every leaf/wire/foreground obstruction. Faces without detected bodies receive face-box protection only.

If another sufficiently large non-face detection overlaps >28% of selected bounds, ask for a clearer angle and block readiness. The competing box must have >10% of the selected box area. Selected group members and duplicate face detections are excluded. This is a box-overlap heuristic, not depth, foreground-order, obstruction or background segmentation. Nested objects or inaccurate boxes may produce false warnings. An unrelated closed-eye face does not block a selected bottle: face checks apply to faces associated with the selected person/group only.

## More selective zoom and reasons

The existing same-aspect crop lattice (1/.86/.72/.58) and content score remain. A tighter subject crop now needs a score gain >=.07 over the full view; scene-only crops need >=.045. Lesser differences keep the wider view and are not offered as tighter alternatives. This intentionally replaces v0.2's .005 threshold. Find another shot cycles only qualifying diverse proposals; it does not invent an alternative when only the wide frame qualifies. The button is omitted for explicit Whole scene.

Each proposal adds a short reason: **Keep this wider view**, **Give [subject] more of the frame**, or **Concentrate visible detail**. Margin/overlap warnings provide their own reason. The overlay retains aim, relative zoom and hold-steady cues. Zoom remains an estimated multiplier from the current visible view, not the stock camera's absolute zoom reading, and is never applied automatically.

Green now says **Framing aligned • capture when ready**. It means the supported framing, exposure, motion and applicable face checks passed for >=1.3 seconds plus instruction hysteresis. It does not rate beauty, guarantee focus, or identify the best moment. Changing the detected focus's tracker signature restarts readiness; a different same-class subject cannot inherit a green state. Red failure/lost-readiness transitions remain immediate. Manual shutter control remains with the user.

## Calmer animation

SmoothFrame ignores coordinate target changes smaller than .006 normalized units on all four edges. Changes accumulate relative to the last accepted target, so real movement eventually updates. Meaningful movement still interpolates with the existing 180 ms time constant. Readiness-color/progress interpolation remains 140 ms; the aligned-state pulse amplitude drops to 20% of its adjustment-state amplitude. Draw scheduling remains separate from inference. No on-phone FPS or battery improvement is claimed without measurement.

## Verification and remaining phone tests

New pure Kotlin tests cover explicit routing, whole-scene behavior, subject loss, stale-choice eligibility, duplicate-face suppression, bounded menus, crop margins, unselected-person cuts, box-overlap warnings, unrelated-face checks, scope changes and deadband behavior. Existing tests also cover geometry, zoom progression, readiness, transforms and smoothing. Two prior assertions that always expected a scenery crop/alternative were updated to reflect the approved cautious-zoom contract; concentrated detail still has a separate test proving it can earn a tighter frame.

The user's three photographs informed the changes; they are not training data, bundled test assets or distributed with the source. The new Android controls have compiled/lint/automated logic coverage; no attached phone/emulator was available for native interaction testing. Live detection availability, selection usability, lock persistence, crop aesthetics, animation, stock-camera touch-through, interruption behavior and sustained performance still require phone validation.
