# Automatic preparation for the overnight trial

18 October 2026. M1-05 and M5-02 remain ACTIVE. This build is for a real overnight trial, not evidence that a large library finishes in one night.

## What changed

- Search library offers one explicit **Prepare automatically** approval for the selected content types. It discovers later arrivals within the approved accessible library; selected-file automation remains restricted to those IDs. Access permission alone does not enable AI work.
- Automatic work requires charging and yields when the screen is on. Battery, free-storage, low-memory and calibrated thermal checks remain active. Gentle pacing, small internal passes and saved checkpoints remain in use. Manual on-battery work is a separate explicit choice and queue.
- A persistent WorkManager planner checks opportunities approximately every 15 minutes, with an earlier request after enable/resume. Android chooses execution time. Normal work constraints and quotas apply; there is no foreground-service exemption or exact overnight deadline.
- Manual Pause persists across process restart and takes priority over automatic scheduling. Disabling automation cancels only its planner/queue, retaining completed content and independent manual work. The optional low-priority preparation notification opens Search library and has a Pause action. A native model call may finish before cancellation takes effect.
- Persistent folder/album exclusions apply to descendants and later arrivals, including known SAF/MediaStore aliases. Individual exclusions remain separate. Rules remove derived search content and reject late writes; files remain visible in browsing. Room v6 migrates existing catalogs without clearing originals, favorites or compatible prepared content.
- PDFs and text now checkpoint bounded page/section work as well as audio/video windows. Existing limits remain: the first 24 PDF pages and first 512 KB of supported UTF-8 text. OCR and full large-document coverage are not introduced.
- A session card records searchable count at the start and now, last saved progress and active processing time. Counts can also change when sources or exclusions change. There is no calibrated completion estimate or claimed battery-energy measurement yet.

## Tonight

1. Open **Settings → Search library**. Select content types; Photos is a useful first-night scope. Review unwanted files, long videos and **Excluded folders** first.
2. Turn on **Prepare automatically**, approve the displayed scope and allow progress notifications if wanted. Existing compatible prepared files are skipped.
3. Leave the phone charging with its screen off. Home/lock does not mean Pause. Keep Android's normal app battery policy; do not Force stop Keeps. The first opportunity can take several minutes.
4. Tomorrow, check the session counts, last progress, active time and any waiting/failed/partial status. Opening the phone intentionally makes automatic work wait. Use **Pause** to stop further preparation while retaining completed work.

Charging itself produces heat; cooling checks can extend the run. Video/audio cost depends strongly on duration, not just file count or disk GB. No number of files or finish-by-morning promise is made before this trial.

## Verification record

- Final `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleBenchmark` succeeds in 6m6s. 37 JVM tests pass; lint has 0 errors / 88 warnings. Existing R8 Kotlin metadata warnings remain. [Build log](checks/overnight-build-2026-10-18.log).
- 21 native tests pass, no skips, in 2.059s: database/checkpoint guards (11), v1/v4/v5→v6 migrations (3), pacing/persistence/automatic policy (4), folder scopes and queue separation (3). These use isolated databases and controlled sensor inputs, not personal-file exclusion or real thermal stress. [Runner](device/overnight-instrumentation-2026-10-18.log).
- Optimized mapping retains all seven required JNI names; four ELF libraries and ZIP contents pass static 16 KB alignment checks, and the v2 signature verifies. The phone itself has 4 KB pages. [JNI](checks/overnight-jni-2026-10-18.json), [native alignment](checks/overnight-native-2026-10-18.json), [signature](checks/overnight-signature-2026-10-18.log).
- The actual installed model prepares both generated files in normal charging/screen-off WorkManager execution after the arm instrumentation exits. Last saved progress predates the verification instrumentation; elapsed time from arming to last progress is 102.244s, including scheduling and pacing, not a throughput forecast. The existing compatible/lexical chunk digest remains unchanged; the Pause receiver persists Pause and retains prepared chunks. No job was force-run and no cooling check was bypassed. [Probe result](device/overnight-background-probe-2026-10-18.json), [arm runner](device/overnight-background-arm-2026-10-18.log), [completion runner](device/overnight-background-check-2026-10-18.log).
- The probe's first fixture attempt failed because file URIs were passed through document-picker persistable-grant import; it was corrected to use the existing storage observation path, and the two failed-attempt fixtures were removed. A prior test compile error was corrected before this final build. The small actual-model probe runs on the debug build; the optimized build separately passes installation/UI and static native checks. This is not an optimized inference or relevance benchmark.
- The optimized build is installed using `-r`; no app data was cleared. Search library still shows 25 ready files. Its automatic confirmation is opened and cancelled, leaving the switch off for tonight's user approval. The two generated files/catalog rows are removed and original typed preparation preferences restored. Notification permission is granted. [Final controls](device/overnight-controls-2026-10-18.png).

The background probe uses an explicitly approved set of two UUID-owned generated files (one image, one short text), not the user's library. It records a digest of existing prepared chunks, exits instrumentation, allows normal WorkManager execution with the screen off, then checks that progress predates its verification process. It exercises the notification Pause receiver, removes only its fixtures and restores the original preparation preferences. The full-library switch is left off for the user to enable tonight.

Installed preview: `artifacts/Keeps-preview-overnight-2026-10-18.apk`, **54,708,895 bytes**, SHA-256 `6bead18854b9c4601aa13ae327313324f5c685437646045cccbfd53fdbd98586`. It is optimized and locally debug-signed. [Artifact record](checks/overnight-artifact-2026-10-18.json).

## Remaining gates

The actual full night, sustained comfort, throughput/ETA, measured battery use, unplug/replug and reboot/force-stop/OEM-restriction recovery matrix remain unverified. The small background probe cannot establish those results. Exact search still loads candidate vectors into memory; very large prepared libraries require M5-01 measurements. Representative image/action/speech/negative-query relevance remains M3-05/M4-03 work. Consumer model delivery, full provider reconciliation, accessibility, production signing and Play acceptance remain open.

Background policy references: [Android power restrictions](https://developer.android.com/topic/performance/power/power-details), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). These inform the design; the verification above records what this phone actually did.
