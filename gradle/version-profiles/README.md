# Minecraft Version Profiles

This directory contains the multi-version profile metadata used by Gradle.
The default local development profile is `1.21.11`; supported release profiles
are `1.20-1.21.11` and `26.1-26.2-pre-3`.

## Goal

Build profiles should keep one source tree while letting Gradle swap Minecraft,
Fabric Loader, Fabric API, Loom, Java, metadata, and optional compatibility
source overlays.

Profiles are release compatibility groups. A profile does not have to be one
exact Minecraft patch version; it can represent one compiled jar that is tested
and published for several compatible Minecraft versions.

Exact smoke runtime profiles, such as `1.20.1.properties`, are different from
release profiles. They exist so the smoke launcher can boot the exact Minecraft
runtime claimed by a release profile. These smoke-only profiles are now present
for all currently claimed runtime versions. Do not add them to
`supported_minecraft_version_profiles` or `candidate_minecraft_version_profiles`
unless we intentionally decide to publish more jars.

## Profile Lists

The current `gradle.properties` model uses:

```properties
minecraft_version_profile=1.21.11
supported_minecraft_version_profiles=1.20-1.21.11,26.1-26.2-pre-3
candidate_minecraft_version_profiles=
```

Only keep a profile supported while it builds, verifies metadata, passes binary
runtime checks, and has passing launcher smoke records for every listed game
version.

Candidate profiles should start as broad as honestly possible. The promoted
supported list is `1.20-1.21.11,26.1-26.2-pre-3`; both profiles pass local
`buildRelease` compile probes, release-jar metadata probes, and
dedicated-server smoke validation. Donor split profiles such as
`1.20-1.20.4` and `1.20.5-1.21.10`, plus the cleaner `1.20.5-1.21.11`
fallback, are probes, not the target shape for HC Autopsy. Split a profile
only after binary runtime checks, dependency metadata, or smoke tests prove
that one jar cannot honestly cover the proposed range.

## Profile Fields

```properties
profile_id=1.20-1.21.11
minecraft_version=<proven_compile_anchor>
minecraft_dependency=>=1.20 <=1.21.11
modrinth_game_versions=1.20,1.20.1,1.20.2,1.20.3,1.20.4,1.20.5,1.20.6,1.21,1.21.1,1.21.2,1.21.3,1.21.4,1.21.5,1.21.6,1.21.7,1.21.8,1.21.9,1.21.10,1.21.11
compat_group=1.20-1.21.11
loader_version=0.18.4
loom_version=1.14-SNAPSHOT
fabric_api_version=<matching Fabric API version>
java_version=17
unobfuscated_minecraft=false
```

- `profile_id` is the release output folder and Modrinth version suffix.
- `minecraft_version` is the compile anchor used by Loom and mappings.
- `minecraft_dependency` is the Fabric Loader dependency range written into
  `fabric.mod.json`.
- `modrinth_game_versions` is the exact set of game versions to publish for the
  jar after smoke testing.
- `compat_group` selects any version-specific source overlay.
- `java_version` selects the Java compile release and generated Mixin
  compatibility level.
- `unobfuscated_minecraft=true` is expected only for Minecraft `26.x` profiles
  if this repo follows the donor non-remap build lane.

## Current Commands

Show the active profile:

```powershell
.\gradlew.bat printVersionProfile --no-daemon --console=plain
```

List supported and candidate profiles:

```powershell
.\gradlew.bat listVersionProfiles --no-daemon --console=plain
```

Build the active profile:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

Build and collect the active profile jar:

```powershell
.\gradlew.bat buildRelease --no-daemon --console=plain
```

Build every supported profile:

```powershell
.\gradlew.bat buildAllVersions --no-daemon --console=plain
```

`buildValidationVersions` also exists and currently builds the supported
profiles plus any configured candidate profiles. Passing that task is still a
compile/package gate, not a runtime promotion signal.

After smoke support exists:

```powershell
.\gradlew.bat verifySmokeTestMatrix
.\gradlew.bat smokeTestSupportedServers
.\gradlew.bat smokeTestValidationServers
.\gradlew.bat publishValidation
.\gradlew.bat ciValidation
```

## Compatibility Source Layout

Compatibility-specific code should live under:

```text
src/compat/<compat_group>/main/java/
src/compat/<compat_group>/main/resources/
```

Keep shared behavior in `src/main/java`. Add compatibility sources only for
target-specific server APIs that cannot compile across the intended range.
