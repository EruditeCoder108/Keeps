# Contributing to Keeps

Keeps is an Android file manager and gallery with optional local content search.
The [roadmap](ROADMAP.md) owns scope and task IDs; the
[design specification](DESIGN_SPEC.md) owns the interaction contract.

## Before changing code

1. Read the roadmap section, current implementation and evidence for the feature.
2. Choose one concrete behavior and its acceptance check. Keep unrelated changes separate.
3. Preserve originals, catalogs, prepared content and grants. Database changes need explicit Room migrations and upgrade evidence.
4. Keep Browse, filename search and viewers independent of model setup.
5. Treat search exclusion and browsing visibility as different features.

## Build and verify

Use JDK 21 and Android SDK 36. On Windows:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On Linux/macOS, use `bash ./gradlew` with the same tasks. Device tests and actual
model tests are distinct from JVM/Robolectric tests. Read [DEVICE_TESTING.md](DEVICE_TESTING.md)
before instrumentation; opt-in probes may require generated fixtures, a compatible
model and an isolated installation. Never run destructive or whole-library probes
against someone's personal catalog.

For preparation changes, verify Pause/Resume, queue scope, notification state,
checkpoints, resource waits and process recovery. For file changes, cover capability
denial and recovery. Record the environment actually checked; a build is not phone
or thermal evidence.

## Reports and pull requests

Include the concrete problem, resulting behavior, relevant task IDs, checks actually
run and remaining limitations. Update ROADMAP.md accurately: simulated tests alone
do not satisfy physical-device gates.

Use generated or explicitly cleared fixtures. Do not upload personal media,
filenames, device identifiers, catalog/vector databases, keys or raw diagnostic
archives. Review screenshots and logs before attaching them.

Run `python tools/audit_publication.py` before sharing. This limited pattern check
does not replace manual review or hosting-platform secret scanning. `.gitignore`
does not remove anything already committed.

## Reuse and licensing

Keeps uses Apache 2.0. By submitting a contribution for inclusion, you agree to
license it under that license. Only contribute material you have the right to
share. Retain upstream headers, mark modifications, and add the source revision
and verification to [THIRD_PARTY.md](docs/THIRD_PARTY.md).

README artwork uses code-drawn shapes, the existing vector identity and OFL Geist.
The GIF is illustrative, not captured UI or an inference speed demonstration.
