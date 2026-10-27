# Consumer interface and exclusion review

18 October 2026. M1-05 and M5-05 ACTIVE. This is an implemented preview of the [consumer product contract](PRODUCT_DIRECTION.md), not a completed launch or overnight-preparation claim.

## Implemented

- Root Settings is six short destinations: Search library, Device access, Appearance, Storage, Privacy & help and Advanced. Model import and RAM controls live below Advanced; operation review/retry/recovery has a separate File activity screen reachable from Browse and its operation banner.
- Search library shows actual searchable coverage, selected types, exclusion count, live work/wait status, pause and explicit preparation choices. The charging-only and on-battery actions show a confirmation before queuing the remaining approved types. Bounded internal worker passes replace repeated consumer 25-file requests for this flow. Compatible completed work is still skipped. This does not subscribe future discoveries to automatic preparation or require idle/screen-off conditions.
- Exclusion review supports name filtering, multi-selection, an Excluded view and Include selected. Long-video review sorts by duration then size. Bulk exclusion is transactional, ignores missing/duplicate IDs and does not erase an already-included prepared index when reapplying Include. Late commit/checkpoint guards remain in place. No schema change or migration is required.
- Browse/Gallery selection menus also expose explicit bulk exclude/include. Originals, favorites, ordinary browsing and filename search are preserved. Including content does not automatically start preparation.
- Licensed Geist body typography; original canvas-drawn dot lettering only on main headings; quiet monochrome category surfaces, restrained red selection, a compact Photos/Albums segmented control and a slimmer translucent dock. The second visual pass removes the first pass's heavy dock shadow/black selection bubble. A pending gallery refresh now shows loading rather than an empty-library invitation.
- Dock/segment color transitions and Settings destination fade/short slide use Compose animation. No continuous decorative animation or per-photo backdrop blur is introduced. The dock uses surface translucency; it is not a live blurred view of media behind it. Reduced-motion and large-font coverage remain acceptance work.

The [official Nothing OS 5 reference](https://nothing.tech/nothing-os) informed Geist typography, clearer layouts, transparent surfaces and restrained motion. Nothing branding, proprietary fonts and system assets were not copied. The original 5x7 circle glyphs are Keeps code. Geist source is [Google Fonts](https://github.com/google/fonts/tree/main/ofl/geist), font SHA-256 `73894e0448cae90a92b6c2f8732b7bb9acb7b94c418bff559dad4a18e1de9659`; OFL notice is bundled and accessible beside the retained Gallery notice.

## Verification

- 37 existing JVM tests pass; no failures/errors. These cover core preparation/ranking/file primitives, not subjective visual quality.
- 14 selected Android tests pass, no failures/skips, 1.355 seconds: LibraryDatabaseTest (10), MigrationTest (2), PreparationHeatTest (2). The new bulk exclusion test covers idempotent Include, duplicate/missing IDs, favorite/original URI retention, chunk removal and rejection of a late result. Database tests use isolated in-memory/migration fixtures; they do not exclude personal files.
- First debug/test/lint build passes after correcting a missing Compose import. The final debug/test/lint + optimized build passes in 5m56s; lint reports 0 errors / 83 warnings (three new URI-to-KTX suggestions in SettingsHub, no suppression). Existing R8 Kotlin-metadata warnings remain.
- Data-preserving `install -r` updates were used. The connected installation showed 24 prepared items and an installed model before this slice; the new Search library also shows 24 and a ready model. This is observed coverage, not a byte-for-byte index audit. No personal-file preparation was started by these UI checks.
- Physical phone navigation covers short Settings, Search library, long-video review, Excluded empty state, nested Back, Light/Dark appearance and Gallery. The permission/model-disabled and 200% text/TalkBack matrices are not rerun. Light/dark captures with no personal file names are retained in the evidence folder; personal-gallery captures remain local under ignored `.work`.
- A debug mixed-navigation snapshot reports 4,299 rendered frames, current jank 20 (0.47%), legacy jank 463 (10.77%), p50 8 ms / p90 14 ms / p95 19 ms / p99 109 ms. This includes discovery/navigation and is not a controlled animation benchmark or before/after improvement claim. Recent AndroidRuntime output at that check contains no crash entry.
- Optimized mapping retains all seven literal LiteRT JNI names; four packaged ELF libraries pass static 16 KB alignment checks. Real 16 KB execution is not established by this phone (4 KB pages).

## Delivered preview and retained evidence

Installed optimized preview: **54,642,783 bytes**, SHA-256 `eea8255c09c4d533b4c6fb4904bf6c2778417d0f0b9ed0325bec7549a57bdd63`. Copy: `artifacts/Keeps-preview-consumer-ui-2026-10-18.apk`; [artifact record](checks/redesign-artifact-2026-10-18.json). The final optimized build opens Gallery, performs a fixture filename lookup, displays the generated `fixture-01.png` red-circle image and returns to Settings with 24 ready files. A brief natural-language query showed lexical results; that probe does not establish current semantic ranking or inference success. No new relevance claim is made.

- [Final build log](checks/redesign-build-2026-10-18.log), [14-test phone runner](device/redesign-instrumentation-2026-10-18.log), [JNI mapping](checks/redesign-jni-2026-10-18.json), [static native alignment](checks/redesign-native-2026-10-18.json).
- [Optimized dark Settings](device/redesign-settings-optimized-2026-10-18.png), [light Settings](device/redesign-settings-light-2026-10-18.png), [Excluded empty state](device/redesign-excluded-2026-10-18.png), [generated fixture viewer](device/redesign-fixture-viewer-2026-10-18.png).

The phone changed foreground during a probe; no stale-label tap was accepted. The user then left it untouched for the final viewer check. The helper now also requires foreground Keeps before text injection. Personal gallery/result captures and XML are not included in retained public-facing evidence. The app was left on optimized Settings in Dark appearance; no preparation was started.

## Remaining boundaries

Persistent folder/album exclusion rules for future arrivals, inclusion/location filters beyond type and individual review, automatic discovery-to-preparation, reliable screen-off/overnight scheduling, learned ETA, measured battery cost and an ongoing preparation notification remain M1-02/M1-05/M4-04/M5-02 work. Model delivery is still browser download followed by validated manual import. Storage has model bytes plus Android app-information access; separate index/cache totals and in-app cleanup remain pending. Large-library paging, 200% text/TalkBack, lower-memory/device matrices, representative search relevance, true backdrop blur and measured animation frame pacing are not proved here. Video moment requirements remain unchanged.

No original deletion/move, model recipe change, inference backend change or data reset is part of this slice. The optimized preview is locally debug-signed; production signing, Play review and release acceptance remain open.
