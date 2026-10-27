# Build evidence

## 18 October 2026 — consumer interface slice

Settings/Search library, bulk individual exclusion, Geist/original dot headings, gallery controls and dock revision are implemented. Final core tests + debug/test APK + debug lint + benchmark APK build passes in 5m56s. Lint: 0 errors / 83 warnings, including three new URI KTX suggestions; no suppression. Existing R8 metadata warnings remain. Seven JNI names and four static ELF 16 KB alignment checks pass. Native 14-test database/migration/policy suite passes. [Current report and artifact evidence](docs/CONSUMER_UI_SLICE.md).


Date: 7 October 2026.

## Verified

The final command completed successfully:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

- **11 JVM tests passed**, with zero failures: 6 search/vector/chunking tests, 3 WAV tests, and 2 bounded-stream tests.
- The debug application APK was built successfully at `app/build/outputs/apk/debug/app-debug.apk`.
- **6 instrumentation tests passed on the connected phone**: three Room database tests, an Android lexical behavior test, an opt-in native model/retrieval test, and an opt-in audio/video decoder/playback test. The final runner reported `OK (6 tests)` in 7.842 seconds with no skipped tests. Separate ADB UI assertions verified the first visible search result after query/filter changes.
- Android lint completed with **0 errors and 21 warnings**. Remaining warnings concern newer dependency/Gradle versions (13), targeting API 36 rather than the newest API (1), and optional Kotlin extension style (7). No lint baseline or error suppression was used.
- `apksigner verify --verbose` verified the APK's v2 debug signature.
- `aapt2 dump permissions` confirmed that the actual APK has **no Internet or broad storage permission**. WorkManager contributes wake-lock, network-state, boot, and foreground-service permissions; network-state access does not grant Internet access.
- Cloud backup and device-transfer exclusions are explicit in the Android 12+ extraction rules.

APK SHA-256:

```text
B87C2DE00F0541EB94F29606FCD11E124B2852D3BF642ED443614BD895E248BD
```

## Toolchain

JDK 21, Gradle 8.14.3, Android Gradle Plugin 8.13.2, Kotlin 2.3.20, KSP 2.3.12, SDK 36, and LiteRT-LM 0.18.0. The initial Kotlin 2.2.20 compiler could not read the inference artifact's Kotlin 2.4 metadata; upgrading to 2.3.20 resolved compilation without bypassing metadata checks.

The latest build log is in the ignored `.work/device/validation.log`; the initial scaffold log is `.work/validation-final.log`. Lint reports are generated under `app/build/reports/`. Model artifacts are not bundled. The debug APK contains both arm64 and x86_64 runtimes and is approximately 125 MB before importing a model.

## Device verification

The debug app was installed and tested over USB on a **CMF Phone 2 Pro (Nothing A001), Android 16 / API 36, arm64**. The official portable EmbeddingGemma 2 pack was downloaded, verified against the upstream SHA-256, imported through Android's document picker, and used for actual CPU inference. Six synthetic files produced seven 768-dimensional vectors, with PDF page and video/audio time provenance verified.

The UI smoke test covered import, image/text/PDF previews, PDF page navigation, gallery, favorites, indexing pause/resume, source disappearance/reselection, and removal without deleting the original. Temporary validation caches were fixed after observing duplicate caches: the inference cache fell from 734 MiB to 367 MiB. Search lists now scroll to the best result after new results publish; previously their retained position could hide it. Access-restoration and singular item messages were also corrected.

Full scope, model provenance, timings, and limitations are in [DEVICE_TESTING.md](DEVICE_TESTING.md). This verifies a working prototype on one phone. Representative retrieval accuracy, sustained battery/thermal behavior, additional storage providers/devices, compressed audio, and motion recognition remain unverified or unimplemented.

## M0/M1 slice — 8 October 2026

The historical result above is retained. The new chosen-folder slice was built with:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleBenchmark :app:lintDebug --console=plain --max-workers=2
```

**BUILD SUCCESSFUL in 4m 3s**, 133 tasks (28 executed/105 up-to-date). The unchanged core suite's retained results contain **15 passing JVM tests**, zero failures/skips, including four new interleaving tests run on 7 October. On 8 October the debug APK first passed 11 phone tests in 34.836 seconds; after expanding the source test to re-enumerate the actual granted tree/direct folder, **all 11 passed again with zero skips in 37.389 seconds**. Coverage includes migration, exclusion/late-commit/version safety, preserved folder refresh, retrieval, sequence inputs and playback. [Final runner output](docs/device/slice-source-instrumentation-2026-10-08.log).

The first optimized native query aborted because JNI could not find the renamed `InputData$Text`. Inspected native string names and the R8 mapping confirmed that nested Text/Image/Audio DTOs were obfuscated. [Failure evidence](docs/device/optimized-jni-failure-2026-10-08.log). Narrow [JNI data-boundary keep rules](app/proguard-rules.pro) preserve these types plus native response/exception DTOs, retaining optimization elsewhere. Rebuilding `:app:assembleBenchmark` succeeded in **3m 40s**, 55 tasks. The fixed optimized APK completed actual model-backed note/PDF queries and opened matching PDF page 2 on the phone.

Lint: **0 errors, 34 warnings**, without a baseline or warning suppression. R8 separately emits upstream Kotlin metadata parsing warnings. The optimized build completes, but these warnings remain an M0-04 compatibility risk to resolve before production packaging.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 125,120,790 | `A1267D18EF442CF0C46E148B67F28758E1DA398C75B0F42CCB5992CF425A4314` |
| `app/build/outputs/apk/benchmark/app-benchmark.apk` | 54,190,869 | `339A3CB79CB4D09E0BD8ED15DF07CBB70F028885901FB917C2C74716ABEC5D39` |

The benchmark variant enables R8/resource shrinking and uses the local **debug signing key**, with the same application ID. It is a local optimized probe, not a production-signed release/AAB. Actual optimized APK signature verification passed v2. Both APKs passed `zipalign -c -P 16 4`; all four embedded ELF libraries passed the 16 KB LOAD alignment check. This phone uses 4 KB pages, so **real 16 KB runtime behavior remains unverified**.

The actual optimized APK has no Internet or broad-storage permission. Retained outputs: [artifact hashes](docs/checks/artifacts-2026-10-08.json), [debug ELF](docs/checks/debug-native-2026-10-08.json), [optimized ELF](docs/checks/benchmark-native-2026-10-08.json), [ZIP alignment](docs/checks/zip-alignment-2026-10-08.json), [signature](docs/checks/benchmark-signature-2026-10-08.log), [permissions](docs/checks/benchmark-permissions-2026-10-08.txt), [JNI names](docs/checks/jni-mapping-2026-10-08.json). `tools/check_native_jni_mapping.py` checks the observed literal JNI class names before installation; it complements actual inference testing.

Final build log: ignored `.work/slice-delivery-final.log`. Implementation details, actual-pack sequence observations, phone flow results and remaining gates are in [slice evidence](docs/M0_M1_SLICE.md). Full M0/M1 status remains in [ROADMAP](ROADMAP.md).

## Ordered video and adaptive logo — 8 October 2026

The current artifacts supersede the M1 hashes above. `:core:test` passed **23 tests**. The debug/test/lint build succeeded in **34s**, with **0 lint errors, 37 warnings**. The final `:app:assembleDebugAndroidTest :app:assembleBenchmark` build succeeded in **3m 9s**. Existing upstream R8 Kotlin metadata warnings remain recorded; no blanket suppression was added.

The debug APK is 125,121,764 bytes, SHA-256 `31c6031d1126c30b63ce5235e14760c23730ee8f5c452a78cd5d1eee309858be`. The optimized debug-signed benchmark APK is 54,225,401 bytes, SHA-256 `2045b0aca3ae72497f3efa0607f17f61f6c6530862e7c0f3bf2415bc5ffe0a1a`. [Machine-readable artifact record](docs/checks/video-artifacts-2026-10-08.json).

Eight focused native/database tests passed in 81.516s and one production WorkManager/scoped-library retrieval/Media3 test passed in 5.341s using saved interval checkpoints. The optimized app was updated in place and performed the expected new-video native query on the phone. The original generated files, existing catalog data and old vectors were retained. [Detailed new-feature evidence and limitations](docs/VIDEO_MOMENTS_SLICE.md).

The final optimized artifact passes all seven observed JNI class-name checks, four native ELF static 16 KB checks, ZIP 16 KB alignment and v2 signature verification. The connected phone still uses 4 KB pages; real 16 KB runtime is unverified. New-window preparation was tested in debug; its optimized counterpart remains a separate gate. [Identity exports](docs/brand/README.md) include the original SVG, 512px PNG, adaptive foreground and themed monochrome resource. The generated video fixture is test-APK-only; neither it nor the model is bundled in the app APK.

## Compressed audio, continuation and replacement identity — 8 October 2026

The new debug artifact supersedes the debug hashes above. The same four-task debug/test/lint command completed in **41s**, with **24 JVM tests passed**, zero failures/skips, and **0 lint errors, 37 warnings**. The added regression checks bit-identical overlap after 44.1 kHz resampling. After the passing native run, a thumbnail-only correction to nearest-frame selection built/linted in **18s**; core/indexing code is unchanged. The final installed application is **125,258,736 bytes**, SHA-256 `58a9707ca572af30131190040dc65395a2eb679f4d0367ab895a5847637f7f4a`. [Artifact record](docs/checks/audio-artifacts-2026-10-08.json) distinguishes the instrumented and final UI APKs.

This build adds platform-decoded standalone audio windows/checkpoints/continuation, multiple audio candidates, actual video thumbnail cards, main-screen viewers and the stacked-horizon folder/image launcher mark. Room stays at v4; existing migrations and native DTO keep rules are retained. The first phone run passed all seven Room tests and migration, then exposed an exact-overlap resampling defect and an overly strict WorkManager history assertion. Both were corrected and the affected native tests rerun; [new-feature evidence](docs/AUDIO_CONTINUATION_SLICE.md) records the scope/results.

The corrected native runner passed **two tests, zero skips, 411.294s**. Fresh nine-window AAC and video preparation each used two successful production work records. Final phone UI checks verify correct thumbnails, 12-second video return, MP3 playback/seeks, fresh viewer trees and ordinary image Back; all installations used `-r` and retained grants/model/catalog. The optional user-assisted real-video test remains pending.

The benchmark artifact recorded in the preceding section was **not rebuilt for these additions**. Its optimized/JNI/static alignment results remain valid for that historical artifact; they do not establish optimized preparation or viewer behavior for this new debug build. Production signing/AAB, actual 16 KB runtime and the upstream R8 metadata warnings remain open gates.

## Daily-use preview — 8 October 2026

The current artifacts supersede the earlier hashes above. The debug/test/optimized/lint build succeeded in **9m 27s**, 131 tasks; the final test-only correction rebuilt successfully in **2m 58s**. **26 JVM tests passed**, zero failures/skips. Current lint reports **0 errors, 73 warnings** (mostly UseKtx/dependency notices, plus storage/API/resource/icon notices); no suppression or baseline was added. Upstream R8 Kotlin metadata warnings remain. [Production build log](docs/checks/daily-use-build-2026-10-08.log), [test build log](docs/checks/daily-use-test-build-2026-10-08.log).

Room is now **v5**, with real v1/v4→v5 migration tests. The daily-use storage/database runner passed **12 tests, no skips, 4.976s**; the actual-pack resource probe passed **one test, 26.635s**. The optimized APK completed the actual create/copy/rename/preview/trash/restore UI and saved-moment playback through to the end on the phone. [Implementation, boundaries and measured RAM](docs/DAILY_USE_SLICE.md).

The installed benchmark APK is **54,423,001 bytes**, SHA-256 `0225233659794f45b81ca213f4c40145cf7bed78f7b58f9ed8266cae301c6014`. It remains locally debug-signed and optimized, without production signing/AAB. [Artifact record](docs/checks/daily-use-artifacts-2026-10-08.json) includes the debug/test APKs and delivered copy. All seven native JNI names, four ELF libraries' static 16 KB alignment, ZIP 16 KB alignment and v2 signature verify for **this** artifact. [JNI record](docs/checks/daily-use-jni-mapping-2026-10-08.json), [ELF record](docs/checks/daily-use-native-alignment-2026-10-08.json), [signature](docs/checks/daily-use-signature-2026-10-08.log).

The APK now declares optional broad-media and all-files permissions for the implemented gallery/file workflows; it still has **no Internet permission**. [Actual manifest permissions](docs/checks/daily-use-permissions-2026-10-08.txt), [access strategy](docs/ACCESS_STRATEGY.md). The previous no-broad-storage artifact description is historical. Play restricted-permission review, actual 16 KB runtime and wider device/provider/codec tests remain open. All phone app updates used `install -r`; app data was not cleared.

## Device access and batched discovery — 8 October 2026

This follow-up supersedes the installed daily-use APK hash above. Debug/test/lint/core build succeeded in **4m39s**; optimized/test build succeeded in **10m52s**. **26 core tests pass**, lint has **0 errors/71 warnings**, and **12 focused Android database/migration/media/discovery tests pass with no skips in 15.531s**. Room stays v5. [Debug build](docs/checks/device-access-debug-build-2026-10-08.log), [optimized build](docs/checks/device-access-optimized-build-2026-10-08.log), [native runner](docs/device/device-access-instrumentation-2026-10-08.log).

The installed optimized non-debuggable benchmark APK is **54,455,769 bytes**, SHA-256 `6d539c90b9ad070e0c2c37b2311da1ebf70e6bbc4f1cf378a75e21d583e94035`; delivered as `artifacts/Keeps-preview-device-access-2026-10-08.apk`. [Artifact record](docs/checks/device-access-artifacts-2026-10-08.json). Seven literal JNI DTO names, four ELF libraries' static 16 KB alignment, ZIP alignment and v2 signature pass for this APK. [JNI](docs/checks/device-access-jni-mapping-2026-10-08.json), [native](docs/checks/device-access-native-2026-10-08.json), [signature](docs/checks/device-access-signature-2026-10-08.log).

New phone evidence covers 5,079 real permitted metadata items, storage access, generated-file preview and bounded Gallery scrolling/RAM; it does not rerun model inference. [Scope, numbers and limitations](docs/DEVICE_ACCESS_SLICE.md). All updates used `install -r`, with no data reset. Existing R8 metadata warnings, production signing/AAB, Play permission review and actual 16 KB runtime remain open.

## Paced preparation — 17 October 2026

The calibrated debug/test/lint build passes in 1m36s; optimized build passes in 3m1s. 37 JVM tests and 15 Android checks pass with zero skips (116.968s); lint has 0 errors/80 warnings. Room remains v5; original model/input/vector namespace is retained. The optimized APK is 54,521,305 bytes, SHA-256 `476f1e319e2adc80f78823ab10e6e55de6835aef98b6ad6017dccc136bc220be`, locally debug-signed. JNI names, static ELF/ZIP alignment and v2 signing pass. [Current report](docs/THERMAL_PREPARATION_SLICE.md), [artifact hashes](docs/checks/thermal-artifacts-2026-10-17.json). The phone disconnected after successful installation, leaving the final optimized UI/inference smoke pending. Continuous large-library thermal comfort is unverified.

## Overnight trial build — 18 October 2026

Room v6 adds persistent folder exclusions and separate automatic/manual queue lanes. Charging/screen-off automatic work, notification Pause, session counters and bounded PDF/text checkpoints are implemented. The final core/debug/test/lint/benchmark build succeeds in 6m6s: 37 core tests pass; lint reports 0 errors / 88 warnings. Existing R8 Kotlin metadata warnings remain. A fixture-only correction subsequently rebuilds Android tests in 8s without changing the main APK.

21 focused native tests pass with no skips in 2.059s, including real v1/v4/v5→v6 migrations. The two-stage actual-model probe prepares only two generated files in normal background execution with the screen off; completion precedes verification, prior compatible/lexical chunks are unchanged, and Pause persists. This runs on debug; optimized installation and automatic-confirmation UI are checked separately. [Full evidence and boundaries](docs/OVERNIGHT_PREPARATION_SLICE.md).

Installed optimized preview: `artifacts/Keeps-preview-overnight-2026-10-18.apk`, 54,708,895 bytes, SHA-256 `6bead18854b9c4601aa13ae327313324f5c685437646045cccbfd53fdbd98586`. Seven JNI names, four ELF libraries, ZIP alignment and v2 signature pass static checks; it remains locally debug-signed. [Artifact record](docs/checks/overnight-artifact-2026-10-18.json). Updates use `install -r`; the user's 25 ready files remain visible. Full-library automatic work is left off for their nightly scope approval. A sustained night and reboot/charger/OEM matrix remain open.

## Preparation control audit — 25 October 2026

Core/debug/unit/test-APK/lint/benchmark build succeeds in 5m49s. **66 laptop tests pass, no failures/errors/skips**: 43 core JVM and 23 Robolectric API35 tests. Lint has 0 errors / 85 warnings; existing R8 Kotlin metadata warnings remain. [Audit and verification record](docs/PREPARATION_CONTROL_AUDIT.md), [build log](docs/checks/preparation-audit-build-2026-10-25.log), [suite record](docs/checks/preparation-audit-tests-2026-10-25.json).

The locally debug-signed optimized APK is `artifacts/Keeps-preview-preparation-audit-2026-10-25.apk`, 54,741,663 bytes, SHA-256 `38934f33538c88f798447093d5fc7d9f8dc9d42bf4b899ad816cd9182c8c5a1d`. Its signing certificate matches the previous delivery; seven JNI names, four ELF libraries, ZIP alignment and v2 signing pass static checks. No Internet permission is packaged. [Artifact record](docs/checks/preparation-audit-artifact-2026-10-25.json). Room remains v6; model recipe and native integration are retained. No phone installation, actual-model inference or sustained thermal/performance result is claimed for this build.
