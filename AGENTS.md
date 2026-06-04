# AGENTS.md

## Fresh Agent Start Here

Read these files before making changes:

1. `AGENTS.md` for workflow rules and verification expectations.
2. `TODO.md` for the current checkpoint, confirmed behavior, and migration plan.
3. `README.md` for the user-facing mod shape and command reference.
4. `COMPATIBILITY.md` for the current supported target, drift points, and
   source compat groups.
5. `gradle/version-profiles/README.md` for the active multi-version profile
   model, Java toolchains, compat overlays, and candidate-vs-supported rules.
6. `gradle/smoke-tests.md` for the planned local and CI smoke-test gates.
7. `gradle/modrinth-publishing.md` for release notes, secrets, dry runs, and
   guarded publishing.
8. `gradle/modrinth-project-pages.md` for the Modrinth project summary and
   description-page source copy.
9. `gradle/compatibility-release-playbook.md` for the portable strategy borrowed
   from Lifetime Stat Tracker and adapted to this simpler server-only repo.

If one of these project-process docs is missing, treat the docs foundation as
the active checkpoint and create or update the missing docs before implementation
work. After reading the docs, run `git status --short` before editing. Preserve
any unrelated user changes.

## Project Workflow

- Keep the repo in a clean checkpoint-driven state while migrating from the
  current single-target `1.21.11` build to a compatibility-group release
  pipeline.
- After each major change or implementation step:
  1. Update `TODO.md` with completed work, current state, and next relevant task.
  2. Update `CHANGELOG.md` with repo-facing engineering history.
  3. Update `gradle/release-notes/<mod_version>.md` only when the change is
     user-facing for the release being prepared. Do not put internal-only CI,
     shim, refactor, or docs housekeeping into Modrinth release notes.
  4. Run the appropriate verification command.
  5. Commit the change before starting the next major step.
- Current verification ladder:
  - Docs-only changes: `git diff --check`.
  - Current single-target build:
    `.\gradlew.bat build --no-daemon --console=plain`.
  - After profile tasks exist, normal local/push sanity check:
    `.\gradlew.bat buildAllVersions --no-daemon --console=plain` or the
    narrower default-profile task documented by the implementation.
  - Targeted Minecraft profile build:
    `.\gradlew.bat build "-Pminecraft_version_profile=<profile>" --no-daemon --console=plain`.
  - Full release validation after smoke tasks exist:
    `.\gradlew.bat ciValidation --no-daemon --console=plain`.
- Before any Modrinth publish or dry-run publish for a new `mod_version`, add a
  concise per-release note file at `gradle/release-notes/<mod_version>.md`.
  This file is the Modrinth changelog for that version. Do not rely on the full
  `CHANGELOG.md` or the whole `## Unreleased` section for Modrinth uploads.
- Keep Modrinth project-page copy in `gradle/modrinth-project-pages.md`. Version
  publishing uploads jars and per-version changelogs only; it does not update
  the project summary, description body, gallery, categories, or other
  page-level metadata.
- After a real Modrinth publish succeeds, create an annotated Git tag for the
  released `mod_version`, using `v<mod_version>` such as `v1.0.0`. The tag must
  point at the exact commit used by the successful publish workflow, not a later
  docs-only or metadata-only follow-up commit.
- After pushing the release tag, create a GitHub Release from that tag. Use one
  GitHub Release per `mod_version`, even when Modrinth has multiple
  profile-suffixed versions for that mod version. Link the Modrinth project and
  published Modrinth version ids instead of making GitHub the primary download
  surface.
- Keep the active `gradle/release-notes/<mod_version>.md` file updated as
  user-facing changes accumulate for that Modrinth release. Put internal build,
  shim, CI, docs, and migration details in `CHANGELOG.md` and `TODO.md`
  instead.
- If multiple major changes happen in one session, stop between each major
  boundary to update `TODO.md`, update `CHANGELOG.md`, verify, and commit.
- Keep commits focused. Do not bundle unrelated Gradle migration, compatibility
  shims, docs cleanup, and publishing work into one commit.
- Before editing or committing, check `git status --short` and preserve any user
  changes that are unrelated to the current task.

## Local vs GitHub Validation

- Use the current `build` task for rapid development until the compatibility
  profile tasks are implemented.
- Once version profiles exist, use targeted profile builds when touching a
  specific Minecraft version and full supported-profile builds when touching
  shared code, Gradle metadata, resource expansion, packaging, or compat
  overlays.
- Keep the normal push/PR workflow fast. The expensive matrix should live in a
  manual GitHub Actions workflow before publishing.
- For this repo, dedicated-server smoke testing is the primary invariant because
  HC Autopsy is server-only: the packaged jar must launch on every exact
  Minecraft version listed in its profile metadata, initialize the server
  entrypoint, register `/hcautopsy`, create or resume run state, and shut down
  cleanly.
- `ciValidation` and `publishValidation` should include dedicated-server launch
  smoke once the smoke tasks exist. Use focused selected smoke tasks for local
  checks and aggregate validation before publishing.
- Real Modrinth uploads should go through the guarded GitHub workflow unless the
  user explicitly asks for a local publish task.

## Major Change Boundaries

Examples of major boundaries for this project:

- Baseline cleanup or metadata correction.
- Documentation and planning foundation.
- Gradle version-profile foundation.
- Java toolchain and CI workflow changes.
- Resource expansion for Minecraft, Java, Fabric Loader, Fabric API, and Mixin
  compatibility metadata.
- Server lifecycle, player join, command, wipe detection, stats snapshot, or
  world-path compatibility shims.
- Minecraft `26.x` remap vs non-remap build lane.
- Smoke-test launcher automation.
- Modrinth publishing automation.
- Release promotion from candidate to supported profiles.

## Current Direction

- Keep HC Autopsy as one public mod jar unless compile probes, binary runtime
  checks, dependency metadata, or smoke tests prove that separate artifacts are
  required.
- Preserve the server-only Hardcore postmortem workflow: detect the first
  player death in a run, record the wipe cause, snapshot vanilla stat JSON,
  aggregate run and lifetime totals, expose `/hcautopsy` commands, and send
  optional Discord notifications.
- Product decision implemented for the current build: HC Autopsy is
  server-only. Preserve the absence of client entrypoints, client mixin
  configs, client source sets, and client smoke assumptions.
- Preserve the on-disk JSON data model, run metadata, lifetime totals, and
  recalculation behavior unless an explicit migration is added and documented.
- Treat Minecraft version profiles as release compatibility groups, not
  necessarily one profile per exact patch version.
- Prefer the fewest unique build artifacts possible. Split release profiles or
  source compat groups only when one jar literally cannot cover the combined
  range because of compatibility requirements, dependency metadata, binary
  runtime behavior, or smoke-test failure.
- A profile should compile one jar from one anchor Minecraft version, list every
  Minecraft version that exact jar has passed smoke testing on, and publish only
  those tested game versions to Modrinth.
- `supported_minecraft_version_profiles` and
  `candidate_minecraft_version_profiles` list profile file names. Release
  folders and Modrinth version suffixes use each profile's `profile_id`, which
  can be broader than the file name.
- Initial release profiles should align with the source compat groups:
  `1.20-1.20.4`, `1.20.5-1.21.10`, `1.21.11`, and `26.1-26.2-pre-3`, mapped
  onto source compat groups `1.20-1.20.4`, `1.20.5-1.21.10`, `1.21.11`, and
  `26.x`.
- Split a release profile away from its source compat group only when compile
  probes, binary runtime checks, dependency metadata, or smoke tests prove that
  one jar cannot honestly cover the proposed range.
- For the Minecraft `26.x` lane, start with one broad `26.1-26.2-pre-3`
  candidate mapped to `26.x`. Split it to `26.1-26.1.2` and `26.2-pre-3` only
  if Fabric dependency metadata, compile anchors, or smoke tests require that.
  For prerelease Fabric metadata, remember that Modrinth uses labels like
  `26.2-pre-3` while Fabric Loader may report or compare runtime versions like
  `26.2-pre.3`; follow Fabric API's `minecraft` dependency string for
  `fabric.mod.json` and keep `modrinth_game_versions` as the Modrinth label.
- This mod should need fewer compatibility overlays than client-heavy mods, but
  do not collapse ranges by assumption. Let compile probes and launcher smoke
  tests prove where the API breaks are.
- Confirmed mapping decision implemented for the current build: HC Autopsy has
  moved away from Yarn mappings and now aligns with the Lifetime Stat Tracker
  donor pipeline's official/Mojang-name strategy. Preserve official-name source
  instead of reshaping the donor pipeline around Yarn.
- Keep automated validation ahead of Modrinth publishing: compile/build checks,
  release jar metadata checks, and launcher smoke tests must pass for every
  Minecraft version claimed by a compatibility-group profile.
- Keep `CHANGELOG.md` as the broad repo history. Keep Modrinth-facing release
  notes focused, version-specific, and user-facing in `gradle/release-notes/`.
- Critical compatibility surfaces are official-name `ServerPlayer#die`,
  `DamageSource` death-message/source/attacker APIs, player UUID/name access,
  server lifecycle events, server play connection events, server command
  registration, `CommandSourceStack` permission checks, text click/hover style
  APIs, `MinecraftServer` world-path and world-data APIs, `LevelResource`,
  stat saves, Mixin compatibility levels, Fabric metadata expansion, and the
  Minecraft `26.x` Java/non-remap lane.
