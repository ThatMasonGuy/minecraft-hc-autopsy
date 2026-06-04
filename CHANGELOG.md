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

### Changed

- Normalized the current `1.21.11` build to official/Mojang mappings with
  `loom.officialMojangMappings()`.
- Changed the current Fabric metadata to server-only by declaring
  `environment: "server"` and keeping only the main entrypoint and server mixin
  config.
- Migrated current Minecraft imports and calls to official-name APIs including
  `ServerPlayer#die`, `CommandSourceStack`, `Commands`, `Component`,
  `ChatFormatting`, `LevelResource.PLAYER_STATS_DIR`, and
  `ServerPlayer#getStats().save()`.

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

- Current Gradle setup builds one server-only Fabric jar for Minecraft
  `1.21.11` with Java 21 and official/Mojang mappings.
