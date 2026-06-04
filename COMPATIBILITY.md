# Minecraft Compatibility

Research date: 2026-06-04

Scope: HC Autopsy source compatibility planning from Minecraft `1.20` through
`26.2-pre-3`, using the Lifetime Stat Tracker pipeline as the release/profile
model while auditing this mod's server-only API surface.

## Current Status

HC Autopsy currently has a supported `1.21.11` build profile. Candidate profile
files exist for `1.20-1.20.4`, `1.20.5-1.21.10`, and `26.1-26.2-pre-3`, but
those candidate profiles are not proven or publishable yet.

This document is a source-read and local bytecode-inspection risk map, not a
completed compile-probe report. Do not promote any profile to supported until
the exact jar has built, verified metadata, launched in dedicated-server smoke
tests, and passed every exact runtime listed by the profile.

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
Minecraft range. Start with candidate profiles aligned to known cross-version
breakpoints from the donor pipeline:

| Release profile | Compile anchor | Runtime claim after smoke tests | Java | Source compat group |
| --- | --- | --- | ---: | --- |
| `1.20-1.20.4` | `1.20` | `1.20`, `1.20.1`, `1.20.2`, `1.20.3`, `1.20.4` | 17 | `1.20-1.20.4` |
| `1.20.5-1.21.10` | `1.21.10` | `1.20.5` through `1.21.10` | 21 | `1.20.5-1.21.10` |
| `1.21.11` | `1.21.11` | `1.21.11` | 21 | `1.21.11` |
| `26.1-26.2-pre-3` | `26.2-pre-3` | `26.1`, `26.1.1`, `26.1.2`, `26.2-pre-3` | 25 | `26.x` |

Split or collapse these profiles only after compile probes, binary runtime
checks, dependency metadata, or smoke tests prove the better map.

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
| Text click/hover events | `1.20` uses class constructors; `1.21.11+` and `26.x` use newer interface/subtype shapes. | Add `TextEventCompat` or initially remove clickable run-list entries. |

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
and console-only permission checks.

Risk:

- Command source imports change during the official-name migration.
- Click and hover event construction differs between older and newer anchors.
- Operator-player permission checks should not be broadened until the correct
  version-safe server API is verified.

Likely shim:

- `TextEventCompat` for clickable/hoverable run-list entries.
- `ServerPermissionCompat` if player operators are allowed to continue runs
  later.
- If `TextEventCompat` is not worth the first implementation pass, keep run
  list entries plain text and restore clickability after profile builds pass.

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

- `1.20-1.20.4` should build with Java 17 and Mixin `JAVA_17`.
- `1.20.5-1.21.11` should build with Java 21 and Mixin `JAVA_21`.
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

### `1.20-1.20.4`

Purpose: oldest target lane with Java 17.

Potential contents:

- text click/hover helper variant
- death mixin variant only if `die` descriptor differs
- Java 17 Mixin metadata

### `1.20.5-1.21.10`

Purpose: Java 21 lane before the current `1.21.11` anchor.

Potential contents:

- text click/hover helper variant if the `1.20.5+` shape differs from the
  current anchor

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
- a world run is created or resumed
- the server reaches the tick loop
- the server exits cleanly

No client smoke gate is planned because the mod is server-only.

## Immediate Implementation Notes

- Preserve the server-only metadata and official/Mojang source naming while
  adding the next compile-probe shims.
- Keep compatibility overlays server-only:

```text
src/main/java/
src/main/resources/
src/compat/<compat_group>/main/java/
src/compat/<compat_group>/main/resources/
```

- Add the smallest compatibility helpers that compile probes actually require.
- Do not publish a profile until its dedicated-server smoke matrix is green for
  every exact listed game version.

## Evidence Sources

- Current HC Autopsy source and metadata.
- Lifetime Stat Tracker docs, source naming, version profiles, smoke launcher,
  and publishing workflow as a local donor pattern.
- Local cached Minecraft jars inspected with `javap` for `1.20`, `1.21.10`,
  `1.21.11`, `26.1.2`, and `26.2-pre-3` where available.
- Local cached Fabric API jars inspected for lifecycle, networking, and command
  registration callback surfaces.
