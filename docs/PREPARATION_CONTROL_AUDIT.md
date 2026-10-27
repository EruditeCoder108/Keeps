# Preparation control and pacing audit

Audit started 25 October 2026; final verification and packaging 26 October 2026. M1-05/M5-02 ACTIVE. Trigger: the user reports approximately 200 photos, 26 processing minutes and 2–3 hours elapsed, plus stale notification Pause and confusing resume controls. This audit uses source inspection and laptop tests. No phone is connected or accessed; earlier two-file phone evidence does not validate these new changes.

## Findings and fixes

| Previous problem | Implemented change |
|---|---|
| Settings Pause did not update the notification; its receiver had a separate update path. | One persisted status projection drives Settings, the Browse banner and notifications. Both surfaces call the same commands. Pausing removes Pause immediately; Resume appears after the current step drains. Completed notices have no stale Pause action. |
| Pause discarded queue membership. “Resume while charging” bulk-selected the library again. | Retain queued IDs, lane, policy and checkpoints. Resume never calls queueAll. A different selection requires explicitly ending the paused manual run. Recover known old pause markers without adding unrelated imported files. |
| Manual/automatic starts could conflict with a shared Pause flag. | One run-control group. Starts are explicit modes; a manual request cannot silently unpause/expand an automatic scope. End/disable keeps prepared content. |
| Late worker/notification actions could race a newer command or append after cancellation. | Persisted command revisions guard publications, actions and enqueue. Active-worker ownership distinguishes Pausing from Paused, including process recovery. Interrupted INDEXING rows rejoin the saved queue. |
| Reopening Keeps/planning again appended duplicate worker requests. | Unique KEEP for external scheduling; only the current worker appends its continuation. |
| Gentle rested twice the processing time, with minute-long burst rests. | Sensor-aware short rests: Gentle 25% up to 5s when cool, 50% up to 10s when warm; Balanced zero cool rest, 25% up to 5s when warm. Existing thermal/battery hysteresis and charging/memory guards remain active. Clear obsolete long rest deadlines. |
| Four-file/60s passes and long rests repeatedly handed work back to Android. | At most 48 sections/files or six minutes per pass; short rests stay inside the pass. Native work remains serialized, media/document checkpoints bounded. A native call can outlast a nominal boundary. |
| Resource waits used increasingly long linear retry backoff. | Finish the pass and request a fixed recheck: 30s for resources, 5min for interactive use. Android can defer actual execution. Periodic discovery remains 15min; no forced jobs/exemption. |
| Only processing time was shown. | Show total elapsed, processing, measured app breaks and other time. Other includes scheduling, cooling, phone use, Pause and previously unmeasured time. Resume preserves counters. No calibrated ETA or energy claim. |
| Stop checked media timestamps for saved content, overlooking PDF/text checkpoints. | Any saved chunks survive end/disable and leave incomplete content partial. Originals are never mutated by preparation controls. |

Room remains v6. Model digest/prompts/preprocessing/vector namespace and the two requested CPU threads per backend are unchanged. Production still constructs the real indexer; an interface permits deterministic lifecycle tests. Main changes: [controller](../app/src/main/java/dev/keeps/library/indexing/PreparationController.kt), [notifications](../app/src/main/java/dev/keeps/library/indexing/PreparationNotifications.kt), [queue commands](../app/src/main/java/dev/keeps/library/catalog/LibraryRepository.kt), [worker](../app/src/main/java/dev/keeps/library/indexing/IndexWorker.kt).

## Interpreting the reported timing

A deterministic 200 × 7.8s workload represents 26 active minutes. The **old Gentle policy alone requests 70m30s of rests**: about 96m30s before Android scheduling, cooling and other delays. This explains how the source could produce a large gap; it is not a reconstruction of the user's actual run.

For the same synthetic workload, new Gentle requests 6m30s of rests when cool or 13m when warm, with at most seven worker passes. These are policy regression calculations, **not phone speed/ETA promises**. A hot phone can still need longer cooling; Android controls actual scheduling.

## Controls

- One **Pause preparation / Resume preparation** control follows the current run. Pausing drains the current non-interruptible step; no next file starts.
- Manual Resume keeps its charging/battery choice. **End this run** clears waiting membership while keeping originals/prepared data, then permits a new scope.
- **Prepare automatically** approves the selected scope and future arrivals while charging/screen off. Competing manual-start buttons are hidden while enabled. Pause remains authoritative; switching it off keeps prepared content.
- With no pending run, **Prepare while charging** or **Prepare now, including on battery** starts a new confirmed manual scope. No resume button secretly reselects the library.
- Notification intents carry their command revision. Receiver failures leave a recoverable paused state rather than an uncaught background coroutine exception.

## Verification

- **66 tests pass, zero failures/errors/skips:** 43 core JVM tests and 23 Robolectric API35 tests (controller 9, repository 11, worker 3). [Suite record](checks/preparation-audit-tests-2026-10-25.json).
- Final core/unit/debug/test-APK/lint/optimized build succeeds in **5m49s**. Lint: **0 errors / 85 warnings**. Existing R8 Kotlin metadata warnings remain. [Build log](checks/preparation-audit-build-2026-10-25.log).
- Seven required JNI names remain; four ELF libraries and ZIP contents pass static 16 KB alignment checks. V2 signature verifies, and the certificate matches the 18 October delivery. No Internet permission is packaged. These are static checks, not a 16 KB runtime test. [JNI](checks/preparation-audit-jni-2026-10-25.json), [ELF](checks/preparation-audit-native-2026-10-25.json), [ZIP](checks/preparation-audit-zipalign-2026-10-25.log), [signature](checks/preparation-audit-signature-2026-10-25.log).
- Optimized, locally debug-signed APK: `artifacts/Keeps-preview-preparation-audit-2026-10-25.apk`, **54,741,663 bytes**, SHA-256 `38934f33538c88f798447093d5fc7d9f8dc9d42bf4b899ad816cd9182c8c5a1d`. **Not installed or tested on the phone in this slice.** [Artifact record](checks/preparation-audit-artifact-2026-10-25.json).

Coverage includes notification parity/stale actions, persistent policy/scope, Room queue identity/checkpoints/legacy recovery, deduplicated scheduling, interrupted rows, fixed resource waits, failed-file isolation and Pause during a synthetic non-interruptible indexing step. Robolectric executes Android/Room/WorkManager code on the laptop; it does not run LiteRT inference. Its broadcast test explicitly registers the production receiver because the simulator does not auto-register filterless manifest receivers, then exercises real onReceive/goAsync. An initial simulator-dispatch timeout was corrected with that explicit test registration; the production manifest receiver stays non-exported. No personal files/catalog/model are used. New flow acceptance is VERIFY on the next phone run, not DONE from this suite.

## Remaining acceptance

The new optimized APK still needs phone checks: Settings/notification parity, one draining native call, same-scope resume, charger/screen transitions, sustained comfort and wall-time breakdown, process death/reboot and OEM restrictions. Actual-model relevance and lower-memory/large-vector performance remain M0-05/M3-05/M4-03/M5-01 gates. Existing PDF/text caps and missing OCR are unchanged. This audit does not establish that the entire app is production ready.

Platform reference: [Android work-chain cancellation and retries](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work), [power restrictions](https://developer.android.com/topic/performance/power/power-details). Requested delays do not guarantee deadlines.
