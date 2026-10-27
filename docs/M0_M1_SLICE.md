# Chosen-folder interface and early technical checks

Implementation: 7–8 October 2026. Task status is authoritative in [ROADMAP](../ROADMAP.md#11-milestones-and-task-ledger). This delivers a usable slice; it does not satisfy the complete M0/M1 exit gates or the video moment launch contract.

## Implemented behavior

- Browse opens immediately without a model: real file categories/counts, chosen folders, subfolders, favorites, recently added files, filename filtering, and sorting. Downloads offers Android's folder picker and explains that a folder must be chosen.
- Gallery uses real catalog images/videos, date groups, folder albums, filters, three/four-column density, favorites, selection and sharing. Dates use provider modification time with addition-time fallback, labeled **File date**.
- Search publishes filename results first, then supported prepared-content results. Query cancellation prevents stale rankings; a completed new ranking returns to its first result. Excluded files remain visible and filename-searchable.
- Settings provides light/dark/system appearance, sources, explicit preparation scope review, per-file content exclusion, pause/resume, and optional model setup. Browsing access alone never starts AI preparation.
- Existing image, text, PDF, audio and video viewers remain available. Images have bounded pinch/pan plus accessible zoom/reset controls. Favorite, share and Open with remain available; viewers return to the previous tab/folder.
- The design uses warm neutral surfaces, a restrained red accent, regular readable filenames, solid controls and real thumbnails. Activity and viewer system icons follow the selected app appearance.
- Room v1→v2 preserves assets, chunks, favorites and model namespaces; it adds provider identity/version, folder/location metadata and explicit content-search exclusion. A transaction rejects index commits after exclusion or observed source-version changes.
- Read-only SAF tree discovery deduplicates by provider document identity, retains ordinary visibility after exclusion, reports bounded/failed discovery and invalidates known missing direct files. No original write/delete operation is exposed. See [access contract](ACCESS_STRATEGY.md).

Main implementation: [UI](../app/src/main/java/dev/keeps/library/ui/KeepsApp.kt), [state](../app/src/main/java/dev/keeps/library/LibraryViewModel.kt), [source discovery](../app/src/main/java/dev/keeps/library/sources/DocumentImporter.kt), [catalog/migration](../app/src/main/java/dev/keeps/library/catalog/LibraryDatabase.kt), [viewers](../app/src/main/java/dev/keeps/library/ui/FilePreview.kt).

## M0 runtime and reuse evidence

The pinned Gallery commit is `3210720d3f808f51bae52798435d0c590ddc05bf`. Its audio/frame interleaving helper was adapted into pure Kotlin; original copyright, Apache header and modification notice remain. The full license ships in app assets and can be read in Settings. [File inventory and decisions](THIRD_PARTY.md), [exact source hashes](upstream-gallery-inventory.json).

Keeps retains LiteRT-LM 0.18.0. A serialized sequence input now sends ordered image/text/audio parts in one embedding call. Production indexing still uses the existing single-frame video recipe. Decoder, resampler, persistent windows, multiple video moments, within-video search and timeline UI remain M3 work.

The 7 October opt-in native probe used the **actual imported pack**, not a substitute:

| Property | Observed value |
|---|---|
| Pack | `embeddinggemma-2-740m.litertlm`, 484,622,336 bytes |
| SHA-256 | `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c` |
| Upstream model revision | `24d962e906c7d332c6428e71c9676855024569e2` |
| ModelInfo | Context 8,192; vision budget 140; text/vision/audio true; native video flag false |
| Inputs | Two generated ball frames in forward/reverse order; aligned generated 440 Hz PCM tone |
| Output | All three calls returned finite 768-dimensional vectors |
| Forward/reverse cosine | 0.9986909 |
| Forward/audio cosine | 0.9469436 |
| Sequence batch elapsed | 28,964 ms, including first engine load |

This proves **input acceptance** through this runtime/pack combination. The near-identical forward/reverse vectors do not establish action discrimination. MediaPipe `tasks-retrieval` resolution/parity, context boundaries, quality comparisons, compressed soundtrack decoding, resource ceilings and launch moment retrieval remain unverified. The upstream token estimate is not used as a validated production limit.

## Checks actually run

### 7 October phone session

Device: CMF Phone 2 Pro / Nothing A001, Android 16/API 36, arm64, kernel pages **4,096 bytes**. Keeps was absent at the first package check in this session, so this is not evidence of an on-phone v1 upgrade. The separate migration test creates a v1 database from the exported schema and opens it through the production migration.

Only six generated fixture originals and a chosen `Download/KeepsBrowseTest` folder containing copies were used. The user granted this folder in Android's picker. Discovery found exactly six files without starting indexing. Explicit scope review then produced the expected seven vectors. Instrumentation guards refuse mixed personal libraries and restore fixture exclusion/chunk state.

**11 instrumentation tests passed, zero skips, 35.018 seconds**: Room integrity/favorites/duplicates (3), exclusion and late-commit/version safety (2), real v1 migration (1), chosen-folder/exclusion/name behavior (1), actual audio/video playback (1), native retrieval (1), native sequence compatibility (1), and Android lexical matching (1). [Retained runner output](device/m1-instrumentation-2026-10-07.log).

The eight-query synthetic retrieval batch took 3,932 ms. These fixtures establish integration and provenance; they do not measure representative relevance, speech recognition or motion understanding.

Observed UI: first-use Browse without a model, real Pictures folder rows, Gallery and two real albums, light/dark Settings/Gallery, and Gallery at 200% font scale. The system font scale was restored to its prior 1.0 value. The dark status-bar contrast and Gallery process-recreation scope issues found in these captures were corrected afterwards; final correction verification is recorded separately below.

### 8 October final source

The final source built successfully in **4m 3s**, including debug, test and optimized/resource-shrunk benchmark APKs and lint. The retained unchanged core results contain **15 passing JVM tests**. Lint reports **0 errors/34 warnings**; upstream R8 Kotlin metadata parsing warnings remain unresolved. [Build notes and artifact hashes](../BUILD_NOTES.md#m0m1-slice--8-october-2026).

The debug APK first passed 11 instrumentation tests in 34.836 seconds. Expanding the source test to refresh the actual granted tree and direct Pictures folder produced **11 passing tests, zero skips, in 37.389 seconds**. The catalog remained unchanged after real enumeration. [Final runner output](device/slice-source-instrumentation-2026-10-08.log). The six generated originals and all six chosen-folder copies still match host SHA-256 values: [preservation report](device/fixture-preservation-2026-10-08.json).

Both APKs passed ELF LOAD and ZIP 16 KB alignment checks; the rebuilt optimized APK's v2 debug signature verifies and its manifest contains no Internet or broad-storage permission. A real 16 KB runtime and production-signed AAB are not verified.

The initial optimized query exposed a native abort: R8 had renamed `InputData$Text`, which JNI loads by literal name. [Failure evidence](device/optimized-jni-failure-2026-10-08.log). The inspected 0.18.0 native library also references Image/Audio and response/exception DTOs. [Targeted keep rules](../app/proguard-rules.pro) repair that native data boundary. [Final mapping check](checks/jni-mapping-2026-10-08.json) passes; actual fixed optimized inference ranks the generated note first for “green helmet” and finds **PDF page 2** for “blue square.” The PDF viewer opens that page.

APK replacements, the failed query and subsequent real folder refresh preserve all six asset IDs/metadata/favorites/exclusions, eight chunks (seven vectors plus lexical text), folders and location exactly: [catalog comparison](device/catalog-preservation-2026-10-08.json). Light and dark system bars are readable; [dark capture](device/slice-dark-system-bars.png). Gallery cold starts with all three media items rather than retaining a Browse-folder scope. Image zoom/reset and viewer close return to Gallery.

## Reproduce

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleBenchmark :app:lintDebug --console=plain --max-workers=2
python tools/check_native_alignment.py app/build/outputs/apk/benchmark/app-benchmark.apk --output .work/benchmark-native-alignment-final.json
python tools/check_native_jni_mapping.py app/build/outputs/mapping/benchmark/mapping.txt --output .work/jni-mapping-final.json
```

The `benchmark` variant is optimized/resource-shrunk but signed with the local **debug key**, using the same package. It is an installable packaging probe, not a production-signed release. Run Android instrumentation on the debug variant; its direct application references are not a minified-app test.

With an explicitly authorized device containing only the six chosen-folder fixtures, an imported verified pack, and an idle prepared index:

```powershell
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r -e keepsDeviceFixtureTests true -e keepsCatalogTests true -e keepsSequenceTests true dev.keeps.library.test/androidx.test.runner.AndroidJUnitRunner
python tools/check_device_catalog.py --serial SERIAL --label slice-check
```

Do not clear app data to test upgrades. A benchmark installation must retain the same assets, favorites, model and persisted grants. `zipalign -c -P 16`, `apksigner verify` and manifest inspection complement ELF checks; a real 16 KB device/emulator is still required.

## Remaining acceptance work

- M0-01/M1-02: MediaStore/shared-storage policy and discovery, removable/read-only/alternative providers, stronger content versions, overlapping source routes, missing descendants and proactive reconciliation.
- M0-02/03: complete transitive notices, clean MediaPipe artifact resolution and actual-pack parity, boundary/resource measurements and representative multimodal quality. Working LiteRT integration remains intact.
- M0-04: R8 emits Kotlin metadata parsing warnings from upstream artifacts; these are recorded without suppressing them. Production signing/AAB/splits and real 16 KB runtime checks remain open.
- M1: pinned locations, paging/large libraries, captured-date metadata, complete selection workflows, process-state restoration and accessibility/RTL/landscape/recovery matrix. Present sorting/recent semantics are truthful but limited.
- M2: original file operations, durable journals, verified conflict/recovery/trash behavior. This slice preserves originals and offers catalog removal with truthful rediscovery wording.
- M3 remains required for launch. The subsequent [ordered-video slice](VIDEO_MOMENTS_SLICE.md) implements persistent windows, available soundtrack, multiple candidates, scoped/library search and timeline controls. Its focused evidence is separate from this earlier M1 probe; representative quality, long-work/codec coverage and complete viewer gates remain open.
