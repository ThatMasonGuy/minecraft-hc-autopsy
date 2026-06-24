# HC Autopsy TODO

Current checkpoint: HC Autopsy `1.4.0` is published on Modrinth and GitHub.
The release updates the 26.x compatibility lane for Minecraft `26.2` final and
`26.3-snapshot-1`, while preserving the existing server-only HC Autopsy
workflow and the two-artifact release shape.

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
  `1.20-1.21.11` and `26.1-26.3-snapshot-1`.
- `candidate_minecraft_version_profiles` is intentionally empty after
  promotion.
- Supported profiles have passing dedicated-server smoke records in
  `gradle/smoke-tests.json` for every exact Minecraft runtime they claim.
- Supported profiles pass compile and release-jar metadata probes.
- The `26.1-26.3-snapshot-1` profile uses the existing `26.x` compat group; no
  new Java shim was required for Minecraft `26.2` final or `26.3-snapshot-1`.
- Admin command gates use `ServerPermissionCompat`, allowing console,
  command-block, and operator-player sources with command permission level 2 /
  gamemaster or higher across the supported profiles.
- In-game wipe summary broadcasts use `PlayerMessageCompat` so player message
  delivery can fall back across supported runtime method shapes.
- Clickable and hoverable command output uses `TextEventCompat` with official
  and intermediary runtime class-name fallbacks for the legacy constructor API
  and newer record-style text event API.
- The dedicated-server smoke helper now verifies registration and executes
  representative `/hcautopsy` command paths from a server command source, then
  exercises wipe-summary and post-wipe leaderboard broadcast construction and
  server-thread dispatch.
- The dedicated-server smoke helper seeds a tiny wiped run with saved player
  snapshots before executing `/hcautopsy leaderboard postwipe`, so the manual
  command's full ranking path is covered instead of only its no-data branch.
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
- Runtime persistence is under the fixed Tempest Studios app-data folder:
  `%APPDATA%\TempestStudios\HC-Autopsy\` on Windows,
  `~/Library/Application Support/TempestStudios/HC-Autopsy/` on macOS, and
  `$XDG_DATA_HOME/tempest-studios/hc-autopsy/` or
  `~/.local/share/tempest-studios/hc-autopsy/` on Linux.
- First launch copies existing launcher-local `config/hc-autopsy/` data into
  the app-data folder only when the app-data folder does not already contain HC
  Autopsy data. Legacy files are left untouched as a backup.
- Data files are run metadata, per-player run snapshots, run aggregates,
  lifetime player totals, and server lifetime totals.
- Optional Discord notifications use a webhook URL stored in
  the app-data `config.json`.
- Wipe finalization now builds a post-wipe leaderboard from the captured player
  snapshots and sends it after the wipe summary in both game chat and Discord.
- `/hcautopsy leaderboard postwipe` broadcasts the latest wiped run's full
  category rankings in game chat and queues Discord leaderboard embeds.
- Discord post-wipe leaderboards use category-specific colors, podium fields,
  and compact rank chunks instead of one dense description block.
- Player-facing run output trims leading `worlds/` and displayed run ids trim
  the matching `worlds_` prefix; persisted metadata and run folder names remain
  unchanged.
- `.env` is ignored for local publishing and metadata-update secrets.

## Current Command Root

```text
/hcautopsy
```

Implemented subcommands:

- `/hcautopsy status`
- `/hcautopsy players`
- `/hcautopsy leaderboard playtime`
- `/hcautopsy leaderboard deaths`
- `/hcautopsy leaderboard walked`
- `/hcautopsy leaderboard jumps`
- `/hcautopsy leaderboard postwipe`
- `/hcautopsy run last`
- `/hcautopsy run list`
- `/hcautopsy run <id>`
- `/hcautopsy run continue <reason>`
- `/hcautopsy player <name> totals`
- `/hcautopsy server totals`
- `/hcautopsy recalc`
- `/hcautopsy config reload`
- `/hcautopsy discord test`

Known command limitations:

- Admin subcommands require console, command-block, or operator-player command
  sources with command permission level 2 / gamemaster or higher.
- `/hcautopsy player <name> totals` now resolves online players and players in
  the persisted HC Autopsy name cache.

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
- Published HC Autopsy `1.0.0` to Modrinth project `4eBkeUAl` with GitHub
  Actions workflow run `26956796078`.
- Published Modrinth version `N4AixEjM` for `1.0.0+mc1.20-1.21.11`.
- Published Modrinth version `KdsBXXNZ` for `1.0.0+mc26.1-26.2-pre-3`.
- Tagged the exact publish commit as `v1.0.0` and created the GitHub Release.
- Hardened wipe finalization so `/hcautopsy run continue <reason>` cannot race
  against delayed stat snapshot capture.
- Switched wipe stat capture to use the configured `statSaveDelayMs` and wait
  for server-thread stat saves before reading vanilla stat JSON.
- Added atomic writes for config, metadata, run snapshots, run aggregates, and
  lifetime totals.
- Added tolerant config and run metadata loading so malformed JSON is logged and
  skipped or reset instead of crashing command/list paths.
- Added a persisted player-name cache populated from joins and deaths.
- Added cached-player listing, offline player totals, stat leaderboards,
  lifetime recalculation, config reload, Discord test notification, and an
  automatic in-game wipe summary broadcast.
- Added focused JUnit coverage for stat aggregation and run continuation
  metadata.
- Expanded the dedicated-server smoke helper to verify expected `/hcautopsy`
  subcommands, not only the root command literal.
- Removed local Gradle 10 deprecation warnings from release metadata and smoke
  task wiring.
- Bumped `mod_version` to `1.1.0` and refreshed README, compatibility smoke
  examples, Fabric metadata description, and Modrinth project-page source copy
  for the expanded command and postmortem feature set.
- Verified `1.1.0` release metadata for both supported profiles with
  `.\gradlew.bat buildAllVersions --no-daemon --console=plain`.
- Completed the local `1.1.0` Modrinth dry-run publish gate with 19 passing
  `1.20-1.21.11` server smoke rows, 4 passing `26.1-26.2-pre-3` server smoke
  rows, and a two-entry upload plan for the supported profile jars.
- Updated the live Modrinth project summary and description body from
  `gradle/modrinth-project-pages.md`; readback matched the committed source
  copy and before/after snapshots were saved under ignored `build/modrinth/`.
- Pushed `main` to `8bebff4c76a764ae49ed9d237b5eac5d6fa50bd1` and passed
  GitHub build workflow run `27085981202`.
- Published HC Autopsy `1.1.0` to Modrinth through GitHub Actions workflow run
  `27086049479`.
- Published Modrinth version `O1UvL8GT` for `1.1.0+mc1.20-1.21.11`.
- Published Modrinth version `ytzyFHiY` for `1.1.0+mc26.1-26.2-pre-3`.
- Tagged the exact publish commit as `v1.1.0` and created the GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.1.0`.
- Added `ServerPermissionCompat` for legacy integer and modern permission-set
  command gates, including support for the `1.21.11+` / `26.x` gamemaster
  permission shape.
- Expanded the dedicated-server smoke helper to execute representative
  `/hcautopsy` command paths after verifying registration.
- Updated README, compatibility notes, smoke-test docs, and Modrinth
  project-page source copy for operator/console admin command support.
- Verified the shim pass with `git diff --check`,
  `.\gradlew.bat buildAllVersions --no-daemon --console=plain`, endpoint
  selected server smokes for `1.20`, `1.21.11`, and `26.2-pre-3`, and the full
  `.\gradlew.bat smokeTestSupportedServers --no-daemon --console=plain` gate.
  The full local smoke run passed all 23 supported exact runtimes with
  `commandsExecuted=true`.
- Confirmed the GitHub Release for `v1.1.0` exists before starting the
  `1.2.0` version bump.
- Bumped `mod_version` to `1.2.0` and added
  `gradle/release-notes/1.2.0.md` for the command-permission compatibility
  release.
- Verified `1.2.0` release metadata for both supported profiles with
  `.\gradlew.bat buildAllVersions verifySmokeTestMatrix --no-daemon --console=plain`.
- Pushed `main` to `5d5c9d28a247d71cc9b5906c620a0480a3b628d6` and passed
  GitHub build workflow run `27090363029`.
- Published HC Autopsy `1.2.0` to Modrinth through GitHub Actions workflow run
  `27090442427`.
- Published Modrinth version `aTPKOz6I` for `1.2.0+mc1.20-1.21.11`.
- Published Modrinth version `WO6HnQEM` for `1.2.0+mc26.1-26.2-pre-3`.
- Tagged the exact publish commit as `v1.2.0` and created the GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.2.0`.
- Removed merged branch refs for the completed `1.1.0` hardening and `1.2.0`
  compatibility-shim work after verifying they had no commits outside `main`.
- Added launcher-agnostic Tempest Studios app-data storage for HC Autopsy
  config/data and guarded first-launch migration from the old
  `config/hc-autopsy/` folder.
- Added `PlayerMessageCompat` and fail-soft server-thread dispatch around the
  delayed wipe summary broadcast so a message API mismatch cannot crash wipe
  finalization.
- Expanded dedicated-server smoke coverage to exercise wipe-summary broadcast
  construction and dispatch.
- Bumped `mod_version` to `1.2.1` and added
  `gradle/release-notes/1.2.1.md` for the storage migration and broadcast crash
  fix patch.
- Verified the `1.2.1` patch locally with `git diff --check`,
  `.\gradlew.bat build --no-daemon --console=plain`,
  `.\gradlew.bat buildAllVersions --no-daemon --console=plain`, and selected
  dedicated-server smoke launches for `1.20`, `1.21.5`, `1.21.11`, and
  `26.2-pre-3`.
  The smoke runs passed with isolated `hcautopsy.dataDir` app-data folders
  under `build/smoke-run/`.
- Ran a pre-publish compatibility pass across all exact supported game
  versions. The symbol audit covered death handling, player stats, world paths,
  command output, player messages, permissions, and text click/hover events for
  all `1.20-1.21.11` and `26.1-26.2-pre-3` runtime targets.
- Added intermediary runtime fallbacks to `TextEventCompat` so clickable
  command links and hover text keep working in the remapped `1.20-1.21.11`
  release jar.
- Pushed `main` to `f2d78fa95d2b7eff5fe2192b9aa542c86eb0327c` and passed
  GitHub build workflow run `27429173534`.
- Updated the live Modrinth project page from `gradle/modrinth-project-pages.md`
  and verified the app-data storage copy read back from the API.
- Published HC Autopsy `1.2.1` to Modrinth through GitHub Actions workflow run
  `27429181718`. The run repeated the full supported dedicated-server smoke
  matrix with 23 `HCAUTOPSY_SERVER_SMOKE_TEST_PASS` markers and
  `commandsExecuted=true`.
- Published Modrinth version `OubGKHg0` for
  `1.2.1+mc1.20-1.21.11`.
- Published Modrinth version `HPDTTcI3` for
  `1.2.1+mc26.1-26.2-pre-3`.
- Tagged the exact publish commit as `v1.2.1` and created the GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.2.1`.
- Added automatic post-wipe leaderboards for most time played, blocks broken,
  damage taken, damage dealt, and diamonds mined from regular plus deepslate
  diamond ore.
- Added `/hcautopsy leaderboard postwipe` to broadcast every player's rank and
  value for each post-wipe stat category from the latest wiped run.
- Polished post-wipe Discord leaderboard embeds and hid the noisy leading
  `worlds/` prefix from player-facing run names.
- Hardened the smoke hook to exercise the full saved-run post-wipe leaderboard
  command path before publishing.
- Updated the wipe broadcast smoke hook to construct the post-wipe leaderboard
  from a representative stat snapshot.
- Bumped `mod_version` to `1.3.0` and added
  `gradle/release-notes/1.3.0.md` for the leaderboard feature.
- Verified the `1.3.0` release locally with `git diff --check`,
  `.\gradlew.bat test --no-daemon --console=plain`,
  `.\gradlew.bat buildAllVersions --no-daemon --console=plain`, selected
  `1.21.11` dedicated-server smoke, and the full
  `.\gradlew.bat publishModrinthDryRun --no-daemon --console=plain` gate.
- Updated the live Modrinth project page from
  `gradle/modrinth-project-pages.md` and verified the leaderboard copy read
  back from the API. Before/after snapshots were saved under ignored
  `build/modrinth/` artifacts.
- Pushed `main` to `5aeb5e71338ef024e35c8b825a0e538d83594c64` and passed
  GitHub build workflow run `27496967898`.
- Published HC Autopsy `1.3.0` to Modrinth through GitHub Actions workflow run
  `27497063024`. The run repeated the full supported dedicated-server smoke
  matrix with 23 `HCAUTOPSY_SERVER_SMOKE_TEST_PASS` markers and
  `commandsExecuted=true`.
- Published Modrinth version `EY25dRsh` for
  `1.3.0+mc1.20-1.21.11`.
- Published Modrinth version `ouw0TJqM` for
  `1.3.0+mc26.1-26.2-pre-3`.
- Tagged the exact publish commit as `v1.3.0` and created the GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.3.0`.
- Added exact runtime profiles for Minecraft `26.2` and `26.3-snapshot-1`.
- Added the broad `26.1-26.3-snapshot-1` release profile using the existing
  `26.x` compat group, Minecraft `26.2` as the compile anchor, Fabric Loader
  `0.19.3`, Fabric API `0.153.0+26.2`, Loom `1.17-SNAPSHOT`, Java `25`, and
  Gradle `9.5.1`.
- Verified the new 26.x release jar metadata with
  `.\gradlew.bat buildRelease "-Pminecraft_version_profile=26.1-26.3-snapshot-1" --no-daemon --console=plain`.
- Passed local dedicated-server smoke tests for the new 26.x jar on Minecraft
  `26.1`, `26.1.1`, `26.1.2`, `26.2`, and `26.3-snapshot-1`, with
  `commandsExecuted=true` and the seeded post-wipe leaderboard path covered.
- Promoted `26.1-26.3-snapshot-1` to supported and bumped `mod_version` to
  `1.4.0` with Modrinth-facing release notes.
- Completed the local `1.4.0` Modrinth dry-run gate with
  `.\gradlew.bat publishModrinthDryRun --no-daemon --console=plain`. The run
  repeated all 24 supported dedicated-server smokes with `commandsExecuted=true`
  and wrote a two-entry upload plan for `1.4.0+mc1.20-1.21.11` and
  `1.4.0+mc26.1-26.3-snapshot-1`.
- Pushed `main` to `e1e15ccede53833f781d05cebd906f920c520c6e`.
- Published HC Autopsy `1.4.0` to Modrinth through GitHub Actions workflow run
  `28090111826`. The run repeated all 24 supported dedicated-server smokes with
  `commandsExecuted=true`.
- Published Modrinth version `v0ZZSbOU` for
  `1.4.0+mc1.20-1.21.11`.
- Published Modrinth version `jYjUmqCJ` for
  `1.4.0+mc26.1-26.3-snapshot-1`.
- Tagged the exact publish commit as `v1.4.0` and created the GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.4.0`.
- Updated the live Modrinth project page from
  `gradle/modrinth-project-pages.md` and verified the support-profile copy read
  back from the API. Before/after snapshots were saved under ignored
  `build/modrinth/` artifacts.

## Current Compatibility Conclusion

The current supported release shape is the preferred two-artifact plan:

- `1.20-1.21.11`
- `26.1-26.3-snapshot-1`

The target migration should use compatibility-group profiles rather than one jar
per exact Minecraft patch. The active supported list now uses the broadest
honest shape proven by compile, metadata, and launcher smoke validation:

- supported: `1.20-1.21.11`
- supported: `26.1-26.3-snapshot-1`, using source compat group
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
Autopsy for Minecraft `1.20` through `26.3-snapshot-1` using the fewest honest
release artifacts possible. The preferred outcome is two artifacts:
`1.20-1.21.11` and `26.1-26.3-snapshot-1`. A three-artifact fallback is
acceptable if Java/API boundaries require it.

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
   - Reconciled repository license and Fabric metadata to `LGPL-3.0-or-later`.

6. Compatibility shims. Completed for the current supported profile shape.
   - Add only the small adapters needed by compile probes.
   - Prefer shared code calling compat adapters over copying full feature
     classes into overlay folders.
   - Current shims are `TextEventCompat` for chat events and
     `ServerPermissionCompat` for command gates.

7. Release jar verification. Completed for current profile tasks.
   - `buildRelease` and `buildAllVersions` exist.
   - Metadata verification is wired into `buildRelease`.
   - Verifies mod id, version, license, dependencies, icon, server-only
     environment, mixin configs, and expanded placeholders.

8. Smoke launcher automation. Completed for the promoted supported profiles.
   - Added the `smokelaunch` module.
   - Added dedicated-server smoke tests as the required launcher gate.
   - Recorded passing supported smoke rows for every exact runtime claimed by
     `1.20-1.21.11` and `26.1-26.3-snapshot-1`.
   - Smoke now verifies command registration and executes representative
     `/hcautopsy` command paths.

9. GitHub Actions workflows. Completed for initial smoke/dry-run gates.
   - Push/PR validation runs supported-profile builds and smoke matrix checks.
   - Manual candidate smoke validation workflow exists and passed run
     `26953422031` for the promoted profiles.
   - Guarded manual Modrinth publishing workflow exists with dry-run default.

10. Modrinth publishing automation. Started.
    - Upload-plan generation and guarded publish tasks exist.
    - Per-version release notes are required before dry-run or publishing.
    - Only supported profiles are included in upload plans.
    - Real publishing is guarded by explicit confirmation and token.
    - `1.0.0` published successfully through GitHub Actions run `26956796078`.

11. Release promotion.
    - Completed for `1.20-1.21.11` and `26.1-26.3-snapshot-1`.
    - Tagged the exact publish commit with `v1.0.0`.
    - Created the GitHub Release for `v1.0.0`.

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

Current release record:

- `v1.0.0`: published from commit
  `504a625dff156ac5689a806c991d3fbd677def56`.
- GitHub Actions publish run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/26956796078`
- GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.0.0`
- Modrinth versions: `N4AixEjM` and `KdsBXXNZ`.
- `v1.1.0`: published from commit
  `8bebff4c76a764ae49ed9d237b5eac5d6fa50bd1`.
- GitHub Actions publish run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27086049479`
- GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.1.0`
- Modrinth versions: `O1UvL8GT` and `ytzyFHiY`.
- `v1.2.0`: published from commit
  `5d5c9d28a247d71cc9b5906c620a0480a3b628d6`.
- GitHub Actions build run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27090363029`
- GitHub Actions publish run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27090442427`
- GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.2.0`
- Modrinth versions: `aTPKOz6I` and `WO6HnQEM`.
- `v1.2.1`: published from commit
  `f2d78fa95d2b7eff5fe2192b9aa542c86eb0327c`.
- GitHub Actions build run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27429173534`
- GitHub Actions publish run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27429181718`
- GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.2.1`
- Modrinth versions: `OubGKHg0` and `HPDTTcI3`.
- `v1.3.0`: published from commit
  `5aeb5e71338ef024e35c8b825a0e538d83594c64`.
- GitHub Actions build run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27496967898`
- GitHub Actions publish run:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/actions/runs/27497063024`
- GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.3.0`
- Modrinth versions: `EY25dRsh` and `ouw0TJqM`.
