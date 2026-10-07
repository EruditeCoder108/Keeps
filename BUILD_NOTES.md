# Build evidence

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
