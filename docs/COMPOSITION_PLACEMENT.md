# Composition placement in v0.4

**Overlay appearance → Composition placement** controls the horizontal target for a detected subject. Left third and Right third target one-third or two-thirds across the suggested crop. Center targets the middle. Nearest third chooses a side using the subject position or the direction a detected face is turned. Groups default to center under Nearest third. Explicit placement takes priority.

For a person with an associated face, the planner anchors horizontal placement to the face center. It scores space above the face and favors room in front of it when ML Kit reports 15–60 degrees of sideways head turn. The headroom target is 11% of crop height when a person box is available or 17% for a face-only detection. The rule uses the detected face boundary as an estimate of the head. It depends on face angle and box quality. With no associated face, placement uses the selected subject box.

The planner checks a candidate at the intended placement for each existing crop size alongside the existing search grid. The same subject and bystander crop protection and minimum improvement thresholds apply to all candidates. Whole scene keeps the full view. An explicit third is a framing preference, so the wider frame can still win when a safe crop cannot achieve it or the gain is too small.

With the optional rule-of-thirds grid on, grid cells move with the suggested shot. The thin outer border marks the calibrated camera preview. Manual mode draws its grid over the full calibrated preview. The user controls the stock-camera shutter and zoom.

The composition tests cover opposite thirds, facing direction, headroom, group balance, unrelated-face rejection and integration into the crop planner. Phone verification should include a person turning each way and a group near the preview edges. Check that manual preview calibration, the floating selector, and camera controls remain usable.
