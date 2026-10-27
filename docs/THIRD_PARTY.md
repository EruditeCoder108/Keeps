# Google AI Edge Gallery reuse inventory

## Additional interface asset: Geist (18 October 2026)

Geist variable TTF from `https://github.com/google/fonts/tree/main/ofl/geist` is bundled as `app/src/main/res/font/geist.ttf`, SHA-256 `73894e0448cae90a92b6c2f8732b7bb9acb7b94c418bff559dad4a18e1de9659`. Copyright 2024 The Geist Project Authors. SIL Open Font License 1.1 is retained in `app/src/main/assets/notices/geist-OFL.txt` and the in-app notices. This font is not a proprietary Nothing asset. Dot-matrix heading glyphs are original Keeps canvas code. Gallery attribution and pinned implementation provenance below remain intact.

Reviewed 8 October 2026. Pinned revision: `3210720d3f808f51bae52798435d0c590ddc05bf` of [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery/tree/3210720d3f808f51bae52798435d0c590ddc05bf). Exact source hashes are in [upstream-gallery-inventory.json](upstream-gallery-inventory.json). The ignored `.work/upstream-gallery` checkout is detached at that revision.

## Imported components

`Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/videomomentfinder/AudioInterleaving.kt` → `core/src/main/kotlin/dev/keeps/core/AudioInterleaving.kt`.

Copyright 2026 Google LLC. Apache-2.0. The original header is retained, modifications are marked, and the full upstream license is distributed in `app/src/main/assets/notices/google-ai-edge-gallery-LICENSE.txt`. The pinned Git tree contains no file named NOTICE. No Gallery brand assets, services, network code, or query logging are imported.

Changes: pure Kotlin `WindowPart` types replace Gallery's `EmbedPart`; logging is removed; timestamps must be ordered and nonnegative; frame count is bounded to eight; public functions allow use behind Keeps' inference boundary. Keeps retains upstream frame-only behavior (no time markers) and audio-interleaved ordering. Truncated PCM is sliced by actual offsets and missing tails remain absent. Token estimation is retained for study; its 6.25 tokens/s estimate is **not an independently verified pack limit** and is not used as production validation.

Verification: `AudioInterleavingTest` checks ordered silent frames, truncated soundtrack tails, continuous slice boundaries, and reversed-time rejection. `DeviceSequenceTest` submits ordered/reversed frames and aligned generated tone slices to the exact local pack through LiteRT-LM. Production preparation now uses four ordered frames plus available soundtrack and a separate visual anchor per interval; actual-pack results and remaining quality gates are in [VIDEO_MOMENTS_SLICE.md](VIDEO_MOMENTS_SLICE.md).

`Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/videomomentfinder/VideoAudioExtractor.kt` → `app/src/main/java/dev/keeps/library/indexing/VideoAudioExtractor.kt` and downmix/resampling helpers in `core/src/main/kotlin/dev/keeps/core/AudioPcm.kt`.

Copyright/Apache headers and modification notices are retained. Adaptations use SAF URIs, actual decoded PCM presentation timestamps, sparse gaps, bounded decode stalls, cancellation and resource cleanup. The new rolling buffer retains overlap without allocating across large timestamp gaps. Mono PCM is resampled to 16 kHz; unsupported audio decoding is recorded as partial preparation while visual search remains available. No Gallery content-bearing logs are copied.

Verification: `AudioPcmTest` covers stereo overflow, resampling duration/tone, timestamp gaps, preroll/overlap, long-window planning and exact 44.1 kHz overlap. Keeps uses integer interpolation phase to avoid the one-sample rounding difference found in the native MP3 overlap check. `DeviceVideoWindowTest` decodes generated 48 kHz stereo AAC, checks exact two-second overlap, interrupts after a persisted window and resumes on the actual model pack. The same adapted decoder now supports standalone recordings; `DeviceAudioLibraryTest` checks generated 44.1 kHz stereo AAC/MP3, production worker continuation and original/vector preservation. This is format/fixture evidence, not a universal codec guarantee.

## Studied components and dependency differences

| Pinned file | Observed boundary | Keeps decision |
|---|---|---|
| `VideoAudioExtractor.kt` | MediaExtractor/MediaCodec forward decode, rolling mono buffer, 16 kHz resampling, overlap retention, cancellation | Selectively adapted as recorded above; actual generated AAC alignment/resume checks pass |
| `VideoDecoderSupport.kt` | Decoder/container capability helpers | Study for truthful format matrix; not imported |
| `Mp4Normalizer.kt` | Temporary fragmented MP4 normalization | Keep behind a bounded temporary-copy policy; not imported |
| `OnDeviceEmbedder.kt` | MediaPipe UniversalEmbedder, ordered `embedContent`, typed AudioData, model limits/backend recovery | No runtime migration; keep working LiteRT path; compare on actual pack before adoption |
| `SemanticRetrievalService.kt` | Metadata-scoped vectors, persistent retrieval and failures | Do not duplicate Keeps catalog; scale/ANN behavior still requires measurement |

Pinned Gallery declares AGP 9.0.1, Kotlin 2.2.21, `com.google.mediapipe:tasks-retrieval:1.1.0`, LiteRT-LM 0.18.0, and Kotlin reflect 2.4.0. Keeps uses AGP 8.13.2/Kotlin 2.3.20 and the proven LiteRT-LM 0.18.0 API. Shared LiteRT version does not prove equivalence of MediaPipe preprocessing, sequence configuration, native binaries or model-pack behavior. MediaPipe is not added to the production dependency graph in this slice. M0-02/03 remain open for clean artifact resolution and parity.

The owner selected Apache 2.0 for Keeps on 27 October 2026; the root LICENSE and NOTICE retain the project and incorporated-source attribution. Model license distribution and the full resolved transitive notice audit remain publisher/release work. This inventory covers the components actually copied here; see [source-publication review](OPEN_SOURCE_REVIEW.md) for its scope.
