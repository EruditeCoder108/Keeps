# Paced preparation and heat controls

17 October 2026 · M5-02 ACTIVE; M0-05/M4-04/M5-01 ACTIVE; M1-05 VERIFY.

The user stopped an approximately 3,800-photo preparation run after about 145 photos because the phone felt warm in hand. The user subsequently clarified that this was not a report of overheating; the concern is sustained resource use and a practical full-library preparation experience. Concurrency bounds prevent loading the whole library at once, but continuous CPU inference still needs pacing. Catalog capacity does not prove sustained thermal comfort.

## Changes

Content search is first in Settings: model source/import and readiness, content types, counts and Pause. Missing-model media is not queued; unsupported formats retain filenames. Compatible completed work is skipped.

Default **Gentle + 25 files** adds rests approximately twice the preceding file's preparation time. Balanced uses approximately equal rest. Rest is bounded to five minutes; accumulated work triggers longer burst breaks. Optional 100/all batches and charging-only are available. Set charging-only before starting; pause/restart before changing a constrained pending run.

LiteRT-LM requests two CPU threads per main/vision/audio backend using the resolved 0.18.0 SDK's threadCount API. This is not a process-wide thread cap. Input resolution, model digest, vector namespace and Room v5 remain unchanged.

The worker checks heat before each file and at completed audio/video checkpoints. Headroom polling respects Android's ten-second interval. Android 16+ device-reported light/moderate thresholds determine headroom hysteresis; unavailable thresholds use a fallback. Battery 40°C/38°C start/resume values are scheduling heuristics, not universal safe-temperature ratings. Cooling defers the persistent queue; manual Pause stays authoritative.

## Actual checks

- **37 JVM tests**, no failures/skips: includes pacing bounds, hysteresis, invalid sensors and the observed vendor mapping.
- **15 Android tests**, no failures/skips, **116.968s**: database/migrations, simulated cooling/resume/poll cadence, persistent controls, actual-model image batches and sequence compatibility. [Runner](device/thermal-instrumentation-2026-10-17.log).
- Six UUID-owned generated images: two batches prepare four and leave two unqueued. Repeat requests queue zero; completed vectors remain bit-identical. **84.191s** includes initialization, scheduling and rest. [Probe](device/thermal-image-batch-2026-10-17.json).
- The actual pack accepts ordered/reversed two-frame inputs and aligned soundtrack with finite 768-dimensional output under the CPU request. This is compatibility evidence, not motion/relevance validation or a full four-frame corpus comparison.
- Debug/test/lint build **1m36s**, **0 errors/80 warnings**; optimized build **3m1s**. [Debug](checks/thermal-debug-build-2026-10-17.log), [optimized](checks/thermal-optimized-build-2026-10-17.log). Existing R8 metadata warnings remain.
- Seven JNI names, four static ELF 16 KB alignments, ZIP alignment and v2 signing pass. [JNI](checks/thermal-jni-2026-10-17.json), [native](checks/thermal-native-2026-10-17.json), [ZIP](checks/thermal-zipalign-2026-10-17.log), [signature](checks/thermal-signature-2026-10-17.log).

The first attempt passed 14 tests but timed out its image batch in cooldown: this phone's light state reported headroom about 0.84, above the initial absolute cutoff. Its reported light/moderate thresholds are approximately 0.8333/0.9667. Using those levels corrected the false wait; the full selected suite then passed. [First attempt](device/thermal-first-attempt-2026-10-17.log). No thermal override or deliberate overheating was used.

## Delivery and limits

Installed optimized preview: **54,521,305 bytes**, SHA-256 `476f1e319e2adc80f78823ab10e6e55de6835aef98b6ad6017dccc136bc220be`; delivery `artifacts/Keeps-preview-gentle-2026-10-17.apk`. [Artifact record](checks/thermal-artifacts-2026-10-17.json). Locally debug-signed; installs used -r. The phone disconnected after installation, before final optimized Settings/inference smoke.

Before launch, this connected installation had cache directories but no catalog/model. Discovery rebuilt metadata (5,367 files, 3,908 images), and the existing verified pack was reimported. This pass did not clear app data. The previous 145 prepared items were unavailable to verify; no recovery claim is made. Only generated fixtures and app-owned metadata/model storage were modified.

Pacing cannot guarantee a cool surface or immediately interrupt native computation. Long text/PDF work still needs finer checkpoint boundaries. Continuous 3,800-photo comfort, mixed use/charging, lower-memory phones, full-library ETA and representative relevance remain unverified. Four generated photos are not a throughput forecast for real photographs. Production signing, Play review and real 16 KB execution also remain open.

Try **Photos → Gentle → 25 → Prepare next 25** and assess comfort. Pause if uncomfortable and let the phone cool. Completed work is retained across sessions; thousands can be prepared incrementally, but the initial pass may still take hours or longer.

[Android thermal guidance](https://developer.android.com/games/optimize/adpf/thermal) documents device-dependent thresholds and polling. [Google's heat guidance](https://support.google.com/pixelphone/answer/3333708?hl=en) gives examples of reducing workload and letting a warm phone cool.
