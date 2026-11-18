# Legacy rule contract

For v0.2 automatic modes with a scene map, see [SHOT_DIRECTOR.md](SHOT_DIRECTOR.md). The table below documents the older no-scene-map fallback; its Landscape readiness restriction does not apply to the new shot-goal pipeline.

Coordinates are normalized inside the user-selected preview rectangle. These are geometric/image heuristics, not learned aesthetic judgments.

| Rule | Inputs and threshold | Output/limits |
| --- | --- | --- |
| Subject | Model floor .35; user threshold .4–.9, default .55 | Auto favors people then largest area×confidence; no separate semantic mode classifier |
| Tracking | Same label and one-to-one IoU > .25 | Box EMA .65; temporary ID only, no identity recognition |
| Edge cropping | Relevant box union within .025 of a frame edge | Immediate red widen-framing warning; not anatomical segmentation |
| Placement/negative space/balance | Center or selected/nearest third, error > .075 | Arrow = desired subject position minus current position; describes image space, not physical camera direction |
| Headroom | Largest detected face top > .28 | Reduce empty space above face; hair/hats not segmented |
| Facing room | Reported yaw absolute >15 degrees at far facing-side third | Leave facing space; mirror/pose limitations apply |
| Subject size | Union height < .25 outside Street | Ask larger framing; no inferred physical distance |
| Background detail | Outer-third edge density > .65, Portrait | Suggest simpler background; not depth/segmentation |
| Stability | 64×64 luminance mean absolute frame difference > .055 | Hold steady; UI, exposure and subject motion can confound it |
| Exposure | Mean luminance < .13 or >42% pixels above .97 | Add light/reduce exposure; not sensor metering |
| Unusable preview | Mean < .025 for >2 seconds | Suggest manual fallback; cannot prove protected content versus darkness |
| Faces | Reported \|yaw\| >32 degrees in Portrait/Group | Wait for faces toward camera; missing faces do not prove no obstruction |
| Eyes | Minimum reported open-eye estimate < .35 | Wait; unknown stays unknown |
| Horizon candidate | Edge sweep -12..12 degrees; energy > .18 and >1.12× runner-up | Conservative confidence .7, angle >3° prompts alignment; may confuse architecture with horizon |
| Symmetry | Mirrored luminance mean difference | Computed but does not trigger advice |
| Readiness | Applicable supported checks pass | Score clamp(100−100×placement error−150×motion−20×clipping); at least 1300 ms qualifying observations +550 ms smoothing |
| Manual/Landscape | User choice | No automatic readiness claim |

One primary instruction. Significant visibility/exposure failures and loss of readiness replace stale capture advice immediately. A >2000 ms observation gap restarts readiness. Pause, settings changes and rotation reset analysis state. Overlay lines may be present in capture and influence simple image features. No perfect-moment or reliable physical-movement guarantee is made.

ML Kit's face API does not expose a detection-score probability. A returned face uses an internal routing weight of .9; it is not a claimed 90% probability. The sensitivity threshold primarily filters reported object-model scores. Eye-open probabilities and yaw are used only when actually reported by the face API.
