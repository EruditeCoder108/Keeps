# Ordered video moments — 8 October 2026

M3's first usable implementation keeps the existing LiteRT-LM/Room/WorkManager/Media3 stack. Preparation uses overlapping eight-second intervals with two-second overlap, four ordered frames and the available soundtrack. Decoded PCM is aligned by its presentation timestamps, downmixed and resampled to 16 kHz. Every interval stores the joint sequence vector and a separate middle-frame visual anchor under recipe `video-tav-anchor-v1`.

Each interval is committed with source-version/exclusion checks. Interrupted work retains prepared intervals; a retry skips compatible checkpoints. A worker prepares at most eight new intervals and appends continuation work when coverage remains. The old 48-frame cap is removed. Room v1→v2→v3→v4 migrations preserve existing catalog items, favorites, lexical text and legacy vectors. Existing videos acquire the new recipe when prepared again.

Search collects up to five distinct candidate intervals per video, supports the whole accessible video library and a single-video scope, and keeps filename matching available without the model. The viewer offers candidate interval buttons, the ordinary Media3 timeline, one second of lead-in and Return to match. Search this video is available from its menu and viewer. Candidate intervals are approximate retrieval windows, not exact event boundaries.

## Why there are two vectors

The first actual-pack test used only the joint sequence. For the generated blue-square chapter, it selected the 0–8-second red chapter rather than 6–14 seconds. [Failure output](device/video-sequence-only-failure-2026-10-08.log) is retained and the expected assertion was preserved. A separate visual anchor fixed this fixture by scoring each interval with the maximum of joint-sequence and visual cosine. This is a provisional recipe requiring representative evaluation, especially sound-only queries and motion. It is not a broad quality claim.

## Verified checks

- **23 JVM tests passed**, including sparse PCM gaps/preroll, exact overlap, resampling, interval deduplication, invalid scores, and planning beyond 48 intervals.
- **Eight focused phone tests passed, zero skips, 81.516 seconds**: Room version/exclusion safety, v1→v4 migration, native stereo-AAC alignment, interruption after one persisted interval, two bounded resumed batches, finite 768-dimensional sequence/visual vectors, and the expected 6-second blue-square match. [Runner output](device/video-anchor-instrumentation-2026-10-08.log), [actual cosine values](device/video-anchor-probe-2026-10-08.json).
- Exact tested pack: SHA-256 `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`; LiteRT-LM 0.18.0 CPU on the CMF Phone 2 Pro, Android 16/API 36.
- Generated fixture: 18-second 320×240 MP4, red/blue/green six-second chapters and 48 kHz stereo AAC tone. Created by `tools/create_video_moment_fixture.py`, packaged only in the test APK. No personal content used.
- **Production SAF/WorkManager/retrieval/Media3 test passed**, zero skips, 5.341 seconds on saved checkpoints. The preceding fresh-preparation run completed all three real worker intervals and correct library/scoped ranking before a faulty filename-negative assertion stopped the test: `fixture-note` shares the token `fixture` with every generated file. The assertion was corrected to the unique term `note`; the whole test then passed. [First attempt](device/video-library-first-attempt-2026-10-08.log), [final runner](device/video-library-instrumentation-2026-10-08.log), [production metrics](device/video-library-probe-2026-10-08.json), [seven-asset catalog](device/video-catalog-2026-10-08.json). It checks original SHA-256 values and retention of prior asset metadata/text/vectors, playback advancement and exact 5/6/12-second Media3 seeks.
- Actual UI: best video result shows 0:06–0:14, all three interval buttons appear, one-second lead-in opens at 0:05, and Return to match shows the blue square at 0:06. Search this video opens the fixture-specific scope with Whole library recovery. [Viewer](device/video-moments-viewer.png), [exact match](device/video-match-blue.png). ADB XML reported the underlying activity during this check. The later [continuation slice](AUDIO_CONTINUATION_SLICE.md) traced that stale tree to the helper accepting an Android idle-timeout response; it was not valid evidence of a dialog defect. TalkBack needs a separate acceptance check.
- Debug/test APK and lint build succeeded in **34 seconds**; lint has **0 errors, 37 warnings**. Optimized build succeeded in **3m 9s**, including resource shrinking. All seven observed JNI class names and four native ELF libraries pass the mapping/static 16 KB checks, plus APK ZIP 16 KB alignment. [Hashes](checks/video-artifacts-2026-10-08.json), [JNI mapping](checks/video-jni-mapping-2026-10-08.json), [ELF checks](checks/video-native-alignment-2026-10-08.json). R8's existing Kotlin metadata parsing warnings remain visible.
- The final optimized APK was installed with `-r`, retaining Gallery/model/grants and the prepared fixtures. A fresh actual native blue-square query returned the new video first with 0:06–0:14. [Optimized query capture](device/video-optimized-query.png), [v2 signature verification](checks/video-signature-2026-10-08.log). New window preparation was exercised in debug; optimized preparation of new windows has not been separately exercised in this slice.

## Reproduce the focused phone tests

Import the exact model pack first. The decoder/resume test uses its own generated fixture in private cache and an in-memory catalog:

```powershell
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r -e keepsVideoTests true -e class dev.keeps.library.catalog.LibraryDatabaseTest,dev.keeps.library.catalog.MigrationTest,dev.keeps.library.inference.DeviceVideoWindowTest dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

For the production-path test, explicitly choose the generated six-file KeepsBrowseTest folder through SAF. Add only the generated chapter video to its Media subfolder. The test accepts that dedicated six/seven-file catalog, discovers the added video, prepares it and retains it for manual use:

```powershell
adb -s SERIAL push app/src/androidTest/assets/video/fixture-moments.mp4 /sdcard/Download/KeepsBrowseTest/Media/fixture-moments.mp4
adb -s SERIAL shell am instrument -w -r -e keepsVideoLibraryTests true -e class dev.keeps.library.inference.DeviceVideoLibraryTest dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
```

The older opt-in six-fixture tests require their original six-file setup; they intentionally skip on this expanded seven-file catalog. Do not infer a pass from a skipped test. Debug instrumentation directly references application classes and is not an optimized-runtime test.

## Remaining gates

M0-03 and M3 remain open: MediaPipe parity, input/context boundaries, representative annotated videos and negative queries, motion/action discrimination, common codec/provider matrix, multi-minute/process-death/thermal measurements, and timeline refinement. Eight-second intervals are candidates; no confidence percentage or speech transcript is shown. CPU preparation is expensive and remains optional. The subsequent [continuation slice](AUDIO_CONTINUATION_SLICE.md) verifies nine fresh video windows across two production workers and adds timeline thumbnails. Optimized querying is verified for the historical artifact; optimized new-window preparation, real 16 KB runtime and production signing/AAB remain separate gates.
