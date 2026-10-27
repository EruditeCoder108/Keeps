# Keeps: real phone testing

## 18 October 2026 — consumer interface slice

CMF Phone 2 Pro / Android 16: 14 selected tests pass (10 database including new transactional/idempotent bulk exclusion, 2 migrations, 2 isolated cooling policy), 1.355 seconds. UI checks cover the separated Settings/Search library flow, long-video review, Excluded empty state, nested Back and Light/Dark/Gallery. Existing model and displayed 24 prepared files remain after data-preserving updates. This is not a full-library preparation, overnight or broad relevance test. [Current report and retained evidence](docs/CONSUMER_UI_SLICE.md).


Date: 7 October 2026. The user authorized installing and testing on their USB-connected phone.

## Result

The final debug build is installed, the official local model is imported, and six synthetic test files are indexed. **11 JVM tests and all 6 phone instrumentation tests passed.** ADB UI assertions also verified the first visible result after changing searches. Lint reported 0 errors and 21 warnings. The final APK signature and absence of Internet permission were rechecked.

Device: CMF Phone 2 Pro, shown by Android's storage picker; system manufacturer `Nothing`, model `A001`, Android 16 / API 36, `arm64-v8a`. Reported total RAM was about 7.2 GiB, with about 2.9 GiB available before testing. About 158 GB of storage was free. The phone was USB charging.

Only generated fixtures under `Download/KeepsTest` were imported. Personal media was not indexed. Test media and the source model pack remain in that folder so the app can be tried again.

## Model provenance

- [Official LiteRT community repository](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/tree/24d962e906c7d332c6428e71c9676855024569e2)
- File: `embeddinggemma-2-740m.litertlm`, the portable pack, not a chip-specific NPU pack.
- Repository revision observed: `24d962e906c7d332c6428e71c9676855024569e2`.
- Size: **484,622,336 bytes**.
- SHA-256 from upstream LFS metadata, matched against the downloaded file:

```text
e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c
```

Import used the normal Settings document picker. The app copied the model into its private storage, initialized LiteRT-LM 0.18.0 on CPU, and verified an actual text embedding. Model replacement was repeated successfully. The app has no Internet permission; model downloading happened on the development computer.

## Automated phone checks

The final runner reported `OK (6 tests)` in **7.842 seconds**, with no failures or skipped tests:

| Test | Observed result |
|---|---|
| Database deletion | Removing an asset cascades to its chunks |
| Model revisions | Incompatible vectors are excluded/removed; lexical text survives |
| Duplicate sources | A URI cannot create two catalog entries |
| Android lexical behavior | English and Hindi word matching behaves correctly without unrelated filename matches |
| Native retrieval | All eight fixture queries passed; seven vectors have the expected 768 dimensions and finite values |
| Playback | The actual WAV and MP4 decoders prepared successfully and playback position advanced; expected durations were verified |

The native retrieval test checked:

- “red circle on a white background” ranked `fixture-01.png` first among images.
- “blue square on a white background” ranked `fixture-02.png` first among images.
- “appointment to fix a bicycle tire” returned the test note in the text filter.
- “blue square” selected **page 2** of the two-page PDF.
- The video result carried **00:02** frame provenance in its four-second clip.
- The audio result carried the three-second WAV's time interval.
- Without a media filter, “green helmet” and the exact test reference code ranked the matching note first.

The eight-query batch took **3,393 ms** in the final native test, including that test's first engine initialization. An earlier eight-query run took 1,774 ms. These are small-run observations, not latency percentiles or performance guarantees.

The audio and text categories each had only one fixture. Those assertions verify pipeline integration and provenance, not competitive retrieval accuracy. The audio fixture is a generated tone, not a speech-recognition benchmark. Playback tests used zero player volume and verified decoding/progress, not physical speaker output.

## UI and source-lifecycle checks

The app launched and rendered on the phone. Normal document selection imported six local fixtures and preserved access across app replacement and instrumentation process restarts. Image search placed the matching shape first. Image, text, and PDF previews rendered, PDF search opened page 2, and the Previous button rendered page 1. Video and audio previews loaded the expected durations. The Gallery displayed the two images and video. Saving/unsaving a file updated Saved and its empty state.

Indexing Pause preserved completed items and reset unfinished items to a resumable state. A subsequent Index library run completed the batch.

To test missing sources, only `fixture-02.png` was temporarily renamed. Preview detected the missing original; a debug database snapshot confirmed `NO_ACCESS` and **zero chunks** for that asset. Restoring the fixture and selecting it again recovered access without creating a duplicate. Reindexing restored its vector.

Removing the audio fixture through the confirmation dialog removed its catalog entry and chunks. Its original WAV remained in Downloads with the same SHA-256 as the host fixture. Selecting it again restored the six-file test library, which passed the final tests.

## Fixes found during testing

- Model validation initially wrote caches beside permanent inference caches. The app now uses a disposable validation directory and removes obsolete model caches after replacement. Existing temporary caches are cleaned up. Observed permanent cache usage dropped from **734 MiB to 367 MiB**; no validation directory remained after import.
- Reselecting an unavailable file now reports that access was restored instead of saying “Added 0 files.” Already-imported files also receive a clear message.
- The result count uses “1 item” for a single result.
- A new search used to preserve the previously visible asset's position in the keyed list, even when the correct ranking moved it below the best match. Lists and grids now reset after the new query/filter/destination results publish. Database refreshes for the same search retain the user's scroll position. Native ranking checks and ADB checks for the first visible note/image both passed after this fix.

One memory sample during initial indexing showed **1,111,080 KiB PSS** and **1,209,448 KiB RSS** for the whole app. A later sample after relaunch and text-query inference showed **488,685 KiB PSS** and **604,972 KiB RSS**. These include native inference and model-backed memory and differ with the operations performed; neither is a peak measurement or a claim about all devices. The model file occupies about 485 MB separately from its caches. The source copy in Downloads is an additional copy.

## Reproduce

Use a dedicated test installation containing only these generated fixtures. The retrieval test refuses to run against a mixed personal library. Both device integration tests are opt-in; a normal `connectedDebugAndroidTest` run executes the database and platform-ranking tests and skips the integration tests.

Generate fixtures on the development computer (Python, Pillow, and ffmpeg required):

```powershell
python tools/create_device_fixtures.py
```

Replace `SERIAL` below with the selected device from `adb devices -l`. Create `Download/KeepsTest`, copy the six `fixture-*` media/text files there, and copy the verified model pack. Import the model in Settings, add the six fixtures through **Add files**, then use **Index library** and wait until all six are indexed.

For ADB-copied files, the Downloads provider did not initially list the fixtures on this phone. They were visible through **Show roots → phone storage → Download → KeepsTest** in Android's document picker.

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r -e keepsDeviceFixtureTests true dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

The fixture generator's `expected.json` describes each file. ADB helpers require an explicit serial:

```powershell
python tools/device_ui.py --serial SERIAL inspect
python tools/device_ui.py --serial SERIAL capture my-check
python tools/check_device_catalog.py --serial SERIAL --label my-check
```

Use the catalog helper only when indexing is idle. It snapshots the debug app's database/WAL into ignored local output and refuses to report non-fixture asset names. The UI helper reads a fresh accessibility tree before tapping a label. The later continuation slice found that Android returns exit code zero on idle timeout while leaving old XML; the helper now rejects that response. Hide the updating media-control overlay before dumping its parent viewer. The earlier underlying-activity dump was stale and does not establish a dialog accessibility defect.

Logs, snapshots, and screenshots for this session are in ignored `.work/device/`; selected screenshots are in `docs/device/`. The final APK hash is recorded in [BUILD_NOTES.md](BUILD_NOTES.md).

Selected phone captures: [image search](docs/device/blue-square-search-final.png), [text search after the scroll fix](docs/device/text-search-fixed.png), [text preview](docs/device/text-preview.png), [matching PDF page](docs/device/pdf-page-two.png), and [gallery](docs/device/gallery.png).

The final current-app log capture contained no fatal exception, fatal signal, or ANR. The temporary instrumentation APK and `/data/local/tmp` capture files were removed after testing. Keeps remains installed and open; its model, index, and synthetic source files remain available.

## Remaining limits

This is one phone and six synthetic files. It does not establish representative search accuracy, robust no-match handling, multilingual performance, long-session stability, battery consumption, thermal throttling, or compatibility with every document provider. GPU/NPU backends were not tested. Native logs warned that an NPU accelerator was unavailable; the configured CPU path worked.

At that prototype snapshot video embedded individual sampled frames and reported partial indexing. The later ordered-video slice below supersedes that implementation. Standalone audio still requires mono 16 kHz PCM WAV up to 30 seconds. Compressed standalone recordings, OCR/selectable PDF text, gallery-wide discovery, and folder watching remain future work as described in [README.md](README.md).

## Chosen-folder slice — 7–8 October 2026

This new session tests the new features rather than inheriting the historical prototype pass above. At the first package check Keeps was absent from the phone; a real on-phone v1 upgrade is therefore not claimed. `MigrationTest` independently creates the exported v1 schema with seeded favorite/content data and opens it through the production Room migration, which validates the resulting schema.

The user selected `Download/KeepsBrowseTest` in Android's folder picker. It contains copies of only the six generated fixtures, organized in Pictures/Documents/Media. Discovery produced six unique catalog identities without automatically preparing content. Real Browse folder rows, category counts, Gallery media/parent-folder albums and model-absent browsing were observed. Explicit preparation scope review produced the expected seven vectors. Per-file exclusion removes text/vectors while preserving ordinary visibility and filename matches.

The final 8 October debug installation passed **11 instrumentation tests with no skips in 34.836 seconds**. This includes migration and exclusion/version races, native model/retrieval, ordered-frame/aligned-tone inputs, and actual audio/video decoding/playback. [Retained runner output](docs/device/slice-instrumentation-2026-10-08.log). [All six original/copy SHA-256 checks](docs/device/fixture-preservation-2026-10-08.json) matched the host fixtures after testing.

The exact pack accepted finite 768-dimensional embeddings for forward/reverse image sequences and aligned generated audio. These checks demonstrate runtime input compatibility, **not useful motion understanding, MediaPipe parity, speech quality, or completed video moment search**. At this M1 snapshot production video indexing remained the single-frame recipe. The subsequent M3 implementation is recorded below. The full sequence probe observations and license provenance are in [slice evidence](docs/M0_M1_SLICE.md).

Initial UI checks covered light/dark and Gallery at 200% font size; global font scale was restored to 1.0. Dark system-bar contrast and Gallery process-restoration scope defects found then were corrected before the final build. Final optimized runtime and correction checks are recorded in the slice evidence. TalkBack, RTL, landscape, alternative providers, huge libraries and the complete recovery matrix remain open.

Use the new command with all three opt-in flags for this generated chosen-folder test setup:

```powershell
adb -s SERIAL shell am instrument -w -r -e keepsDeviceFixtureTests true -e keepsCatalogTests true -e keepsSequenceTests true dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

The benchmark APK is an optimized debug-signed probe. Install it with `-r` only after recording the idle fixture catalog; preserve app data/model/grants and perform ordinary search/viewer checks. Instrumentation's direct application references are for the debug APK, not an optimized runtime test. Static native/ZIP/signing evidence is in [build notes](BUILD_NOTES.md); this phone's 4 KB kernel does not verify 16 KB runtime behavior.

## Ordered-video slice — 8 October 2026

The user continued to authorize connected-phone testing. A generated 18-second red/blue/green chapter video with stereo 48 kHz AAC was added to the already granted KeepsBrowseTest/Media folder. The catalog now contains **seven generated assets**. No personal content or original file mutation was used.

**Eight focused tests passed, no skips, 81.516s:** actual-pack finite sequence/anchor embeddings, exact decoded AAC overlap, interrupted checkpoint retention, two bounded resume batches, correct 6-second blue chapter, and Room migration/exclusion/source-version protection. The first sequence-only recipe failed the expected chapter assertion; adding a visual anchor fixed it without relaxing the expected result.

The actual SAF/WorkManager production path prepared all three intervals with four ordered frames and soundtrack. Its complete fixture test then passed in **5.341s** on saved checkpoints, including scoped/library rankings, three candidates, filename-only scope, original SHA-256/old-chunk preservation, and Media3 playback advancement with 5/6/12-second seeks. The initial production test stopped after its successful preparation/ranking checks because its negative filename query contained the common token `fixture`; the test input was corrected to `note`.

Actual UI checks observed the interval buttons and timeline, lead-in at 0:05, Return to match showing blue at 0:06, and Search this video opening the correct scope. The optimized final app retained the library/model/grants during installation and completed a new native query with the correct best interval. The new logo is integrated into both APKs. The phone is left on the optimized Keeps build with the prepared fixture library available.

[New evidence, reproduction commands and remaining gates](docs/VIDEO_MOMENTS_SLICE.md). The old six-fixture opt-in tests intentionally skip on the expanded seven-file catalog. Motion/relevance, long-worker continuation/process death, codecs/providers, TalkBack, actual 16 KB runtime and optimized new-window preparation remain unverified.

## Compressed audio and continuation — 8 October 2026

The installed app is now the new **debug** build with the stacked-horizon folder/image logo. Two generated recordings were added to the existing granted Media folder: 54-second stereo 44.1 kHz AAC/M4A and 18-second stereo 44.1 kHz MP3. The library contains **nine generated assets**; original files, the model, tree grant and prior vectors remain available. The independent 54-second video test used a temporary app-owned source/catalog row and removed both after its run.

Seven Room tests plus migration passed in the first run. Its new audio overlap assertion then exposed a one-sample interpolation rounding defect; the video test persisted nine windows but timed out due to a faulty assumption about retained WorkManager history. Exact overlap and expected scene assertions were preserved. After integer-phase resampling and corrected completion checks, the focused native runner reports **OK (2 tests), zero skips, 411.294s**. MP3 has three prepared windows, AAC nine; the fresh video has nine windows across two successful work records and retrieves repeated blue chapters at 0:06/0:24/0:42.

The audio test took 37.545s including retrieval. Video preparation/test/query took 371.178s; checkpoint-sampled process PSS reached about 1.15 GiB. These are controlled fixture observations, not a continuous peak, battery/thermal benchmark or a multi-minute recording test. The user offered manual testing, so a short real-video relevance check was requested; its result remains pending.

Final UI checks on the subsequent thumbnail-only correction verify red/blue/green nearest-frame cards, 00:12 Return to match showing the green scene, close preserving results, MP3 00:06→00:09 playback then pause, and image Back returning to its original folder. Fresh video/audio XML exposes the viewer controls. The ADB helper now rejects Android idle-timeout responses rather than reusing old XML; hide the updating Media3 overlay before dumping. Full TalkBack remains unverified.

"White noise" returns the expected interval inside the AAC/MP3, while the old tone WAV ranks above them across the library. Sound relevance/calibration remains an explicit gate. [Full evidence, native logs, artifact distinction and reproduction](docs/AUDIO_CONTINUATION_SLICE.md). The phone was left in Browse; no private/user footage was read in this automated pass. Optimized preparation of these additions, multi-minute/process-death recovery, codecs/providers, real 16 KB runtime and production signing remain open.

## Daily-use file/gallery handover — 8 October 2026

Phone testing remained explicitly authorized. The user added two real videos to the chosen library; this pass creates/mutates only its own generated files. The old nine-file fixture-only helpers are not evidence for the expanded library and were not used to bypass their dataset guards.

**12 native tests passed, no skips, 4.976s:** Room/late-commit guards, v1/v4→v5 migrations preserving seeded originals/favorites/text, disposable SAF copy/rename/move/trash/restore/permanent deletion and interrupted-journal recovery, and prefix-restricted real MediaStore metadata/albums/duration/identity. Native tests clean their owned originals/media rows. A separate generated-input actual-pack resource test passed (**1 test, 26.635s**) and retained the selected model/index. [Final storage runner](docs/device/daily-use-storage-final-2026-10-08.log), [resource runner](docs/device/daily-use-resource-instrumentation-2026-10-08.log).

On the **optimized** app, actual UI created `KeepsBrowseTest/KeepsDailyUse`, copied the generated note, opened its text, renamed it `daily-note.txt`, trashed it and restored it. The 204-byte restored note is deliberately left for inspection. The folder/file survive process restart and in-place APK updates. Filename search finds it with AI unloaded. The existing prepared index returns the generated blue square first at 6–14 seconds, and a saved bookmark survives restart and plays through to the real 18-second end. No claim about relevance of the other two real videos is made from this single query.

Fresh Android App info verifies the inset four-tile icon without circular clipping. The user's accidental Android cache clear did not remove the private model or catalog. A separate ~367 MiB experimental inference-budget cache was removed by its verified private-app path; production cache, model, catalog and originals remain. The final installed application is the optimized debug-signed benchmark APK, with no app-data reset.

Current samples: optimized Browse **71 MiB PSS**, query screen **425 MiB**, initial unloaded Settings **77 MiB**. The same optimized process goes from loaded Settings **386 MiB** to **186 MiB** after automatic idle release. Debug production-image probe reaches **903 MiB**; the experimental smaller-budget four-frame peak sample is **877 MiB**, without soundtrack/representative quality validation. These are one-device samples, not guaranteed peaks. [Full report, screenshots, artifact hash, manual daily-use steps and limitations](docs/DAILY_USE_SLICE.md).

Reproduce only the new scoped tests on the debug app:

```powershell
adb -s SERIAL shell am instrument -w -r -e class dev.keeps.library.catalog.LibraryDatabaseTest,dev.keeps.library.catalog.MigrationTest,dev.keeps.library.catalog.DeviceFileOperationsTest,dev.keeps.library.catalog.DeviceMediaLibraryTest dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL shell am instrument -w -r -e class dev.keeps.library.inference.DeviceResourceTest dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

The file test requires the already-authorized generated SAF tree; the resource test requires the actual imported pack. Do not run fixture helpers against a personal library unless their explicit dataset guards accept it. The resource experiment creates `cache/budget-probe`; close its engine and remove only that app-owned test cache after collecting evidence. Full provider/grant consent, actual OS process death during mutation, full disk, large/long media, TalkBack, real 16 KB and signed release coverage remain unverified.

## Device-access follow-up — 8 October 2026

User-authorized metadata-only discovery used the phone's actual media/all-files grants and an isolated in-memory catalog. No file contents were read by the discovery test, no originals were mutated, no AI work was queued, and no app data was cleared. **12 focused native tests pass, zero skips, 15.531s**: database/batch guards, migrations, generated MediaStore discovery and real permitted-library cold/warm discovery. [Runner](docs/device/device-access-instrumentation-2026-10-08.log).

The new empty-catalog probe finds **5,079 unique items in 9.216s**, then rescans in **4.653s** while retaining identity/addition times. “Cold” does not mean flushed OS caches. [Counts/probe](docs/device/device-access-discovery-2026-10-08.json). The user granted all-files access through Android settings; the optimized app recognizes it after restart, browses the internal root and opens the earlier generated restored note without AI. Existing full grants were retained rather than cleared to stage a fresh-install test.

Five optimized Gallery swipes record 536 frames and 1 janky frame under Android's current metric (legacy: 60), p95 17ms. AI-unloaded PSS is approximately 183 MiB after Gallery scrolling and 142 MiB in restarted Browse after discovery/settings return. [Full report and memory records](docs/DEVICE_ACCESS_SLICE.md). These are bounded samples on one 4 KB-page phone; larger libraries, fresh denial/selected-access recovery and model inference/relevance are not verified by this pass.

Reproduce the new scoped native runner on the debug APK with explicitly authorized media access:

```powershell
adb -s SERIAL shell am instrument -w -r -e class dev.keeps.library.catalog.LibraryDatabaseTest,dev.keeps.library.catalog.MigrationTest,dev.keeps.library.catalog.DeviceMediaLibraryTest,dev.keeps.library.catalog.DeviceDiscoveryTest dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

`DeviceDiscoveryTest` inventories permitted metadata only; ensure the device library is stable during its two scans. It requires at least one media grant. The generated-media test restricts every test query/mutation to its UUID prefix. The optimized delivery artifact and the debug native-test artifact are separately recorded in the [artifact record](docs/checks/device-access-artifacts-2026-10-08.json).

## Paced preparation — 17 October 2026

37 JVM tests and 15 scoped Android checks pass; no skips, native time 116.968s. The selected runner covers database/migrations, simulated heat hysteresis and polling, persistent controls, bounded generated image batches, and actual-pack ordered frames/audio with two requested CPU threads per backend. Four of six generated images finish in 84.191s including pacing; two stay unqueued; repeat requests queue zero and keep completed vectors unchanged. No user originals were mutated. [Report and scope](docs/THERMAL_PREPARATION_SLICE.md), [runner](docs/device/thermal-instrumentation-2026-10-17.log).

The first native run held its batch in an overly conservative cooldown at this phone's normal light headroom (~0.84); device-reported light/moderate levels corrected it. The full selected suite passed afterward, without thermal overrides. The connected installation had no catalog/model before launch, so the earlier 145-item index was not available to verify. Metadata and the existing pack were restored through normal app flows; no app-data reset was performed. Optimized installation succeeded, but disconnection prevented final optimized UI/inference smoke. Full 3,800-photo sustained thermal comfort remains an acceptance task.

## Automatic overnight trial — 18 October 2026

21 focused database, migration, folder-rule, session-persistence and automatic-policy tests pass with no skips in 2.059s. [Runner](docs/device/overnight-instrumentation-2026-10-18.log). Controlled sensors cover charging, interactive use, low memory and cooling policy; these do not measure sustained physical heat.

`AutomaticBackgroundProbe` requires an installed model, authorized all-files access, no personal preparation pending and no existing automatic opt-in. Its `arm` phase generates one tiny image and one text file, records the existing prepared-index digest/preferences, and approves only those two IDs. After instrumentation exits, turn off the screen normally while charging; do not force JobScheduler execution. The `check` phase requires both AI_READY and a progress timestamp before the check started, verifies the prior index digest, exercises the Pause receiver, then removes only its owned files/rows and restores typed preferences. Both phases pass; arming to last progress is 102.244s including scheduling/pacing. [Result](docs/device/overnight-background-probe-2026-10-18.json).

```powershell
adb -s SERIAL shell am instrument -w -r -e keepsAutomaticProbe arm -e class dev.keeps.library.catalog.AutomaticBackgroundProbe dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL shell input keyevent KEYCODE_SLEEP
# Allow normal scheduling/completion, then always run check to clean up the probe.
adb -s SERIAL shell am instrument -w -r -e keepsAutomaticProbe check -e class dev.keeps.library.catalog.AutomaticBackgroundProbe dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

Optimized APK installed with `-r`; Search library shows the existing 25 ready files, automatic confirmation opens/cancels, and the switch remains off for the user's chosen scope tonight. Notification permission is granted. The actual-model background probe runs on debug, not the optimized APK. No personal preparation or original mutation is initiated by testing. [Report, trial steps and outstanding full-night/reboot/charger/quality gates](docs/OVERNIGHT_PREPARATION_SLICE.md).

## Preparation controls — 25 October 2026 (laptop only)

No ADB device is used. **43 core JVM tests and 23 Robolectric API35 tests pass**, with zero failures/errors/skips. The Android simulation covers Settings/repository/notification state parity, real receiver goAsync behavior, stale command actions and continuations, queue identity, power policy, legacy/interrupted recovery, PDF/text checkpoint retention, deduplication, fixed resource waits and failure isolation. A fake uninterruptible indexer step verifies that Pause drains the current step, retains its checkpoint and starts no next file; it does not run LiteRT or establish actual phone cancellation latency. [Audit](docs/PREPARATION_CONTROL_AUDIT.md), [suite record](docs/checks/preparation-audit-tests-2026-10-25.json).

Reproduce on the laptop:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Native instrumentation sources are updated/compiled but not executed in this pass. Paused queues now remain QUEUED, so a fixture probe's no-personal-pending-work guard intentionally refuses a paused personal run as well. Do not clear someone's waiting selection merely to run fixtures. The optimized update is built, not installed. Next phone checks are one real draining call, Settings/notification parity, same-scope/policy resume, charger/screen transitions, elapsed-time report and sustained comfort; full-night/reboot/OEM matrices remain open. Preserve app data when installing the update.
