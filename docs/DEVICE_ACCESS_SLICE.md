# Automatic device access and faster discovery

8 October 2026 · M0-01, M1-02/03/05, M5-01 · Kotlin/Compose/Room/WorkManager/Media3/LiteRT integration retained.

The user reported a laggy 2–3 minute gallery connection for approximately 3,000 items and a rejected internal-storage-root folder selection. This update gives device access a first-launch flow and removes per-item provider/permission work from the UI. The previous preview and its evidence remain historical in [DAILY_USE_SLICE.md](DAILY_USE_SLICE.md).

## What changed

- First launch offers **Open your device library**, native gallery permissions and Android's separate **All files access** setting. Existing grants are recognized. **Not now** remains usable; Settings → **Run access setup** retries. Returning from settings or resuming the app rechecks actual access.
- Permitted media and Android-cataloged shared documents/other files load automatically. Direct folders enumerate their children when opened. Discovery reports progress while browsing remains available; it never queues AI preparation.
- Each pass caches access/identity maps, avoids a primary-volume provider identity RPC per media item, and writes Room metadata in 128-item transactions. Permission filtering, live file-capability checks and gallery grouping run away from Compose's main thread. Search groups lexical chunks once per asset.
- Refresh preserves IDs, original addition times, favorites, exclusions, pins, opening history and compatible prepared content. Unchanged rows avoid redundant writes; stale observations cannot roll back a moved file. Room remains v5 with no model namespace change. Mutations still perform live capability checks.

The folder-picker error is an Android restriction: it cannot grant the internal-storage root. Use **Allow device files**, enable Android's all-files toggle and return to Keeps for shared-storage browsing. Apps' private storage remains protected. [Android SAF restrictions](https://developer.android.com/training/data-storage/shared/documents-files#access-restrictions), [all-files access](https://developer.android.com/training/data-storage/manage-all-files).

## Actual verification

Phone: CMF Phone 2 Pro / Nothing A001, Android 16/API 36, arm64, 4 KB pages. The user granted media and all-files access; no ADB permission bypass was used. Installs used `-r`; no app-data reset or original-file mutation occurred in this pass.

| Check | Result / evidence |
|---|---|
| Core / build / lint | 26 core tests pass; debug/test build 4m39s, optimized/test build 10m52s; lint 0 errors, 71 warnings. [Debug log](checks/device-access-debug-build-2026-10-08.log), [optimized log](checks/device-access-optimized-build-2026-10-08.log) |
| Focused native checks on debug | 12 tests, zero skips, 15.531s: database guards/batch preservation, migrations, generated MediaStore discovery and metadata-only real-library discovery. [Runner](device/device-access-instrumentation-2026-10-08.log) |
| Empty Keeps catalog | 5,079 unique items in 9.216s: 3,778 images, 239 videos, 10 audio, 1,052 other files. Same IDs/addition times after a 4.653s rescan; no original mutations or AI queued. [Probe](device/device-access-discovery-2026-10-08.json) |
| Optimized UI | Internal storage → Download → generated Keeps test folders, **Read & write**, prior restored 204-byte `daily-note.txt` opens its actual text without AI; grants recognized after process restart. [Generated viewer tree](device/device-access-note-2026-10-08.xml), [access-status tree](device/device-access-settings-2026-10-08.xml) |
| Optimized Gallery scrolling | Five down swipes: 536 frames; current jank metric 1/536 (0.19%), legacy metric 60/536 (11.19%); p50/p90/p95/p99 frame times 9/15/17/24ms. No slow-UI-thread frames recorded. [Sample](device/device-access-ui-probe-2026-10-08.json) |
| RAM with AI unloaded | Gallery after scrolling 187,279 KiB PSS (~183 MiB), RSS 262,556 KiB. Restarted Browse after discovery and settings return 145,528 KiB PSS (~142 MiB), RSS 251,564 KiB. [Gallery](device/device-access-gallery-memory-2026-10-08.txt), [Browse](device/device-access-browse-restarted-memory-2026-10-08.txt) |
| Optimized native/package checks | Seven JNI DTO names retained; four ELF libraries statically 16 KB compatible; ZIP alignment and v2 signing pass. [JNI](checks/device-access-jni-mapping-2026-10-08.json), [native](checks/device-access-native-2026-10-08.json), [ZIP](checks/device-access-zipalign-2026-10-08.log), [signature](checks/device-access-signature-2026-10-08.log) |

“Empty catalog” uses an isolated in-memory Room database, not flushed Android/OS caches. Timings and RAM are single-device samples, not peaks or repeated percentiles. The earlier user observation is not a controlled before/after benchmark. Android also reports a high-input-latency counter of 1,070 in this short sample; no interaction-latency pass is inferred from the frame/jank numbers. Personal gallery images and folder names are not published as screenshots.

## Delivered artifact and remaining work

Installed optimized, non-debuggable `benchmark` APK: **54,455,769 bytes**, SHA-256 **`6d539c90b9ad070e0c2c37b2311da1ebf70e6bbc4f1cf378a75e21d583e94035`**. Delivery: `artifacts/Keeps-preview-device-access-2026-10-08.apk`; [artifact record](checks/device-access-artifacts-2026-10-08.json). It is locally debug-signed, not a signed production release.

Automatic discovery follows Android's catalog; it is not a recursive inventory of every hidden/unindexed path. Other providers, overlapping routes, removal reconciliation and 100k-item paging remain M1-02/03/M5-01 work. Fresh-install denial/selected-access/revocation recovery needs the complete M1-05 matrix. Existing full grants, storage browsing and restart were checked without erasing the user's state.

Model inference/relevance was not rerun in this access-focused pass. The historical AI measurements remain separate. Continue M0-05 CPU/memory budgets and M3-05/M4-03 annotated video and negative-query evaluation. R8 metadata warnings, actual 16 KB execution, wider codecs/providers, accessibility, Play restricted-permission review and production signing remain release gates.
