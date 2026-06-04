# Changelog

All notable repo-facing changes should be recorded here. Keep Modrinth-facing
release notes in `gradle/release-notes/<mod_version>.md`.

## Unreleased

### Added

- Added `.env` to `.gitignore` so local publishing secrets and Modrinth tokens
  stay outside version control.
- Added `AGENTS.md` with checkpoint, verification, and release workflow rules
  adapted from the Lifetime Stat Tracker compatibility pipeline.
- Added the initial project documentation foundation for the planned
  multi-version migration:
  - `README.md`
  - `TODO.md`
  - `COMPATIBILITY.md`
  - `gradle/compatibility-release-playbook.md`
  - `gradle/version-profiles/README.md`
  - `gradle/smoke-tests.md`
  - `gradle/modrinth-publishing.md`
  - `gradle/modrinth-project-pages.md`
  - `gradle/release-notes/README.md`
- Added Gradle version-profile files for the supported `1.21.11` profile, the
  preferred broad `1.20-1.21.11` candidate, and fallback candidate/probe
  compatibility groups:
  - `1.20-1.21.11`
  - `1.20-1.20.4`
  - `1.20.5-1.21.10`
  - `1.20.5-1.21.11`
  - `26.1-26.2-pre-3`
- Added profile helper tasks: `printVersionProfile`, `listVersionProfiles`,
  `buildRelease`, `buildAllVersions`, and `buildValidationVersions`.
- Added `TextEventCompat` to bridge chat click/hover event construction across
  the `1.20` text API and the `1.21.11+` / `26.x` record-style event API.

### Changed

- Changed Gradle to load Minecraft, Fabric Loader, Fabric API, Loom, Java,
  metadata, and server-only compat overlays from the active version profile.
- Aligned the Gradle wrapper distribution with the donor pipeline at Gradle
  `9.4.0` so the `26.x` Loom `1.16-SNAPSHOT` profile can configure.
- Changed `settings.gradle` to select the active Loom version before plugin
  resolution.
- Changed Fabric and Mixin metadata to expand Minecraft, Java, Fabric Loader,
  and Mixin compatibility values from the active profile.
- Changed the active candidate list to probe the fewest-artifact shape first:
  `1.20-1.21.11` plus `26.1-26.2-pre-3`.
- Normalized the current `1.21.11` build to official/Mojang mappings with
  `loom.officialMojangMappings()`.
- Changed the current Fabric metadata to server-only by declaring
  `environment: "server"` and keeping only the main entrypoint and server mixin
  config.
- Migrated current Minecraft imports and calls to official-name APIs including
  `ServerPlayer#die`, `CommandSourceStack`, `Commands`, `Component`,
  `ChatFormatting`, `LevelResource.PLAYER_STATS_DIR`, and
  `ServerPlayer#getStats().save()`.
- Changed `/hcautopsy player <name> totals` online lookup to use
  `PlayerList#getPlayerByName`, which is available across the current
  compatibility anchors.

### Removed

- Removed the no-op client entrypoint, client mixin config, client source tree,
  split client Loom source set, and unused template server mixin.

### Documented

- Documented that the current repo still targets Minecraft `1.21.11` only.
- Documented the proposed compatibility-group profile map for Minecraft `1.20`
  through `26.2-pre-3`.
- Documented HC Autopsy's current server-side runtime shape, data paths,
  command surface, Discord webhook config, and compatibility risk surfaces.
- Documented the server-only product decision and the required removal of
  client entrypoints, client mixins, client source sets, and client smoke gates
  during the migration.
- Documented local source and `javap` compatibility findings for the
  official-name server API surface used by the donor pipeline.
- Documented that profile builds, smoke launcher automation, and guarded
  Modrinth publishing are planned but not implemented yet.
- Documented that the preferred two-artifact candidate shape now passes local
  compile and release-jar metadata probes while still requiring binary runtime
  checks and dedicated-server smoke validation before promotion.

## 1.0.0 Current Baseline

### Current Behavior

- Detects the first player death in a run through a server-player death mixin.
- Records wipe cause metadata and marks the active run as wiped.
- Snapshots vanilla stat JSON from the world's `stats` directory.
- Stores run metadata, per-player run snapshots, run aggregates, lifetime
  player totals, and server lifetime totals under `config/hc-autopsy/`.
- Provides `/hcautopsy` commands for status, run history, run continuation, and
  player/server totals.
- Sends optional Discord webhook notifications for wipes.

### Build

- Current Gradle setup builds profile-driven server-only Fabric jars. The only
  supported profile is `1.21.11`; older and `26.x` profiles are candidates
  until compile probes, metadata checks, and smoke validation prove them.
