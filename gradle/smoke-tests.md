# Smoke Test Matrix

Smoke automation is planned but not implemented yet.

The future smoke matrix should record launcher smoke-test status for each
release compatibility profile. A profile is publishable only after the packaged
jar has launched on every exact Minecraft version listed by that profile.

## Status Meanings

- `pass`: this exact profile jar has launched on this exact Minecraft version.
- `pending`: the profile builds, but this Minecraft version still needs smoke
  testing.
- `fail`: this Minecraft version has been tested and currently fails.

Only profiles listed in `supported_minecraft_version_profiles` are publishable.
Profiles listed in `candidate_minecraft_version_profiles` must stay unpublished
until every exact runtime they claim has passing smoke evidence.

## Primary Server Smoke

HC Autopsy is server-only, so the dedicated-server smoke test is the launcher
gate.

Each automated dedicated-server smoke launch should:

- install the packaged release jar
- launch the exact dedicated Minecraft server runtime
- use an isolated smoke run directory with accepted EULA and low-cost
  `server.properties`
- reach the server tick loop
- verify `/hcautopsy` is registered
- verify config and persistence initialization
- verify an active run was created or resumed
- log a clear pass marker such as `HCAUTOPSY_SERVER_SMOKE_TEST_PASS`
- exit cleanly

## Planned Install Sets

Initial install sets:

- `hc-autopsy-server-only`: jar installed on a dedicated Fabric server

## Planned Commands

```powershell
.\gradlew.bat verifySmokeTestMatrix
.\gradlew.bat smokeTestSupportedServers
.\gradlew.bat smokeTestValidationServers
.\gradlew.bat smokeTestSelectedServers "-Phcautopsy_smoke_profiles=1.21.11" "-Phcautopsy_smoke_game_versions=1.21.11"
.\gradlew.bat smokeTestSupported
.\gradlew.bat smokeTestValidation
.\gradlew.bat publishValidation
.\gradlew.bat ciValidation
```

Dedicated-server smoke should not require `xvfb`.

Smoke logs should be written under:

```text
build/smoke-logs/<profile>/<game_version>/<install_set>.log
```

Run directories should be isolated under:

```text
build/smoke-run/
```

## Promotion Rule

After a candidate profile passes required smoke testing on every version in
`modrinth_game_versions`, update its matrix records to `pass`. To make that
profile publishable, move it from `candidate_minecraft_version_profiles` to
`supported_minecraft_version_profiles`, then run the full release validation.
