# Keeps — a private Android library

Native Android scaffold for the [initial local library research plan](docs/INITIAL_RESEARCH_PLAN.md). Built with Kotlin, Compose, Room, WorkManager, Media3, and LiteRT-LM **0.18.0**. The working name is Keeps.

## Product direction and release plan

The intended product is a polished file manager, gallery, and private content search app. The current build is a working prototype; the launch features are planned, not yet implemented.

- [Roadmap to Google Play](PLAY_STORE_ROADMAP.md): launch scope, engineering milestones, quality gates, permissions, testing, and store preparation.
- [UI and UX specification](DESIGN_SPEC.md): original Nothing-inspired visual direction, Browse/Gallery/Search flows, accessibility, and recovery states.
- [Generated four-screen concept](docs/design/keeps-concept-v1.png) and [exact generation prompt](docs/design/IMAGE_PROMPT.md): design exploration, not shipped screens.
- [Phone verification](DEVICE_TESTING.md) and [build evidence](BUILD_NOTES.md): what has actually been tested.

## Run it

Open this directory as a project in Android Studio. Use JDK 21, Android SDK 36, and an Android 12+ device. Gradle 8.14.3 is pinned in the included wrapper. `local.properties` is machine-specific and ignored; Android Studio can create it.

From this directory on Windows:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

On macOS/Linux, use `bash ./gradlew` with the same tasks. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

To install on a connected test phone:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The initial build packages arm64 and x86_64. Check the selected runtime's accelerator support on the actual device; a successful APK build does not verify inference hardware compatibility.

## First use

1. Add a few files from the system document picker. Keeps persists read access and creates a local catalog; originals remain in their existing locations.
2. Search filenames immediately. Select **Index library** to extract searchable text from plain-text files, even without an AI model.
3. Download an official **full multimodal EmbeddingGemma 2 `.litertlm` pack** separately, then import it in Settings. Use [Google's LiteRT model resources](https://developers.google.com/edge/litert-lm/embedding_models) to locate the current pack. A generative Gemma model is a different artifact.
4. Model import copies the pack into private storage, computes its SHA-256 digest, loads it, and checks a real 768-dimensional text embedding before selecting it. This is a compatibility check, not a publisher-signature verification. Import trusted model artifacts.
5. Index the library again. Try image descriptions, PDF concepts, short audio descriptions, and sampled video-frame queries. Open a result to navigate to its PDF page or media timestamp.
6. Bookmark useful files. Removing a file from Keeps deletes its derived index and releases its grant; it does not delete the original.

The APK deliberately has **no Internet permission**, account, remote API, automatic download, or synthetic embedding fallback. Inference runs locally. Files selected from a cloud document provider can still be fetched by that provider; choose local providers when testing fully offline behavior.

## What works in this scaffold

| Area | Implemented behavior |
|---|---|
| Interface | Search, Gallery, Files, Saved, Settings; light/dark theme; empty and indexing states |
| File access | Multiple selected documents with persistent URI grants; revoked grants lose their search data |
| Catalog | Room persistence; favorites; indexing statuses; source/chunk links; cascading deletion |
| Local model | Actual LiteRT-LM embedding API, CPU configuration, serialized native calls, validated import, revision namespace |
| Images | Bounded, oriented image decoding and real image embeddings |
| Plain text | UTF-8 text, lexical matching, overlapping chunks, optional semantic vectors |
| PDF | Visual page embeddings, up to 24 pages; built-in page viewer |
| Video | One representative frame per overlapping 8-second window, at most 48 frames; built-in timestamp playback |
| Audio | Mono 16 kHz, 16-bit PCM WAV up to 30 seconds; real audio embeddings; playback |
| Retrieval | Exact cosine baseline plus Unicode-aware lexical matching and reciprocal rank fusion; one best chunk per source |
| Index jobs | Persistent WorkManager jobs per asset, cancellation, battery/storage constraints, retry via the UI |
| Privacy | Private app storage, backup disabled, no content-bearing logging in application code |

Search results are similarity candidates. Scores are not displayed as probabilities. No threshold has yet been calibrated against a representative personal library.

## Current boundaries

- Video uses individual frame embeddings. It does **not yet** embed ordered sequences, recognize motion reliably, or index the soundtrack. All video indexing is marked partial.
- Compressed audio, resampling, long recording segmentation, and transcripts need a MediaCodec/ASR pipeline. Unsupported recordings remain searchable by filename.
- PDFs are indexed visually. Selectable text extraction, OCR, and exact number/string search inside images or PDFs are still pending.
- Files are imported individually. Folder trees, MediaStore discovery, gallery-wide permission handling, and live content-change detection are pending. If an imported original changes, manually re-index it.
- Work resumes at the asset level after process death; it does not yet checkpoint every video frame or PDF page. Bounded work limits protect the initial prototype from huge inputs.
- Source access is checked through grants during retrieval and again before preview. Missing files are purged from the searchable index when preview detects them; a complete background reconciliation pass remains pending.
- Exact search loads candidate vectors into memory. It is a correctness baseline for small test libraries, not a proven 100,000-vector mobile index.
- CPU is the initial backend. GPU/NPU configuration, thermal throttling, foreground priority, and automatic unload are future performance work.
- The prompt template follows the model card (`task: search result | query:` for queries, `title: ... | text:` for text documents). The runtime guide has a different convention. Device relevance/parity tests must settle that before a release.
- No destructive file operations, face recognition, vault, generative chat, or sync are implemented.

## Project map

```text
core/                  Platform-independent ranking, vectors, chunking, WAV validation
app/src/main/.../
  catalog/             Room catalog and search repository
  sources/             Document picker import
  inference/           Embedding interface and real LiteRT-LM adapter
  indexing/            Bounded preprocessing and WorkManager execution
  ui/                  Compose library and viewers
app/src/androidTest/   Database tests and opt-in real-device retrieval/playback tests
tools/                 Synthetic fixtures and ADB smoke-test helpers
```

Model digest, dimensions, prompt version, backend, and preprocessing version form the vector namespace. Model changes discard incompatible vectors and retain lexical text. Schemas are exported into `app/schemas`; add a real Room migration before changing the schema version.

## Verification

Pure JVM tests cover window boundaries, overlapping text, malformed/zero vectors, vector serialization, Unicode lexical ranking, fusion duplicate handling, bounded reads, and WAV validation.

Database instrumentation tests cover cascade removal, model namespace separation, lexical preservation across a model change, and source deduplication. Run them on a device/emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The official portable model has also been tested on a CMF Phone 2 Pro (Nothing A001), Android 16, using a synthetic six-file library. All six device tests passed, including model retrieval, Android lexical behavior, and real decoder/playback progress. ADB UI checks confirmed that a new query exposes the best result. See [DEVICE_TESTING.md](DEVICE_TESTING.md) for the exact scope and reproduction steps, and [BUILD_NOTES.md](BUILD_NOTES.md) for build evidence. This small smoke test does not establish accuracy on a personal library or sustained battery/thermal performance.

## Next implementation milestones

1. Expand the successful six-file phone test to representative personal libraries and more phones; benchmark prompt formats, latency, memory, and relevance.
2. Add MediaStore and chosen-folder sources, stable content versions, and background reconciliation.
3. Add OCR and selectable PDF text to the lexical channel.
4. Decode/resample compressed audio and video soundtracks into aligned local windows.
5. Compare ordered-window embeddings with the frame baseline, then add per-chunk checkpoints.
6. Add charging/thermal policy and a scalable vector store behind the retrieval interface.
7. Calibrate ranking and no-match thresholds on held-out queries before beta claims.

## Sources and notices

The application adapter is an original integration against these public APIs; it does not copy another photo/file app:

- [LiteRT-LM v0.18.0 embedding engine](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/EmbeddingEngine.kt)
- [Embedding configuration](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/EmbeddingConfig.kt)
- [Input types](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Session.kt)
- [EmbeddingGemma 2 model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2)

LiteRT-LM and EmbeddingGemma 2 are Apache-2.0. AndroidX libraries are generally Apache-2.0; Coil is Apache-2.0. Gradle carries its own included license files. Audit resolved transitive dependencies and distribute their required notices before publishing. An application license has not yet been chosen. Model packs are not bundled in this repository or APK.
