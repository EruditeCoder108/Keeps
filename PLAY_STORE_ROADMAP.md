# Keeps: roadmap from working prototype to Google Play

> **Historical planning document.** The [master roadmap](ROADMAP.md) supersedes this document's scope, priority, and delivery order from 7 October 2026. Track new work there. In particular, video moment search is required for launch and ZIP tools move to P1. This file preserves the original research and estimate.

**Planning date:** 7 October 2026

**Status:** proposed product and delivery plan; the production features below are not yet implemented.

**Working name:** Keeps; confirm the final name and publisher identity before the first Play upload.

Build **a dependable Android file manager with a polished gallery and private content search**. Users should be able to organize files normally, then describe what they remember to find a photo, document, recording, or video moment. The interface should open directly into their library. Indexing statistics and prototype feature banners belong in development tools or Settings.

My recommendation is **Nothing Gallery's visual restraint, Files by Google's browsing clarity, and Apple Files' organization patterns**, expressed as an original Android interface. The detailed [design specification](DESIGN_SPEC.md) and [generated concept board](docs/design/keeps-concept-v1.png) accompany this roadmap.

## 1. What we have, and what “finished” means

The current native Kotlin/Compose app has a real local EmbeddingGemma 2 integration, a persistent catalog, filename/text matching, image/PDF/sample-frame indexing, short WAV embeddings, favorites, background jobs, and working previews. The recorded verification is **11 JVM tests and 6 instrumentation tests on one CMF Phone 2 Pro**, using six synthetic files. That proves the integration, not everyday search accuracy or production reliability. See [device evidence](DEVICE_TESTING.md) and [current boundaries](README.md#current-boundaries).

Important gaps: gallery/folder discovery, safe file mutations, content-change detection, compressed audio, OCR/PDF text, large-library performance, model setup, accessibility, release packaging, and store preparation. Video currently samples individual frames; it does not demonstrate motion recognition.

**The 1.0 finish line:**

- A person can browse, copy, move, rename, share, delete safely, and restore supported files without understanding the index.
- The gallery feels fast and complete: photos, videos, albums, favorites, sorting, selection, and dependable viewers.
- Content search covers the tested image, document, audio, and video formats, returning a relevant page or time interval.
- Files remain usable while AI is absent, downloading, paused, or unsupported on a phone.
- Permission choices, exclusions, storage consumption, and deletion of derived data are understandable.
- A signed release has passed representative phone testing, permission review, beta feedback, and Play's publishing checks.
- The store listing describes capabilities that the release actually delivers.

**Motion-specific video retrieval is a separate quality gate.** It is a valuable goal; a still-frame hit is insufficient evidence. If temporal testing fails, 1.0 can ship visual/audio moment search with accurate wording while motion work continues in 1.1.

## 2. Small interface study and the recommendation

These are product references, not designs to copy verbatim. The observations use official descriptions and available official interface imagery, not hands-on testing of every competitor.

| Reference | Useful pattern | What I would take into Keeps |
|---|---|---|
| [Files by Google](https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.files&hl=en), [official usage guide](https://support.google.com/files/answer/9848742?hl=en) | Familiar categories, straightforward file search, ordinary organization/sharing, and media viewing | Clear Browse entry, useful file rows, categories, recent items, and Android's existing sharing tools |
| [Apple Files](https://support.apple.com/guide/iphone/modify-files-and-folders-iphc61044c11/ios) | Locations/favorites, view and sort controls, folder organization, and context actions | Pinned folders, consistent list/grid switching, remembered sorting, a clear destination picker |
| [Samsung My Files](https://www.samsung.com/us/support/answer/ANS10002531/) | Categories, multi-selection, and explicit Copy/Move followed by choosing a destination | Visible selection mode and a “Move here”/“Copy here” flow; removable storage when accessible |
| [Nothing Gallery / OS 5 announcement](https://nothing.community/d/61730-nothing-os-50-new-features-explained) | Remembered photo sorting, combined filters, album prioritization, and transparent details | Content-first gallery, Photos/Albums switching, restrained floating controls, deliberate spacing |

**Which UI do I like most for this app?** Nothing Gallery for the visual direction. For the daily workflow, I would use Files by Google's clarity and Apple's folder organization. A file manager needs readable filenames, visible destinations, and predictable actions as much as an attractive photo grid.

### What “latest Nothing” currently means

Nothing's current official page calls **OS 5.0 an open beta**, with Phone (3)'s general release listed for mid-October. It highlights Geist typography, revised icons/layouts, smoother motion, and transparent elements. These are current design references, not a claim that every Nothing phone already has the finished release. [Nothing OS 5.0](https://us.nothing.tech/nothing-os)

The Gallery reference image shows a compact translucent navigation capsule over an image grid. That is a useful inspiration for separating controls from media. We should develop our own shapes, icons, colors, and brand, and avoid copying Nothing's identity.

### A more precise moat

**Local content search alone is not unique.** Files by Google's Smart Search already describes on-device scanning for image/PDF text, image objects/locations, and audio/video metadata. [Google's Smart Search help](https://support.google.com/files/answer/9765316?hl=en)

Our product hypothesis is a better combination of:

1. One private search experience across files, photos, recordings, and videos.
2. Results that open the useful PDF page or media moment.
3. Explicit choices about indexed folders, excluded content, and AI storage.
4. A polished file manager that remains useful before AI setup.
5. Reliable relevance on real libraries, including tested English/Hindi queries.

An open model is available to other developers. The durable advantage must come from retrieval quality, source lifecycle handling, comfortable UX, and trust. Validate that advantage with users rather than describing it as already proven.

## 3. Product layout and everyday behavior

Use **three primary destinations**:

| Destination | Main purpose | Contents |
|---|---|---|
| **Browse** | Find and organize files | Categories, pinned folders, recent files, storage locations; Favorites and Trash as secondary destinations |
| **Gallery** | Enjoy and manage media | Photos/Albums, date grouping, filters, multiselect, image/video viewers |
| **Search** | Find by name or remembered content | One query field, scope/type/date filters, useful excerpts, PDF pages and media timestamps |

Settings is a top-bar action. Index/model management lives there and in a small contextual Search status. A separate Saved tab and a feature-promoting home page are unnecessary. Remember the last Browse/Gallery destination, folder, view mode, and scroll position.

The full screen flows, typography, materials, interaction states, and accessibility rules are in [DESIGN_SPEC.md](DESIGN_SPEC.md).

## 4. Launch features: normal files and gallery

**P0 = required for 1.0. P1 = first useful updates. P2 = later, only after evidence of demand.** Relative effort is an initial estimate: S = small, M = moderate, L = substantial. An attractive menu is not evidence that the underlying action is safe.

| Capability | Priority / effort | Release behavior |
|---|---|---|
| Browse shared storage and chosen folders | P0 / L | Local locations, breadcrumbs, categories, Downloads where access allows, clear read-only/access states |
| Filename search; sort and filter | P0 / M | Name, type, modified date, size; folder scope or all accessible locations; never wait for AI |
| List/grid, thumbnails, recent items | P0 / M | Remember view/sort per location; paginate instead of loading the whole library |
| Create folder; rename file/folder | P0 / M | Validate names, preserve extensions appropriately, detect collisions, refresh catalog identity |
| Copy, move, destination picker | P0 / L | Single/batch operations, conflict choices, progress/cancel, free-space checks, recover after interruption |
| Select multiple; share; Open with | P0 / M | Consistent selection toolbar; use Android share/open intents and temporary URI grants |
| Delete, Trash, restore | P0 / L | Recoverable removal where supported; explicit permanent-delete flow otherwise; no false Undo |
| Favorites and pinned folders | P0 / S–M | Same state across Browse, Gallery, and Search; moves/reconciliation preserve state where identity can be resolved |
| File details and storage information | P0 / M | Real size, type, location, dates; clearly separate originals from AI/cache storage; no fabricated cleanup recommendations |
| SD card / USB storage | P0 where supported / M | Appear only when mounted and accessible; handle removal mid-operation |
| Basic ZIP create/extract | P0 / M | Destination selection, progress, collisions, bounded extraction, path traversal and decompression-bomb protection |
| Photos and videos timeline | P0 / M | Captured date with documented fallback; Recently added option; remembered filters and grid density |
| Albums | P0 / M | Device folders and favorite/pinned albums; disclose that moving between folder albums moves real files |
| View images/PDF/text/audio/video | P0 / M–L | Zoom/swipe, page navigation, seek/speed, favorites/share/details; Open with for unsupported formats |
| Simple crop/rotate and save a copy | P1 / M | Useful gallery addition after file reliability; non-destructive by default |
| Virtual collections, tags, saved searches | P1 / M | Organize references without silently moving originals; distinct from folder albums |
| Duplicate/large-file review | P1 / M–L | Manual review with verified duplicates; conservative suggested cleanup, no automatic deletion |
| DOCX/other office text extraction | P1 / M–L | Browse/Open with in 1.0; semantic indexing only after parsers and format limits are validated |
| Scan documents, PDF annotation | P1 / M–L | Separate document tools, not prerequisites for a dependable file manager |
| Speech transcripts and exact spoken-word search | P1 / L | Separate local ASR model/pipeline, storage and language evaluation |
| Encrypted vault, face grouping, cloud sync, SMB/FTP | P2 / L | Each creates a substantial privacy, security, or maintenance surface; require a separate design and demand evidence |

**What “all essential features” means:** launch the common file and gallery tasks above, not every specialty tool found in mature apps. Cloud accounts, vaults, network servers, full editors, and automatic cleanup should not delay safe core operations.

### Rules that keep file management trustworthy

- Check each source's actual capabilities before offering actions. A read-only provider must not show a working-looking Move or Delete.
- Distinguish **remove from content search** from **delete the original**. Clearing the index never deletes user files.
- Treat a cross-location move as copy → verify destination → delete source, with a persistent operation record. If copying or verification fails, keep the source. Never announce success for a partial batch.
- Do not assume an arbitrary provider can roll back. Use clear per-file results and truthful recovery options.
- Use system media trash/consent where appropriate. For ordinary local files, design a recoverable trash on the source volume; validate retention, capacity, restore collisions, and app uninstall behavior. Providers without safe trash must say “Delete permanently” and ask confirmation.
- Propose 30-day retention for app-managed trash, clearly stated. System/provider retention may differ. Restoring after expiry is not promised.
- A location disappearing must invalidate access promptly. Removing access/index scope removes derived text/vectors/thumbnails as applicable, without deleting originals.
- A model upgrade should rebuild only incompatible derived data, retaining folder settings, filenames, and favorites.

## 5. AI search: launch contract and research gates

Keep EmbeddingGemma 2 as the initial inference integration. Its model card describes a shared 768-dimensional multimodal space and an Apache-2.0 license. The app must validate the actual converted pack and runtime rather than equating upstream benchmark scores with phone quality. [Official model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2)

| Content | 1.0 target | Evidence required |
|---|---|---|
| Images | Visual descriptions plus OCR for supported scripts | Representative personal-style photos/screenshots; orientation, large images, mixed-language text |
| Plain text / Markdown | Exact words/numbers and semantic concepts | Encoding handling, bounded overlapping chunks, snippets tied to the original |
| PDFs | Selectable text plus page images/OCR where needed | Relevant page opens correctly; large/encrypted/damaged documents handled honestly |
| Audio | Common WAV, MP3, M4A/AAC formats supported by the decoder; resampling and overlapping windows | More than one recording, varied sounds/speech, long-file resumption, meaningful top results and seek positions |
| Video | Scene/frame and soundtrack windows with timestamp results | Varied codecs/durations; several candidate moments per video; useful matched intervals |
| Motion queries | Ordered temporal representation experiment | Action contrast tests where a still frame cannot solve the query; publish claims only if this passes |
| Other files | Filename/metadata browse/search and Open with | Unsupported semantic formats stay visible; no misleading “everything indexed” state |

Playback format support and semantic indexing support are separate. Declare unsupported codecs, encrypted PDFs, OCR script coverage, and partial preparation in context.

An embedding model is not a transcription engine. Sound/meaning search can use audio embeddings; verbatim spoken-word search needs a separately tested transcript channel. A PDF/image embedding likewise cannot replace exact OCR for reference numbers.

### Retrieval and indexing work

1. Preserve filename/exact-text retrieval as a fast independent channel. Use an indexed Unicode-aware lexical implementation with suitable tokenization; prove Hindi and mixed-script behavior on Android.
2. Add selectable PDF text and on-device OCR. A practical starting point is bundled Latin + Devanagari ML Kit OCR; measure added APK size and review SDK behavior. Bundled and downloaded variants have different setup behavior. [ML Kit Android OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
3. Decode/resample recordings and video audio. Store source page/time coordinates for every chunk.
4. Compare query/document prefix conventions using the real phone pack; pin the winning prompt and preprocessing versions.
5. Deduplicate overlapping hits but let users open several useful moments from one long source.
6. Implement per-chunk checkpoints, source-version checks, resumable work, and deterministic removal of stale data.
7. Calibrate exact-match priority, fusion, and no-match handling on held-out queries. Similarity is not a confidence percentage.
8. Keep the exact cosine implementation as a correctness oracle. Replace full-vector loading with a measured store/retrieval strategy only when scale demands it.
9. Evaluate 256/512-dimensional embeddings and quantization against full 768-dimensional results before choosing them; any change gets a new index namespace and relevance thresholds.

For scale planning, **100,000 × 768 float32 values = 307.2 MB of raw vectors**, before metadata and search structures. At 256 dimensions that component is 102.4 MB. Those are storage calculations, not measured memory savings. Do not load an entire large vector catalog on every search.

### Motion: an explicit experiment, not a promise

Use paired clips with similar settings and different actions: a person sitting versus standing up, walking versus running, opening versus closing a door. Annotate matching time intervals, separate development/test clips, and compare single-frame, ordered-frame/window, audio-only, and combined approaches.

Confirm that the selected LiteRT API/pack preserves a useful temporal representation; concatenating still-frame embeddings does not automatically do this. Require a measurable improvement on held-out action queries and acceptable indexing cost. If it fails, document the failure and keep motion claims out of the listing.

## 6. Model setup, privacy, and phone resources

Manual model import is suitable for the prototype, not the main consumer setup.

**Recommended consumer path:** the file manager works immediately. Enabling Content search offers an optional verified download, with its size, expected storage, Wi-Fi preference, cancel/resume, and an offline import alternative under advanced Settings.

The current tested pack is **484,622,336 bytes, approximately 485 MB**. Recorded inference caches used about **367 MiB** on the test phone. Together that is roughly **870 MB before vectors, thumbnails, app installation, and temporary downloads**. The recorded memory sample during initial indexing was around 1.1 GiB PSS; this is not a peak or a guarantee. [Measured prototype evidence](DEVICE_TESTING.md)

Release work must:

- Stream into a private staging file; verify an authenticated manifest and digest before activating the model; resume safely and reject corrupt packs.
- Calculate free-space requirements for model + expected caches/index + staging + update overlap + margin. Display the estimate, then measure the actual usage.
- Pin a model revision and preprocessing namespace; avoid surprise automatic model changes or mass reindexing.
- Provide Pause, charging-only indexing, excluded folders/types, cache limits, and separate “Delete search data” / “Remove AI model” actions.
- Prioritize foreground browsing/search; serialize memory-heavy inference; unload idle resources and respond to thermal/low-memory conditions.
- Measure feasibility on 4 GB and 6 GB phones before declaring AI support. If AI is unsuitable, ordinary file tasks still work; explain the limitation.
- Keep CPU as the compatibility baseline. Ship GPU/NPU options only after equivalent results and stability tests.
- Schedule bounded background work with checkpoints. Android 16 long-running WorkManager jobs can exhaust job quotas; foreground operation is not an unrestricted indexing loophole. [Android background-work guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

**Honest privacy wording:** “Content search runs on your device. Internet is needed to download the optional model.” After setup, local indexing/search must work in airplane mode. A cloud document provider or user-selected sharing destination may use its own network connection.

The prototype has no Internet permission. A consumer downloader changes that fact: audit the release manifest, destinations, logs, and SDKs again. Do not keep claiming “no network permission.”

Keep files, OCR text, embeddings, and filenames out of application logs, analytics, crash payloads, and download requests. Derived indexes are sensitive data; use private app storage and retain explicit backup exclusions. No account or advertising SDK is recommended for 1.0.

Bandwidth is a real running cost even with local inference: 1,000 complete downloads of this pack are approximately **485 GB**, excluding retries and updates. Choose trusted hosting and estimate costs before enabling broad rollout. Do not promise unlimited free model delivery without a budget.

## 7. Storage permissions: decide early

**Recommended product intent:** a genuine file manager plus gallery, with shared-storage management as a core function. Build privacy-friendly MediaStore and chosen-folder access first; add an all-files backend only where the actual file-manager requirement cannot be met otherwise.

| Access mode | Purpose | Important limitation |
|---|---|---|
| MediaStore / media permission | Gallery discovery, media metadata and operations | Selected-media grants and write/trash consent vary by Android version |
| SAF file/folder grants | User-chosen documents, folders, removable/provider locations | Provider capabilities vary; a folder grant is not whole-phone access |
| All-files special access, if justified and approved | General shared-storage navigation and management | Restricted Play permission; does not unlock other apps' private files |

On Android 11+, SAF tree selection cannot grant internal-storage root or the Download root, and excludes Android/data and Android/obb. All-files access still cannot read another app's private directories. These boundaries need honest location labels, not permission workarounds. [SAF restrictions and capabilities](https://developer.android.com/training/data-storage/shared/documents-files), [all-files platform limits](https://developer.android.com/training/data-storage/manage-all-files)

Google Play permits qualifying file managers to request MANAGE_EXTERNAL_STORAGE, subject to declaration, justification, and approval; a media-only gallery or manual picker use is insufficient by itself. Demonstrate the specific shared-storage workflow that SAF/MediaStore cannot adequately provide. [All-files access policy](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en)

Persistent broad photo/video access has a separate review. A gallery/media manager may qualify, but approval is not automatic; otherwise use selected media/pickers. Keep permissions, the store description, and all active track artifacts consistent. [Photo/video permissions policy](https://support.google.com/googleplay/android-developer/answer/14115180?hl=en)

**M0 decision:** document the permission strategy, fallback scope, and review evidence. A permission rejection requires resolving the issue or explicitly revising the product to a chosen-library app; do not quietly ship a limited picker while advertising complete phone management.

Request access at the relevant action with a short explanation. Declining broad access should leave usable chosen locations. Browsing access is separate from consent to content indexing: allow exclusions before background processing starts. Do not add accessibility-service bypasses or broad installed-app visibility merely for Open with.

## 8. Engineering structure: keep the proven core, build around it

Retain Kotlin, Compose, Room, WorkManager, Media3, Coil, and the isolated LiteRT adapter. Refactor by responsibility; a wholesale rewrite of the working inference/ranking code is not justified.

| Boundary | Responsibility |
|---|---|
| Source adapters | MediaStore, SAF, approved shared-storage paths; stable identity, capabilities, access and content-version checks |
| Catalog | File/folder metadata, favorites, observed locations, source versions; independent of AI availability |
| File operations | Durable operation journal, conflicts, verified copy/move, trash/restore, reconciliation |
| Extractors | Text/PDF/OCR/image/audio/video preprocessing with bounded resources and page/time provenance |
| Index coordinator | Scope/exclusions, checkpoints, work budgets, retry/pause/cancel, source-change invalidation |
| Retrieval | Lexical + semantic fusion, filters, candidate limits, source grouping, no-match rules |
| Inference | Model lifecycle, verified setup, one controlled native execution boundary, tested backend configurations |
| UI | Shared components and state models for Browse/Gallery/Search/viewers; no storage or inference work on the UI thread |

Add modules only when a dependency boundary benefits; avoid an unnecessary module explosion. Room schema migrations and operation recovery are part of each data-changing feature, not cleanup work at the end.

A changed/deleted file must never keep presenting an old content result as current. Reconcile on observed changes, app return, mounted-volume changes, and bounded background passes; do not rely on one perfect filesystem watcher for every provider. Validate source identity/version again before publishing extracted chunks.

### Reuse rather than reinvent

- Use existing Android sharing/opening and media APIs.
- Use the current proven Media3 and LiteRT integrations; upgrade only with release notes and regression checks.
- Evaluate a compatible, maintained PDF text parser in a small format/license/security spike rather than implementing a parser from scratch.
- Keep archive support narrow and test it against malicious/corrupt input.
- Use standard Compose semantics/components beneath the custom theme.
- Track licenses for the exact resolved versions, converted model pack, fonts, icons, and parsers. Packaging exclusions are not a substitute for required notices.

## 9. Milestones, order, and a realistic schedule

**Planning estimate: 18–24 engineering weeks** from the prototype for the full P0 scope, assuming one focused Android engineer with AI assistance, regular design/product review, and access to several test phones and real testers. Budget roughly **5–7 calendar months**, allowing additional testing/review delays. This is a scope estimate, not a promised release date; revise it after M0/M1.

The upper-end sequence below is deliberately gate-based. UI work starts early, and store/account preparation runs alongside engineering.

| Milestone | Planning window | Concrete deliverable | Exit gate |
|---|---|---|---|
| **M0 — Product and release decisions** | Week 1 | 1.0 contract, supported-device/format matrix, permission rationale, benchmark plan, name/package/account decisions | No ambiguity about full file-manager access, AI claims, or first-launch scope |
| **M1 — Real product interface** | Weeks 2–3 | Tokens/components; working Browse/Gallery/Search shell using real catalog data; folder, selection, search-result, viewer, and setup states | Prototype promotional UI replaced; reviewable phone flows; basic accessibility works |
| **M2 — File manager foundation** | Weeks 4–7 | Source adapters, location discovery, copy/move/rename/folders, journal, trash/restore, ZIP, access restoration | Critical operation and interruption tests preserve originals and truthful state |
| **M3 — Gallery and viewers** | Weeks 8–9 | Photos/Albums, sorting/filtering, paging, favorites, selection, dependable media/PDF viewers | Fast gallery on representative media; consistent source state across all three tabs |
| **M4 — Useful content search** | Weeks 10–14 | Model setup, PDF text/OCR, compressed audio/video soundtrack windows, relevance calibration, temporal experiment, chunk checkpoints | Every advertised modality passes its quality contract; unsupported/partial content is explicit |
| **M5 — Scale, battery, and polish** | Weeks 15–18 | Large-library retrieval, unload/thermal policies, indexing budgets, remaining UI/accessibility/localization polish | Performance/resource gates pass on supported phones; no foreground freezes |
| **M6 — Release candidate and internal testing** | Weeks 19–21 | Signed/minified AAB, native compatibility evidence, upgrade tests, privacy/notices/listing/forms, internal Play install | Release behavior matches documentation; critical bugs fixed; submission package complete |
| **M7 — Closed beta and first public release** | Weeks 22–24+, review dependent | Real-user feedback, eligibility evidence, permission/production reviews, first launch runbook | Beta exit criteria pass and required approvals exist; publish only after final review |

**Dependencies:** M2 follows the M0 storage decision. M4 builds on source versions and chunk identity. M5 needs realistic M2–M4 data. M6 tests the actual release artifact, not only debug. M7's calendar depends on the developer account and reviews.

If the full schedule proves too long, reduce specialty scope first: editing, collections, cleanup, office indexing, vaults, network access, and motion-specific claims. Preserve professional design, safe basic file operations, gallery reliability, and the tested multimodal search contract. Any reduction to P0 must be explicit.

### The next implementation slice

The next build should make the app look and behave like a usable utility:

1. Establish the three-tab shell and theme/component tokens.
2. Replace the promotional home with categories, pinned folders, and real recent files.
3. Build the Photos/Albums gallery and consistent viewers.
4. Build mixed search results with page/time labels and scoped filters.
5. Add selection/destination/conflict/trash flows against a testable source abstraction.
6. Move diagnostics to debug tools; build ordinary-user Settings and model/setup states.
7. Complete one real path end-to-end: choose a location → browse → copy a test file → search it → preview → trash → restore.

This first slice is a reviewable product milestone, not an attempt to finish every backend feature at once.

## 10. Quality gates: evidence needed before public release

These are **proposed acceptance targets**, not current results or Google's requirements. Freeze the final definitions at M0 after initial measurement; record any changed target and reason.

### Search evaluation

Create a consented or synthetic-but-realistic labeled corpus of roughly **500–1,000 varied sources** and at least **250 held-out queries**, with separate sets for tuning and final evaluation. Include similar-looking distractors, text/number lookups, multilingual queries, several audio sources, long videos, page/moment annotations, and at least 40 queries with no relevant result.

Report per modality and language slice, not just one average.

| Check | Proposed beta target |
|---|---|
| Unique exact filenames/reference strings actually present in the indexed text | At least 99% correct first result on the supported test set |
| Image/document semantic queries | Relevant result within top 5 for at least 85% of held-out queries |
| Audio/video semantic queries | Relevant source within top 5 for at least 75%; otherwise narrow or improve the claim |
| No-match set | At least 90% avoid presenting an unrelated result as a strong match |
| PDF provenance | All evaluated result links open the annotated page |
| Audio/video provenance | Matching annotated intervals overlap the returned window; playback seeks within the defined viewer tolerance |
| Permissions/exclusions | Zero hits from revoked, excluded, deleted, or incompatible-index sources after reconciliation |
| Motion | Improvement over still-frame baseline on held-out contrast clips; no claim based solely on frame appearance |

Compare with the prototype's lexical/exact-vector baseline. If a new approximate index or smaller embedding loses useful results, its speed alone is not sufficient.

### Performance and resource budgets

Use a **10,000-file / up-to-100,000-chunk** scale suite plus a larger metadata-only browsing suite. Measure release builds, cold/warm states, indexing concurrent with browsing, and low free storage. Do not equate file count with vector count.

Initial targets on a declared representative 6–8 GB phone:

- First usable Browse within about 1.5 seconds after a cold launch; the model does not block it.
- Filename/filter response p95 below 250 ms, measured separately from deliberate debounce.
- Warm semantic results p95 below 2 seconds; cold model/search below about 5 seconds with cancelable feedback.
- Smooth 60 Hz browsing: target fewer than 5% slow frames in defined scroll tests; fall back to solid surfaces if blur causes regressions.
- AI steady memory target below roughly 900 MiB PSS and transient target below roughly 1.3 GiB; investigate deviations and independently gate low-memory phones.
- No OOM, ANR, or repeated crash during a one-hour mixed browse/search/index session.
- Background indexing pauses under the agreed low-battery/thermal policy, resumes from checkpoints, and avoids repeated full-file work.
- Report throughput, battery consumption, temperature/thermal status, disk growth, and model load/unload behavior. Set the sustainable indexing budget from measurements, not a guessed “10,000 photos in five minutes.”

A 4 GB phone needs a separate support decision based on tests. CPU/GPU/NPU results cannot be mixed into a single flattering latency number.

### File safety and UI testing

Test local shared storage, MediaStore, a writable SAF provider, a read-only provider, and removable storage where available. Cover same-name destinations, case/Unicode names, large files, zero-byte files, full disk, source change during copying, process death, cancellation, grant revocation, card removal, partial batches, archive attacks, and restore collisions.

Required UI cases include empty libraries, permission denial/selected-media access, loading, offline setup, failed download, preparing search, unsupported file, no match, partial results, recovery, and oversized text. Verify TalkBack, 200% text, touch targets, contrast, landscape, predictive Back, keyboard behavior, and RTL. Launch with English; prioritize Hindi localization and benchmark its search behavior before making language claims.

Device coverage should include Android 12 through the latest shipping Android version, at least one Samsung, the current CMF/Nothing phone, a Pixel/reference device, low-memory hardware, and a **16 KB page-size environment**. The current phone test is not a substitute for this matrix.

## 11. Google Play preparation

Re-check these rules at submission time; the sources were checked for this plan on 7 October 2026.

### Account, identity, and release artifact

- Decide personal versus organization publisher, complete the applicable identity/contact/device verification, and maintain a support contact.
- Confirm the final application ID before the first upload. The current prototype uses `dev.keeps.library`; Play package identifiers are permanent. Separate development builds from consumer releases.
- Produce a signed **Android App Bundle**, configure Play App Signing, secure/back up the upload key, increment version codes, and preserve release mapping files/symbols. Test Play-delivered split APKs. [App setup and bundles](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)
- Add and test release shrinking/minification, dependency notices, actual per-device download size, and native ABI selection. The roughly 125 MB debug APK is not a release-size measurement.
- Keep the 485 MB model out of the base app for the recommended optional-download design. Confirm model redistribution rights and the exact artifact's notices.
- Validate fresh install and upgrade from a previous signed beta, including Room migrations and retained source access. A debug-signed installation cannot simply be upgraded by a differently signed store build; plan the development-to-beta transition without casually deleting user data.

As of this check, new phone apps/updates must target **Android 16 / API 36 or higher** from 31 August 2026. The prototype already targets 36, but any later release must re-check the then-current requirement. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)

Native dependencies make **16 KB memory-page compatibility a release gate**. Inspect every shipped native library and APK alignment and run inference/playback on a 16 KB environment; merely using a recent Gradle plugin does not prove compatibility. Current Android guidance also states that unsupported updates are blocked from 1 February 2027. [16 KB guidance](https://developer.android.com/guide/practices/page-sizes)

### Privacy, declarations, and listing

| Deliverable | What must be ready |
|---|---|
| Privacy policy | Public accessible HTML page, developer/app identity and contact, local processing, model-download behavior, providers/sharing, retention, exclusions and deletion controls; also accessible in-app |
| Data safety | Map real release data flows, including SDKs and model-host/CDN logs; answer from evidence, not the absence of a login |
| Permission declarations | Actual justification and reviewer walkthrough for any restricted all-files or broad photo/video permission |
| App content | Content rating, target audience, ads declaration, app-access instructions, category and contact details; applicable service declarations if the release uses them |
| Third-party notices | Runtime, model, fonts, icons, parsers, libraries and transitive dependencies |
| Review instructions | Repeatable steps with synthetic fixtures: grant a supported location, enable the model, run content search, open a page/moment; explain preparation time and offline behavior |
| Listing | Final name, concise description, real release screenshots, feature graphic, icon, supported formats/device limitations, truthful AI/download wording |

Locally processed data that is not transmitted is outside Play's collection definition, but the release still needs accurate declarations and a privacy policy. Audit downloads, diagnostics, SDKs, and hosting before deciding what the form says. [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [User Data/privacy requirements](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)

Prepare a **512 × 512 store icon**, **1024 × 500 feature graphic**, and at least the required screenshot set; aim for 5–7 useful phone screenshots from the actual release. The generated concept board is design material, not proof of shipped features or a store screenshot. [Official preview asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)

Potential listing line, subject to testing: **“Organize files and find photos, documents and media moments with private search.”** Explain the optional model download in the full description. Do not claim universal motion understanding, zero battery use, unrestricted phone access, or perfect accuracy.

### Testing tracks and first launch

1. Use internal testing for signed installs, delivery, compatibility, and migration checks.
2. Run closed beta with genuine testers performing ordinary tasks on their own consenting libraries.
3. For personal accounts created after **13 November 2023**, the current rule is at least **12 opted-in testers continuously for 14 days** before applying for production access. Recruit 15–20 to allow attrition; record feedback and fixes. Open testing is available after production access under that process. Other account situations should follow the actual Console requirements. [Personal-account testing rules](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
4. Complete required reviews and apply for production access when eligible. Passing the tester count is not automatic approval.
5. Choose initial countries/languages according to support and testing coverage; validate availability in Console. Set a release date only after approval and operational readiness.
6. Publish the first production release after the final release review. **Percentage-based staged rollout is for updates, not first publication.** Use beta/initial availability to limit first-launch risk. For subsequent updates, consider 5% → 20% → 50% → 100% with measured checks between steps. [Staged rollout policy](https://support.google.com/googleplay/android-developer/answer/6346149?hl=en)

The roadmap does not create a Play account, accept publisher agreements, upload an app, or publish a release; those are future execution steps after the concrete release package exists.

## 12. Beta exit, launch operations, and next versions

**Stop release for:** lost/corrupted originals, incorrect restore behavior, leaked/excluded content, unbounded resource use, unresolved native incompatibility, misleading AI claims, or critical permission/release failures. A polished appearance cannot compensate for these.

Beta exit evidence should include:

- Testers can complete copy/move/find/share/trash/restore without coaching; aim for at least 90% success in the agreed usability tasks.
- Search, resource, and source-lifecycle gates pass; no unresolved critical/high-severity defects.
- A stable closed-test period with real feedback and corrected defects; report sample sizes with any crash-free percentage.
- Store installs, model setup, offline use, and signed upgrades succeed.
- Privacy/declarations/listing match the release, and required reviews are complete.

For the first month, assign an owner to daily crash/ANR checks in Play Console, support triage, model-host availability/cost, permission regressions, and search feedback. Content uploads remain opt-in and explicit; support should usually start with redacted diagnostics. Local “Not what I meant” feedback can remain on-device unless the user chooses to share it.

Have a halt/forward-fix plan. Stopping an update rollout does not revert already-installed versions; maintain versioned migrations and avoid destructive remote switches.

**1.1:** refine ranking, broaden tested formats/languages, editing, collections/tags, duplicate review, and successful motion experiments. Add local speech transcription only with a measured resource/accuracy contract.

**1.2+:** consider advanced document tools and smarter organization based on use. Vaults, cloud synchronization, network drives, and subscriptions need separate product decisions.

Recommend a free, ad-free beta with no account. Decide production pricing after user value and model-delivery costs are understood; any paid digital-feature design needs its own billing implementation/review. Monetization should not distort basic file organization.

## 13. Decisions to settle at M0

These do not prevent the design and roadmap from being useful now:

| Decision | Recommended starting position |
|---|---|
| Product | File manager + gallery + private search |
| Identity | Keeps as working name; confirm name/package availability and originality before upload |
| Design | Original restrained Nothing-inspired theme; readable normal typography; limited glass |
| Platform | Keep Android 12+ initially; prioritize arm64 production and the existing CPU path; validate the supported-phone matrix |
| Access | Genuine file-manager scope; evidence-based restricted permission decision plus chosen-location fallback |
| AI setup | Optional verified download; basic file tasks available without it |
| Languages | English UI first, Hindi next; language-specific search tests before claims |
| Business | Free ad-free beta; production pricing undecided |
| Publisher | Actual personal/organization account and verification requirements must be confirmed |
| Launch region | Start where testing/support is credible; expand after measured results |

**The next step is M0 followed by M1: settle the launch/access contract, then turn the concept into the real everyday interface.** The working model pipeline stays as the foundation while file safety, search quality, and release readiness are built around it.
