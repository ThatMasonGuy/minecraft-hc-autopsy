# Minecraft Compatibility

Research date: 2026-06-04

Scope: HC Autopsy source compatibility planning from Minecraft `1.20` through
`26.2-pre-3`, using the Lifetime Stat Tracker pipeline as the release/profile
model but auditing this mod's own server-first API surface.

## Current Status

HC Autopsy currently builds for Minecraft `1.21.11` only.

This document is a source-read risk map, not a completed compile-probe report.
Do not promote any profile to supported until the exact jar has built, verified
metadata, launched in smoke tests, and passed every exact runtime listed by the
profile.

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

## HCAutopsy API Surface

Current Minecraft and Fabric API touchpoints:

- `ServerLifecycleEvents.SERVER_STARTED`
- `ServerLifecycleEvents.SERVER_STOPPING`
- `ServerPlayConnectionEvents.JOIN`
- `CommandRegistrationCallback`
- `CommandManager`
- `ServerCommandSource`
- `ClickEvent`, `HoverEvent`, `MutableText`, `Text`, and `Formatting`
- `ServerPlayerEntity`
- `ServerPlayerEntity#onDeath` mixin target
- `DamageSource`
- `DamageSource#getDeathMessage`
- `DamageSource#getType().msgId()`
- `DamageSource#getAttacker`
- `Entity#getType`
- `Entity#getName`
- `MinecraftServer`
- `MinecraftServer#getSavePath(WorldSavePath.ROOT)`
- `server.getSaveProperties().getLevelName()`
- `ServerPlayerEntity#getStatHandler().save()`
- Fabric Loader config directory lookup
- Mixin compatibility levels
- Fabric metadata dependency ranges

## Expected Drift Points

### Death Detection

Current code injects:

```java
@Inject(method = "onDeath", at = @At("HEAD"))
private void hcautopsy$onDeath(DamageSource damageSource, CallbackInfo ci)
```

Risk:

- `ServerPlayerEntity#onDeath` method descriptor may drift.
- `DamageSource` death-message, type, and attacker APIs may drift.
- Text and entity display APIs may change descriptors.

Likely shim:

- Keep wipe routing shared.
- Add source compat mixins only where the target method descriptor changes.
- Add a small `DamageSourceCompat` helper if death-message or source-id access
  changes across ranges.

### Server Lifecycle And Joins

Current code uses Fabric lifecycle and connection callbacks directly.

Risk:

- Fabric event class names or callback signatures may drift in `26.x`.

Likely shim:

- Add `ServerEventsCompat.register(...)` only if compile probes prove event
  registration diverges.

### Commands And Text

Current code uses server command v2, click events, hover events, text literals,
and console-only permission checks.

Risk:

- Command builder classes and text event constructors can drift.
- `ClickEvent.RunCommand` and `HoverEvent.ShowText` shapes may differ across
  the target range.
- Server permission checks may need descriptor-safe helpers.

Likely shim:

- Add `ServerCommandCompat.literal(...)`, `argument(...)`, text event helpers,
  or permission helpers only if compile probes require them.

### Stat Snapshot Paths

Current code resolves:

```java
server.getSavePath(WorldSavePath.ROOT).resolve("stats")
```

Risk:

- Save path or world save path APIs may drift.
- `getSaveProperties().getLevelName()` may drift.

Likely shim:

- Add `ServerPathCompat` to normalize world root and world display name if
  direct calls do not compile or smoke tests show bad world ids.

### Stat Saving

Current code forces:

```java
player.getStatHandler().save()
```

Risk:

- Player stat handler accessor or save method may drift.

Likely shim:

- Add `PlayerStatsCompat.forceSave(ServerPlayerEntity player)` if needed.

### Java, Mixin, And Build Lane

Current metadata hardcodes Java 21 and Mixin `JAVA_21`.

Expected requirements:

- `1.20-1.20.4` should build with Java 17 and Mixin `JAVA_17`.
- `1.20.5-1.21.11` should build with Java 21 and Mixin `JAVA_21`.
- `26.x` likely needs Java 25 and the donor repo's non-remap build lane.
- `fabric.mod.json` must expand Minecraft, Fabric Loader, Java, and Fabric API
  metadata from the active profile.

## Source Compat Group Plan

### `1.20-1.20.4`

Purpose: oldest target lane with Java 17.

Potential contents:

- death mixin variant if `onDeath` descriptor differs
- command/text helpers if server command APIs differ
- stat save/path helpers if descriptors differ
- Java 17 Mixin metadata

### `1.20.5-1.21.10`

Purpose: Java 21 lane before the `1.21.11` mapping and descriptor changes seen
in other mods.

Potential contents:

- command/text helpers if needed
- path/stat save helpers if needed

### `1.21.11`

Purpose: current proven lane.

Potential contents:

- current direct implementations
- metadata expansion and smoke hooks

### `26.x`

Purpose: Java 25 forward lane.

Potential contents:

- event registration helpers if Fabric APIs moved
- command/text helpers if descriptors changed
- Java 25 mixin/build metadata
- non-remap release artifact behavior

## Smoke-Test Invariant

The packaged release jar must launch on every exact Minecraft version listed in
the profile's `modrinth_game_versions`.

Primary server smoke should prove:

- Fabric server launches with the packaged jar installed
- HC Autopsy main entrypoint initializes
- config and persistence directories are created
- `/hcautopsy` is registered
- a world run is created or resumed
- the server reaches the tick loop
- the server exits cleanly

Optional client smoke should prove:

- the jar can be installed on the client if metadata still allows it
- the no-op client entrypoint and client mixin config do not crash launch

## Evidence Sources

- Current HC Autopsy source and metadata.
- Lifetime Stat Tracker docs, version profiles, smoke launcher, and publishing
  workflow as a local donor pattern.
- Future work: local compile probes and `javap` inspection for Minecraft and
  Fabric API jars across the proposed profile ranges.
