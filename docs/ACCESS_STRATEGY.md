# Daily-use source/access contract

## Automatic device setup follow-up — 8 October 2026

First launch explains device access and invokes Android's native gallery permissions followed by its all-files settings screen. Actual grants are checked on return/resume; existing grants skip unnecessary requests. Setup can be declined and retried in Settings. Android intentionally rejects the internal-storage root in the SAF picker; the separate optional all-files grant supplies shared-storage browsing. Other apps' private storage remains protected. [SAF restrictions](https://developer.android.com/training/data-storage/shared/documents-files#access-restrictions).

Permitted media and, with all-files access, Android-cataloged non-media shared files are discovered automatically. Discovery uses 128-item Room transactions, cached access/identity maps and background work; originals are neither modified nor queued for AI. Direct storage folders enumerate children when opened, so discovery is not a claim that every hidden/unindexed path has been recursively cataloged. Live mutation capability checks remain required. [New source, setup, preservation and performance evidence](DEVICE_ACCESS_SLICE.md).

8 October 2026 · M0-01/M1/M2. Optional device-storage access, MediaStore discovery and write-capable chosen folders now coexist. Browsing consent never queues AI preparation.

| Source | Access and discovery | Available actions |
|---|---|---|
| SAF document/tree | Persisted read/write grants when the provider offers them; live flags checked before mutation; bounded tree discovery | View/share, copy to writable destination, rename/create folder, verified copy-then-remove move, Keeps trash/restore where root and original parent are recorded |
| Device shared storage | User opens Android's all-files settings and chooses whether to grant it; mounted StorageManager roots; direct children discovered on opening a folder | File actions with real filesystem capabilities; app-private Android folders remain restricted |
| MediaStore | Optional photos/videos/audio grants, including Android 14+ selected visual access; query each mounted media volume; real albums, available capture date and duration; debounced observer | View/share/copy, Android-approved rename/trash/restore/permanent deletion. Copy-then-remove moves from MediaStore are not exposed yet |

All-files access supports the app's file-manager purpose and is separate from optional local AI. Google Play restricted-permission review is still a release gate, not an approval already obtained. [Android all-files documentation](https://developer.android.com/training/data-storage/manage-all-files), [selected visual access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access), [media changes](https://developer.android.com/training/data-storage/shared/media).

Transfers persist source/version/destination and stages in Room v5. A 256 KiB stream hashes copied bytes; reopening the destination verifies byte count and SHA-256. Moves also reopen/hash the source and recheck its metadata before deletion. Name collisions keep both. Cancellation/failed transfers retain originals and track partial destinations for retry. A restart at source-removal stage requires review. Retry revalidates both files; **Use verified copy** recovers a checked destination without deleting any remaining source. Atomic native moves, overwrite/skip conflict choices, physical full-disk and removed-volume tests remain open.

Keeps trash lives under `.KeepsTrash` in the chosen tree/volume and remains until explicit restore or permanent deletion. Android media trash uses Android's retention. Trashed assets remain in the catalog for recovery and are filtered from normal browsing and search; late indexing commits cannot republish them. Restore resolves collisions by keeping both and retains the asset's favorite/bookmarks. Saved moments are references to original intervals; no clip export is advertised.

Known provider IDs reconcile SAF/MediaStore/filesystem routes; unknown identities stay separate. This is still a metadata/version baseline, not a complete large-library synchronizer. Paging, broad provider/device coverage and overlapping-route reconciliation remain active. Image files without capture metadata use real modified/addition dates; they do not receive an invented capture date.

Per-file **Exclude from content search** deletes derived search data while keeping ordinary browsing and filename search available. It is separate from trash. A general Hide-from-browsing control is not implemented.

## Historical read-only slice contract

The following describes the earlier prototype and is retained as evidence context; the contract above supersedes its implementation status.

8 October 2026 · M0-01/M1-02 chosen-folder slice. This is an implementation decision for the first slice, not the final launch permission strategy.

| Source | Browse/read/share | Original mutations | Current boundary |
|---|---|---|---|
| Individually chosen SAF document | Persisted read grant, provider metadata, local viewer, temporary share grant | None exposed | Existing catalog supported |
| Chosen SAF tree | Persisted read grant, directory enumeration, metadata, folder navigation, media albums | None exposed | Bounded refresh: 2,000 total entries, 200 directories, depth 8; opening one folder lists up to 2,000 direct entries; partial coverage is reported |
| Mounted SD/USB/provider tree | Same contract when selectable and readable | None exposed | Real removable/read-only-provider testing remains open |
| MediaStore/shared storage | Not implemented in this slice | Not implemented | M0-01 still decides broad-media/all-files policy and launch workflows |

The app requests only read access and does not infer writable capability from a document provider's flags. Flags are recorded for later live mutation checks. Rename/copy/move/trash/delete are not advertised. Original deletion is never performed by removing a catalog item or its search data.

Identity uses document authority plus provider document ID when available. Selecting the same document directly and through a tree retains its catalog ID/favorite/exclusion. Equal names or sizes are never deduplicated. Unknown provider identities remain separate. Overlapping tree routes, MediaStore identity reconciliation, source-volume changes, and durable operation journals remain M1/M2 work.

Provider last-modified and size form the current observed version. Known changes invalidate chunks. A worker cannot publish after a catalog version change or exclusion. Providers that change bytes without updating metadata require explicit re-preparation; strong version/content validation is still outstanding. Failed/truncated listings do not prove deletion. Reconnect storage, refresh, or choose the folder again restores access; a successful complete listing marks missing direct children unavailable and clears their derived data.

Browsing access does not start content indexing. Preparation has a scope review dialog. Per-file **Exclude from content search** deletes derived text/vectors and blocks late worker commits while keeping the file visible and filename-searchable. Hiding files is not implemented.

Gallery uses provider modification date, falling back to actual catalog addition time. It says **File date**, not capture date. Albums use discovered real parent folders; individually selected files without parent evidence are not fabricated into albums. Captured-date extraction, pins, paging, and proactive reconciliation remain open.

Android restricts storage roots, Downloads root and Android/data/obb in SAF. A Downloads tile opens the chosen-folder route; it does not claim whole-Downloads access. See [Android SAF documentation](https://developer.android.com/training/data-storage/shared/documents-files). Permission eligibility for a complete shared-storage file manager is not settled by this limited slice.
