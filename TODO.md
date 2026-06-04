# HC Autopsy TODO

Current checkpoint: baseline-normalized single-version build. The repo still
builds a single Minecraft `1.21.11` jar, but that jar is now server-only and
compiled against official/Mojang mappings.

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

- Current source and metadata target Minecraft `1.21.11`.
- Current Gradle setup is a compact single-project Fabric Loom build using
  `loom.officialMojangMappings()`.
- The mod id is `hc-autopsy`.
- Product decision implemented for the current build: HC Autopsy is
  server-only.
- Current metadata declares `environment: "server"`, has only the main
  entrypoint, and declares only the server mixin config.
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

## Current Compatibility Conclusion

The current repo is only proven at Minecraft `1.21.11`.

The target migration should use compatibility-group profiles rather than one jar
per exact Minecraft patch. Initial candidate profiles should align with the
source compatibility groups:

- `1.20-1.20.4`
- `1.20.5-1.21.10`
- `1.21.11`
- `26.1-26.2-pre-3`, using source compat group `26.x`

These ranges are not proven for HC Autopsy yet. Promote a profile to supported
only after build, metadata verification, and launcher smoke validation pass for
every exact Minecraft runtime listed by the profile.

Confirmed mapping decision implemented for the current build: HC Autopsy has
moved away from Yarn mappings and now aligns with the Lifetime Stat Tracker
donor pipeline's official/Mojang-name strategy. This keeps the pipeline
transplant close to the donor and preserves the expected `26.x` non-remap lane.

## Migration Goal

Create a single-repo release pipeline that can build, validate, and publish HC
Autopsy for Minecraft `1.20` through `26.2-pre-3` using the fewest honest
release artifacts possible.

Each release profile should:

- compile from one anchor Minecraft version
- use one source compat group
- generate correct Fabric, Java, Minecraft, and Mixin metadata
- collect a release jar under `build/release/<profile_id>/`
- publish only exact Minecraft versions that passed launcher smoke testing

## Proposed Compatibility Groups

```text
src/compat/1.20-1.20.4/
src/compat/1.20.5-1.21.10/
src/compat/1.21.11/
src/compat/26.x/
```

Keep shared behavior in `src/main/java`. Add server-side compat source only for
API shapes that cannot compile across the intended range.

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

4. Gradle version-profile foundation.
   - Add `gradle/version-profiles/*.properties`.
   - Update `settings.gradle` to select Loom from the active profile.
   - Update `build.gradle` to select Minecraft, mappings, Fabric Loader,
     Fabric API, Java toolchain, metadata expansion, and compat overlays from
     the active profile.

5. Metadata expansion.
   - Expand `fabric.mod.json` dependencies from the active profile.
   - Expand mixin compatibility levels from the active profile.
   - Preserve server-only Fabric metadata: `environment: "server"`, no client
     entrypoint, and no client mixin config.
   - Reconcile repository license and Fabric metadata before publishing.

6. Compatibility shims.
   - Add only the small adapters needed by compile probes.
   - Prefer shared code calling compat adapters over copying full feature
     classes into overlay folders.

7. Release jar verification.
   - Add `buildRelease`, `buildAllVersions`, and metadata verification tasks.
   - Verify mod id, version, license, dependencies, icon, mixin configs, and
     expanded placeholders.

8. Smoke launcher automation.
   - Add a smoke-launch module.
   - Add dedicated-server smoke tests as the required launcher gate.
   - Record smoke status before promoting any profile to supported.

9. GitHub Actions workflows.
   - Keep push/PR validation fast with supported-profile builds.
   - Add a manual candidate smoke validation workflow.
   - Add a guarded manual Modrinth publishing workflow with dry-run default.

10. Modrinth publishing automation.
    - Add upload-plan generation and guarded publish tasks.
    - Require per-version release notes before publishing.
    - Publish supported profiles only.

11. Release promotion.
    - Move candidates to supported only after exact-version launcher smoke
      passes.
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
- Mixin config currently hardcodes `JAVA_21`.
- `fabric.mod.json` currently declares `minecraft: ~1.21.11`, `java: >=21`,
  `fabricloader: >=0.18.4`, and `environment: "server"`.
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
