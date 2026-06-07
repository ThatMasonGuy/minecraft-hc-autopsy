# Minecraft Compatibility

Research date: 2026-06-04

Scope: HC Autopsy source compatibility planning from Minecraft `1.20` through
`26.2-pre-3`, using the Lifetime Stat Tracker pipeline as the release/profile
model while auditing this mod's server-only API surface.

## Current Status

HC Autopsy now uses the preferred two-artifact supported release shape:
`1.20-1.21.11` plus `26.1-26.2-pre-3`. Both profiles pass local
`buildRelease` compile probes, generated release-jar metadata checks, and
dedicated-server smoke validation on every exact Minecraft runtime they claim.
GitHub Actions candidate smoke validation run `26953422031` is the promotion
evidence for the exact-version runtime matrix.

Fallback donor split profiles still exist for deeper probing, but they are not
the recommended release shape. Do not add or promote future profiles until the
exact jar has built, verified metadata, passed binary runtime checks, launched
in dedicated-server smoke tests, and passed every exact runtime listed by the
profile.

## Product Decision

HC Autopsy should be server-only.

The baseline-normalized current build has removed the old Fabric template
leftovers:

- `fabric.mod.json` now declares `environment: "server"`
- `fabric.mod.json` declares only the main entrypoint
- `fabric.mod.json` declares only the server mixin config
- `build.gradle` no longer uses `splitEnvironmentSourceSets()`
- the `src/client` tree and unused template mixin are removed

Future profile work should preserve this server-only metadata shape:

- `environment: "server"`
- no `entrypoints.client`
- no client mixin config
- no client source set
- no client launcher smoke gate

## Executive Recommendation

Use the fewest compatibility-group jars that can honestly support the targeted
Minecraft range. The donor repo's split was required by that mod; it is not a
recommendation for HC Autopsy.

Supported two-artifact shape after compile, metadata, and smoke validation:

| Release profile | Compile anchor | Runtime claim | Java | Source compat group |
| --- | --- | --- | ---: | --- |
| `1.20-1.21.11` | `1.20` | `1.20` through `1.21.11` | 17 | `1.20-1.21.11` |
| `26.1-26.2-pre-3` | `26.2-pre-3` | `26.1`, `26.1.1`, `26.1.2`, `26.2-pre-3` | 25 | `26.x` |

Acceptable three-artifact fallback if Java/API boundaries require it:

| Release profile | Compile anchor | Runtime claim after smoke tests | Java | Source compat group |
| --- | --- | --- | ---: | --- |
| `1.20-1.20.4` | `1.20` | `1.20`, `1.20.1`, `1.20.2`, `1.20.3`, `1.20.4` | 17 | `1.20-1.20.4` |
| `1.20.5-1.21.11` | `1.21.11` | `1.20.5` through `1.21.11` | 21 | `1.20.5-1.21.11` |
| `26.1-26.2-pre-3` | `26.2-pre-3` | `26.1`, `26.1.1`, `26.1.2`, `26.2-pre-3` | 25 | `26.x` |

Only fall back to narrower shapes if binary runtime checks, dependency
metadata, or smoke tests prove HC Autopsy really needs it. Prefer the
three-artifact fallback before the donor-style four-way split.

## Mapping Strategy

Confirmed decision: move HC Autopsy away from Yarn mappings and align with the
Lifetime Stat Tracker donor pipeline's official/Mojang-name strategy.

The donor pipeline already has a `26.x` path that assumes that shape. The
current `1.21.11` source has been converted to official names. Future profile
work should preserve that direction while transplanting the donor profile
pipeline, rather than reshaping the donor pipeline back to Yarn.

Important current-to-target mapping changes:

| Current Yarn surface | Official-name target |
| --- | --- |
| `ServerPlayerEntity` | `ServerPlayer` |
| `ServerPlayerEntity#onDeath(DamageSource)` | `ServerPlayer#die(DamageSource)` |
| `ServerCommandSource` | `CommandSourceStack` |
| `CommandManager` | `Commands` |
| `Text` | `Component` |
| `MutableText` | `MutableComponent` |
| `WorldSavePath` | `LevelResource` |
| `player.getStatHandler().save()` | `player.getStats().save()` |

This is the largest planning consequence from the audit. The APIs themselves
look fairly stable once viewed through official names; the mapping mismatch is
the part that would make a naive pipeline copy painful.

## Local Audit Findings

Local inspection covered current HC Autopsy source, donor source/profile files,
cached Minecraft jars, and cached Fabric API jars. `javap` checks were run
against anchor versions including `1.20`, `1.21.10`, `1.21.11`, `26.1.2`, and
`26.2-pre-3` where local artifacts were available.

| Surface | Finding | Planning consequence |
| --- | --- | --- |
| Death mixin target | Official `ServerPlayer#die(DamageSource)` exists across inspected anchors. | A shared official-name mixin may be enough unless compile probes expose descriptor drift. |
| Death message | `DamageSource#getLocalizedDeathMessage(LivingEntity)` exists across inspected anchors. | Prefer the official method instead of Yarn `getDeathMessage`. |
| Damage id | `DamageSource#getMsgId()` exists across inspected anchors. | Prefer this over `getType().msgId()` for shared code. |
| Attacker/source entity | `DamageSource#getEntity()` and `getDirectEntity()` exist across inspected anchors. | Use `getEntity()` for the attacker semantics closest to current Yarn `getAttacker()`. |
| Stat save | `ServerPlayer#getStats().save()` exists across inspected anchors. | A tiny wrapper is optional, but full overlays do not look necessary yet. |
| Stats path | `MinecraftServer#getWorldPath(LevelResource.PLAYER_STATS_DIR)` exists across inspected anchors. | Prefer this over resolving `ROOT/stats` manually. |
| World name | `MinecraftServer#getWorldData().getLevelName()` exists across inspected anchors. | Official-name source can keep the same behavior. |
| Server directory | `MinecraftServer#getServerDirectory()` changes from `File` in `1.20` to `Path` later. | Avoid it for stats paths; use donor-style path normalization only if needed elsewhere. |
| Fabric lifecycle | `SERVER_STARTED` and `SERVER_STOPPING` are present across inspected Fabric API anchors. | Direct registration is probably fine. |
| Fabric join event | `ServerPlayConnectionEvents.JOIN` is present across inspected Fabric API anchors. | Direct registration is probably fine. |
| Command registration | `CommandRegistrationCallback.register(dispatcher, registryAccess, environment)` is stable in inspected anchors. | Official-name command source imports are the bigger change. |
| Text click/hover events | `1.20` uses class constructors; `1.21.11+` and `26.x` use newer interface/subtype shapes. | `TextEventCompat` now constructs events reflectively while leaving command rendering shared. |
| Online player name lookup | `PlayerList#getPlayerByName(String)` exists across inspected anchors. | Use `getPlayerByName` instead of the newer overloaded `getPlayer(String)` call. |

## HCAutopsy API Surface

Current Minecraft and Fabric API touchpoints after the official-name
normalization:

- `ServerLifecycleEvents.SERVER_STARTED`
- `ServerLifecycleEvents.SERVER_STOPPING`
- `ServerPlayConnectionEvents.JOIN`
- `CommandRegistrationCallback`
- `Commands`
- `CommandSourceStack`
- `ClickEvent`, `HoverEvent`, `MutableComponent`, `Component`, and `ChatFormatting`
- `TextEventCompat` for cross-version click/hover event construction
- `ServerPlayer`
- `ServerPlayer#die` mixin target
- `DamageSource`
- `DamageSource#getLocalizedDeathMessage`
- `DamageSource#getMsgId`
- `DamageSource#getEntity`
- `Entity#getType`
- `Entity#getName`
- `MinecraftServer`
- `MinecraftServer#getWorldPath(LevelResource.PLAYER_STATS_DIR)`
- `MinecraftServer#getWorldData().getLevelName()`
- `ServerPlayer#getStats().save()`
- Fabric Loader config directory lookup
- Mixin compatibility levels
- Fabric metadata dependency ranges

## Expected Drift Points

### Death Detection

Target official-name mixin:

```java
@Inject(method = "die", at = @At("HEAD"))
private void hcautopsy$die(DamageSource damageSource, CallbackInfo ci)
```

Risk:

- The target method descriptor must be compile-probed in every source group.
- Death-message, source-id, and attacker extraction should be centralized so
  any future drift is isolated.

Likely shim:

- `DamageSourceCompat` with methods for death message, damage id, attacker type,
  and attacker name. It may be a thin wrapper around stable official calls at
  first.

### Server Lifecycle And Joins

Current code uses Fabric lifecycle and connection callbacks directly.

Risk:

- Fabric event class names or callback signatures may still drift in untested
  `26.x` artifacts.

Likely shim:

- Add `ServerEventsCompat.register(...)` only if compile probes prove event
  registration diverges.

### Commands And Text

Current code uses server command v2, click events, hover events, text literals,
and admin command permission checks.

Risk:

- Command source imports change during the official-name migration.
- Click and hover event construction differs between older and newer anchors.
- Command permission checks changed around `1.21.11` from integer permission
  levels to permission-set objects.

Implemented shim:

- `TextEventCompat` for clickable/hoverable run-list entries.
- `ServerPermissionCompat` for legacy integer permission checks and modern
  permission-set checks. Admin commands map to command permission level 2 /
  gamemaster, using `COMMANDS_GAMEMASTER` or the equivalent runtime field.

### Stat Snapshot Paths

Target official-name code should resolve:

```java
server.getWorldPath(LevelResource.PLAYER_STATS_DIR)
```

Risk:

- Save-path APIs appear stable in inspected anchors, but smoke tests should
  still verify that stat JSON is read from the correct world.

Likely shim:

- `ServerPathCompat` only if compile probes or smoke tests prove direct calls
  are not enough. The donor repo already has a reflective path-normalization
  pattern if `getServerDirectory()` is needed for a fallback.

### Stat Saving

Target official-name code should force:

```java
player.getStats().save()
```

Risk:

- The inspected anchors expose the same call, but this should still be
  compile-probed in each profile.

Likely shim:

- `PlayerStatsCompat.forceSave(ServerPlayer player)` if a future anchor breaks
  the direct call.

### Java, Mixin, And Build Lane

Current metadata expands Java and Mixin compatibility level from the active
profile.

Expected requirements:

- Preferred `1.20-1.21.11` should first be probed as a Java 17 artifact.
- Fallback `1.20.5-1.21.11` should build with Java 21 and Mixin `JAVA_21` only
  if the broad Java 17 pre-26 artifact fails honestly.
- `26.x` likely needs Java 25 and the donor repo's non-remap build lane.
- `fabric.mod.json` must expand Minecraft, Fabric Loader, Java, Fabric API, and
  server-only environment metadata from the active profile.

## Source Compat Group Plan

### Shared Server Source

Purpose: all behavior that appears stable once converted to official names.

Expected contents:

- main mod entrypoint
- lifecycle and join registration
- command registration and handlers
- run manager, persistence, aggregation, config, notification, and data models
- death routing through small compat helpers
- stat path and stat save calls through direct official APIs or thin wrappers

### `1.20-1.21.11`

Purpose: preferred broad pre-26 lane with Java 17, if one artifact can honestly
cover Minecraft `1.20` through `1.21.11`.

Potential contents:

- text click/hover helper variant
- death mixin variant only if `die` descriptor differs
- Java 17 Mixin metadata
- any tiny wrappers needed to keep shared server code binary-compatible across
  the full range

### Fallback `1.20-1.20.4` And `1.20.5-1.21.11`

Purpose: fallback lanes only if Java/API boundaries prove the broad
`1.20-1.21.11` jar cannot honestly cover the full range.

Potential contents:

- split text click/hover helpers if the `1.20` and `1.20.5+` shapes cannot be
  bridged cleanly
- Java 21 metadata for the newer pre-26 lane

### `1.21.11`

Purpose: current proven lane after the official-name conversion.

Potential contents:

- current direct implementations
- metadata expansion and smoke hooks

### `26.x`

Purpose: Java 25 forward lane.

Potential contents:

- Java 25 mixin/build metadata
- non-remap release artifact behavior
- text click/hover helper variant if needed

## Smoke-Test Invariant

The packaged release jar must launch on every exact Minecraft version listed in
the profile's `modrinth_game_versions`.

Dedicated-server smoke should prove:

- Fabric server launches with the packaged jar installed
- HC Autopsy main entrypoint initializes
- config and persistence directories are created
- `/hcautopsy` is registered
- representative `/hcautopsy` command paths execute from a server command
  source, including admin-gated commands
- a world run is created or resumed
- the server reaches the tick loop
- the server exits cleanly

No client smoke gate is planned because the mod is server-only.

## Current Probe Evidence

Local and GitHub Actions evidence from 2026-06-04:

- `.\gradlew.bat buildRelease "-Pminecraft_version_profile=1.20-1.21.11" --no-daemon --console=plain`
  passed.
- The `1.20-1.21.11` release jar metadata expands to server-only Fabric
  metadata with `minecraft: >=1.20 <=1.21.11`, `java: >=17`, and Mixin
  `JAVA_17`.
- `.\gradlew.bat buildRelease "-Pminecraft_version_profile=26.1-26.2-pre-3" --no-daemon --console=plain`
  passed.
- The `26.1-26.2-pre-3` release jar metadata expands to server-only Fabric
  metadata with `minecraft: >=26.1 <=26.2-pre.3`, `java: >=25`, and Mixin
  `JAVA_25`.
- `.\gradlew.bat smokeTestSelectedServers "-Phcautopsy_smoke_profiles=1.20-1.21.11" "-Phcautopsy_smoke_game_versions=1.21.11" --no-daemon --console=plain`
  passed for the supported `1.21.11` release jar.
- `.\gradlew.bat publishModrinthDryRun --no-daemon --console=plain`
  passed, wrote a local upload plan, and did not call the Modrinth API.
- GitHub Actions candidate smoke validation run `26953422031` passed for
  `1.20-1.21.11` on Minecraft `1.20`, `1.20.1`, `1.20.2`, `1.20.3`,
  `1.20.4`, `1.20.5`, `1.20.6`, `1.21`, `1.21.1`, `1.21.2`, `1.21.3`,
  `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`,
  and `1.21.11`.
- The same GitHub Actions run passed for `26.1-26.2-pre-3` on Minecraft
  `26.1`, `26.1.1`, `26.1.2`, and `26.2-pre-3`.
- GitHub Actions Modrinth publish run `26956796078` repeated the supported
  smoke matrix, prepared the upload plan, and published the two supported
  profile jars.
- The published Modrinth versions are `N4AixEjM` for `1.0.0+mc1.20-1.21.11`
  and `KdsBXXNZ` for `1.0.0+mc26.1-26.2-pre-3`.

The promoted supported profiles have passing dedicated-server smoke rows for
all exact Minecraft versions they claim.

Local `1.1.0` release-prep evidence from 2026-06-07:

- `.\gradlew.bat buildAllVersions --no-daemon --console=plain` passed and
  verified `hc-autopsy-1.1.0.jar` release metadata for both supported profiles.
- `.\gradlew.bat publishModrinthDryRun --no-daemon --console=plain` completed
  the full supported-profile publish validation path locally, including 19
  `1.20-1.21.11` dedicated-server smoke rows and 4 `26.1-26.2-pre-3`
  dedicated-server smoke rows.
- The local dry-run upload plan wrote two listed release entries:
  `1.1.0+mc1.20-1.21.11` and `1.1.0+mc26.1-26.2-pre-3`, both using
  `gradle/release-notes/1.1.0.md`.
- GitHub Actions dry-run publish validation run `27085465683` passed on the
  hardening branch.
- GitHub Actions publish run `27086049479` repeated the supported smoke matrix
  from `main`, published `1.1.0+mc1.20-1.21.11` as Modrinth version
  `O1UvL8GT`, and published `1.1.0+mc26.1-26.2-pre-3` as Modrinth version
  `ytzyFHiY`.

Local post-release shim evidence from 2026-06-07:

- `.\gradlew.bat buildAllVersions --no-daemon --console=plain` passed and
  verified release metadata for both supported profile jars.
- `.\gradlew.bat smokeTestSelectedServers "-Phcautopsy_smoke_profiles=1.20-1.21.11,26.1-26.2-pre-3" "-Phcautopsy_smoke_game_versions=1.20,1.21.11,26.2-pre-3" --no-daemon --console=plain`
  passed for the oldest supported runtime, the newest pre-26 runtime, and
  `26.2-pre-3`, with `commandsExecuted=true` in every pass marker.
- `.\gradlew.bat smokeTestSupportedServers --no-daemon --console=plain`
  passed locally in 14m 4s with 23 dedicated-server pass markers and 23
  `commandsExecuted=true` markers, covering all exact runtimes claimed by
  `1.20-1.21.11` and `26.1-26.2-pre-3`.

Release `1.2.0` evidence from 2026-06-07:

- `.\gradlew.bat buildAllVersions verifySmokeTestMatrix --no-daemon --console=plain`
  passed locally and verified `hc-autopsy-1.2.0.jar` release metadata for both
  supported profiles.
- GitHub Actions build run `27090363029` passed from `main` at commit
  `5d5c9d28a247d71cc9b5906c620a0480a3b628d6`.
- GitHub Actions publish run `27090442427` repeated the supported smoke matrix,
  published `1.2.0+mc1.20-1.21.11` as Modrinth version `aTPKOz6I`, and
  published `1.2.0+mc26.1-26.2-pre-3` as Modrinth version `WO6HnQEM`.
- The exact publish commit is tagged `v1.2.0` and has a GitHub Release:
  `https://github.com/ThatMasonGuy/minecraft-hc-autopsy/releases/tag/v1.2.0`.

## Immediate Implementation Notes

- Preserve the server-only metadata and official/Mojang source naming while
  adding any future compile-probe shims.
- Keep compatibility overlays server-only:

```text
src/main/java/
src/main/resources/
src/compat/<compat_group>/main/java/
src/compat/<compat_group>/main/resources/
```

- Add the smallest compatibility helpers that compile probes actually require.
- Do not publish or add a future profile until its dedicated-server smoke
  matrix is green for every exact listed game version.

## Evidence Sources

- Current HC Autopsy source and metadata.
- Lifetime Stat Tracker docs, source naming, version profiles, smoke launcher,
  and publishing workflow as a local donor pattern.
- Local cached Minecraft jars inspected with `javap` for `1.20`, `1.21.10`,
  `1.21.11`, `26.1.2`, and `26.2-pre-3` where available.
- Local cached Fabric API jars inspected for lifecycle, networking, and command
  registration callback surfaces.
