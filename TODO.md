# HC Autopsy TODO

Current checkpoint: smoke-validated compatibility promotion. The preferred
two-artifact release shape has passing dedicated-server smoke evidence from
GitHub Actions candidate smoke validation run `26953422031` and is now
configured as supported. The next gate is a supported-profile Modrinth dry run
without publishing.

## Project Workflow

- After every major change, update `TODO.md`, update `CHANGELOG.md`, verify the
  change, and commit before starting the next major step.
- If a session includes more than one major change, stop between major
  boundaries and checkpoint intentionally.
- Keep user-facing release notes in `gradle/release-notes/<mod_version>.md`.
  Internal docs, CI, Gradle, and shim work belongs in `CHANGELOG.md` and this
  file.
- Before editing, run `git status --short` and preserve unrelated user changes.

## Confirmed Current Shape

- Current default source and metadata target Minecraft `1.21.11` for local
  development.
- Current Gradle setup is profile-driven. The default profile is `1.21.11`.
- `supported_minecraft_version_profiles` currently contains
  `1.20-1.21.11` and `26.1-26.2-pre-3`.
- `candidate_minecraft_version_profiles` is intentionally empty after
  promotion.
- Supported profiles have passing dedicated-server smoke records in
  `gradle/smoke-tests.json` for every exact Minecraft runtime they claim.
- Supported profiles pass compile and release-jar metadata probes.
- Fallback probes exist for `1.20-1.20.4`, `1.20.5-1.21.10`, and
  `1.20.5-1.21.11`. These are not the recommended release shape unless
  evidence forces a split.
- The mod id is `hc-autopsy`.
- Product decision implemented for the current build: HC Autopsy is
  server-only.
- Current metadata declares `environment: "server"`, has only the main
  entrypoint, declares only the server mixin config, and expands Minecraft,
  Java, Fabric Loader, and Mixin compatibility values from the active profile.
- Server initialization loads config, initializes persistence, creates the
  Discord notifier, registers `/hcautopsy`, and creates or resumes a run on
  `ServerLifecycleEvents.SERVER_STARTED`.
- Player joins are tracked through `ServerPlayConnectionEvents.JOIN`.
- Wipe detection injects into official-name `ServerPlayer#die`.
- Stats are captured by reading raw vanilla stat JSON from
  `LevelResource.PLAYER_STATS_DIR`.
- Runtime persistence is under `config/hc-autopsy/`.
- Data files are run metadata, per-player run snapshots, run aggregates,
  lifetime player totals, and server lifetime totals.
- Optional Discord notifications use a webhook URL stored in
  `config/hc-autopsy/config.json`.
- `.env` is ignored for local publishing and metadata-update secrets.

## Current Command Root

```text
/hcautopsy
```

Implemented subcommands:

- `/hcautopsy status`
- `/hcautopsy run last`
- `/hcautopsy run list`
- `/hcautopsy run <id>`
- `/hcautopsy run continue <reason>`
- `/hcautopsy player <name> totals`
- `/hcautopsy server totals`

Known command limitations:

- `/hcautopsy run continue <reason>` currently allows console or command-block
  sources only.
- `/hcautopsy player <name> totals` resolves online players only. Offline name
  lookup is not implemented yet.

## Recently Completed

- Added `.env` to `.gitignore` so local Modrinth and metadata-update secrets
  stay out of version control.
- Added `AGENTS.md` with checkpoint, verification, and release-process rules
  adapted from Lifetime Stat Tracker.
- Added this documentation foundation for the upcoming compatibility migration.
- Recorded the server-only product decision and the local compatibility audit
  findings before Gradle/source migration work.
- Added a baseline normalization step before Gradle profile work.
- Removed the stale client entrypoint, client mixin config, client source tree,
  client Loom source set, and unused template server mixin.
- Migrated the current `1.21.11` source and build from Yarn mappings to
  official/Mojang mappings.
- Verified the normalized current build with
  `.\gradlew.bat clean build --no-daemon --console=plain`.
- Added version-profile properties for the supported `1.21.11` profile, the
  preferred broad `1.20-1.21.11` candidate, the `26.x` candidate, and fallback
  donor split probe anchors.
- Updated `settings.gradle` to select the active Loom version from the active
  profile before plugin resolution.
- Updated `build.gradle` to select the active Minecraft/Fabric/Java/Loom lane,
  official mappings for remapped profiles, the non-remap plugin lane for
  `26.x`, and server-only compatibility overlays.
- Aligned the Gradle wrapper to `9.4.0` so the `26.x` Loom `1.16-SNAPSHOT`
  profile can configure like the donor pipeline.
- Added profile helper tasks: `printVersionProfile`, `listVersionProfiles`,
  `buildRelease`, `buildAllVersions`, and `buildValidationVersions`.
- Added `TextEventCompat` so `/hcautopsy run list` keeps clickable run entries
  across the `1.20` class-constructor chat event API and the `1.21.11+` /
  `26.x` record-style chat event API.
- Switched online player name resolution to `PlayerList#getPlayerByName`, which
  is available across the inspected `1.20`, `1.21.11`, and `26.x` anchors.
- Verified `buildRelease` for the preferred `1.20-1.21.11` candidate and
  confirmed generated metadata declares `minecraft: >=1.20 <=1.21.11`,
  `java: >=17`, `environment: server`, and Mixin `JAVA_17`.
- Verified `buildRelease` for the preferred `26.1-26.2-pre-3` candidate and
  confirmed generated metadata declares `minecraft: >=26.1 <=26.2-pre.3`,
  `java: >=25`, `environment: server`, and Mixin `JAVA_25`.
- Added release metadata verification to `buildRelease`.
- Added the `smokelaunch` Gradle module and server-only smoke tasks:
  `smokeTestSupportedServers`, `smokeTestValidationServers`, and
  `smokeTestSelectedServers`.
- Added exact smoke runtime profiles for every Minecraft version claimed by
  the release profiles.
- Added `gradle/smoke-tests.json` with passing supported server smoke records
  for all exact versions claimed by the two release profiles.
- Added GitHub Actions workflows for fast supported-profile builds, manual
  candidate smoke validation, and guarded Modrinth publish/dry-run validation.
- Added guarded Modrinth upload-plan tasks and verified
  `publishModrinthDryRun` locally. No Modrinth API upload was performed.
- Passed GitHub Actions candidate smoke validation run `26953422031` for
  `1.20` through `1.21.11`, plus `26.1`, `26.1.1`, `26.1.2`, and
  `26.2-pre-3`.
- Promoted `1.20-1.21.11` and `26.1-26.2-pre-3` from candidate profiles to
  supported profiles.

## Current Compatibility Conclusion

The current supported release shape is the preferred two-artifact plan:

- `1.20-1.21.11`
- `26.1-26.2-pre-3`

The target migration should use compatibility-group profiles rather than one jar
per exact Minecraft patch. The active supported list now uses the broadest
honest shape proven by compile, metadata, and launcher smoke validation:

- supported: `1.20-1.21.11`
- supported: `26.1-26.2-pre-3`, using source compat group
  `26.x`
- candidates: none

Fallback probe profiles `1.20-1.20.4`, `1.20.5-1.21.10`, and
`1.20.5-1.21.11` exist because we may need narrower evidence anchors. They are
not recommendations for HC Autopsy unless future binary runtime checks or smoke
tests prove the broad profile cannot honestly hold. Promote any future profile
to supported only after build, metadata verification, binary runtime checks, and
launcher smoke validation pass for every exact Minecraft runtime listed by the
profile.

Confirmed mapping decision implemented for the current build: HC Autopsy has
moved away from Yarn mappings and now aligns with the Lifetime Stat Tracker
donor pipeline's official/Mojang-name strategy. This keeps the pipeline
transplant close to the donor and preserves the expected `26.x` non-remap lane.

## Migration Goal

Create a single-repo release pipeline that can build, validate, and publish HC
Autopsy for Minecraft `1.20` through `26.2-pre-3` using the fewest honest
release artifacts possible. The preferred outcome is two artifacts:
`1.20-1.21.11` and `26.1-26.2-pre-3`. A three-artifact fallback is acceptable
if Java/API boundaries require it.

Each release profile should:

- compile from one anchor Minecraft version
- use one source compat group
- generate correct Fabric, Java, Minecraft, and Mixin metadata
- collect a release jar under `build/release/<profile_id>/`
- publish only exact Minecraft versions that passed launcher smoke testing

## Proposed Compatibility Groups

```text
src/compat/1.20-1.21.11/
src/compat/1.21.11/
src/compat/26.x/
```

Keep shared behavior in `src/main/java`. Add server-side compat source only for
API shapes that cannot compile across the intended range. Use the older donor
split compat groups only if compile probes prove the broad `1.20-1.21.11`
candidate cannot honestly hold.

## Migration Roadmap

1. Documentation foundation.
   - Add `AGENTS.md`, `README.md`, `TODO.md`, `CHANGELOG.md`,
     `COMPATIBILITY.md`, and Gradle planning docs.
   - Keep this pass docs-only.

2. Compatibility audit.
   - Probe Minecraft and Fabric API drift for HC Autopsy's actual source
     surface.
   - Confirm or adjust the proposed profile map.
   - Document exact breakpoints and required shims.

3. Baseline normalization on current `1.21.11`. Completed for the current
   single-version build.
   - Remove stale client-directed template code and build wiring.
   - Change the current single-version build to server-only metadata.
   - Move the current source from Yarn names to official/Mojang names while
     still targeting only Minecraft `1.21.11`.
   - Prove the normalized single-version build before adding profile
     complexity.

4. Gradle version-profile foundation. Completed for supported and candidate
   profile selection.
   - Add `gradle/version-profiles/*.properties`.
   - Update `settings.gradle` to select Loom from the active profile.
   - Update `build.gradle` to select Minecraft, mappings, Fabric Loader,
     Fabric API, Java toolchain, metadata expansion, and compat overlays from
     the active profile.

5. Metadata expansion. Completed for profile-derived Minecraft, Java, Fabric
   Loader, and Mixin values.
   - Expand `fabric.mod.json` dependencies from the active profile.
   - Expand mixin compatibility levels from the active profile.
   - Preserve server-only Fabric metadata: `environment: "server"`, no client
     entrypoint, and no client mixin config.
   - Reconcile repository license and Fabric metadata before publishing.

6. Compatibility shims. Started.
   - Add only the small adapters needed by compile probes.
   - Prefer shared code calling compat adapters over copying full feature
     classes into overlay folders.

7. Release jar verification. Completed for current profile tasks.
   - `buildRelease` and `buildAllVersions` exist.
   - Metadata verification is wired into `buildRelease`.
   - Verifies mod id, version, license, dependencies, icon, server-only
     environment, mixin configs, and expanded placeholders.

8. Smoke launcher automation. Completed for the promoted supported profiles.
   - Added the `smokelaunch` module.
   - Added dedicated-server smoke tests as the required launcher gate.
   - Recorded passing supported smoke rows for every exact runtime claimed by
     `1.20-1.21.11` and `26.1-26.2-pre-3`.

9. GitHub Actions workflows. Completed for initial smoke/dry-run gates.
   - Push/PR validation runs supported-profile builds and smoke matrix checks.
   - Manual candidate smoke validation workflow exists and passed run
     `26953422031` for the promoted profiles.
   - Guarded manual Modrinth publishing workflow exists with dry-run default.

10. Modrinth publishing automation. Started.
    - Upload-plan generation and guarded publish tasks exist.
    - Per-version release notes are required before dry-run or publishing.
    - Only supported profiles are included in upload plans.
    - Real publishing remains blocked without explicit confirmation and token.

11. Release promotion.
    - Completed for `1.20-1.21.11` and `26.1-26.2-pre-3`.
    - Tag the exact publish commit with `v<mod_version>`.
    - Create one GitHub Release per `mod_version`.

## Compatibility Risk Surfaces

- Current official-name source targets `ServerPlayer#die(DamageSource)`.
- `DamageSource#getLocalizedDeathMessage`, `DamageSource#getMsgId`,
  `DamageSource#getEntity`, entity type stringification, and text APIs are the
  relevant official-name death-detail surface.
- `ServerLifecycleEvents.SERVER_STARTED` and `SERVER_STOPPING` drive run
  lifecycle.
- `ServerPlayConnectionEvents.JOIN` tracks participating players.
- `CommandRegistrationCallback`, `Commands`, `CommandSourceStack`, text
  click/hover events, and permission checks may drift.
- `MinecraftServer#getWorldPath(LevelResource.PLAYER_STATS_DIR)` and
  `server.getWorldData().getLevelName()` should drive stats and world identity
  in official-name source.
- `ServerPlayer#getStats().save()` should remain callable or be wrapped.
- Mixin compatibility level now expands from the active profile.
- `fabric.mod.json` now expands Minecraft, Java, and Fabric Loader dependency
  metadata from the active profile while preserving `environment: "server"`.
- The Minecraft `26.x` lane may require Java 25 and the non-remap Loom pattern
  borrowed from the donor repo.

## Backlog

- Add fixture-based validation for `AggregationEngine` before changing stat
  aggregation behavior.
- Add a name cache or offline profile lookup for
  `/hcautopsy player <name> totals`.
- Decide whether operator players should be able to run
  `/hcautopsy run continue <reason>`.
- Reconcile `LICENSE` with Fabric metadata before publishing.
- Document any data migration if run metadata or lifetime stat formats change.
- Keep README command documentation aligned with actual command behavior.

## Release Process

Before a real release:

1. Bump `mod_version`.
2. Create or update `gradle/release-notes/<mod_version>.md`.
3. Run the full supported-profile validation gate.
4. Dry-run the Modrinth upload plan.
5. Publish through the guarded GitHub Actions workflow.
6. Tag the exact publish commit.
7. Create a GitHub Release that links the Modrinth versions.
