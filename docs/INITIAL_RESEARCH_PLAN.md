# Android local library: product and engineering plan

Research date: **7 October 2026**

Model: **EmbeddingGemma 2**, following your clarification.

Status: research and proposed implementation plan; no Android prototype or device benchmark has been run for this report.

## 1. Recommendation

Build a **private, searchable files library with a gallery-quality interface**. Its central promise should be:

> Find something on your phone by describing what you remember—even when you do not remember its filename.

Photos, videos, recordings, screenshots, PDFs, and text belong in the same searchable library. Gallery and folder views are two ways to browse that library. Start with search, previews, and opening originals; add broader file management after retrieval works reliably.

The first compelling demonstration should be: type “someone blowing out birthday candles,” find a matching section of a local video, and tap to play it near that moment in airplane mode. A second demonstration should retrieve a receipt screenshot, a PDF, and a voice recording through the same search interface.

**Use EmbeddingGemma 2 locally for both indexing and queries.** No account, cloud inference, or paid embedding API is required for the proposed core product. Internet access is needed only for optional downloads and updates after installation; provide manual model import for offline setup.

This is technically plausible, with unusually relevant upstream examples. The remaining uncertainties are sustained performance on ordinary phones, retrieval accuracy on personal media, and the maturity of newly released Android integrations. These should be tested before investing in a full file-manager implementation.

## 2. What the research establishes

### The correct model

Google announced **EmbeddingGemma 2 on 6 October 2026**. It supports local multimodal search and is released under Apache 2.0. Google also announced “Instant Media Search” and “Video Moments Finder” experiences in AI Edge Gallery. This is directly relevant evidence that the proposed interaction is feasible, but does not establish performance across all Android phones. [Google announcement](https://blog.google/innovation-and-ai/technology/developers-tools/embeddinggemma-2/)

Do not confuse it with **Gemini Embedding 2**, a separately offered cloud API model. The application described here does not depend on that service. [Gemini model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-embedding-2)

The following are documented model properties, rather than our measurements:

| Property | EmbeddingGemma 2 |
|---|---|
| Modalities | Text, images, audio, video; combinations share one representation space |
| Parameters | 740M total: 270M text, 170M vision, 300M audio |
| Output | 768 dimensions; supported truncation to 512, 256, 128 |
| Context | 8,192 tokens shared across inputs |
| Default input budgets | About 29 images, 58 video frames, or 327 seconds of audio separately |
| Video sampling | Default 1 frame/second; configurable |
| Audio preparation | Mono, 16 kHz |
| Compression caveat | Re-normalize truncated vectors; 128 dimensions is primarily suitable for text |

These limits shrink when modalities are combined. They are input limits, not a recommendation to use one vector for an entire recording. Published model-card benchmarks use full-precision weights; quantized phone behavior needs separate evaluation. [EmbeddingGemma 2 model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2)

Google reports approximately **567 MB of active weight RAM** for a quantized full multimodal configuration on a Pixel 11 Pro. This is not total application memory: decoding, tensors, indexes, thumbnails, and the interface add more. It is also not a model download-size guarantee. [Launch measurements](https://blog.google/innovation-and-ai/technology/developers-tools/embeddinggemma-2/)

### Runtime readiness

LiteRT-LM's repository describes EmbeddingGemma 2 support in **v0.18.0**, including Kotlin and multimodal embeddings. That is the direct runtime candidate. [LiteRT-LM repository](https://github.com/google-ai-edge/LiteRT-LM)

MediaPipe's new **Universal Embedder** provides an Android integration through `com.google.mediapipe:tasks-retrieval`, with LiteRT-LM underneath. Start by attempting this higher-level route. Resolve and pin an actual tested dependency version; do not ship the documentation's `latest.release` placeholder. [Android integration guide](https://developers.google.com/edge/mediapipe/solutions/retrieval/universal_embedder/android)

The **Semantic Retriever** is another candidate, with a vector-store abstraction and local retrieval. Evaluate its persistence, filtering, deletion, and recovery behavior before selecting it as the production index. [Retriever guide](https://developers.google.com/edge/mediapipe/solutions/retrieval/semantic_retriever)

The launch article says the new ML Kit integration is coming **in the coming weeks**. Do not make an unreleased ML Kit embedding API a prerequisite. Existing ML Kit OCR is a separate, already documented feature. [Google's integration announcement](https://developers.googleblog.com/google-ai-edge-with-embeddinggemma-2/)

**Specific integration uncertainty:** the model card and LiteRT-LM guide currently show different text task-prefix conventions. Check the exact shipped model/runtime preprocessing, avoid double-prefixing, and compare both documented formats on a fixed retrieval fixture. Store the selected prompt-template version with the index. [Model-card instructions](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2), [LiteRT-LM instructions](https://developers.google.com/edge/litert-lm/embedding_models)

## 3. What the app should—and should not—claim

An embedding is a numerical representation used to find related content. It is not a transcript, caption, action detector, or database of verified facts. The application supplies the file access, segmentation, ranking, timestamps, and previews.

| User request | Proposed implementation | Limitation to communicate |
|---|---|---|
| “A red bicycle beside a lake” | Compare text with image embeddings | Small objects and relationships can be missed |
| “The moment a dog jumps into water” | Search short ordered video windows | A match is an approximate interval, not guaranteed action boundaries |
| “The recording with applause” | Compare text with audio-window embeddings | Similar sounds can be confused |
| “Where someone said the invoice number” | Optional transcription plus exact text search | Native audio embeddings do not produce exact quotations |
| “My electricity bill” | PDF page images, extracted text, and OCR | Encrypted or unreadable documents need explicit unsupported states |
| “More pictures like this one” | Image-to-image similarity | Similarity does not establish an exact duplicate |
| “Photos from last March” | Metadata filters | Do not infer dates from semantic resemblance |
| “Photos of Rahul” | User labels or a separate opt-in identity feature | The embedding model does not know the user's contacts |

“Finding movements” needs care. Sparse frame sampling may identify a jumping scene but miss a brief gesture or confuse picking something up with putting it down. Test ordered frame sequences against single-frame retrieval and reversed-action examples. Denser sampling can help coverage; it does not prove reliable motion understanding.

The initial product should say **“matching moments”**, show the surrounding clip, and let the user judge. Do not advertise forensic precision, exact movement tracking, or reliable counting.

## 4. Product scope and experience

### Why combine files and gallery?

A gallery alone leaves out PDFs, notes, and recordings. A complete file manager adds substantial work—copy/move reliability, storage providers, removable drives, conflicts, and destructive operations—without first proving the feature that makes this app valuable.

Use one library with four destinations:

1. **Search:** a prominent search box, optional query image, and type/date/folder filters.
2. **Gallery:** fast photo and video browsing, albums, favorites, and full-screen previews.
3. **Files:** authorized folders, documents, recordings, file details, open-with, and sharing.
4. **Collections:** manually saved groups and saved searches such as “receipts” or “whiteboards.”

Every result should show its actual source: image thumbnail, PDF page, audio segment, or video interval. A result reason can say “matching image,” “text found on page 3,” or “matching audio.” These are provenance labels; do not invent explanations from a similarity score.

### First-use flow

1. Explain that processing happens on the phone.
2. Let the user select a small collection or grant media-library access.
3. Check device capability and offer the required model pack, with verified download size.
4. Index a small batch first so the user can try search quickly.
5. Offer background indexing while charging, with pause/resume and storage controls.

The library must remain usable while indexing. Show “640 of 2,400 available items indexed,” not a misleading claim that all files have been searched. Failed, unsupported, excluded, and not-yet-indexed items are different states.

### Initial release versus later work

| First release | Later, after evidence of demand |
|---|---|
| Text search across all four modalities in selected sources | Full file-manager replacement |
| Gallery, file browsing, previews, open-with, sharing | Copy/move/rename workflows across providers |
| Playable video/audio intervals and PDF page navigation | Precise temporal localization with an extra model |
| OCR and filename search alongside semantic search | Optional speech transcription pack |
| Folder exclusions, indexing controls, local model management | Face grouping, explicitly opt-in |
| Favorites and saved searches | Local question answering with a separate generative model |
| Offline operation after setup | Encrypted device-to-device sync or desktop indexing |

Do not include automatic deletion, automatic file rearrangement, or cloud fallback in the first release.

## 5. Recommended architecture

Use **native Kotlin and Jetpack Compose** for Android. This is an engineering recommendation based on the amount of platform-specific media, storage, background-work, and inference integration involved. Flutter is possible, but adds bridges without helping the initial Android-only goal.

```text
MediaStore / chosen documents and folders / share-to-app
                         |
                 Source catalog (Room)
                         |
              Resumable indexing queue
                         |
           Decode / OCR / split into chunks
                         |
             Local EmbeddingGemma 2
                         |
        Vector index + text index + metadata
                         |
Query -> local embedding -> retrieval and filters
                         |
       Group results -> open original at page/time
```

Suggested modules:

| Module | Responsibility |
|---|---|
| `sources` | MediaStore, document providers, URI grants, change discovery |
| `catalog` | Room entities, metadata, exclusions, jobs, model/index versions |
| `preprocess` | Image orientation, bounded decoding, audio extraction, frame sampling, PDF rendering |
| `inference` | Embedding provider interface, model loading, device configuration |
| `search` | Vector retrieval, text retrieval, filters, result merging |
| `indexing` | Work scheduling, retries, checkpoints, power and thermal controls |
| `viewer` | Media3 playback, image previews, document navigation |
| `features` | Search, gallery, files, collections, setup, settings |
| `benchmark` | Reproducible relevance and device-performance measurements |

Keep inference and vector storage behind small interfaces. This allows a fallback from MediaPipe to direct LiteRT-LM without rewriting file discovery or the interface.

Start with Room for metadata and a Room-compatible full-text index for extracted text. Evaluate MediaPipe's local store first; keep an exact cosine-search implementation as a correctness baseline. Adopt an approximate index only when measured scale or latency requires it.

`sqlite-vec` is an alternative worth testing, not a guaranteed drop-in extension for Android's platform SQLite or Room. Verify native packaging, supported ABIs, database integration, crash recovery, and encryption compatibility. [sqlite-vec repository](https://github.com/asg017/sqlite-vec)

## 6. Indexing each content type

All chunk sizes below are **starting hypotheses to benchmark**, not model guarantees.

| Type | First indexing strategy | Extra channel |
|---|---|---|
| Photos | One oriented, bounded-resolution image embedding | OCR for screenshots, receipts, signs |
| Video | Ordered 8-second windows, 2-second overlap, initially 1 fps | Separate aligned audio windows; selected-frame OCR |
| Audio | 15–30-second windows with small overlap | Optional transcript chunks later |
| PDF | Render and embed each page independently | Extract selectable text; OCR scans where needed |
| TXT / Markdown | Roughly 300–600 tokenizer tokens per chunk, modest overlap | Exact text and filename search |
| Other files | Metadata-only listing | Add explicit parsers later |

EmbeddingGemma's input modalities do not make it a universal file parser. PDF rendering and text extraction are application responsibilities. DOCX, spreadsheets, archives, and uncommon codecs should remain metadata-only until tested adapters exist. Do not silently label them fully indexed.

Use Android decoding facilities and Media3 where suitable. Read through `ContentResolver`; do not require every content URI to have a normal filesystem path. Copy to a bounded temporary file only when a component requires seekable input, then clean it up.

Use existing ML Kit Text Recognition for the first OCR implementation, with Latin and Devanagari support as an initial proposal. Offer other supported scripts as needed. OCR models can be bundled or downloaded; complete that setup before promising offline text search. [ML Kit OCR Android guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)

Preserve OCR text and page coordinates where available. Text such as serial numbers, addresses, and amounts needs lexical matching even when semantic search is excellent.

### Video moments: the essential pipeline

1. Read duration, rotation, codec, and presentation timestamps.
2. Decode frames once through a bounded stream and form overlapping windows.
3. Encode ordered frames through a supported multimodal input path. Validate the actual Android binding; the high-level Android examples currently emphasize text, image, and audio.
4. Separately decode and embed the corresponding audio. Do not assume passing video automatically indexes its soundtrack.
5. Save the original URI and precise source interval with every vector.
6. Search windows, combine overlapping matches, and cap repeated results from the same long video.
7. Play from slightly before the match; offer other matching intervals within that file.

Compare frame-only, ordered-window, audio-only, and combined representations. Keep independently searchable visual and audio evidence even if a fused vector improves ranking. Fusion can obscure whether a result matched what was seen or heard.

If coarse search misses short actions, experiment with 2–4 fps for short windows or a user-selected “detailed video indexing” pass. Refining only the top results cannot recover a relevant clip that the coarse stage never retrieved, so measure candidate recall separately.

Do not use scene changes as the only boundary signal: a single continuous shot can contain many meaningful actions.

## 7. Retrieval design

For each query:

1. Apply explicit type, source, and date filters to the authorized corpus.
2. Generate the query embedding locally with the matching model configuration.
3. Retrieve semantic candidates alongside filename and extracted-text matches.
4. Combine ranked lists using an initial reciprocal-rank-fusion baseline; tune weights on held-out queries.
5. Group adjacent chunks and duplicate source hits into understandable results.
6. Recheck access before showing content or opening the original.

Use a short debounce and cancellation for typing. Do not run an expensive search for every keystroke while the user is still entering text.

Start with 768-dimensional vectors as the relevance baseline and test 512 and 256 for storage savings. Choose the smallest size meeting the quality target; do not assume every modality degrades equally.

Semantic scores are not probabilities. Avoid “97% accurate” badges. Calibrate weak-match thresholds with queries that have no correct answer, and show “No strong matches” where appropriate.

An image query can use the same retrieval path. Spoken queries are a later option: compare native audio-to-library retrieval against an offline transcript-to-text query. Microphone permission is only needed when the user chooses recording.

### Index integrity

Proposed persistent records:

```text
Asset: id, source, contentUri, mimeType, size, modification/generation,
       availability, grant state, metadata, fingerprint, indexStatus

Chunk: id, assetId, kind, startMs/endMs or pageNumber/textOffsets,
       preprocessingVersion, extractedTextReference

Embedding: chunkId, modelId, weightsDigest, tokenizer/promptVersion,
           runtimeConfig, dimensions, normalization, vectorReference

Job: assetId, contentVersion, stage, checkpoint, attempts, nextRetry,
     lastError, cancellationState
```

The configuration fields form an index namespace. Never compare vectors from different models merely because they have the same dimension. Re-embed in a new namespace when compatibility is uncertain, retain a working old index until migration completes, and atomically switch.

Use idempotent jobs and transactional metadata updates. If vectors live outside Room, use a durable outbox or rebuildable index with stable chunk IDs. Permission revocation, exclusion, or confirmed deletion must remove corresponding results and derived data. A temporarily unavailable SD card is different from a confirmed deletion.

## 8. Android access and background behavior

Use MediaStore for authorized shared photos, videos, and audio. Request only relevant access; handle Android's partial photo access as a normal mode. Preserve a picker-based path for users who do not grant full-library access. MediaStore generation/version information is useful for change detection; reconcile after a version reset. [MediaStore documentation](https://developer.android.com/training/data-storage/shared/media), [Partial photo access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access)

Use the Storage Access Framework for documents and chosen folder trees. Persist eligible grants and handle revocation. On Android 11+, users cannot grant the internal-storage root, the Download directory itself, or Android/data and Android/obb through the tree picker. Individual accessible downloads can be selected separately. Some providers expose cloud content: do not silently fetch it in a supposedly local workflow. [Document-access documentation](https://developer.android.com/training/data-storage/shared/documents-files)

Avoid `MANAGE_EXTERNAL_STORAGE` for the initial product. Calling an app a “file manager” does not automatically justify all-files access; Play restricts and reviews that permission. Revisit only if later core file-management functionality actually requires it. [Google Play all-files policy](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en-GB)

Use bounded, resumable WorkManager jobs for deferred indexing. Prioritize recent or explicitly selected files; default bulk video/audio work to charging, with thermal and battery checks. Give foreground user searches priority over background inference.

Android 16 can charge long-running foreground workers against job quotas. Do not design one worker that assumes it can process the whole library uninterrupted. Checkpoint frequently and use platform-compliant user-visible work when necessary. [WorkManager guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

If later adding rename, move, or trash actions, route them through supported provider/MediaStore operations and user confirmations. Finish the storage operation before updating the catalog. “Remove from this app's index” and “delete original” must be visibly separate actions.

## 9. Storage, battery, and device tiers

Vector payload is predictable:

`bytes = number_of_vectors × dimensions × bytes_per_component`

For **100,000 vectors**, float32 payload alone is:

| Dimensions | Decimal MB | MiB, approximately |
|---|---:|---:|
| 256 | 102.4 | 97.7 |
| 512 | 204.8 | 195.3 |
| 768 | 307.2 | 293.0 |

These are calculated estimates, excluding index structures, text, thumbnails, model files, and temporary media. Float16 storage halves payload but requires accuracy and runtime checks; output-vector compression and model-weight quantization are different optimizations.

A 10-minute video with 8-second windows and 6-second stride produces **about 100 visual vectors**. Add audio and it may roughly double; keep selected frame vectors only if they improve quality. At 10 hours of video, the same visual scheme produces about 6,000 windows. Do not retain decoded frames or transcoded copies of every video.

Initial device policy, to be revised from measurements:

| Device profile | Proposed experience |
|---|---|
| 4 GB RAM / older processor | Prove text+image operation first; smaller active batches; video on demand if feasible |
| 6–8 GB RAM | Target the full experience, with charging-based bulk indexing |
| 12 GB+ / flagship | Test faster indexing and optional detailed video processing |

RAM alone does not determine support. Decoder behavior, available memory, drivers, thermal limits, and model backend matter. Start the prototype on Android 12+ arm64 to align with the Gallery reference baseline; lower the minimum only after validating the selected runtime and hardware. [Gallery requirements](https://raw.githubusercontent.com/google-ai-edge/gallery/main/README.md)

Record actual peak application memory and sustained throughput. Do not translate a manufacturer's short inference measurement directly into an overnight indexing guarantee.

A local product has no per-search embedding API bill. Costs still include development, QA phones, model distribution bandwidth, support, and the user's battery/storage. If distributing a model pack of size `S` GB to `U` users, first-download traffic is roughly `S × U` GB before retries and updates. Measure `S` from the selected artifacts.

## 10. Privacy and reliability requirements

These are proposed product requirements:

- No user-media uploads or cloud fallback. Downloads use a separate, narrowly scoped code path with visible controls.
- No file content, queries, transcripts, or thumbnails in analytics/crash logs.
- Keep the catalog and derived data in app-private storage. Exclude them from automatic backup by default.
- Treat embeddings as sensitive derived data, not anonymous hashes. If providing encrypted storage, protect the full catalog, text index, vectors, and temporary files consistently.
- Offer folder exclusions, index deletion, model deletion, and an explanation that removing a model does not delete originals.
- Check model integrity before loading and retain a recoverable known-good version during updates.
- Bound file size, decoded resolution, PDF pages per job, and processing time; quarantine repeated failures without blocking the queue.
- An app lock does not encrypt the original photos elsewhere on the phone. A true vault is a separate future feature.
- Offline tests must cover fresh indexing and new queries after all required models have been installed, not just cached results.

## 11. Open-source projects worth using

These were reviewed at documentation/repository level. They have not been cloned, built, audited, or benchmarked for this report. License labels below are repository-level findings; verify the exact files, model artifacts, and notices at the selected revision before reuse.

| Project | How it helps | Proposed use and caveat |
|---|---|---|
| [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) | Android model management and on-device AI reference app; Google links it for new media-search demos | First reference to inspect. Apache-2.0. The fetched README still emphasizes broader generative demos; confirm the release/source containing the announced search features |
| [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) | Actual Kotlin-capable inference runtime with announced EmbeddingGemma 2 support | Primary low-level runtime candidate. Apache-2.0. Pin and smoke-test the exact release |
| [MediaPipe samples](https://github.com/google-ai-edge/mediapipe-samples) | Official examples linked by the Android Universal Embedder guide | Locate `examples/universal_embedder/android`; verify availability and its dependency/model versions. The sample link could not be retrieved in this research pass |
| [Ente](https://github.com/ente/ente) | Existing private photo product and on-device ML design | Study indexing UX and privacy architecture. AGPL-3.0; not a small Kotlin library to import |
| [Immich](https://github.com/immich-app/immich) | Semantic photo search, filtering, and library organization | Product/retrieval reference. Server-oriented architecture is not our phone-only foundation. AGPL-3.0 |
| [Fossify Gallery](https://github.com/FossifyOrg/Gallery) and [File Manager](https://github.com/FossifyOrg/File-Manager) | Real Android browsing and file-management behavior | Learn platform edge cases. GPL-3.0; a fork is an explicit product/licensing choice |
| [sqlite-vec](https://github.com/asg017/sqlite-vec) | Embedded vector-search alternative | Benchmark if the initial store is unsuitable. MIT/Apache-2.0 options; Android integration still needs a build spike |
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | Offline transcription with an Android example | Optional speech pack for exact words and transcript snippets. MIT code; additional model, memory, and battery budget |
| [MobileCLIP / MobileCLIP2](https://github.com/apple-aiml-research/ml-mobileclip) | Smaller image-text retrieval baseline | Compare on constrained phones. Not an all-modality replacement. MIT code; weights have separate Apple terms and data has separate restrictions |
| [Moment-DETR](https://github.com/jayleicn/moment_detr) | Temporal localization reference and evaluation code | Research reference, not a first-release Android dependency. MIT code; QVHighlights annotations are CC BY-NC-SA 4.0 |

Ente explicitly documents that its machine-learning search runs on the user's device, making it a particularly relevant architectural reference. Immich documents a CLIP-based smart-search pipeline. [Ente privacy documentation](https://github.com/ente/ente/blob/main/docs/docs/photos/faq/security-and-privacy.md), [Immich search explanation](https://github.com/immich-app/immich/blob/main/docs/docs/FAQ.mdx)

**Build-versus-fork decision:** build a small native app around the inference components, borrowing compatible sample code with attribution. Study the complete photo/file apps for behavior. Forking a large app is only attractive if its architecture and license match the intended product; otherwise it creates unrelated migration work.

## 12. Research papers and ideas to adopt

| Reading | Useful idea | Decision for this app |
|---|---|---|
| [Matryoshka Representation Learning, 2022](https://arxiv.org/abs/2205.13147) | A model can learn embeddings that remain useful at nested dimensionalities | Test 768/512/256 dimensions on the same corpus. Adopt if storage improves without unacceptable retrieval loss; no training needed |
| [QVHighlights / Moment-DETR, 2021](https://arxiv.org/abs/2107.09609) | Finding a video and finding a relevant time interval are distinct evaluation tasks | Label ground-truth intervals and evaluate temporal overlap. Consider a specialized reranker only if window retrieval is inadequate |
| [MobileCLIP, 2023 / CVPR 2024](https://arxiv.org/abs/2311.17049) | Efficient image-text models can be competitive under mobile constraints | Use as a performance/relevance baseline for the gallery portion; Android export and model terms need checking |
| [Gemini Embedding 2 technical report, 2026](https://arxiv.org/abs/2605.27295) | Shared multimodal representations support retrieval across media types | Background reading about unified retrieval. This is a different model; do not transfer its scores or API limits to EmbeddingGemma 2 |

Useful product experiments arising from these ideas:

- **Related across formats:** a trip photo leads to a matching itinerary or recording, evaluated as a suggestion rather than a claimed factual link.
- **Saved semantic collections:** store a query such as “handwritten notes,” then update results as authorized files are indexed.
- **Progressive video search:** deliver a usable first pass, with optional denser indexing for important videos.
- **Local relevance feedback:** “useful” and “not useful” choices can adjust ranking experiments. Keep feedback local and avoid immediate model fine-tuning.

Do not start by training a new model or building a knowledge graph. First determine whether indexing, OCR, segmentation, and ranking already meet the use cases.

## 13. Evaluation and acceptance criteria

Create a consented, representative local evaluation library: approximately 1,000 images, 30–50 videos, 30 audio recordings, and 50 documents. Write at least 150 queries across modalities, including English, Hindi, mixed-language queries, exact strings, fast actions, negation, and queries with no correct answer. Keep tuning and final evaluation queries separate.

Include confusing pairs: jumping versus standing, opening versus closing, applause versus rain, a screenshot mentioning a dog versus an actual dog photo. Test silent videos and videos whose soundtrack is unrelated to the image.

Compare:

1. Filenames, metadata, and OCR only.
2. Single-image or sampled-frame semantic retrieval.
3. Proposed multimodal windows with lexical fusion.
4. 768-, 512-, and 256-dimensional variants.
5. Quantized phone output against a full-precision reference on a small fixture.

Provisional release gates—**targets, not achieved results**:

| Area | Proposed gate |
|---|---|
| Image/document search | At least one relevant result in top 5 for 85% of answerable test queries |
| Audio/video search | At least one relevant segment in top 5 for 75% of answerable queries, reported separately per modality |
| Temporal quality | Report Recall@5 at temporal IoU ≥0.3 and ≥0.5, plus start-time error; simple interval overlap alone is insufficient |
| Added value | At least 10 percentage-point Hit@5 gain over lexical-only search on the semantic subset |
| Compression | No more than 3 percentage-point Hit@5 loss against the 768-dimensional baseline per modality |
| Approximate index, if used | At least 95% candidate Recall@50 compared with exact search |
| No-answer behavior | At most 10% false strong-match rate on held-out unanswerable queries |
| Interactive latency | Warm search p95 under 1 second on the chosen midrange phone at 50,000 vectors; report cold start separately |
| Reliability | Resume after process death/reboot; survive denied grants, corrupt files, low storage, and removable-volume loss |
| Privacy | No content-bearing network requests during indexing, searching, previewing, or playback |

Report battery use, peak memory, thermals, indexing time per 1,000 photos and per video hour, model download size, and index growth. Establish acceptable sustained limits from the first device runs; do not invent a universal images-per-second promise.

Use at least three physical device classes and more than one chipset/vendor. Emulator checks help functional development but do not validate thermals, battery, or accelerator support.

## 14. Implementation roadmap

Planning estimate for one experienced Android/ML engineer: **roughly 11–17 engineering weeks**, plus release contingency and access to test devices. This is a scope estimate, not a delivery commitment; new SDK issues may change it.

| Phase | Estimate | Concrete output and exit condition |
|---|---:|---|
| 0. Feasibility | 1–2 weeks | Pinned model/runtime; Android text/image/audio smoke tests; short-video retrieval; prefix parity; memory/thermal measurements on representative phones |
| 1. Searchable gallery | 2–3 weeks | Permissions, catalog, model download/import, photo indexing, OCR, search, thumbnails; works offline after setup |
| 2. Video moments | 2–3 weeks | Resumable frame/audio windows, interval ranking, playback navigation; passes temporal test set |
| 3. Files and audio | 2–3 weeks | Chosen folders/documents, PDFs/text, standalone audio, mixed result types; unsupported formats clearly identified |
| 4. Reliability and performance | 2–4 weeks | Incremental updates, deletion/revocation handling, device tiers, compression decision, migration, failure recovery |
| 5. Beta and release preparation | 2 weeks | Accessibility, large-library tests, privacy/network verification, license notices, store declarations and user feedback |

Phase 0 is a genuine decision gate. If the unified runtime cannot run acceptably on the target midrange device, reduce the supported device range or release photo/document search first with video on demand. A local specialized-model fallback is possible, but needs its own indexes and evaluation. Do not silently switch to cloud inference.

### First implementation backlog

1. Create a separate Android project and benchmark fixture set.
2. Resolve a working MediaPipe/LiteRT-LM version and exact model artifact; record hashes and licenses.
3. Load the smallest suitable text+vision configuration and then the full audio configuration.
4. Verify query/document preprocessing and multimodal input ordering against reference outputs.
5. Index 100 photos, 5 short videos, 5 recordings, and 5 PDFs.
6. Build one search screen with source-linked results and interval/page navigation.
7. Kill and restart during indexing; revoke access; change and delete indexed files.
8. Measure relevance, memory, battery, and sustained speed; publish a go/no-go benchmark report.

Only after that should the interface expand into the complete library experience.

## 15. Decisions to carry into development

- **Product:** a private files library with gallery-quality browsing and one multimodal search.
- **Model:** EmbeddingGemma 2, fully local; no mandatory cloud service.
- **Platform:** native Android, initially testing Android 12+ arm64.
- **Runtime:** evaluate MediaPipe Universal Embedder first, direct LiteRT-LM as the integration fallback.
- **Retrieval:** semantic vectors plus exact text and metadata, with traceable pages and intervals.
- **Scope:** prove images and video moments first; include audio/documents before the first full multimodal release; defer heavy file operations and generative chat.
- **Biggest unresolved issue:** sustained quality and performance on ordinary phones, especially short visual actions and long recordings.

The model is a strong starting point. The product's lasting value will come from finding the right item quickly, opening the right moment, respecting Android access boundaries, and keeping indexing unobtrusive.
