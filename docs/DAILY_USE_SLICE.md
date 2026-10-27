# Daily-use preview — 8 October 2026

Keeps now supports an ordinary file workflow alongside real Gallery and private content search. The optimized build is installed on the authorized CMF Phone 2 Pro. This is a usable preview for personal feedback; the broader launch gates in ROADMAP.md remain open.

## Implemented

- Browse real chosen folders, optional device-storage roots and permission-scoped Android media. Categories, pins, recently opened files, filename search, favorites and folder albums use actual catalog data. Connecting a source does not automatically prepare AI search.
- Create folders, rename, copy and move with live access/capability checks. Destination conflicts keep both names. Copies are streamed, counted, SHA-256 hashed and reopened for verification before a move removes its source. Persistent WorkManager journals expose progress, cancel, retry and review; recovery can adopt the verified destination while retaining any remaining source.
- Keeps trash for writable chosen trees/shared volumes, restore to the recorded parent, keep-both restore collisions and confirmed permanent deletion. Android media changes use Android's consent flows. Originals outside these explicit operations remain in place.
- Selection actions, sharing/Open with and real file details. Read-only sources expose the reason a mutation is unavailable.
- Room v5 migrations preserve earlier catalog identities/favorites/text. Pins, recent-open time, operation journals, trash origins and saved video moments persist. Late indexing commits reject changed URIs and trashed/excluded assets.
- Black/red/white light, dark and System themes, geometric category cards and restrained controls following the supplied direction. The supplied four-tile logo has a matching native vector and a 76% foreground inset for circular icons.
- Fullscreen image/text/PDF/audio/video viewers. Video uses Media3 playback, seek, ±10 seconds, actual nearest-frame candidate cards, real matched intervals, Return to match and saved moments. Ordinary viewing and filename search work without AI.
- Existing ordered-frame + available-soundtrack indexing, bounded continuation and single-video/library search are retained. Saved moments store source/time/label rather than duplicating or exporting the original video.
- Settings shows measured app RAM, Java/native usage, available phone RAM and model disk size. Explicit release and a 60-second idle timeout close the inference engine without deleting the model or index.

The [access contract](ACCESS_STRATEGY.md) describes provider-specific actions and fallback behavior. Excluding content from AI search retains its normal browsing and filename visibility. General hiding from Browse is a separate feature and is not implemented here.

## What was actually verified

| Check | Result and evidence |
|---|---|
| JVM core suite | **26 passed**, zero failures/skips; includes filename validation/keep-both behavior, ranking and audio/window regressions. [Machine-readable summary](checks/daily-use-artifacts-2026-10-08.json) |
| Debug, optimized and test builds; lint | Successful. **0 lint errors, 73 warnings**; no baseline/suppression added. [Build log](checks/daily-use-build-2026-10-08.log), [final test build](checks/daily-use-test-build-2026-10-08.log) |
| Room, migrations and new storage features | **12 native tests passed**, zero skips, 4.976s. Seven database checks, v1/v4→v5 migrations, two file-operation checks and one real MediaStore discovery check. [Final runner](device/daily-use-storage-final-2026-10-08.log) |
| Storage test scope | Disposable SAF files exercise >1 MiB streaming, Unicode/case collisions, create/pin retention, copy bytes, rename/move, favorite/bookmark identity, trash, restore collision and permanent deletion. A reopened journal models an interrupted source-deletion stage; changing the source retains both files and requires review. These tests do not simulate every OS/provider failure. |
| Media discovery | Three UUID-prefixed, generated MediaStore fixtures validate real sizes, modification-date fallback, duration, album grouping, repeat identity and favorites. Temporary shell permission identity and prefix-restricted queries avoid scanning personal media. Created rows are removed. This does not prove the complete user permission/provider matrix. |
| Actual model and resource probe | **1 native test passed** on the retained pack. Production text/image correctness and finite 768-dimensional candidate outputs tested. [Runner](device/daily-use-resource-instrumentation-2026-10-08.log), [measurements](device/daily-use-resource-probe-2026-10-08.json) |
| Optimized ordinary UI | Created `KeepsDailyUse`, copied generated `fixture-note.txt`, previewed it, renamed it `daily-note.txt`, trashed and restored it. Restored file and folder survived app restart/in-place APK updates. Filename search found the restored file with AI unloaded. [Restored file](device/daily-use-restored-final.png), [filename search](device/daily-use-filename-final.png), [trash screen](device/daily-use-trash.png), [text viewer](device/daily-use-text-preview.png) |
| Optimized video search/bookmark | `blue square` ranks the generated video first at **0:06–0:14**. Saved moment survived restart; reopening seeks with lead-in and playback reaches **0:18/0:18**, the actual video end. [Saved state](device/daily-use-saved-after-restart.png), [candidate viewer](device/daily-use-moment-player.png), [playback at end](device/daily-use-playback-end.png) |
| Circular logo | Fresh Android App info shows all four tiles with clear white padding, without clipping. [Phone screenshot](device/daily-use-logo-final.png), [mask/small-size preview](brand/keeps-icon-preview.png) |
| Idle model release | Optimized Settings first reports AI loaded at 386 MiB, then unloaded at 186 MiB after leaving it idle, without pressing Release. Model remains ready and saved/indexed content remains available. [Idle screen](device/daily-use-idle-unloaded.png). This observes the timeout outcome, not a precision measurement of its 60-second boundary. |
| Optimized native packaging | Seven literal JNI DTO names retained, four ELF libraries statically compatible with 16 KB alignment, APK ZIP alignment passes and v2 signature verifies. [JNI](checks/daily-use-jni-mapping-2026-10-08.json), [ELF](checks/daily-use-native-alignment-2026-10-08.json), [signature](checks/daily-use-signature-2026-10-08.log), [manifest permissions](checks/daily-use-permissions-2026-10-08.txt). This phone uses **4 KB** pages; real 16 KB runtime remains unverified. |

Earlier new-feature test runs revealed harness issues: duplicate node/index assumptions, shell permission identity versus app-UID permission checks, and an invented capture-date expectation for a generated PNG without EXIF. The harness now checks actual provider metadata/date fallback; final tests pass. Earlier logs are preserved as [first attempt](device/daily-use-storage-first-attempt-2026-10-08.log) and [second attempt](device/daily-use-storage-instrumentation-2026-10-08.log).

The production optimized app was tested through its real Compose/WorkManager/native paths. Instrumentation uses the debug variant; its direct application references are not proof of an optimized runtime. Previous prototype/video/audio evidence is retained in its dated reports, rather than counted as new storage tests.

## Memory and disk report

PSS is Android's estimate of RAM attributable to the **whole process**, including its proportional shared pages. These are samples from one phone, not guaranteed maxima or lower-memory device results.

| Workload | Observed process PSS | Build / scope |
|---|---:|---|
| Browse without loaded AI | **71 MiB** | Current optimized app, 72,609 KiB; [raw memory snapshot](device/daily-use-optimized-browse-memory-2026-10-08.txt) |
| Video semantic query screen | **425 MiB** | Current optimized app, 434,848 KiB; existing prepared index, [raw snapshot](device/daily-use-optimized-query-memory-2026-10-08.txt) |
| Settings after restart, AI unloaded | **77 MiB** | Current optimized app, in-app measurement; [screen](device/daily-use-memory-final.png) |
| After query and automatic idle release | **186 MiB** | Current optimized app, down from 386 MiB in the preceding loaded Settings sample; [idle screen](device/daily-use-idle-unloaded.png) |
| Cold production text query | **464 MiB**, 2.888s | Debug native probe, 475,546 KiB; pack/load+query on generated input |
| Production image embedding | **903 MiB** | Debug native probe, 924,200 KiB after two generated images |
| After production engine close | **246 MiB** | Same debug probe, 252,396 KiB; allocator/runtime pages remain in the process |
| Gallery-sized candidate image budget | **767 MiB** after images; **877 MiB** sampled peak with four frames | Debug native probe; `maxInputLength=1024`, `visionTokensPerImage=70`. Total text/two-image probe took 6.331s. **Experimental, not adopted in production.** |
| Earlier 54-second video preparation | About **1.15 GiB** checkpoint PSS | **Historical earlier debug test**, not rerun here and not a continuous peak; [prior record](device/long-video-probe-2026-10-08.json). Preparation/test/query took 371.178s. |

The candidate budget accepts the actual pack and preserves the simple red/blue image ordering. Four ordered frames produce finite 768-dimensional output. Soundtrack/context boundaries and representative retrieval quality were **not** tested under that budget, so existing production preprocessing/vector namespaces remain unchanged. It is a concrete next optimization experiment, not a claimed quality or latency improvement.

On disk: model **484,622,336 bytes (~462 MiB)**; observed production inference cache **376,052 KiB (~367 MiB)**; app databases **692 KiB** at the measured test-library snapshot. The optimized APK is **54,423,001 bytes (~52 MiB)**. Installed code/native libraries and thumbnail/staging growth add to this. The disposable budget test created a second ~367 MiB cache; it was removed before handover, retaining the production cache, pack and catalog.

Clearing Android **cache** removes expendable thumbnails/native compilation data. The first later inference may recreate its cache. The model is under private `files/models`, and Room data is under `databases`; both survived the user's cache clear. Clearing Android **storage/app data** would remove them and should not be used for this test handover.

CPU image/video preparation is the main remaining resource hurdle. The current image sample is near the roadmap's steady RAM target, and long video is slow. Engine close reduces retained RAM but does not promise that every native allocator page immediately returns to Android. Large libraries, thermal behavior, 4 GB phones and sustained peaks remain unmeasured.

## Installed artifact and use

The phone is left on the optimized, locally debug-signed `benchmark` build, updated using `adb install -r`. It has application ID `dev.keeps.library` and version `0.1.0`. SHA-256:

```text
0225233659794f45b81ca213f4c40145cf7bed78f7b58f9ed8266cae301c6014
```

APK: `artifacts/Keeps-preview-2026-10-08.apk` (local delivery copy; APKs are git-ignored). [Artifact record](checks/daily-use-artifacts-2026-10-08.json) records exact hashes and test/lint totals. This is not a production-signed Play release/AAB.

For daily feedback:

1. Browse → choose a folder, or opt into Browse device storage. Gallery → Connect device gallery for Android-selected/full media access. File changes require the actual provider write grant. Existing test locations remain connected.
2. Try browsing, sorting, filename search, albums, favorite/share and viewers first. AI is optional for these tasks. Test file changes with a copy while the wider provider/failure matrix remains open.
3. Prepare selected content explicitly. For a familiar short video, use Search this video and try a few descriptions whose moments you know. Check the candidate thumbnails/times and playback, then save a useful moment. Record the expected time and what Keeps returned for misses.
4. Check Settings → Memory to see AI loaded/unloaded and RAM. Release AI memory keeps the model and index.
5. Report failed tasks, slow screens, permission confusion and a few search misses with expected timestamps. A manual longer-video quality check is useful once preparation time is acceptable; it is not a prerequisite for ordinary file/gallery feedback.

## Remaining limits and next tasks

- **M0-05 / M3-05 / M4-03:** CPU budget and representative search evaluation. Similarity is uncalibrated; no-match, speech/action understanding and superiority over Gallery are unproved. Motion/content misses may require better sampling/reranking; some model limits may remain despite tuning.
- **M1-02/03:** paging/large-library tests, overlapping source routes, denied/selected/full grants and provider/device coverage. SAF discovery remains bounded (2,000 entries/200 directories/depth 8); device-root/media listing is not yet proven scalable.
- **M2-01/02/03/05:** full-disk, removal/revocation, large-transfer foreground execution, cancellation/process death at every stage, cross-provider moves and retention policy. Current moves stream a verified copy then remove the source; native atomic moves are pending. Keeps trash is retained until explicit removal; Android manages MediaStore trash expiry. Other-provider recovery remains a release gate.
- **M3-02/04 / M4-05:** multi-minute recording lifecycle, supported codec failures and full accessibility. Saved bookmarks show the video's general thumbnail and do not highlight the matching candidate card when reopened; timestamps/playback are correct. Clip export is not offered. Viewer recovery and TalkBack are incomplete.
- **M4-01/04:** OCR/selectable PDF text, full supported PDF coverage (currently 24 visual pages), consumer model download/update/space controls. Import remains manual; no Internet permission is present.
- **M0-03/04 / M6:** MediaPipe parity, real 16 KB devices, upstream R8 Kotlin metadata warnings, production signing/AAB and Play permission/release review.

No complete launch milestone is marked DONE based on this narrow daily-use slice. It provides a concrete app to use while these gates are completed.
