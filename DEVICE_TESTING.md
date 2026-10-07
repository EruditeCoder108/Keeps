# Keeps: real phone testing

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

Use the catalog helper only when indexing is idle. It snapshots the debug app's database/WAL into ignored local output and refuses to report non-fixture asset names. The UI helper reads the current accessibility tree before tapping a label; animations and player-control timeouts can require another inspection. A screenshot was checked when the player dialog's accessibility tree exposed the underlying activity instead of the dialog.

Logs, snapshots, and screenshots for this session are in ignored `.work/device/`; selected screenshots are in `docs/device/`. The final APK hash is recorded in [BUILD_NOTES.md](BUILD_NOTES.md).

Selected phone captures: [image search](docs/device/blue-square-search-final.png), [text search after the scroll fix](docs/device/text-search-fixed.png), [text preview](docs/device/text-preview.png), [matching PDF page](docs/device/pdf-page-two.png), and [gallery](docs/device/gallery.png).

The final current-app log capture contained no fatal exception, fatal signal, or ANR. The temporary instrumentation APK and `/data/local/tmp` capture files were removed after testing. Keeps remains installed and open; its model, index, and synthetic source files remain available.

## Remaining limits

This is one phone and six synthetic files. It does not establish representative search accuracy, robust no-match handling, multilingual performance, long-session stability, battery consumption, thermal throttling, or compatibility with every document provider. GPU/NPU backends were not tested. Native logs warned that an NPU accelerator was unavailable; the configured CPU path worked.

Video currently embeds individual sampled frames and is marked partially indexed. It does not recognize ordered movement or index soundtracks. Audio still requires mono 16 kHz PCM WAV up to 30 seconds. Compressed recordings, OCR/selectable PDF text, gallery-wide discovery, and folder watching remain future work as described in [README.md](README.md).
