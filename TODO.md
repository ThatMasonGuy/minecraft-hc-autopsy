# HC Autopsy TODO

Current checkpoint: documentation and compatibility audit foundation for a
server-only multi-version compatibility pipeline. The repo still builds a
single Minecraft `1.21.11` jar.

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
- Current Gradle setup is a compact single-project Fabric Loom build.
- The mod id is `hc-autopsy`.
- Product decision: HC Autopsy should be server-only.
- Current metadata still declares `environment: "*"`, includes a no-op client
  entrypoint, and includes a client mixin config. These are migration cleanup
  targets, not product requirements.
- Server initialization loads config, initializes persistence, creates the
  Discord notifier, registers `/hcautopsy`, and creates or resumes a run on
  `ServerLifecycleEvents.SERVER_STARTED`.
- Player joins are tracked through `ServerPlayConnectionEvents.JOIN`.
- Wipe detection injects into `ServerPlayerEntity#onDeath`.
- Stats are captured by reading raw vanilla stat JSON from the current world's
  `stats` directory.
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

Confirmed mapping decision: move HC Autopsy away from Yarn mappings and align
with the Lifetime Stat Tracker donor pipeline's official/Mojang-name strategy.
This keeps the pipeline transplant close to the donor and preserves the
expected `26.x` non-remap lane. The next implementation pass should convert HC
Autopsy's shared server source to official names rather than adapting the donor
pipeline back to Yarn.

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

3. Gradle version-profile foundation.
   - Add `gradle/version-profiles/*.properties`.
   - Update `settings.gradle` to select Loom from the active profile.
   - Update `build.gradle` to select Minecraft, mappings, Fabric Loader,
     Fabric API, Java toolchain, metadata expansion, and compat overlays from
     the active profile.

4. Metadata expansion.
   - Expand `fabric.mod.json` dependencies from the active profile.
   - Expand mixin compatibility levels from the active profile.
   - Change Fabric metadata to server-only: `environment: "server"`, no client
     entrypoint, and no client mixin config.
   - Reconcile repository license and Fabric metadata before publishing.

5. Compatibility shims.
   - Add only the small adapters needed by compile probes.
   - Prefer shared code calling compat adapters over copying full feature
     classes into overlay folders.

6. Release jar verification.
   - Add `buildRelease`, `buildAllVersions`, and metadata verification tasks.
   - Verify mod id, version, license, dependencies, icon, mixin configs, and
     expanded placeholders.

7. Smoke launcher automation.
   - Add a smoke-launch module.
   - Add dedicated-server smoke tests as the required launcher gate.
   - Record smoke status before promoting any profile to supported.

8. GitHub Actions workflows.
   - Keep push/PR validation fast with supported-profile builds.
   - Add a manual candidate smoke validation workflow.
   - Add a guarded manual Modrinth publishing workflow with dry-run default.

9. Modrinth publishing automation.
   - Add upload-plan generation and guarded publish tasks.
   - Require per-version release notes before publishing.
   - Publish supported profiles only.

10. Release promotion.
    - Move candidates to supported only after exact-version launcher smoke
      passes.
    - Tag the exact publish commit with `v<mod_version>`.
    - Create one GitHub Release per `mod_version`.

## Compatibility Risk Surfaces

- Current Yarn source uses `ServerPlayerEntity#onDeath`; official-name source
  should target `ServerPlayer#die(DamageSource)`.
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
  `fabricloader: >=0.18.4`, and `environment: "*"`.
- Current no-op client entrypoint, client mixin config, and `src/client` tree
  should be removed when the server-only metadata pass lands.
- The Minecraft `26.x` lane may require Java 25 and the non-remap Loom pattern
  borrowed from the donor repo.

## Backlog

- Add fixture-based validation for `AggregationEngine` before changing stat
  aggregation behavior.
- Add a name cache or offline profile lookup for
  `/hcautopsy player <name> totals`.
- Decide whether operator players should be able to run
  `/hcautopsy run continue <reason>`.
- Strip client-directed template code and metadata so the mod is server-only.
- Remove template example mixins if they are confirmed unused.
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
