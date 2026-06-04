# Compatibility Release Playbook

This playbook adapts the compatibility, CI, and publishing strategy from
Lifetime Stat Tracker to HC Autopsy. It should guide implementation without
requiring this repo to copy unrelated client-side systems.

## Core Idea

Build one jar per compatibility group, not one jar per Minecraft patch.

A compatibility group compiles against one anchor Minecraft version, then only
claims exact Minecraft versions that same packaged jar has passed launcher smoke
testing on.

The default design constraint is the fewest unique build artifacts possible.
Split builds only when one jar cannot cover the combined Minecraft range because
of source compatibility, dependency metadata, binary runtime behavior, or
smoke-test evidence.

## What Is Simpler Here

HC Autopsy has one public mod jar and is server-first.

The first release pipeline can focus on:

- one mod id: `hc-autopsy`
- one release jar per compatibility group
- one dedicated-server smoke install set
- optional client launch smoke only while metadata supports client installs
- no client/server networking matrix unless new features add networking

## What Is Still Risky

The migration touches version-sensitive APIs:

- server-player death mixin target and descriptor
- `DamageSource` and death-message extraction
- server lifecycle and join callbacks
- server command registration and text events
- server save-path and world-name APIs
- player stat handler saves
- Java and Mixin compatibility levels
- Minecraft `26.x` non-remap build behavior

## Profile Model

Each profile should describe:

- `minecraft_version`: compile anchor used by Loom and mappings.
- `profile_id`: release folder and Modrinth suffix.
- `minecraft_dependency`: loader dependency range written into mod metadata.
- `modrinth_game_versions`: exact game versions to list after smoke testing.
- `compat_group`: optional source overlay folder for version-specific APIs.
- `java_version`: Java toolchain and generated Mixin compatibility level.
- Loader/build-tool versions.
- A `26.x` non-remap flag if required.

Keep two profile lists:

- `candidate_minecraft_version_profiles`: builds or experiments that are not
  publishable yet.
- `supported_minecraft_version_profiles`: profiles that have compiled, passed
  required launcher smoke testing for every listed game version, and can be
  published.

## Source Layout

Keep shared behavior in the normal source tree. Add compatibility overlays only
for API drift that cannot compile across the whole range.

Suggested layout:

```text
src/main/java/
src/client/java/
src/compat/<compat_group>/main/java/
src/compat/<compat_group>/client/java/
src/compat/<compat_group>/main/resources/
src/compat/<compat_group>/client/resources/
```

Prefer small adapters, wrappers, or replacement mixins over copying complete
feature classes.

## Verification Ladder

Use a fast local loop and move expensive proof to CI:

1. Current/default-profile build for normal development.
2. Targeted profile build when touching a specific Minecraft version.
3. Build all supported profiles when changing metadata, overlays, packaging, or
   shared code with cross-version risk.
4. Focused dedicated-server launcher smoke test for suspected runtime issues.
5. Optional focused client launcher smoke if the jar remains client-installable.
6. Full smoke matrix in GitHub Actions before publishing.

The key invariant is that the packaged release jar launches under every
Minecraft version listed in `modrinth_game_versions`.

## Publishing Gate

Publishing should only target supported profiles.

Before publishing:

1. Bump `mod_version`.
2. Create or update `gradle/release-notes/<mod_version>.md` with user-facing
   changes for that release.
3. Build every supported profile.
4. Verify release jar metadata and Modrinth upload metadata.
5. Run launcher smoke tests for every listed game version.
6. Dry-run the Modrinth upload plan.
7. Publish through a guarded manual workflow after review.

Do not list a Minecraft version on Modrinth because it probably works. List it
only after the exact packaged jar has launched on that version.

## Local vs GitHub

Local development should stay fast. The normal push/PR workflow should run
supported-profile builds and metadata checks. The manual publish workflow should
install all required Java toolchains, run the expensive smoke matrix, and
publish only after that gate passes.

## Release Notes

Keep two changelog tracks:

- `CHANGELOG.md`: broad repo history, including internal build, CI, migration,
  and implementation details.
- `gradle/release-notes/<mod_version>.md`: concise Modrinth-facing notes for
  users installing that specific version.

Update release notes as user-facing changes happen. Skip internal-only docs,
CI, or refactor details unless they affect install, compatibility, commands, or
visible behavior.
