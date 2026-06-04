# Changelog

All notable repo-facing changes should be recorded here. Keep Modrinth-facing
release notes in `gradle/release-notes/<mod_version>.md`.

## Unreleased

### Changed

- Updated repository and Fabric metadata licensing to `LGPL-3.0-or-later`.
- Rewrote the Modrinth project summary and description source copy to match the
  Lifetime Stat Tracker project-page style.
- Updated Fabric metadata contact links to the Modrinth project and GitHub
  source repository.
- Updated the live Modrinth project title, summary, description, license,
  source URL, issue URL, and server/client support metadata.
- Updated GitHub About description, topics, and homepage URL.
- Rewrote the `v1.0.0` GitHub Release notes in the Lifetime Stat Tracker
  format, with Modrinth version links and validation workflow links.

## 1.0.0 - 2026-06-05

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
- Added Gradle version-profile files for the default development `1.21.11`
  profile, the supported broad `1.20-1.21.11` profile, the supported
  `26.1-26.2-pre-3` profile, and fallback candidate/probe compatibility
  groups:
  - `1.20-1.21.11`
  - `1.20-1.20.4`
  - `1.20.5-1.21.10`
  - `1.20.5-1.21.11`
  - `26.1-26.2-pre-3`
- Added profile helper tasks: `printVersionProfile`, `listVersionProfiles`,
  `buildRelease`, `buildAllVersions`, and `buildValidationVersions`.
- Added `TextEventCompat` to bridge chat click/hover event construction across
  the `1.20` text API and the `1.21.11+` / `26.x` record-style event API.
- Added release metadata verification to `buildRelease`.
- Added a `smokelaunch` module and server-only smoke tasks for supported,
  validation, and selected Minecraft profile launches.
- Added exact smoke runtime profiles for the Minecraft versions claimed by the
  release profiles.
- Added `gradle/smoke-tests.json` and recorded passing dedicated-server smoke
  results for every exact Minecraft runtime claimed by the supported release
  profiles.
- Added GitHub Actions workflows for manual candidate smoke validation and
  guarded Modrinth dry-run/publish validation.
- Added guarded Modrinth upload-plan tasks, `publishValidation`,
  `prepareModrinthUploads`, `publishModrinthDryRun`, and `publishModrinth`.
- Added Modrinth-facing `gradle/release-notes/1.0.0.md`.

### Changed

- Changed Gradle to load Minecraft, Fabric Loader, Fabric API, Loom, Java,
  metadata, and server-only compat overlays from the active version profile.
- Aligned the Gradle wrapper distribution with the donor pipeline at Gradle
  `9.4.0` so the `26.x` Loom `1.16-SNAPSHOT` profile can configure.
- Changed `settings.gradle` to select the active Loom version before plugin
  resolution.
- Changed Fabric and Mixin metadata to expand Minecraft, Java, Fabric Loader,
  and Mixin compatibility values from the active profile.
- Promoted the fewest-artifact shape to the supported release list:
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
- Replaced the placeholder Fabric icon with the final HC Autopsy icon.
- Kept the original icon image tracked as `assets/source/HC_Autopsy.jpg`.

### Removed

- Removed the no-op client entrypoint, client mixin config, client source tree,
  split client Loom source set, and unused template server mixin.

### Documented

- Documented that the default development profile is `1.21.11`, while
  supported release profiles now target Minecraft `1.20` through
  `26.2-pre-3`.
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
  Modrinth publishing are implemented.
- Documented that the two-artifact supported release shape passes local
  compile, release-jar metadata probes, and GitHub Actions dedicated-server
  smoke validation run `26953422031`.

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

- Current Gradle setup builds profile-driven server-only Fabric jars. The
  supported release profiles are `1.20-1.21.11` and `26.1-26.2-pre-3`.

### Release

- Published to Modrinth project `4eBkeUAl` by GitHub Actions workflow run
  `26956796078`.
- Published `1.0.0+mc1.20-1.21.11` as Modrinth version `N4AixEjM`.
- Published `1.0.0+mc26.1-26.2-pre-3` as Modrinth version `KdsBXXNZ`.
- Tagged the exact publish commit
  `504a625dff156ac5689a806c991d3fbd677def56` as `v1.0.0`.
- Created the GitHub Release for `v1.0.0`.
