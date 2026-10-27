# Source-publication review

Reviewed 27 October 2026. This is a local source/publication preflight, not a
production-readiness certification, full security audit or permission to publish.

## Result

The source can be shared under the owner's selected **Apache License 2.0**.
The root [LICENSE](../LICENSE) and [NOTICE](../NOTICE) are present. Existing Google
copyright/modification headers and the Geist OFL remain intact. Full resolved
transitive dependency notices and store/package requirements remain release work.

Do **not** upload the entire working folder. It contains local configuration,
phone captures, catalogs, downloaded artifacts and diagnostic material that do
not belong in a public source snapshot. `.gitignore` now keeps new device
evidence, raw build logs, design-reference uploads, original logo uploads,
databases, model packs and signing material out of normal Git additions.
These files were kept locally, not deleted. Force-adding files bypasses this.

## What was checked

- Tracked and nonignored untracked candidate files, plus **all reachable local
  Git history**. The repository currently has one reachable commit.
- Pattern checks for common GitHub/Google/OpenAI/AWS/Slack credentials,
  private-key headers and personal Windows paths. The scanner emits only
  paths, rule IDs and line numbers, never matched values.
- No matching credentials, personal Windows paths or private database/model/key
  artifacts were found in that candidate/history scan. This is limited pattern
  coverage, not proof that no secret of any kind exists.
- All five already-tracked phone screenshots were opened and reviewed. They
  show generated shape/text/PDF fixtures, not personal photos or documents.
  They still contain ordinary phone status-bar indicators and times.
- Android test media generators were inspected: geometric frames and generated
  tones/noise/silence. The wrapper JAR, bundled font and concept art are binary
  assets; pattern scanning does not establish binary provenance or asset rights.
- The app manifest declares no Internet permission. Cloud providers and external
  apps are separate boundaries; this is not a security guarantee for every dependency.

The [machine-readable report](checks/publication-audit-2026-10-27.json) gives exact
candidate/history counts. Rerun after changing the tree:

```bash
python tools/audit_publication.py --output docs/checks/publication-audit-2026-10-27.json
```

## Personal information that remains

**The existing Git commit contains the author's real name and GitHub no-reply
identity.** Public authorship is normal, but it is personal information. It has
not been rewritten or anonymized. No phone serial was found in the scanned
source/history; raw diagnostic folders are intentionally not distributed.

`.gitignore` never removes existing commits or tracked files. No commits,
staging, pushes, remote visibility changes or history rewrites were performed.
No remote refs were fetched, so this review does not certify what a hosting
service, fork, old clone, release asset or unreachable Git object may retain.
[GitHub's guidance on existing sensitive history](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

## README and assets

- The README uses the established four-tile Keeps vector identity, original
  black/red/white artwork and Geist typography.
- The GIF is a code-generated illustration with geometric fixtures, visibly
  labeled as a diagram rather than a product recording or latency benchmark.
  It is opt-in in a collapsed section, with a static alternative.
- Android/Kotlin/Compose badges identify actual technologies. No build-passing,
  app-store, popularity or endorsement badge is fabricated.
- Personal screenshots are not used in the README. New device captures/logs are
  ignored by default; the five historical fixture screenshots remain tracked.
- Historical development documents may link to deliberately local-only
  captures/logs. Those are not part of the distributed source snapshot.

Verification passed for 31 local links/anchors across the README, contribution
guide and this review; all images decode and the optimized GIF has 23 frames
over 5.7 seconds. The artwork was visually inspected. Synthetic detector checks,
Python compilation and changed tracked-file whitespace checks pass. See the
[verification record](checks/readme-verification-2026-10-27.json). No Android
build or device test was rerun for this documentation-only change. The local
Markdown/HTML preview is not a claim that GitHub's live page was rendered.

## Before a public release

Review the exact staged diff, confirm any newly added media is cleared for
publication, and keep secrets/model data out of release assets. Audit the
resolved dependency licenses and distribute their required notices. Review
signing, permissions and user-data claims against the actual release build.
The roadmap's M6/M7 gates remain active; a polished README does not complete them.

References: [GitHub repository licensing](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository),
[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0), and the
[incorporated-source inventory](THIRD_PARTY.md).
