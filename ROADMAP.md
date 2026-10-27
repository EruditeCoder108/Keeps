# Keeps — master product and delivery roadmap

**Version:** 1.0 planning baseline  
**Created:** 7 October 2026 · **Last updated:** 27 October 2026  
**Project:** `android-library/`  
**Current stage:** M1 real library, first M2 daily file workflow, M3 video moments and M4 compressed audio implemented as a usable preview; M0/production gates remain active
**Next milestone:** M0 — decisions and early technical proof

**Latest slice — 17 October:** M5-02 ACTIVE: Gentle/Balanced preparation, 25/100/all batches, device-aware cooling and two requested CPU threads per backend. 37 JVM tests and 15 phone checks pass; optimized preview installed. Full 3,800-photo thermal comfort and the final optimized Settings walkthrough remain unverified. [Evidence and limits](docs/THERMAL_PREPARATION_SLICE.md). [Earlier discovery](docs/DEVICE_ACCESS_SLICE.md) measures metadata, not full-library AI preparation.

**Consumer rewrite baseline — 17 October:** [Product direction and complete screen/control contract](docs/PRODUCT_DIRECTION.md): include approved content with easy exclusions; dedicated Search library; short Settings destinations; charging-first automatic preparation and truthful background status. Computer assistance is future work. This is a specification and source audit, not a shipped rewrite or overnight benchmark. M1-05 returns to ACTIVE for the new consumer flow.

**Consumer interface slice — 18 October:** M1-05/M5-05 ACTIVE: Settings destinations, Search library, transactional bulk exclusion/long-video review, Geist/dot headings and revised dock/segmented controls implemented. 37 JVM and 14 isolated phone checks pass. [Changes, evidence and pending acceptance](docs/CONSUMER_UI_SLICE.md). Background automation, measured overnight behavior and full visual/accessibility acceptance remain open.

**Overnight preparation slice — 18 October:** M1-05/M5-02 ACTIVE: charging/screen-off opt-in automation, persistent folder rules, notification Pause, session counters and text/PDF checkpoints are implemented. The build, 37 JVM tests, 21 focused native tests and a generated two-file screen-off background probe pass. Optimized preview installed; existing 25 ready files retained. [Trial instructions, evidence and remaining gates](docs/OVERNIGHT_PREPARATION_SLICE.md). The user's full night remains a new measurement, not a completed gate.

**Preparation control audit — 25 October:** M1-05/M5-02 ACTIVE; revised control flow VERIFY on phone. Reported 200 photos / 26 active minutes / 2–3 hours elapsed prompted unified status, retained-queue resume, revision guards, deduplicated scheduling, adaptive short rests, fixed resource rechecks and total-time accounting. **66 laptop tests pass** (43 core / 23 simulated Android); debug/test/optimized builds and lint pass (0 errors / 85 warnings). [Findings, artifact and evidence](docs/PREPARATION_CONTROL_AUDIT.md). No phone is used; the new APK is not installed and earlier background evidence does not prove these changes.

Build a dependable Android file manager and gallery with private content search. A person should be able to describe a photo, a document, a sound, or something that happens halfway through a video, find the useful result, and open its page or moment. Ordinary file browsing and organization work immediately, even without the AI model.

**Source publication and README — 27 October:** Owner-selected Apache 2.0 license and NOTICE added. README now includes the established logo, original artwork, an opt-in illustrative GIF, setup/build instructions, contribution guidance and explicit preview limits. A local candidate/history privacy-pattern scan passes; raw captures/logs and private artifacts remain ignored. Git author identity remains personal metadata. Initially prepared locally, the source and later repo card were pushed to main at the owner's explicit request; no visibility or history rewrite was performed. [Review and exact scan scope](docs/OPEN_SOURCE_REVIEW.md). M6-03/M6-04 remain incomplete: dependency notices, release/store assets and production gates are not satisfied by source licensing or documentation.

**GitHub APK preview — 27 October:** At the owner's request, optimized debug-signed `v0.1.0-preview.1` is downloadable as a [GitHub pre-release](https://github.com/EruditeCoder108/Keeps/releases/tag/v0.1.0-preview.1), with SHA256SUMS and build metadata. The APK targets source `4b14413`; its package/signature, static native/ZIP alignment, JNI names and GitHub-upload digest pass checks. No new phone, overnight or production-signing evidence is claimed. [Artifact scope](docs/checks/github-preview-apk-2026-10-27.json) and [public download verification](docs/checks/github-preview-release-2026-10-27.json). M6/M7 production gates remain unchanged.

**Video moment search is required for 1.0.** Search both inside one video and across the prepared videos in the accessible library. Multiple useful moments, real time intervals, soundtrack support where available, and reliable playback are part of that feature. Universal understanding of motion and exact speech transcription require separate evidence.

This is the single source of truth for scope, priorities, delivery order, open decisions, and completion. It supersedes the original [Play Store planning document](PLAY_STORE_ROADMAP.md). The [design specification](DESIGN_SPEC.md) remains the detailed UI reference; where scope differs, this roadmap takes precedence. [Device testing](DEVICE_TESTING.md) and [build notes](BUILD_NOTES.md) record actual evidence. The [concept board](docs/design/keeps-concept-v1.png) and [generation prompt](docs/design/IMAGE_PROMPT.md) establish visual direction, not shipped screens.

## Contents

1. [Product contract and finished experience](#1-product-contract-and-finished-experience)
2. [Verified starting point](#2-verified-starting-point)
3. [Launch scope and later versions](#3-launch-scope-and-later-versions)
4. [Interface and recovery contracts](#4-interface-and-recovery-contracts)
5. [Google AI Edge Gallery reference and reuse](#5-google-ai-edge-gallery-reference-and-reuse)
6. [Video moment search implementation](#6-video-moment-search-implementation)
7. [Other content search and retrieval](#7-other-content-search-and-retrieval)
8. [Sources, permissions, and safe file operations](#8-sources-permissions-and-safe-file-operations)
9. [Model setup, privacy, and resource policy](#9-model-setup-privacy-and-resource-policy)
10. [Engineering boundaries](#10-engineering-boundaries)
11. [Milestones and task ledger](#11-milestones-and-task-ledger)
12. [Quality gates and comparison protocol](#12-quality-gates-and-comparison-protocol)
13. [Google Play, beta, and launch](#13-google-play-beta-and-launch)
14. [Risks and open decisions](#14-risks-and-open-decisions)
15. [How to keep this roadmap current](#15-how-to-keep-this-roadmap-current)
16. [Decision and change log](#16-decision-and-change-log)
17. [Reference sources](#17-reference-sources)

## 1. Product contract and finished experience

### What a finished 1.0 feels like

- **Browse:** folders, categories, pinned locations, recent files, favorites, and supported trash. Readable filenames and predictable copy/move/rename/share actions.
- **Gallery:** a fast photos/videos timeline, real folder albums, filtering, selection, and fullscreen viewers that return to the previous position.
- **Search:** one field for names, exact supported text, and remembered content. Results open the useful page or time interval. Several moments from one recording remain accessible.
- **Settings:** short destinations for Search library, access, appearance, storage, privacy/help and Advanced. Search library owns setup, approved scope/exclusions, preparation and progress; diagnostics and file-operation recovery have separate homes. No search-history control until history exists.
- **Recovery:** denied access, disappeared storage, corrupt input, failed downloads, partial indexing, full disk, conflicts, and interrupted operations have usable next actions.

Example: search for “the dog jumping into the pool.” Keeps searches prepared video windows, shows candidate recordings and moments, and opens the chosen interval. Search inside a single long recording uses the same index. The UI shows when only part of a recording is prepared.

An embedding result is a retrieval candidate. Exact frame-level event boundaries, every subtle action, every codec, and every spoken word are not guaranteed by a shared model.

### Product decisions already established

| Decision | Working contract |
|---|---|
| Product | File manager + gallery + private multimodal search |
| Navigation | Browse, Gallery, Search; Settings in the top bar |
| Visual direction | User-directed high-contrast black/red/pure white, geometric surfaces, restrained translucency and natural media colors; supplied four-tile logo |
| AI | Optional local EmbeddingGemma 2; basic file tasks never depend on inference |
| Video | Required launch feature, including multiple moments and library-wide search |
| Reuse | Study and selectively adapt Google AI Edge Gallery code and proven Android libraries |
| Privacy | No account or advertising SDK in the initial plan; indexing consent separate from browsing access |
| Launch posture | Free, ad-free beta; production pricing remains a decision |
| Platform | Keep Android 12+ initially; CPU compatibility baseline; other accelerators need evidence |
| Languages | English UI initially; test Hindi and mixed-script search; Hindi UI is an early follow-up |

### What would make Keeps useful relative to Google

Google AI Edge Gallery is our implementation reference for on-device multimodal retrieval. Its inspected moment finder searches a selected video project. Keeps aims to integrate that capability into ordinary file organization, persistent library-wide search, and consistent viewers. Our improvement hypotheses are more complete video coverage, better short-event localization, diverse moments, resumable preparation, and a useful everyday workflow. They become claims only after measurement.

## 2. Verified starting point

The facts below come from the existing repository and the recorded 7 October phone session. No new build or phone test is implied by creating this roadmap.

| Area | Current implementation / evidence | Production gap |
|---|---|---|
| Stack | Kotlin, Compose, Room, WorkManager, Media3, Coil, LiteRT-LM 0.18.0 | Real product navigation, source abstractions, release configuration |
| Sources | Individually selected document URIs with persistent read grants | Folder trees, MediaStore discovery, content versions, reconciliation |
| Catalog | Persistent assets/chunks, favorites, namespaces, cascade removal | Cross-source identity, operation-aware updates, migrations for new state |
| Model | Real local CPU inference, validated manual import, serialized native calls | Consumer download, lifecycle budgets, multimodal sequence input, device tiers |
| Retrieval | Unicode lexical matching, exact cosine, rank fusion | Scalable candidate retrieval, no-match calibration, multiple moments per source |
| Images / text | Bounded oriented image decoding; overlapping UTF-8 text chunks | OCR, additional encodings, representative relevance |
| PDF | Visual page embeddings, first 24 pages; page viewer | Selectable text/OCR, resumable full supported document coverage |
| Video | One image per 8-second window, 2-second overlap; first 48 windows only | Ordered windows, soundtrack, full coverage, multiple results, timeline |
| Audio | Mono 16 kHz 16-bit PCM WAV, up to 30 seconds | Common compressed formats, resampling, long recordings |
| Operations | Import/remove from catalog; originals retained | Copy, move, rename, folder creation, original deletion, trash/restore |
| Jobs | WorkManager per asset, cancellation, battery/storage constraints | Per-chunk checkpoints and foreground priority |
| Tests | 11 JVM tests and 6 instrumentation tests on one CMF Phone 2 Pro; six synthetic files | Representative libraries, other phones/providers, sustained resource tests |
| Build | Debug build and lint recorded; 0 lint errors, 21 warnings | Signed/minified AAB, Play splits, upgrades, 16 KB compatibility |

Tested model pack: 484,622,336 bytes, upstream revision `24d962e906c7d332c6428e71c9676855024569e2`; exact digest and reproduction are in [DEVICE_TESTING.md](DEVICE_TESTING.md). Recorded inference cache was about 367 MiB. One initial indexing sample was about 1.1 GiB PSS; it was not a measured peak.

**Prototype integration is verified. Search quality, production reliability, motion retrieval, and broad device support are not yet verified.**

## 3. Launch scope and later versions

Priority meanings: **P0** = required launch behavior; **P1** = first useful updates; **P2** = separate later product work. “Where supported” requires a documented capability/format matrix and honest UI. It does not excuse silent failure.

### P0 — file manager, gallery, and viewers

| Capability | Acceptance contract |
|---|---|
| Locations and browsing | Chosen folders, accessible shared storage under the selected permission strategy, media categories, breadcrumbs, paging, read-only/access state |
| File discovery and identity | Reconcile external changes; one file discovered through multiple access routes appears once when identity can be resolved |
| Names, sorting, and filtering | Fast AI-independent filename search; name/type/date/size filters; folder scope; remembered list/grid/sort |
| Organization | Create folders, rename supported files/folders, favorites, pinned folders; preserve state through resolvable moves |
| Copy and move | Single/batch destination picker, conflict handling, progress/cancel, free-space checks, interruption recovery, verified destination before cross-source deletion |
| Sharing and opening | Multiselect, Android share sheet, Open with, temporary URI grants |
| Removal and recovery | System/provider trash where supported; validated local trash where feasible; restore/collisions; explicit permanent deletion otherwise |
| Details | Real size/type/location/dates; separate original storage from model/index/cache |
| Removable storage | SD/USB only when mounted and accessible; handle removal during work |
| Gallery | Date grouping, Photos/Albums, All/Photos/Videos/Screenshots filters, favorite filter, density/sort, selection, paging |
| Albums | Real folder albums; pin/reorder presentation; moving between folder albums follows normal real-file move semantics |
| Image viewer | Zoom/swipe, share/favorite/details, reliable return position |
| PDF/text viewers | Relevant page or text excerpt, navigation/zoom as applicable, useful unsupported/encrypted/damaged states |
| Audio/video players | Reliable seek/speed/transport, matched interval entry, return-to-match control; paused by default unless the user explicitly chose play |

### P0 — content search and setup

| Capability | Acceptance contract |
|---|---|
| Photo/image search | Natural descriptions; bounded preprocessing; OCR on validated scripts |
| Text and PDF search | Exact supported words/reference strings and semantic concepts; real snippets/pages; resumable bounded preparation |
| Video moments | Ordered short-window embeddings; soundtrack where decodable/supported; multiple useful intervals; single-video and whole-library search; timeline and playback |
| Recording search | Supported WAV/MP3/M4A/AAC decoder combinations, mono/resampling, windows, sound/meaning retrieval and time links; tested format matrix |
| Search scope | Type/location/date filters, consistent filename-first response, clear partial/unsupported coverage |
| Model setup | Optional verified resumable download, space estimate, Wi-Fi preference, pause/cancel, manual import under Advanced |
| Index controls | Include/exclude locations/types, review scope before processing, charging preference, pause/resume/retry, delete derived search data |
| Resource behavior | Foreground browsing/search priority, bounded background work, thermal/battery/memory handling, idle unload |
| Trust | Airplane-mode local search after setup; originals retained when search data/model is removed |

**Video moments stay P0.** If quality fails, improve or explicitly revise the supported claim with the product owner; do not silently defer the feature. Standalone recording search shares the soundtrack decoder work. A format cannot be advertised solely because playback works.

### P1 — useful follow-ups

- Basic ZIP create/extract with destination/conflict/progress, traversal protection, and bounded extraction.
- Crop/rotate with save-a-copy default; small clip export/trimming after core moment playback is dependable.
- Image-as-query retrieval, saved searches, virtual collections/tags.
- Hindi UI localization and broader validated language/script coverage.
- Conservative duplicate/large-file review with manual confirmation.
- DOCX/office text extraction after parser, size, and license validation.
- Local speech transcripts and exact spoken-word search using a separate ASR pipeline and resource contract.
- Better temporal/action retrieval where held-out tests demonstrate improvement; document scanning/PDF annotation.

### P2 — separate decisions

Encrypted vault, face grouping, cloud sync, network drives/SMB/FTP, advanced editors, subscriptions, automatic organization, or laptop/computer-assisted preparation each need their own demand, privacy, maintenance, and design assessment. They are not prerequisites for this release.

## 4. Interface and recovery contracts

Follow [DESIGN_SPEC.md](DESIGN_SPEC.md) for tokens and detailed behavior. Establish shared components early and use real catalog data.

### Navigation and content

- Browse is the first-run entry; remember the last Browse/Gallery location, filters, sorting, back stack, and scroll position.
- Browse contains categories, accessible pinned folders, real recent items, locations, Favorites, and Trash. A category is a filtered view, not a writable folder destination.
- Gallery uses Photos/Albums with a date-grouped grid. Define capture-date fallback before implementing ordering.
- Search has local optional history, one field, visible scope, type chips, and location/date filtering. New queries reset to the top; changes to the same query preserve reading position. Cancel old requests and reject stale responses.
- Results show filename plus the useful thumbnail, excerpt, PDF page, or time interval. Group video results under a source with “More moments”; retain distinct events within the source.
- A video player provides “Search this video,” matching timeline intervals, and return-to-match. Tab navigation is hidden inside viewers.
- Settings groups appearance, sources/access, content search, storage, privacy/help, and advanced import. Development statistics remain debug-only.

### Visibility and consent

**Exclude from content search** removes OCR/extracted text and vectors from retrieval, while the original stays visible in Browse/Gallery and searchable by filename. **Hide from the library** is a separate explicit presentation choice if implemented. **Remove a location's access** removes its accessible catalog/derived content as appropriate without deleting originals. Do not conflate these controls.

Access to browse is not consent to run content indexing. Review locations/exclusions before processing. Keep search history local, clearable, and disableable.

### Visual implementation

- Warm near-white / near-black theme, readable regular type, restrained selection accent, consistent icons, 4/8 dp spacing.
- Solid file rows, search results, dialogs, Settings, and operation progress. Build solid navigation first.
- Limited optional translucency for Gallery navigation/viewer controls only after contrast and frame-time measurement; supply Solid controls.
- At least 48 dp interactive targets, text contrast targets of 4.5:1 ordinary / 3:1 large, visible labels and semantic actions.
- Verify TalkBack, 200% text, long/Unicode names and extensions, Hindi glyph fallback, RTL, landscape, large screens, keyboard/gesture insets, predictive Back, focus restoration, and reduced motion.

### Required recovery states

| Situation | Usable response |
|---|---|
| No location or permission declined | Choose folder / selected-media route, accurate scope |
| Read-only source | View/share supported; disabled mutation with reason |
| Access lost / volume removed | Restore access or reconnect; cancel/pause affected work safely |
| Full disk / failed copy | Per-file outcomes, original retained, retry destination |
| Conflict | Keep both, supported Replace, Skip; truthful replace consequences |
| Interrupted move | Durable stage and verified recovery action |
| Model absent / unsuitable | Filename browsing/search available; setup or limitation explained |
| Failed/corrupt download | Resume or restart safely; working active model retained where possible |
| Partly prepared video/document | Display actual coverage; resume/retry; filenames remain searchable |
| No relevant result | Clear filters / broaden scope; no invented confidence |
| Unsupported or damaged input | Filename visibility, supported Open with, useful reason |
| Delete / restore | Actual retention/capability, explicit permanent-delete wording, collisions resolved |

## 5. Google AI Edge Gallery reference and reuse

### Pinned research baseline

Repository: [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery). Inspected commit: [`3210720d3f808f51bae52798435d0c590ddc05bf`](https://github.com/google-ai-edge/gallery/tree/3210720d3f808f51bae52798435d0c590ddc05bf), committed 6 October 2026 UTC. Its app build declares version 1.0.20. This records public source behavior; parity with a particular installed Play binary must be verified separately.

The source is [Apache-2.0](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/LICENSE). We can selectively adapt it, preserving required notices and marking modified files. Source availability helps us inspect app logic; it does not establish complete visibility into model training, native runtime internals, or undocumented behavior.

### What the inspected moment finder does

| Detail | Observed source behavior |
|---|---|
| Defaults | 2 frames per 2-second window, zero overlap, top 5, audio requested by default |
| Coverage | Iterates windows over the selected video's duration |
| Visual sampling | Uniform sample positions; indexing uses nearest sync/keyframe retrieval |
| Model input | Ordered image parts; with available audio, interleaves timestamp text, audio slices, and images |
| Soundtrack | Platform decoder, downmix, 16 kHz resampling, matching window slices; checks model audio capability |
| Budget | Moment task sets 1,024 input tokens and 70 vision tokens per frame; audio budget estimated in source |
| Runtime/store | MediaPipe UniversalEmbedder and SemanticRetriever with a SQLite vector store and fallback paths |
| Search | Metadata filter to one selected project, top-K windows; start/end metadata becomes results |
| Presentation | Moment previews and adjacent/overlapping interval merging for timeline; clip saving/export tools |
| Robustness | Decoder checks, fragmented MP4 normalization, model/backend recovery, audio fallback, stale-window cleanup |
| Reprocessing | Clears previous project windows before a new pass; failure cleanup can discard partial indexed windows |

These observations are supported by the pinned [view model](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/videomomentfinder/VideoMomentFinderViewModel.kt), [audio interleaving](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/videomomentfinder/AudioInterleaving.kt), [task config](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/videomomentfinder/VideoMomentFinderTask.kt), [embedder](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/Android/src/app/src/main/java/com/google/ai/edge/gallery/services/semanticretrieval/OnDeviceEmbedder.kt), and [retrieval service](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/Android/src/app/src/main/java/com/google/ai/edge/gallery/services/semanticretrieval/SemanticRetrievalService.kt).

Their [version catalog](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/Android/src/gradle/libs.versions.toml) lists `com.google.mediapipe:tasks-retrieval:1.1.0` and LiteRT-LM 0.18.0. Artifact availability, supported APIs, dependency conflicts, and runtime behavior still need a clean build on our toolchain. Their metadata-check bypass and native-library packaging choices are not a compatibility solution to copy blindly.

### Reuse candidates

| Candidate | Intended use / adaptation | Initial status |
|---|---|---|
| `AudioInterleaving.kt` | Ordered window parts and audio alignment; verify timestamp and token assumptions against our pack | ADAPTED; actual-pack acceptance tested; budget/quality gates remain |
| `VideoAudioExtractor.kt` | MediaExtractor/MediaCodec, downmix/resample, overlap handling; add our checkpoints/cancellation tests | ADAPTED; PTS-aware AAC overlap/resume tested; codec matrix remains |
| `VideoDecoderSupport.kt` | Useful decoder capability/error handling | STUDY |
| `Mp4Normalizer.kt` | Fragmented-container recovery on a bounded temporary copy where needed | STUDY |
| `OnDeviceEmbedder.kt` | UniversalEmbedder sequence input, modality checks, backend recovery behind our adapter | EVALUATE |
| `SemanticRetrievalService.kt` | Persistent vectors, metadata-scoped search, failure paths; evaluate scale/ANN behavior | EVALUATE |
| Moment ranking/interval merge | Multiple moments and timeline grouping with our result model | STUDY |
| `Common.kt` / `ClipTrimmer.kt` | Accurate clip export and partial-output cleanup for later editing | P1 |

### Reuse rules

1. Pin the upstream commit and list every copied/adapted file before importing it.
2. Keep applicable copyright/attribution notices; distribute the license, retain relevant NOTICE content if supplied, and mark modifications as required by Apache-2.0. Audit model/runtime/other dependencies separately.
3. Add a third-party provenance record with source path, revision, license, local destination, changes, and validation; generate in-app/release notices. The app's own license remains a publisher decision.
4. Import only the needed components. Gallery's branding, broad demo feature set, Firebase/network configuration, log statements containing user queries, model discovery/login flows, and unrelated dependencies do not become Keeps requirements.
5. Wrap reused code behind existing source/inference/retrieval boundaries; adapt to private storage, source URIs, namespaces, resource budgets, and our operation lifecycle.
6. Test the copied behavior on our pack and phone. Preserve required functionality while replacing incompatible APIs or unsafe assumptions.
7. Track upstream changes by reviewed revision updates; no automatic unreviewed merge into a release.

## 6. Video moment search implementation

### Required user experience

**Across the library:** query → ranked video groups → useful moment cards → open interval. **Inside a video:** Search this video → same scoped retrieval → highlighted timeline → jump between distinct moments. Filename results remain available before preparation.

### Pipeline to build

1. Resolve the accessible source and content version; inspect duration, orientation, container, video/audio tracks, and decoder capabilities.
2. Generate short windows with bounded frame/token/memory costs. Begin with Google's defaults as a comparison baseline; tune duration/frame density/overlap from results.
3. Decode representative frames in order. Record actual decoded presentation times where possible; requested timestamps and nearest keyframes are not interchangeable.
4. Decode available soundtrack, downmix/resample, and align its slices with the corresponding window. Missing audio must not prevent visual indexing.
5. Submit the ordered parts in one model call per window. Keep the frame-only and audio-interleaved preprocessing recipes versioned. Do not substitute averages of independent frame vectors and call them sequence embeddings.
6. Store normalized window vectors with asset ID, source version, namespace, start/end, sampled times, modality coverage, and preparation status. Persist checkpoints incrementally.
7. Embed the text query; retrieve candidates scoped to one video or the accessible eligible library. Group by source while retaining several relevant intervals.
8. Suppress redundant neighboring hits, form timeline intervals, and preserve distinct events. Refinement may inspect denser frames around promising candidates.
9. Open the interval with truthful provenance and a useful representative thumbnail; retain originating query/list state.
10. Resume after interruption; invalidate changed sources before publishing further chunks. Never describe an unfinished tail as fully searchable.

### Early runtime parity experiment

`EmbeddingProvider` now accepts ordered sequence parts as well as text, image and audio inputs. Actual-pack ordered image/aligned-audio calls and the production window pipeline are verified on generated fixtures; [video](docs/VIDEO_MOMENTS_SLICE.md) and [continuation](docs/AUDIO_CONTINUATION_SLICE.md) evidence record their limits. Compare the existing LiteRT-LM pack with the MediaPipe wrapper on the same fixtures before choosing a production integration. Keep the current working CPU path available during that decision.

The upstream model card's overall context limits differ from the moment task's runtime configuration. The inspected audio token estimate is derived in source and differs from the general model-card rate. Read and test actual pack/API limits; measure representative boundary inputs. Copying constants without that check is insufficient.

### Improvements to experiment with

| Experiment | Potential benefit | Evidence / cost to record |
|---|---|---|
| Actual-time decoding vs nearest keyframes | Less repeated imagery and missed short events | Brief events, sparse keyframes, VFR; decode time/memory |
| Modest overlapping windows | Better events at window boundaries | Recall/localization gain vs extra inference/storage |
| Scene-change / motion-guided extra samples | Better coverage at transitions/action | Detecting transitions alone does not prove action recognition |
| Coarse retrieval then local dense refinement | More precise shortlisted moments | Query latency and missed-event rate; refinement cannot rescue every missed coarse candidate |
| Separate visual/audio and combined channels | Resilience to distracting music or narration | Ablation with sound-only, silent, dubbed, and contradictory clips |
| Short/long window comparison | Objects and longer actions need different context | Added chunk count, ranking, model context and battery |
| Moment diversity | Several distinct events instead of repeated neighbors | Relevant distinct intervals in top results |
| Smaller vectors / persistent candidate index | More scalable retrieval | Recall relative to full exact baseline and filter correctness |

Do not enable all experiments by default. First match the reference capability; retain changes that demonstrate useful improvements within phone budgets.

### Action and speech claims

Find visually or audibly recognizable events in prepared windows. Evaluate action contrasts such as opening/closing, sitting/standing up, walking/running, and throwing/catching with similar scenery. Ordered input is a stronger foundation, but success must be measured. If contrast tests fail, retain validated moment search with accurate claims. Verbatim spoken-word search requires a separate tested transcript channel.

## 7. Other content search and retrieval

- Keep exact filenames and lexical search independent, fast, and Unicode-aware. Preserve meaningful exact identifiers and numbers through OCR/text normalization.
- Extract selectable PDF text first; OCR supported page images/scanned documents as needed. Evaluate a maintained compatible parser; use bounded decoding, encrypted/damaged input handling, and page provenance.
- Start OCR evaluation with Latin and Devanagari ML Kit variants; measure accuracy and APK/setup behavior. Do not imply support for scripts not validated.
- Chunk long text with bounded overlap and original offsets/snippets. Decode recordings into aligned overlapping windows with resume checkpoints.
- Compare the model-card and LiteRT guide's different text prefix conventions on the actual pack. Pin the winning query/document recipe; ensure native wrappers do not add duplicate prompts.
- Calibrate lexical/semantic fusion, exact match priority, scope filters, and abstention using tuning data. Similarity scores are not probability percentages.
- Retain the full exact-cosine implementation as a correctness oracle. Evaluate MediaPipe retrieval or another persistent candidate strategy rather than loading the entire vector catalog for every query; verify actual ANN/index behavior before making speed claims.
- Compare 768/512/256 dimensions on held-out data. If truncating, normalize again and use the same dimensions for queries/documents. Store distinct recipes in compatible namespaces; handle any quantization explicitly.
- Separate source-level and chunk-level ranking. Searching “all files” must not hide useful exact matches behind many video windows.
- Excluded/revoked/deleted/changed/incompatible chunks are ineligible before ranking and revalidated before preview. Apply access filters before limiting candidates where possible.

Storage planning: 100,000 float32 vectors occupy 307.2 MB at 768 dimensions or 102.4 MB at 256, before metadata/index overhead. These are arithmetic estimates, not model RAM or measured device savings.

## 8. Sources, permissions, and safe file operations

### Source/access strategy

Build common adapters for MediaStore, chosen SAF locations, and the justified shared-storage path. Each reports identity/version, enumeration/metadata access, read/write/create/rename/move/trash/delete capabilities, and availability. Account for the same original exposed by more than one adapter.

Product intent is a real file manager. Decide early which essential shared-storage workflows need all-files access; document why MediaStore/SAF cannot adequately provide those workflows and retain useful chosen-location access when declined. Qualifying file managers may request `MANAGE_EXTERNAL_STORAGE`, subject to declaration and Play approval. Broad photo/video access has its own eligibility/review. [All-files policy](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en), [photo/video policy](https://support.google.com/googleplay/android-developer/answer/14115180?hl=en).

SAF does not grant arbitrary whole-phone access: Android 11+ limits root/Download tree selection and Android/data/obb. All-files access does not unlock other apps' private directories. Explain these boundaries in locations and help. [SAF guidance](https://developer.android.com/training/data-storage/shared/documents-files), [shared-storage access](https://developer.android.com/training/data-storage/manage-all-files).

If restricted access is rejected, resolve the issue or explicitly reconsider the product's accessible scope before release. Do not advertise complete phone management through a limited picker path.

### Identity and reconciliation

- Version each source; use observed changes, app return, volume changes, and bounded background passes. No single watcher is reliable for every provider.
- Mark stale content immediately on known change. Validate version/access again before committing extracted chunks or completing an operation.
- Preserve favorites and references after resolvable rename/move; uncertain identity requires reconciliation, not guessed association.
- Deduplicate only with reliable identity evidence. A filename or equal size is not sufficient proof that two originals are the same.
- Apply non-destructive Room migrations with every schema change; test retained grants and state on upgrade.

### Mutation safety

- Persistent operation journal: planned → reading/copying → verifying → source removal if appropriate → complete / cancelled / partial / failed. Record per-file outcomes.
- Cross-source move: copy, verify according to the source/capability contract, then delete source. A length check alone is insufficient integrity proof. If verification/removal fails, keep truthful state and a recoverable source/destination.
- Prefer validated native rename/move within a source when its capabilities allow it. Avoid copying whole files unnecessarily.
- Detect collisions, invalid names, extension edits, full disk, changed source during copy, descendant/self destinations, disappearing volumes, and provider failures.
- Replace only with explicit consequences and safe staging where supported; providers without atomic replacement cannot promise it.
- Cancellation/process death cannot leave a completed-looking partial file. Temporary output is identifiable and cleanup respects recovery state.
- Use system media consent/trash where appropriate. For local ordinary files, validate source-volume trash, retention/capacity, hidden indexing, uninstall behavior, and restore collisions before offering recovery.
- Proposed local trash retention is 30 days; disclose actual provider retention. Permanent deletion is explicit when recovery is unavailable. Undo exists only where restoration is real.
- Removing a model or search index never deletes originals. Shared/Open with URIs use temporary grants.

## 9. Model setup, privacy, and resource policy

### Consumer setup

Browse works first. Enable content search offers a trusted optional download with actual pack size, expected storage, Wi-Fi preference, pause/resume/cancel, and scope/exclusion review. Manual model import remains in Advanced Settings.

Stream to private staging; authenticate the trusted manifest and verify the artifact digest before activation. Resume only when server artifact identity is unchanged. Retain a valid active model until the replacement is ready where space/runtime constraints allow. Namespace incompatible derived data without dropping favorites or source choices.

Free-space calculation includes model, expected inference cache, vectors/thumbnails, temporary download, update overlap, and margin. The tested pack plus recorded cache is roughly 870 MB before app/index/staging. Display an estimate and track actual use. Avoid letting a large optional model make the base app unusable.

Model distribution needs a pinned revision, verified rights/notices, trusted hosting, outage handling, cost budget, and update policy. At 485 MB, 1,000 full downloads are roughly 485 GB before retries. Keep model upgrades deliberate; explain any re-preparation.

### Runtime behavior

- Foreground browsing/search takes precedence over indexing. Serialize heavy inference and limit decoded frame/audio buffers.
- Persist per-window/page/chunk checkpoints; pause/resume without full-file restart. Support charging preference and bounded battery/thermal policy.
- Unload idle model resources, handle low memory/storage, and avoid index growth without controls. Cache clearing must not destroy source choices.
- CPU is the baseline. GPU/NPU need equivalent relevance, stability, native compatibility, thermal behavior, and honest fallback.
- Test 4 GB and 6 GB phones separately; distinguish app installation support from AI support. Unsupported AI leaves normal file use functional.
- Use appropriate bounded WorkManager/foreground work and notifications for the operation type. Android job quotas still apply to long-running workers. [Background-work guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

### Privacy contract

Local content indexing/search works in airplane mode after setup. Network is used for the optional model download; cloud providers and user-selected sharing destinations can have their own network behavior. The prototype has no Internet permission; a downloader changes that fact and requires a fresh manifest/data-flow review.

Keep files, filenames, queries, OCR text, transcripts, and vectors out of application logs, analytics, crash payloads, and download requests. Derived content is sensitive: private app storage, backup/transfer exclusions, separate deletion controls, no unsolicited content upload. Local search feedback can stay local; sharing support data is explicit and redacted.

Index exclusions remove content-derived text/vectors and associated derived cache as appropriate. File browsing permission changes invalidate eligibility immediately when observed. Distinguish thumbnail caches needed for browsing from content-search data in storage labels.

## 10. Engineering boundaries

Keep the proven Kotlin/Compose stack and tested integration during migration. Refactor by responsibility; new modules need a real dependency benefit.

| Boundary | Responsibility |
|---|---|
| Sources | Access/capabilities, URI/path identity, metadata, enumeration, source versions |
| Catalog | Files/folders, locations, favorites, albums, observed changes; no dependency on model availability |
| Operations | Persistent journal, copy/move/conflicts/verification, trash/restore, recovery and reconciliation |
| Extractors | Bounded text/PDF/OCR/image/audio/video preprocessing and real page/time provenance |
| Index coordinator | Consent/scope, checkpoints, work budgets, retry/pause/cancel, stale invalidation |
| Inference | Versioned single/ordered multimodal input, verified model lifecycle, serialized heavy calls, backend recovery |
| Retrieval | Independent lexical channel, candidate store, filters, fusion, grouping/diversity, no-match behavior |
| UI | Shared Compose state/components, navigation/viewers, accessibility and truthful progress |
| Release/evaluation | Signed builds, native checks, benchmark fixtures/reports, licenses, support diagnostics |

The chunk contract includes original identity/version, modality, page/time coordinates, recipe namespace, completion/checkpoint state, and derived artifact references. Index writes are incremental and version-safe; replacing a model/recipe cannot publish mixed incompatible vectors.

No folder enumeration, thumbnail decoding, PDF work, archive processing, or inference runs on the UI thread. Query generations prevent older results overwriting new searches. Reused services fit these boundaries rather than creating a parallel inconsistent catalog.

## 11. Milestones and task ledger

Status vocabulary: **TODO, ACTIVE, BLOCKED, VERIFY, DONE, DEFERRED**. DONE requires linked evidence and the milestone exit gate. BLOCKED names a concrete dependency and next action. The first chosen-folder slice is implemented; [its evidence and limits](docs/M0_M1_SLICE.md) are separate from the historical prototype above. Full task gates remain outstanding; no milestone is DONE after this slice.

### M0 — decisions and early technical proof

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M0-01 | Freeze access strategy and capability matrix for shared storage, MediaStore, SAF, removable/read-only providers | ACTIVE | [Access contract](docs/ACCESS_STRATEGY.md); native all-files grant and internal-root browsing verified in [device-access update](docs/DEVICE_ACCESS_SLICE.md); Play/provider matrix remains |
| M0-02 | Inventory pinned Gallery reuse files, licenses/notices, and exact dependencies | ACTIVE | [Pinned files, hashes, license and adaptations](docs/THIRD_PARTY.md); MediaPipe resolution/full transitive notices remain |
| M0-03 | Prove ordered frames + aligned audio on the actual pack; compare LiteRT and MediaPipe paths | ACTIVE | [Actual-pack sequence acceptance](docs/M0_M1_SLICE.md#m0-runtime-and-reuse-evidence), finite 768-D outputs; parity/boundaries/quality remain |
| M0-04 | Check native 16 KB compatibility and early signed/minified packaging; resolve failures before broad UI investment | VERIFY | [JNI crash repaired; optimized native queries/static alignment pass](docs/M0_M1_SLICE.md); R8 metadata warnings and real 16 KB runtime remain |
| M0-05 | Measure initial memory/load/index cost on current and lower-memory hardware; decide provisional AI tiers | ACTIVE | [Current memory report and actual-pack 70-token experiment](docs/DAILY_USE_SLICE.md#memory-and-disk-report); optimized Browse 71 MiB/query 425 MiB; representative peaks/thermal/lower-memory gates remain |
| M0-06 | Define corpus, moment annotations, tuning/test split, metric definitions, and Google comparison | TODO | Evaluation manifest and frozen initial gates |
| M0-07 | Confirm publisher/name/package path, launch languages, hosting budget, and production licensing decisions | TODO | Decision entries; unresolved business details remain explicit |

**Exit:** a supported inference path can embed genuine windows; storage/release risks have concrete findings; launch scope and evaluation are clear. Business decisions that do not affect implementation may stay open until their release dependency. The UI shell can proceed alongside independent probes.

### M1 — real interface and source catalog

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M1-01 | Shared theme/components, Browse/Gallery/Search navigation and viewer back-state | VERIFY | [Real-data flows and dark/recreation corrections checked](docs/M0_M1_SLICE.md); complete accessibility/recovery matrix remains |
| M1-02 | Source adapters, catalog identity/version, migrations, chosen-folder enumeration, MediaStore discovery | ACTIVE | [Automatic 5,079-item discovery, batched identity/preservation and migrations verified](docs/DEVICE_ACCESS_SLICE.md); observer/live grants implemented; overlapping routes/provider matrix/paging remain |
| M1-03 | Browse folders/categories/pins/recent, paging, sorting, filename search | ACTIVE | [Real folders/categories/pins/recent/sorting/names](docs/DAILY_USE_SLICE.md); [background filtering and optimized real-library scrolling sample](docs/DEVICE_ACCESS_SLICE.md); paging/large-library gates remain |
| M1-04 | Gallery Photos/Albums, date fallback, filters/density, favorites and selection | VERIFY | [Real media albums, available capture dates/modified fallback, selection](docs/DAILY_USE_SLICE.md); full large-library/accessibility checks remain |
| M1-05 | Settings/setup states, scope/exclusions, debug-only diagnostics | ACTIVE | [Settings destinations and bulk exclusion](docs/CONSUMER_UI_SLICE.md); [Room v6 persistent folder rules, automatic scope and notification controls](docs/OVERNIGHT_PREPARATION_SLICE.md); full setup/accessibility/recovery matrix and consumer download remain |

**Exit:** real folders/media appear without AI; identity and access are consistent; basic accessibility works. No fictional file action is presented as complete.

### M2 — complete safe file workflow

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M2-01 | Journal, destination picker, conflict policy, copy verification, cancel/recovery | VERIFY | [Persistent journal, verified Unicode copies and interrupted-deletion review pass](docs/DAILY_USE_SLICE.md); full-disk/revocation/cancel-at-all-stages remain |
| M2-02 | Rename/create folder/native and cross-source move; catalog/favorites reconciliation | ACTIVE | [Actual SAF create/rename/streamed move/favorite identity pass](docs/DAILY_USE_SLICE.md); native moves/cross-provider matrix remain |
| M2-03 | Supported trash/restore, retention, permanent-delete flow, restore collision | ACTIVE | [SAF trash/restore collision/permanent delete pass](docs/DAILY_USE_SLICE.md); Android-consented media flow implemented, wider provider/retention gates remain |
| M2-04 | Selection/share/Open with/details and URI grants | VERIFY | [Selection actions, FileProvider grants and capability-aware details implemented](docs/DAILY_USE_SLICE.md); external receiver/URI matrix remains |
| M2-05 | Complete choose → browse → copy → filename search → preview → trash → restore; repeat after process death | VERIFY | [Actual optimized copy/rename/preview/trash/restore UI and retained restart state](docs/DAILY_USE_SLICE.md); complete repeated workflow/interruption matrix remains |

**Exit:** one ordinary workflow survives interruption, then the supported operation matrix passes. This is the first complete utility slice.

### M3 — video moments baseline and product integration

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M3-01 | Adapt window/frame/audio pipeline and modality/budget checks using approved reuse candidates | ACTIVE | [Four ordered frames + actual AAC soundtrack + visual anchor](docs/VIDEO_MOMENTS_SLICE.md); context boundaries, codecs and parity remain |
| M3-02 | Incremental checkpoint persistence, versions, full supported coverage, pause/resume/error state | ACTIVE | [Version-safe checkpoints/migration](docs/VIDEO_MOMENTS_SLICE.md), [nine fresh windows across two production workers](docs/AUDIO_CONTINUATION_SLICE.md); multi-minute/process-death/access recovery gates remain |
| M3-03 | Multiple intervals, scoped and library-wide retrieval, grouping/deduplication | VERIFY | [Three distinct candidates and correct scoped/library chapter retrieval](docs/VIDEO_MOMENTS_SLICE.md); representative annotated/access-filter evaluation remains |
| M3-04 | Search this video, timeline intervals, thumbnails, seek/return-to-match | VERIFY | [Optimized timeline/cards, saved moment restart and playback to end pass](docs/DAILY_USE_SLICE.md); prior scoped/seek evidence retained; full TalkBack/lifecycle/real-video gates remain |
| M3-05 | Reproduce reference baseline; test sampling/overlap/channel/refinement improvements | TODO | Paired quality/resource comparison |

**Exit:** real nontrivial recordings yield useful multiple moments with correct playback; coverage is truthful; reference comparison is documented. Action claims remain evidence-gated.

### M4 — complete content search, setup, and viewers

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M4-01 | PDF selectable text/OCR, image OCR, bounded long text/page checkpoints | TODO | Exact identifiers, scripts, relevant page/snippet |
| M4-02 | Standalone compressed/long audio windows using the soundtrack infrastructure | ACTIVE | [AAC/MP3 preparation, nine-window continuation, exact overlap and interval retrieval](docs/AUDIO_CONTINUATION_SLICE.md); real sound/speech relevance and multi-minute coverage remain |
| M4-03 | Prompt parity, lexical indexing/fusion, abstention, candidate store/dimension choice | TODO | Held-out slice metrics against exact baseline |
| M4-04 | Optional verified download/import, update/space controls, model unload | ACTIVE | [Validated manual import, explicit/60-second idle release and RAM readout](docs/DAILY_USE_SLICE.md); consumer download/update/space and recovery cases remain |
| M4-05 | Dependable image/PDF/text/audio/video viewers and complete unsupported/recovery flows | ACTIVE | Main-screen viewers and audio/video interval selection implemented; lifecycle/accessibility/codec recovery acceptance remains |

**Exit:** every advertised content format passes its contract; setup is usable; ordinary library tasks stay responsive with AI absent or active.

### M5 — scale, resources, accessibility, and final polish

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M5-01 | Large metadata/chunk suites; query latency, paging, cache/vector budgets | ACTIVE | [First 5,079-item real metadata timing, optimized scrolling and unloaded memory samples](docs/DEVICE_ACCESS_SLICE.md); 100k suites, repeated p50/p95, paging and vector/query budgets remain |
| M5-02 | Foreground priority, charging/thermal/battery policy, bounded background resume | ACTIVE | [Preparation control/pacing audit and laptop regressions](docs/PREPARATION_CONTROL_AUDIT.md); [earlier overnight slice](docs/OVERNIGHT_PREPARATION_SLICE.md); new optimized phone/lifecycle/comfort checks remain |
| M5-03 | Device/provider/codec matrix, migration/recovery, access/exclusion audits | TODO | Supported matrix and unresolved limits |
| M5-04 | TalkBack, 200% text, RTL/Hindi glyphs, landscape/insets/Back/focus | TODO | Phone accessibility checklist and defects |
| M5-05 | Measured motion/translucency, spacing and final visual review | ACTIVE | [Geist/dot-heading/dock and consumer screen revision](docs/CONSUMER_UI_SLICE.md); measured frame pacing, full contrast/accessibility and final subjective review remain |

**Exit:** quality/resource gates hold on supported phones; remaining limits are specific and reflected in the UI/listing.

### M6 — release candidate and internal Play delivery

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M6-01 | Signed/minified AAB, Play App Signing plan, versioning, symbols, ABI/native checks | TODO | Actual release artifact and hashes |
| M6-02 | Play-delivered install, signed upgrades, Room migrations, retained grants/model state | TODO | Internal-track delivery/upgrade evidence |
| M6-03 | Privacy policy, data safety, permission evidence, notices and app content declarations | TODO | Release-specific draft submission package |
| M6-04 | Final icon, feature graphic, actual screenshots, supported claims and reviewer steps | ACTIVE | [Vector/adaptive/themed logo, 512px source and 1280×640 repository social card/template](docs/brand/README.md); Play feature graphic, release screenshots and reviewer steps remain |
| M6-05 | Tester/support preparation, hosting readiness, defect triage and release checklist | TODO | Named owners and operational runbook |

**Exit:** installable production configuration matches every declaration/claim and is ready for closed testing. Upload/account actions require the publisher's actual credentials/authorization when executed.

### M7 — closed beta and publication

| ID | Task | Status | Evidence / output |
|---|---|---|---|
| M7-01 | Recruit genuine diverse testers and collect consented task/search feedback | TODO | Account-specific tester eligibility and feedback |
| M7-02 | Fix critical/high defects, rerun affected gates and signed upgrades | TODO | Stable beta, sample counts, defects resolved |
| M7-03 | Complete restricted-permission/production reviews and final listing checks | TODO | Required approvals and reviewable release |
| M7-04 | Publisher release review, launch availability, first release and support monitoring | TODO | Explicit publication instruction and release record |

**Exit:** beta gates pass, applicable reviews approve, and the publisher authorizes publication of the concrete release.

### Delivery order and estimates

M0 risk checks and M1 UI can overlap where independent. M2 relies on the source/capability contract. M3 uses early sequence proof and versioned chunks. M4 reuses the video decoder work for recordings. Scale/native/security/accessibility measurements start early and become full release gates in M5/M6. Account and tester preparation can run alongside engineering.

The original 18–24 engineering-week estimate was provisional for one focused Android engineer with AI assistance and test-device access. It is not a committed deadline for this refined scope. Re-estimate after M0 and the complete M2 slice using measured work, known defects, and real developer capacity. Open-source reuse can save implementation time; integration, evaluation, and Play review still determine delivery.

## 12. Quality gates and comparison protocol

All numbers below are **initial product targets**, not achievements or Google requirements. Freeze definitions after M0 measurements. Change a target only with a dated reason; never quietly lower it to make a report pass.

### Retrieval corpus and metrics

Begin with 500–1,000 varied sources and at least 250 held-out queries; expand before modality/language claims if slices are too small. Split tuning and final evaluation by source/event families to avoid near-duplicate leakage. Use synthetic or explicitly consented content. Annotate relevant sources, pages, and video/audio intervals; allow multiple correct answers.

Include short events, boundary events, repeated scenes/actions, silent/dubbed/music-heavy video, VFR/rotation/sparse keyframes, fragmented containers, long recordings with events after five minutes, scanned PDFs, exact codes, Hindi/English/mixed script, and no-match queries. Use at least 40 no-match queries initially and enough positive/negative examples per advertised slice; publish denominators and uncertainty.

| Metric | Definition / initial beta target |
|---|---|
| Exact lookup | Supported unique filenames/identifiers present in indexed text: >=99% correct first result |
| Image/document retrieval | At least one relevant source in top 5 for >=85% of held-out supported queries |
| Audio/video retrieval | At least one relevant source in top 5 for >=75%; also measure relevant moments, not only source identity |
| First result and ordering | Report top-1 relevance and graded nDCG@5 per modality/language; freeze thresholds after baseline |
| Moment localization | Report temporal overlap/IoU, missed annotated events, distinct relevant intervals in top 5, and boundary error; overlap alone is insufficient |
| PDF links | 100% of evaluated links open their associated annotated page |
| Player seek | On supported seekable fixtures, actual entry within 500 ms of requested interval start; log failures and real decoded timing separately |
| No-match | On negative queries, <=10% display any semantic content result as a match after calibrated abstention; measure rejected positive queries too |
| Eligibility | Zero content hits from revoked/excluded/deleted/known-stale/incompatible sources; eligible filename behavior follows the exclusion contract |
| Motion contrasts | Improvement over still-frame baseline on held-out paired actions before broad action claims; record resource cost |

Define any event tolerance by annotation and codec rather than using arbitrarily broad windows to earn overlap. Do not trade away all useful positive results to pass no-match. Report each modality and language independently, plus known failures.

### Comparison with Google

1. Record Gallery APK/source version, model revision, runtime/backend, window/frame/audio settings, device, and thermal state. Source inspection alone is not an app benchmark.
2. Run the same consented fixtures and queries in Gallery's single-video mode and Keeps' single-video mode. Keep the original prototype and exact retrieval as additional baselines.
3. Compare top-1/top-5 source and moment relevance, event coverage, localization, preparation time, query latency, memory, battery/thermal behavior, and recovery.
4. Compare like-for-like settings first, then tuned product defaults. Record each changed sampling/channel/index recipe and its cost.
5. Evaluate library-wide behavior separately; do not claim single-video superiority from testing different candidate scopes.
6. Keep held-out failures. Say “better at this measured task on these devices” only when results support it. Google branding is not a quality metric; our ambitions are not measured results.

### Performance and resource targets

Use a 10,000-file / up-to-100,000-chunk suite and a larger metadata-only suite. Count chunks separately from files and include many video windows. Measure minified release builds with cold/warm starts, indexing during browsing, low storage, and repeated searches.

| Check | Initial target on a declared representative 6–8 GB phone |
|---|---|
| Cold Browse | First usable content within about 1.5 seconds; model loading does not block it |
| Filename/filter | p95 <250 ms excluding deliberate debounce |
| Semantic retrieval | Warm p95 <2 seconds; cold model + search about <5 seconds with cancelable feedback |
| Dense refinement | Report separately; budget after M0/M3; first candidates remain usable |
| Scroll | 60 Hz test: <5% slow frames under the defined workload; no routine UI-thread I/O |
| Memory | Whole-app steady AI target <900 MiB PSS; transient target <1.3 GiB; measure peaks and low-memory behavior |
| Stability | No OOM/ANR/repeated crash in a one-hour mixed browse/search/index session |
| Resume | Survive process death/cancel/charging pause without repeating completed compatible chunks |
| Background | Thermal/low-battery pause and foreground priority verified; quotas/notifications correct |
| Capacity | Actual model/cache/vector/thumbnails/staging growth, cleanup, and full-disk behavior reported |

Measure sustainable preparation throughput, energy, temperature/status, and model unload; set battery/time budgets from evidence. A 4 GB phone needs a separate AI support decision. Never combine CPU/GPU/NPU results into one misleading latency number.

### Safety, provider, and device coverage

Test writable/read-only SAF, MediaStore, actual shared-storage strategy, removable volumes, consent requests, grant revocation, external changes, same-name/case/Unicode conflicts, zero-byte/large files, full disk, source edits mid-copy, cancellation/process death, partial batches, restore collisions, and model/index upgrades. P1 archive work adds traversal/decompression-bomb cases before shipping it.

Cover Android 12 through the latest shipping version, current CMF/Nothing, Samsung, Pixel/reference hardware, lower-memory hardware, and a 16 KB page-size environment. Validate actual advertised codecs/containers. Verify TalkBack/accessibility and permission/model/download recovery on release builds.

Stop release for lost/corrupt originals, wrong restore, content exposure, unbounded resources, native incompatibility, misleading claims, or critical permission/upgrade failures. Zero observed loss in a suite is required evidence, not a universal safety guarantee.

## 13. Google Play, beta, and launch

Policy check date: **7 October 2026**. Re-check at submission; actual account/Console requirements govern.

### Publisher and release artifact

- Confirm personal/organization identity, required verification, support contact, final name, application ID, app license, and countries. Current prototype ID is `dev.keeps.library`; decide before first upload.
- Use signed AAB, Play App Signing, secured/backed-up upload key, monotonic version codes, retained mappings/native symbols, and tested Play splits. [App setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).
- Target API requirement currently calls for Android 16 / API 36+ for new phone apps/updates from 31 August 2026. The prototype targets 36; re-check for the actual launch. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
- Inspect all shipped native libraries and APK alignment and run inference/media on 16 KB pages. A recent Gradle plugin does not prove dependency compatibility. [16 KB guidance](https://developer.android.com/guide/practices/page-sizes).
- Configure shrinking/minification and notices, measure release download/install size, choose actual production ABIs, and keep the optional model out of the base app. The 125 MB debug APK is not a release-size result.
- Test signed beta upgrades, migrations, persisted access, and model/index state. A differently signed debug install is not a normal store upgrade; plan the transition without casually deleting data.

### Submission package

| Deliverable | Required content |
|---|---|
| Privacy policy | Public accessible HTML, app/developer identity/contact, local processing, download/provider/sharing behavior, retention and deletion; in-app link |
| Data safety | Real release SDK/network/hosting flows; derive answers from evidence, including any CDN/diagnostic handling |
| Permission evidence | Restricted-permission justification, working file-manager/gallery flows, reviewer walkthrough and fallback limits |
| App content | Rating, audience, ads, app access, category/contact, applicable service declarations |
| Notices | Copied Gallery components, model/pack/runtime, transitive libraries, fonts/icons/parsers |
| Reviewer steps | Synthetic fixtures, access/setup, preparation expectations, text/page/video moment searches, offline behavior |
| Store assets | 512x512 icon, 1024x500 feature graphic, required screenshots; aim for 5–7 actual release phone screenshots |
| Listing | Tested capabilities, optional download/space, supported limits, accurate local-search and moment wording |

Local processing does not automatically settle every declaration; review the actual release flows. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [user data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

Potential listing line, subject to passing gates: **“Organize files and find photos, documents and video moments with private search.”** Explain setup and format/device limits. Concept images are not release screenshots.

### Beta and first publication

1. Internal Play testing: signed installs, delivery/native compatibility, migrations, setup/offline and basic tasks.
2. Closed beta: genuine consenting testers, varied devices/providers, ordinary file tasks and real retrieval feedback. Recruit early; no automatic uploading of personal libraries.
3. New personal accounts created after 13 November 2023 currently need at least 12 continuously opted-in testers for 14 days before applying for production access. Recruit 15–20 for attrition; keep feedback/fix evidence. Eligibility is not automatic production approval. [Testing policy](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
4. Require >=90% success on agreed copy/move/find/share/trash/restore usability tasks without coaching, with sample counts; no unresolved critical/high defects; retrieval/resource gates and signed upgrades pass.
5. Complete reviews, declarations, hosting/support readiness, and publisher review of the final artifact/listing. Set the public date after readiness/approval.
6. First publication uses tested initial country/language availability. Percentage staged rollout is for subsequent updates; then consider 5% → 20% → 50% → 100% with measured stops. [Rollout guidance](https://support.google.com/googleplay/android-developer/answer/6346149?hl=en).

### First month and recovery

Name an owner for Play crash/ANR checks, support triage, permission/codec regressions, model hosting/cost/outages, and search feedback. Prefer redacted diagnostics. Track user task success, model setup completion, preparation/resume reliability, and search failures without collecting file content by default.

Have a halt/forward-fix runbook, versioned migrations, and a tested model update path. Halting an update does not revert installed versions. Retain compatible recovery builds/artifacts; do not implement destructive remote switches.

## 14. Risks and open decisions

| ID | Risk / decision | Current position | Resolve by |
|---|---|---|---|
| D01 | All-files and broad-media strategy | Daily-use build adds optional Android-selected/full media grants and optional all-files access for file management; SAF remains available. Play eligibility and provider/device matrix remain open ([contract](docs/ACCESS_STRATEGY.md)) | M0-01 |
| D02 | Runtime / sequence API / pack parity | LiteRT actual-pack ordered-image/audio acceptance proved; MediaPipe parity still required before migration ([evidence](docs/M0_M1_SLICE.md)) | M0-02/03 |
| D03 | Native 16 KB and minified release behavior | JNI DTO renaming crash fixed with targeted keeps; optimized native queries/static ELF/ZIP checks pass; metadata warnings and real 16 KB runtime remain ([evidence](docs/M0_M1_SLICE.md)) | M0-04, M6-01 |
| D04 | AI device tiers / backend | CPU baseline; low-memory and accelerator support provisional | M0-05, M5 |
| D05 | Window/frame/audio recipe and text prompts | Provisional `video-tav-anchor-v1`: four ordered frames + PTS-aligned soundtrack + separate visual anchor; actual sequence-only chapter failure fixed, broad quality/parity still unproved ([evidence](docs/VIDEO_MOMENTS_SLICE.md)) | M0-03, M3/M4 |
| D06 | Short events / action discrimination | Mandatory useful moments; broad motion claims conditional | M3-05, quality gates |
| D07 | Vector store and dimensions | Full exact oracle; persistent/scalable strategy and truncation to evaluate | M4-03/M5-01 |
| D08 | Trash and identity across providers | Capability-specific recovery; uncertain identity is not guessed | M0-01, M2 |
| D09 | OCR/PDF/codec matrix | Latin/Devanagari and common platform-decoded formats are starting targets | M4/M5 |
| D10 | Model delivery/update/hosting budget | Optional pinned verified download; host and operating budget undecided | M0-07, M4-04 |
| D11 | Name/package/publisher/license/pricing | Keeps working name, free ad-free beta; Apache 2.0 selected by owner on 27 October; permanent identity/business choices pending | Before first upload / listing |
| D12 | Benchmark representativeness | Six fixtures insufficient; labels/slices/negative queries need expansion | M0-06/M3/M4 |
| D13 | Real testers/devices and release schedule | Recruit/procure early; no promised date | M0/M6/M7 |

Choose routine engineering details using evidence and record them here. Publisher identity, paid business terms, external accounts, and final publication require the actual owner's decisions at their execution point. Do not make them artificial blockers for independent implementation.

## 15. How to keep this roadmap current

1. At the start of each build slice, mark selected task IDs ACTIVE, identify dependencies, and keep a concrete acceptance target.
2. After implementation, use VERIFY until meaningful required checks pass. DONE links code/PR or commit plus actual build/device/evaluation evidence and known limits.
3. Update affected current-state rows, task statuses, decision entries, and the date in the same change. Do not check off broader production scope after a small smoke test.
4. Record a scope change explicitly: what moved, why, effect on milestones/claims, and the user's decision if it changes the agreed product. Video moment search stays a launch requirement unless explicitly changed.
5. Keep design behavior aligned with DESIGN_SPEC.md, measured checks in evidence documents, and upstream/license provenance in the reuse inventory when created. Avoid competing task lists in README or the original planning document.
6. Preserve historical evidence and date policy/model checks. Do not overwrite old results with newer targets or claim tests were run when only documentation changed.
7. At each milestone exit, review remaining defects/risks, supported matrices, resource costs, estimate, and next smallest complete workflow.

Suggested progress entry:

```text
Date:
Task IDs / milestone:
Status changes:
Implemented behavior:
Checks actually run and evidence:
Known limits / blockers / next action:
Decision or scope changes:
```

## 16. Decision and change log

| Date | Change / decision | Effect |
|---|---|---|
| 2026-10-07 | Established this master roadmap from the original plan, design, device evidence, and discussion | ROADMAP.md controls scope/progress; original Play plan is historical |
| 2026-10-07 | Video moment search is required for launch, including single-video and whole-library search | Preserve M3 and its quality gates; do not silently defer it |
| 2026-10-07 | Adopt selective open-source reuse from the pinned Google AI Edge Gallery reference | Prove runtime parity, retain notices, adapt components to our lifecycle |
| 2026-10-07 | Move ZIP tools to P1; keep safe file operations, gallery, tested multimodal search in P0 | Focus implementation on the agreed daily workflow |
| 2026-10-07 | Bring source identity, sequence inference, memory, minified/native compatibility checks forward | Find expensive risks before broad production implementation |
| 2026-10-07 | Separate content-search exclusion from browsing visibility; require several useful video moments | Clarify privacy controls and result behavior |
| 2026-10-07 | Add measurable first-result/ordering/negative-query/localization evaluation and Google comparison | Improvement claims depend on held-out evidence |
| 2026-10-08 | Deliver real chosen-folder Browse/Gallery/Search, optional model setup, explicit exclusion, v1→v2 migration and version-safe index commits | [Slice implementation/evidence](docs/M0_M1_SLICE.md); M1 tasks stay ACTIVE/VERIFY until their complete gates pass |
| 2026-10-08 | Preserve working LiteRT integration; adapt only pinned Gallery interleaving helper with distributed Apache notice | Actual pack accepts ordered images/aligned generated audio; MediaPipe parity and representative quality remain M0-02/03 |
| 2026-10-08 | Bound folder refresh by 2,000 total entries/200 directories/depth 8; show partial discovery and truthful read-only capability | Does not replace launch MediaStore/shared-storage/paging requirements or original-operation recovery gates |
| 2026-10-08 | Use actual provider modification date with added-time fallback, labeled File date; Recently added uses addition time | Capture-date extraction and opened-file history are not implied |
| 2026-10-08 | Repair observed optimized native crash by retaining literal JNI input/response DTO names; add artifact mapping check | Preserve application optimization and working LiteRT; actual optimized note/PDF inference passes; broad release gates remain |
| 2026-10-08 | User directs core functionality first, final frontend polish later | Prioritize source integrity, runtime, safe operations and video pipeline; retain usable navigation without expanding aesthetic work |
| 2026-10-08 | Ship first persistent ordered-video pipeline; selectively adapt pinned Gallery decoder with PTS-aware PCM and bounded checkpoints | [Actual-pack alignment, interrupted resume and chapter ranking evidence](docs/VIDEO_MOMENTS_SLICE.md); broader launch gates stay active |
| 2026-10-08 | Preserve failed sequence-only blue-chapter assertion; add separate visual anchor instead of weakening expected result | Provisional maximum-of-two-cosines recipe; representative motion/audio evaluation remains M0-06/M3-05 |
| 2026-10-08 | User requests improved professional logo alongside core work | Original folded-K vector, adaptive and themed icons, reproducible SVG/PNG exports; other frontend polish remains later |
| 2026-10-08 | Replace the monogram at the user's request with an abstract folder/image mark | [Stacked-horizon master/adaptive/themed exports](docs/brand/README.md); common masks and small sizes visually checked, compiled into the installed debug app |
| 2026-10-08 | Extend the pinned Gallery-derived soundtrack decoder to standalone compressed recordings | [Actual AAC/MP3 windows, bounded production continuation and byte-identical prior data](docs/AUDIO_CONTINUATION_SLICE.md); retain LiteRT/Room v4 and Apache notices |
| 2026-10-08 | Keep exact overlap/scene assertions when native testing reveals failures | Integer resampling phase fixes MP3 rounding; completion uses actual WorkManager state/executions rather than assuming retained history; nine fresh video windows and three repeated matches pass |
| 2026-10-08 | Correct thumbnail keyframe selection and reject stale ADB XML | Use nearest actual frame; helper rejects Android idle-timeout responses. Fresh viewer tree, audio playback and image Back pass; full TalkBack remains pending |
| 2026-10-08 | Measure cost before asking for long personal-video tests | Generated 54-second video takes 371.178s including test/query, checkpoint PSS reaches about 1.15 GiB; CPU speed/memory are M0-05 gates. User offered manual real-video testing; short representative test requested |
| 2026-10-08 | User requests a daily-use file/gallery handover and supplies black/red/white direction and four-tile logo | Implement real media access, live-capability file operations, Room v5 journals/trash/pins/moments and usable real-data UI; [daily-use report](docs/DAILY_USE_SLICE.md) records actual evidence |
| 2026-10-08 | Inset supplied logo after observed circular clipping; user clears cache during testing | 76% native foreground fits actual phone circle; retained model/catalog verified, expendable compilation cache recreated, generated budget-test cache removed |
| 2026-10-08 | Probe pinned Gallery-style 1024-input/70-vision-token budget on actual pack without adopting it | Simple image ordering/four-frame acceptance pass; soundtrack/quality/context boundaries remain. Production preprocessing and old vectors stay compatible |

### 8 October slice progress

**Tasks:** M0-01/02/03 ACTIVE, M0-04 VERIFY; M1-01/04/05 VERIFY, M1-02/03 ACTIVE. The usable slice is implemented. No complete milestone or broader production task is marked DONE.

**Evidence:** [Implementation and reproducible checks](docs/M0_M1_SLICE.md), [source contract](docs/ACCESS_STRATEGY.md), [upstream provenance](docs/THIRD_PARTY.md), [build notes](BUILD_NOTES.md), [device notes](DEVICE_TESTING.md). New checks include migration/exclusion/version safety, ordered image/audio calls on the actual pack, real chosen-folder albums and model-absent browsing. Old prototype results are retained as historical evidence.

**Next:** finish M0-01/02/03/04 provider/runtime/release gates and M1-02/03 MediaStore/paging/pins. M2 safe file operations and M3's remaining video acceptance gates follow. Video moment search remains required for launch. No publisher/signing account decision blocks current implementation work.

### 8 October ordered-video and identity progress

**Tasks:** M3-01/02/04 ACTIVE, M3-03 VERIFY, M6-04 ACTIVE. Four ordered frames plus PTS-aligned AAC soundtrack and a visual anchor now prepare per interval, persist checkpoints and resume bounded batches. Scoped/library search returns several intervals; the viewer seeks with lead-in and Return to match. [Video slice](docs/VIDEO_MOMENTS_SLICE.md) records 23 passing JVM tests, eight focused native/database tests, one production worker/retrieval/playback test, and observed UI controls. No complete launch milestone is DONE.

**Identity at that snapshot:** The folded-K logo was integrated with adaptive and monochrome resources. The later progress entry below replaces it with the [stacked-horizon folder/image mark](docs/brand/README.md). Wider frontend polish remains later per the user's direction.

**Next task IDs:** M3-02 long-video continuation/process-death checks, M0-06/M3-05 representative corpus and sequence/audio/motion quality; M3-04 thumbnails/accessibility; M2-01 capability-aware file-operation design and first safe copy flow. MediaPipe parity and real 16 KB runtime remain M0-03/04 gates.

### Audio/continuation progress — 8 October 2026

**Tasks:** M0-05 ACTIVE, M3-01/02/04 ACTIVE, M3-03 VERIFY, M4-02/05 ACTIVE, M6-04 ACTIVE. Standalone AAC/MP3 now prepare checkpointed overlapping intervals and continue after eight new windows. Video/audio viewers offer candidate playback; video cards show actual nearest-frame thumbnails. The installed debug app uses the stacked-horizon folder/image logo. No complete launch task or milestone is marked DONE from these narrow checks.

**Verified:** 24 JVM tests; seven Room tests plus migration in the first run; corrected focused runner **OK (2 tests), zero skips, 411.294s**. It prepares three MP3/nine AAC windows and nine fresh ordered video windows across two work records, with expected repeated blue matches at 0:06/0:24/0:42. Prior originals/vectors remain byte-identical. Final UI checks show red/blue/green thumbnails, selected interval/12-second Return to match, fresh viewer accessibility trees, MP3 6→9-second playback and image Back returning to its original folder. [Evidence and failure/fix history](docs/AUDIO_CONTINUATION_SLICE.md).

**Limits:** Generated 54-second video preparation/test/query took 371.178s with about 1.15 GiB checkpoint-observed process PSS; representative speed/thermal/low-memory gates remain. White-noise retrieval finds the correct interval inside the MP3/AAC, but the old tone WAV ranks above them across the library; M4-03 calibration remains open. TalkBack, multi-minute/process-death recovery and optimized preparation of these additions remain unverified. User's optional short real-video relevance check is pending.

**Next task IDs:** M0-05 performance measurements/tuning, M0-06/M3-05 annotated real-video comparison, M4-03 retrieval calibration; M3-02 process-death/access recovery and M2-01 first safe capability-aware copy flow. MediaPipe parity and actual 16 KB runtime remain M0-03/04.

### Daily-use handover progress — 8 October 2026

**Tasks:** M2-01/04/05 and M3-04 move to VERIFY; M2-02/03, M1-02/03 and M0-05 stay ACTIVE; M4-04 becomes ACTIVE. No complete launch milestone is DONE. [Daily-use report](docs/DAILY_USE_SLICE.md) links code behavior, checks, artifacts, RAM and remaining acceptance gates.

**Implemented:** optional media/shared-storage access, live-capability SAF/file changes, persistent verified transfers and review, create/rename/trash/restore, Room v5 migrations, pins/recent files, selection/details, saved video moments, real timeline controls, memory readout/unload and the inset supplied logo. AI exclusion remains distinct from browsing visibility.

**New verification:** 26 JVM tests; 12 native storage/database tests and one actual-pack resource probe, no skips. Debug/test/optimized builds and lint pass (0 errors/73 warnings). Real optimized UI completes create → copy → rename → preview → trash → restore; restored state and saved moment survive restart. Blue query retrieves 6–14 seconds, bookmarked playback reaches the 18-second end. Fresh Android circular icon shows clear padding. Optimized Browse is about 71 MiB PSS and prepared semantic-query screen about 425 MiB; debug image probe reaches 903 MiB. The experimental smaller budget is not a production change.

**Limits/next:** M0-05 CPU budgets and sustained peaks; M0-06/M3-05/M4-03 annotated real-video/negative-query quality and compact-budget soundtrack evaluation; M1-02/03 paging/provider routes; M2-01/02/03/05 full interruption/storage/provider matrix. MediaPipe parity, real 16 KB runtime, metadata warnings, accessibility and production signing remain release gates. The optimized debug-signed preview is installed for daily feedback; user's model/catalog/originals are retained.

### Device access and discovery progress — 8 October 2026

**Tasks:** M0-01 and M1-02/03 stay ACTIVE; M1-05 stays VERIFY; M5-01 moves to ACTIVE. The user-reported slow gallery import and blocked root picker drove this concrete follow-up. [Report and evidence](docs/DEVICE_ACCESS_SLICE.md).

**Implemented:** first-launch native permission setup, retry/skip/existing-grant states, automatic permitted media and shared-file discovery, background permission/filter/grouping work, 128-item Room batches, preserved catalog preferences/index compatibility, discovery progress and clear all-files versus chosen-folder guidance. AI preparation remains explicit; exclusion still leaves browsing visible. Room stays v5.

**New verification:** 26 core tests, 12 focused Android database/migration/media/discovery tests (zero skips), debug/test/optimized builds, lint (0 errors/71 warnings), native JNI/static alignment/signature checks. Isolated empty catalog discovers 5,079 real permitted items in 9.216s; repeat scan 4.653s. Optimized phone UI browses the internal root and opens the prior generated 204-byte note without AI. A five-swipe Gallery sample records 536 frames, 1 janky frame under the current metric (legacy: 60), and about 183 MiB PSS. Restarted Browse is about 142 MiB after discovery/settings return. Originals were not mutated; updates used install -r without clearing data.

**Limits/next:** M1-02/03 paging and overlap/provider reconciliation; M1-05 fresh-install denied/selected-access recovery; M5-01 repeated larger-library/query/cache budgets. This pass does not retest model inference or establish improved AI relevance. M0-05/M3-05/M4-03 memory and annotated video/negative-query quality gates continue. Full release signing, Play permission review and actual 16 KB runtime remain open.

### Bulk AI preparation scale audit — 8 October 2026

The user started preparation for approximately 5,801 mixed files. ADB reports no device at this check, so live progress, model installation, temperature, free space and ETA are unknown. The successful 5,079-item metadata discovery test does **not** prove full-library AI preparation. M4-04/M5-01 remain ACTIVE.

Source inspection confirms a shared serialized embedding engine, per-file WorkManager jobs, low-battery/low-storage constraints and per-window audio/video checkpoints. It does not establish a bounded global preprocessing queue, an app-specific storage cap, thermal control or sustained bulk reliability. `LibraryRepository.queueAll()` currently also selects already prepared AI_READY/TEXT_READY items; a repeat bulk request can redo completed work. Skip compatible completed work and bound preprocessing before declaring full-library preparation ready. Preserve existing checkpoints and originals when fixing this.

Earlier retained debug evidence used about 903 MiB PSS for images and 1.15 GiB at video checkpoints; the 54-second generated-video preparation/test/query took 371.178s. These are historical fixture samples, not a current optimized 5,801-file forecast. Model/runtime disk footprint and RAM evidence are in [the daily-use report](docs/DAILY_USE_SLICE.md). Obtain live aggregate rates and total audio/video duration before giving an ETA.

### Preparation UX and heat follow-up — 17 October 2026

**Tasks:** M5-02 moves to ACTIVE; M0-05/M4-04/M5-01 remain ACTIVE and M1-05 remains VERIFY. The earlier bulk-repeat audit was corrected in the 8 October queue/UX implementation: bounded pumping, scope/model gating, completed-work skipping and pause/resume passed 13 native tests. This follow-up addresses warmth in hand after approximately 145 of 3,800 photos; the user subsequently clarified this was not overheating. [Report](docs/THERMAL_PREPARATION_SLICE.md).

**Implemented:** Content search first in Settings; model import/source, type selection and counts; default Gentle and 25-file batches, optional 100/all and charging-only. Proportional rests and burst breaks reduce sustained work. Android thermal status/headroom and battery-temperature heuristics defer work without dropping queue/checkpoints. Device-reported thermal levels avoid an OEM-specific false cooldown. Main/vision/audio backends request two CPU threads. Model inputs, vector namespace, Room v5 and originals remain unchanged.

**New evidence:** 37 JVM tests; 15 Android tests, zero skips, 116.968s. Four generated photos across two batches take 84.191s including pacing; two remain unqueued, and repeat requests queue zero with completed vectors unchanged. Actual-pack ordered two-frame/reversed/soundtrack inputs are accepted under the thread request. Debug/test/lint build passes (0 errors/80 warnings); optimized build passes. JNI/static ELF/ZIP/signature checks pass. The initial native image-batch timeout came from an absolute headroom cutoff treating this phone's light state (~0.84) as cooldown; device-reported levels and a regression test corrected it. No thermal protection was overridden.

**Limits/next:** Full-library comfort and ETA remain open. This connected installation had no catalog/model before launch; metadata was rediscovered and the existing pack reimported without an app-data reset. The earlier 145-item index could not be verified. The phone disconnected after successful optimized installation, before final UI/inference smoke; preparation remains paused. Continue M5-02 live Gentle/25 comfort and text/PDF boundaries, M0-05 resource tuning, and M4-03/M3-05 relevance.

### 17 October clarification: unattended library preparation

**Product direction, not completed functionality:** M5-02/M4-04 should provide one opt-in overnight preparation flow: review included locations/types and storage estimate, wait for charging and an idle device, process bounded chunks, cool down and resume automatically, retain progress across interruption, and prioritize foreground use. Charging itself can add heat; it does not bypass cooling checks. User Pause remains authoritative. Show prepared/searchable coverage, remaining media duration, waiting reasons, failures and an updating completion range learned from representative per-type throughput including rests. Before calibration, display that the estimate is being calculated. An overnight target must not promise that every library finishes in one night.

**Acceptance evidence still required:** screen-off/overnight continuation on the actual Android version, unplug/replug, cooldown/resume, process death/reboot, completion without reopening Keeps, and no repeated compatible completed work. Account for Android background execution limits. M0-05 measures actual compute time, wall time, memory, battery and storage; M5-01 measures growing vector counts and search latency. Photo counts and audio/video duration are separate workload measures; 100 GB alone cannot determine cost. Large-library indexing and compact-vector options must preserve representative retrieval quality under M4-03/M3-05 before adoption. No new runtime feature or benchmark is claimed by this record.

### 17 October product consolidation: exclusions and consumer controls

The user chose easy exclusion of a few unwanted files over mandatory selection/reorganization of thousands, deferred the laptop/computer companion, and requested a coherent Gallery/Settings/button and battery/background product direction before rewriting. [The consumer contract](docs/PRODUCT_DIRECTION.md) fixes the screen responsibilities, exclusion semantics, automatic versus manual preparation, close/lock/force-stop behavior and implementation order. M1-05 is ACTIVE again. The code audit confirms individual exclusions and persistent queued work; bulk/location exclusion, short Settings destinations, automatic continuation of an approved scope, learned ETA and verified overnight completion remain implementation work. No new runtime check or APK is claimed.

### 18 October consumer interface implementation

M1-05/M5-05 ACTIVE: the Settings destination split, Search library, Advanced diagnostics and File activity are implemented; bulk individual exclude/include and long-video review are transactional and expose real catalog data. Geist with retained OFL, original dot headings, a revised translucent dock, segmented gallery controls and short state transitions replace the prior generic presentation. 37 JVM tests and 14 isolated phone tests pass; final debug/test/lint and optimized build passes (0 errors / 83 lint warnings). Optimized preview installed with `-r`, 24 ready files/model still displayed, filename lookup and generated image viewing checked. [Report, exact APK, captures and limitations](docs/CONSUMER_UI_SLICE.md). No full-night, motion-quality or semantic-relevance gate is marked complete. Next: M1-05 persistent location scopes and complete setup recovery; M5-02 unattended behavior; M5-04/05 accessibility and measured final visual/motion acceptance; M3-05/M4-03 representative relevance.

### 18 October overnight preparation implementation

M1-05/M5-02 ACTIVE. Room v6 adds persistent descendant/future-arrival exclusion rules and an automatic queue lane. Explicit automatic approval records content types and optional selected IDs; the planner discovers approved arrivals, and automatic workers require charging and a noninteractive phone. Manual Pause survives restart; disabling automation preserves completed content and separate manual work. Notification Pause and persisted session counters make the run observable. PDF/text checkpoints now bound work between supported pages/sections without expanding the existing coverage limits. [Implementation, current verification and tonight's trial](docs/OVERNIGHT_PREPARATION_SLICE.md).

Final build/core/lint/optimized checks pass (37 JVM tests, 0 lint errors / 88 warnings); 21 native database/migration/folder/policy tests pass with no skips in 2.059s. The actual-pack two-file background probe passes with saved progress before verification, unchanged existing chunk digest and persistent Pause (102.244s from arming to last progress, not a library ETA). Optimized UI/confirmation checks pass; automatic mode is left off for user approval tonight. Retained R8 metadata warnings and release gates remain. No full-library preparation has been initiated by these tests. Next: M5-02 full-night/charger/reboot matrix and calibrated estimates; M5-01 resource/search scale; M3-05/M4-03 relevance.

### 25 October preparation control and pacing audit

The user reports approximately 200 photos, 26 minutes of active processing and 2–3 hours elapsed, with stale notification Pause and confusing Resume controls. [The audit](docs/PREPARATION_CONTROL_AUDIT.md) records concrete source defects and fixes: shared durable status, exact-queue resume, command generations/worker ownership, safe legacy recovery, deduplicated requests, short sensor-aware rests, fixed resource rechecks and elapsed/processing/break/other-time reporting. Room stays v6 and model/runtime inputs stay unchanged. Historical Gentle alone requests 70m30s rests for a synthetic 26m workload; this is a policy calculation, not phone telemetry.

66 laptop tests pass without failures/skips, including actual Room/WorkManager code under Robolectric and a synthetic uninterruptible-step cancellation test. Core/debug/test/lint/optimized build succeeds in 5m49s, 0 lint errors/85 warnings; JNI/ELF/ZIP/signing checks pass. Updated optimized APK built but not installed; no ADB, personal data or actual-model run is used. M1-05/M5-02 remain ACTIVE; the new flow is VERIFY until optimized phone pause/resume, charger/screen, process/reboot and sustained comfort/timing acceptance. M5-01 scale/resources and M3-05/M4-03 relevance remain separate next gates.

## 17. Reference sources

### Project evidence and design

[README](README.md), [DEVICE_TESTING](DEVICE_TESTING.md), [BUILD_NOTES](BUILD_NOTES.md), [DESIGN_SPEC](DESIGN_SPEC.md), [original Play planning document](PLAY_STORE_ROADMAP.md), [concept board](docs/design/keeps-concept-v1.png), [generation record](docs/design/IMAGE_PROMPT.md).

### Model and runtime

- [EmbeddingGemma 2 model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2): modalities, text prompts, dimensions, precision and context guidance. Actual mobile-pack behavior must be tested.
- [LiteRT-LM embedding guide](https://developers.google.com/edge/litert-lm/embedding_models): runtime integration and prefix convention; compare against model-card behavior.
- [Google's October 6 announcement](https://developers.googleblog.com/en/google-ai-edge-with-embeddinggemma-2/): Gallery showcases and runtime options; announcement numbers are not Keeps measurements.
- [Pinned Gallery source](https://github.com/google-ai-edge/gallery/tree/3210720d3f808f51bae52798435d0c590ddc05bf) and [license](https://github.com/google-ai-edge/gallery/blob/3210720d3f808f51bae52798435d0c590ddc05bf/LICENSE): file-level links and reuse candidates are in section 5.
- [ML Kit OCR Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android): supported variants/scripts and installation tradeoffs.

Permission, background work, target API, native compatibility, signing, privacy, testing, and publishing sources are linked beside the corresponding requirements above. Re-check changing rules and upstream revisions at their implementation/release milestones.
