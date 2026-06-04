# Modrinth Publishing

Modrinth publishing is planned but not implemented yet. The intended model is
guarded Gradle tasks plus a manual GitHub Actions workflow with dry-run default.

## Publishing Model

Publishing should be driven by supported Minecraft version profiles only.
Profiles in `candidate_minecraft_version_profiles` must be ignored until they
are promoted to `supported_minecraft_version_profiles`.

Modrinth project id: `TODO`.

Fabric API dependency project id: `P7dR8mSH`.

When only one supported profile exists, Modrinth `version_number` can be the mod
version, such as `1.0.0`.

When multiple supported profiles exist, append the profile id to keep Modrinth
version entries unique, such as:

```text
1.1.0+mc1.21.11
1.1.0+mc1.20-1.21.11
1.1.0+mc26.1-26.2-pre-3
```

## Planned Gradle Tasks

```powershell
.\gradlew.bat publishValidation
.\gradlew.bat prepareModrinthUploads
.\gradlew.bat publishModrinthDryRun
.\gradlew.bat publishModrinth -Pmodrinth_confirm_publish=true
```

- `publishValidation` should build and smoke-test supported profiles only.
- `prepareModrinthUploads` should run validation, verify upload metadata, and
  write `build/modrinth/upload-plan.json`.
- `publishModrinthDryRun` should perform the full validation path without
  calling the Modrinth API.
- `publishModrinth` should perform the real upload and require
  `-Pmodrinth_confirm_publish=true`.

The upload plan should include:

- project id
- loader `fabric`
- exact game versions from the supported profile's `modrinth_game_versions`
- required Fabric API dependency `P7dR8mSH`
- release notes from `gradle/release-notes/<mod_version>.md`
- the packaged jar from `build/release/<profile_id>/`

## Secrets

Real uploads require a Modrinth personal access token with version-create
permission. Provide it through a non-repo location:

```powershell
$env:MODRINTH_TOKEN="..."
.\gradlew.bat publishModrinth -Pmodrinth_confirm_publish=true
```

or a user-level Gradle property such as `%USERPROFILE%\.gradle\gradle.properties`:

```properties
modrinth_token=...
```

Do not store tokens in this repository. `.env` is ignored for local secret
management, but tasks should not print token values.

GitHub publishing should read the repository secret named `MODRINTH_TOKEN`.

## Planned GitHub Workflow

Use a manual `modrinth publish` workflow in `.github/workflows/`.

Inputs:

- `dry_run`: keep this enabled to validate and print the upload plan without
  publishing.
- `version_type`: `release`, `beta`, or `alpha`.
- `requested_status`: `listed`, `unlisted`, or `draft`.

The workflow should install required Java toolchains, run supported-profile
validation, and capture upload plans, release jars, smoke logs, smoke mod lists,
smoke run directories, and reports as artifacts.

## Git Tags And GitHub Releases

After a real Modrinth publish succeeds, create an annotated Git tag for the
released `mod_version`:

```powershell
git tag -a "v1.0.0" <publish-workflow-head-sha> -m "HC Autopsy 1.0.0"
git push origin "v1.0.0"
```

The tag must point at the exact commit used by the successful publish workflow.
For a GitHub Actions publish, use the workflow run's `headSha`. Do not tag a
later docs-only, project-page, or release-record commit as the released source.

Then create one GitHub Release for that tag:

```powershell
gh release create "v1.0.0" --verify-tag --title "HC Autopsy 1.0.0" --notes "<release notes>"
```

GitHub Releases are an archive and source checkpoint. Modrinth remains the
primary download surface.

## Release Notes

Modrinth changelogs should come from a concise per-version release note file:

```text
gradle/release-notes/<mod_version>.md
```

For example, `mod_version=1.1.0` should require:

```text
gradle/release-notes/1.1.0.md
```

The publish tasks should fail if the release note file is missing or blank.
Use `CHANGELOG.md` for broad repo history and
`gradle/release-notes/<version>.md` for the exact Modrinth-facing notes.

## Project Page Copy

Modrinth project summaries and long descriptions are tracked in:

```text
gradle/modrinth-project-pages.md
```

Update this file before changing live Modrinth project metadata. Publishing
tasks upload version files and per-version changelogs only; they should not
update project summary, project body, gallery, categories, or other page-level
metadata.
