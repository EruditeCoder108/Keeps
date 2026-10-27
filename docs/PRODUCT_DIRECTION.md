# Keeps consumer product contract

17 October 2026. This is the screen and behavior baseline for the consumer rewrite. [ROADMAP](../ROADMAP.md) remains authoritative for scope, task state and acceptance gates. This document defines intended behavior; it is not evidence that the screens or background guarantees have shipped.

18 October implementation update: [consumer interface slice](CONSUMER_UI_SLICE.md) implements Settings destinations, Search library, bulk individual exclusion/long-video review and a visual-system revision. The [overnight slice](OVERNIGHT_PREPARATION_SLICE.md) adds explicit charging/screen-off automation, persistent folder rules, notification Pause and session progress. The dated audit below is historical; full-night evidence, learned estimates and launch acceptance remain open.

25 October control audit update: [unified pause/notification state, retained-queue resume, explicit end/new scope and adaptive pacing](PREPARATION_CONTROL_AUDIT.md) now pass laptop regressions. Fresh optimized phone/lifecycle/comfort acceptance is still required; the target overnight guarantee is not marked complete.

## Product and boundaries

Keeps is an everyday Android file manager and gallery with optional private content search. Browse, filenames, organization and viewers work before search preparation. Search can find photos, supported document content, sounds and multiple moments inside videos, within one video or across the prepared library. Search results are candidates; do not advertise perfect action recognition or exact transcription.

The main preparation flow includes all supported content within access and scope the person explicitly approves, then lets them exclude a few unwanted files, albums/folders or types. A selected-items-only flow remains available. Access permission never implies consent to AI preparation. Do not require moving originals into a special album. Laptop/computer preparation is future work and has no launch setup, buttons or dependency. Pricing remains undecided; commercial viability does not add accounts, ads or subscriptions to this release.

## Screen ownership and button inventory

| Surface | Main content and controls | Secondary/contextual controls |
|---|---|---|
| Browse | Categories, locations, pinned folders, recent files; search names, sort/filter, select | New folder in a writable folder; share, rename, copy, move, details, favorite, pin, capability-aware trash; original deletion only with explicit consequences |
| Gallery | Photos / Albums; All / Photos / Videos / Screenshots, favorites, sort/density, select | Share, favorite, details, supported file operations; Exclude from AI search / Include in AI search in selection and item menus |
| Search | One query, type/location filters, real file/content results, multiple video/audio moments | Search this video, playback at a match, Save moment; coverage/status card opens Search library; filenames remain usable without the model |
| Search library | Searchable coverage, current status, time range when calibrated, Pause / Resume | Content types and scope, Review exclusions, Review long videos, Prepare automatically, Prepare now, failed/unsupported items and storage link |
| Settings | Search library status row, Device access, Appearance, Storage, Privacy & help | Advanced contains manual model import and diagnostics; source notices under Privacy & help |
| File activity | Operation progress and recovery, reachable from Browse and its active-operation banner | Cancel, retry, review verified destination and per-file outcomes; no operation journal in the main Settings scroll |
| Viewers | Content, playback/navigation, share, favorite, details and return to source position | Video search and matched intervals, Save moment; contextual supported operations |

Keep Browse / Gallery / Search as the three bottom tabs; Settings stays in the top bar. Search library is a dedicated full screen reachable from Search and the single Settings row. Trash and file activity belong to ordinary file use. Saved moments remain reachable from Search/viewers without another primary tab. Do not add an AI chat page.

Settings is a short list of destinations. Model setup, type chips, batch counts, pace controls, raw memory readings, refresh buttons and operation rows must not all be expanded there. Do not show search-history controls until history exists. All visible controls must perform a supported action or explain why it is unavailable.

## Setup and exclusions

1. Request ordinary access in context; discover available metadata while browsing remains usable.
2. Offer optional private search: explain one-time preparation, model/storage cost and on-device processing. Model-not-ready and model-ready flows differ. Consumer download/validation needs completion under M4-04; the present browser-download/manual-import flow is transitional.
3. Review approved scope. Default review is all supported accessible content with type toggles, optional chosen locations/items, exclusions and counts. No inference starts before confirmation.
4. Offer automatic preparation while charging, plus explicit Prepare now. Explain that leaving the app does not mean Pause, Android can delay work, and one night is not a guaranteed deadline.
5. Return to the library with an unobtrusive, actionable progress card.

Review exclusions supports multiselect, filename search, type/location filters and a dedicated Excluded view with Include again. Review long videos sorts by duration and can also sort by size. Show actual duration/size when known; bytes alone do not predict preparation cost. Never silently exclude a file based on size or duration. Album/folder exclusion requires a persistent location rule, not just a one-time update of today's members. New matching files obey that rule; explicit exclusions win over broader inclusion.

Excluding from AI search removes associated derived text/vectors and prevents queued or late work from republishing them. It preserves originals, filename search, browsing and favorites. Including again does not instantly restore deleted vectors; the file needs preparation. Removing from an exclusion list is not permission to begin an otherwise paused session. Scope and exclusions survive discovery, restart and compatible model updates.

Real folder albums remain real locations. Virtual collections/subalbums are P1; do not fake them with duplicate copies or move originals as a prerequisite for AI search. Arbitrary albums inside another gallery app are not assumed to be accessible persistent sources.

## Preparation and battery behavior

One confirmed scope should continue automatically in bounded internal batches. Users must not press Prepare next 25 repeatedly to finish their library. Existing compatible completed work is skipped; media checkpoints survive suspension. New or changed items in the approved scope join subsequent automatic work after discovery. A separate selected-items run does not silently expand into whole-library preparation.

Prepare automatically defaults to charging-only, yields during interactive phone use and respects cooling, low-battery, low-memory and low-storage conditions. It is an opportunity-based mode, not a fixed midnight alarm or a promise to finish by morning. Prepare now permits intentional work on battery after showing that it can use substantial energy; cooling and resource guards remain mandatory. Manual Pause overrides automatic resume, charger reconnection and app restart. Disabling automation stops further automatic preparation while retaining completed search data. Pause/stop may wait for the current non-interruptible native call; do not show a completed pause before it takes effect.

Use one adaptive consumer policy. Internal batch size, headroom thresholds and thread count are implementation details. Do not offer a faster mode that promises equal battery/heat cost. Existing Gentle/Balanced/25/100 controls are transitional, not the final main screen.

During user-started preparation, provide appropriate visible background status and a Pause action using the platform mechanism validated for this work. Notifications and scheduling do not bypass Android quotas or force-stop. Ask for notification permission in context; denied permission still gets a clear in-app status. Do not require unrestricted battery settings at first launch or silently bypass system limits.

| Situation | Consumer contract and limits |
|---|---|
| Home / another app | Work may continue or defer under the selected policy; manual Pause is the reliable stop control |
| Screen locked | Automatic work can proceed under charging/resource policy when Android schedules it; needs real-device verification |
| Removed from Recents | Do not treat as manual Pause; normal Android scheduling can continue, but vendor behavior must be tested |
| Process killed / reboot | Retain compatible completed work and pending scope; reschedule eligible work, respecting manual Pause and access/unlock requirements |
| Android Force stop | Cannot promise execution; require user reopening/interacting with Keeps before recovery |
| Charging removed / cooling / low battery or storage | Save progress and wait with the real reason; do not claim processing while waiting |
| Library prepared | Stop heavy preparation; future discovery/preparation follows the approved automation policy, without a permanent busy loop |

States include Not enabled, Model needed, Ready to prepare, Preparing, Waiting for charging, Waiting for phone to be idle, Cooling down, Paused by you, Delayed by Android, Low storage, Partial, Complete and Needs attention. Only show reasons actually known. A queued row alone cannot identify an Android quota delay. Show last progress time and completed coverage when exact reasons are unavailable. Do not replace determinate progress with a permanent spinning bar.

Battery reporting separates heavy initial preparation, incremental maintenance and occasional queries. Do not promise zero drain or a universal battery percentage. Settings can link to Android app battery information; diagnostics can show observed session timing and battery readings with their limitations. Learned time ranges use per-type photo/page/chunk throughput and audio/video duration, including rest and known waits; start with Calculating estimate. Show coverage even when ETA is unavailable. Storage separates model, search data and cache, with original-file consequences explicit. No accurate per-app energy attribution is inferred from a simple before/after battery-level sample.

## Current implementation audit, not a new device test

Inspected IndexWorker, PreparationController, LibraryRepository, LibraryDatabase, PreparationProgress, KeepsApp and AndroidManifest on 17 October:

- Individual exclusion persists, clears derived chunks, preserves ordinary catalog fields and is checked by queued work and late commit guards. Bulk review, persistent location exclusion rules and the dedicated exclusion screen are still missing.
- Preparation uses a unique persistent WorkManager queue, battery/storage constraints, optional charging constraint, persisted manual pause, short passes, media checkpoints and cooldown/rest checks. Charging-only is currently false by default; the default manual selection is 25 files. No automatic discovery-to-preparation subscription, consumer automatic mode or learned ETA is implemented.
- Leaving an activity does not cancel this queue. This architecture supports deferred work outside the visible app, but the latest overnight/locked-screen/reboot completion is unverified. Do not advertise guaranteed overnight completion from source inspection.
- The preparation worker does not currently call setForeground and the app does not declare a preparation notification permission. A consumer background status flow is not implemented. Adding foreground work alone would not prove full-night execution on Android 16.
- Settings currently expands model setup, scope, pacing, access, memory and operation recovery together. The above information architecture has not yet replaced that UI.
- No battery-drain percentage or full-library ETA was measured for this decision pass. No installation, data migration or source mutation was performed.

## Implementation order and acceptance

1. M1-05: split Settings destinations and build Search library with real coverage/status; move diagnostics and file activity to their proper surfaces. Preserve current working controls during the transition.
2. M1-05/M1-04/M1-02: bulk exclude/include, long-video review and persistent folder scope, including refresh/new-file behavior, late-write guards and migration tests. Do not hide files as an exclusion side effect.
3. M5-02: one-session automatic continuation, charging default, activity priority, visible status, real manual pause and accurate resource-wait reasons. Prove close/lock, unplug/replug, cooldown, process death, reboot and force-stop recovery without repeated compatible completed work. Background restriction policy follows platform requirements.
4. M0-05/M5-01: representative per-type throughput, time ranges, storage/RAM, sustained battery/temperature and growing-library query cost. Compare short generated checks with separately consented real-library trials; do not extrapolate fixture throughput as a promise.
5. M4-04/M3-05/M4-03: consumer model delivery and representative retrieval quality. Video moments remain P0. Computer assistance and virtual collections do not block this phone release.

No launch gate becomes DONE from this specification. Preserve existing working integrations, originals, source grants, favorites, exclusions and compatible prepared data throughout the rewrite.

## Primary platform references

- [WorkManager persistence and scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Long-running worker limits, including Android 16 quotas](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Android stopped-package behavior](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state)
- [Thermal API and device-dependent signals](https://developer.android.com/games/optimize/adpf/thermal)
