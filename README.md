# HC Autopsy

HC Autopsy is a server-side Fabric mod for Hardcore Minecraft run postmortems.

It treats the first death in a world as a wipe event, captures full player stat
snapshots, stores run metadata to disk, and builds lifetime aggregates so you
can answer questions like:

- Who ended the run?
- How long did the world last?
- What did the server accomplish across all hardcore attempts?

## Features

- Detects the first player death as the run wipe
- Records who died, the death message, damage source, and attacker details
- Snapshots raw vanilla stat JSON from the world's `stats` directory
- Stores per-run metadata, per-player snapshots, and aggregated run totals
- Maintains lifetime player and server totals across wiped runs
- Allows an operator or console to continue a wiped run and recalculate totals
- Provides `/hcautopsy` commands for run status, run history, and totals
- Supports optional Discord webhook notifications

## Supported Environment

Supported release profiles:

- Minecraft: `1.20` through `1.21.11` as profile `1.20-1.21.11`
- Minecraft: `26.1`, `26.1.1`, `26.1.2`, and `26.2-pre-3` as profile
  `26.1-26.2-pre-3`
- Fabric Loader: profile-derived
- Fabric API: required
- Java: `17+` for `1.20-1.21.11`; `25+` for `26.1-26.2-pre-3`
- Primary install: dedicated Fabric server
- Target environment: server-only

The default local development profile remains `1.21.11`.

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Install the matching Fabric API in the server `mods` folder.
3. Place the HC Autopsy jar in the server `mods` folder.
4. Launch the server once to generate the config file.
5. Optionally configure a Discord webhook in `config/hc-autopsy/config.json`.

## How Tracking Works

On server start, HC Autopsy creates or resumes an active run for the current
world. The run id is based on the world name and a timestamp.

When a player dies:

1. The run manager confirms an active tracking run exists.
2. A lock prevents duplicate wipe processing from rapid near-simultaneous
   deaths.
3. The run is immediately marked `WIPED` with captured death metadata.
4. After a short delay, player stat files are captured and persisted.
5. Run-level and lifetime aggregates are updated.
6. Optional Discord notification is sent.

Only runs in the `WIPED` state contribute to lifetime totals. If a wiped run is
continued with `/hcautopsy run continue <reason>`, lifetime totals are
recalculated from all remaining wiped runs.

## Commands

Command root:

```text
/hcautopsy
```

Subcommands:

```text
/hcautopsy status
```

Shows the active run, world name, state, duration, player count, and wipe cause
when applicable.

```text
/hcautopsy run last
```

Shows the most recent wiped run.

```text
/hcautopsy run list
```

Lists recent runs and makes entries clickable in chat.

```text
/hcautopsy run <id>
```

Shows details for a run. Partial run id matches are supported by the current
implementation.

```text
/hcautopsy run continue <reason>
```

Continues a wiped run and recalculates lifetime totals. Current implementation
allows this from console or command blocks only.

```text
/hcautopsy player <name> totals
```

Shows lifetime totals for an online player. Offline name resolution is not
implemented yet.

```text
/hcautopsy server totals
```

Shows server lifetime totals across wiped runs.

## Data Files

Data is stored under:

```text
config/hc-autopsy/
```

Files and directories:

- `config.json` - Discord and snapshot timing configuration
- `runs/<world>__<timestamp>/metadata.json` - run metadata and wipe state
- `runs/<world>__<timestamp>/players/<uuid>.json` - raw vanilla stat snapshots
- `runs/<world>__<timestamp>/aggregated.json` - aggregated run stats
- `lifetime/players/<uuid>.json` - per-player lifetime totals
- `lifetime/server.json` - server-wide lifetime totals

Run IDs are generated as:

```text
<sanitized-world-name>__yyyyMMdd-HHmmss
```

## Configuration

Generated config:

```json
{
  "discordWebhookUrl": "",
  "discordNotificationsEnabled": true,
  "statSaveDelayMs": 500
}
```

Fields:

- `discordWebhookUrl`: Discord incoming webhook URL. Leave blank to disable
  actual sends.
- `discordNotificationsEnabled`: keep `true` when using Discord notifications.
- `statSaveDelayMs`: delay before stat snapshot capture after wipe is
  triggered.

Do not commit real Discord or Modrinth tokens. Local secret files such as `.env`
are intentionally ignored.

## Development

Show the active Minecraft profile:

```powershell
.\gradlew.bat printVersionProfile --no-daemon --console=plain
```

Build the default profile with Gradle:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

Build and collect the active profile release jar:

```powershell
.\gradlew.bat buildRelease --no-daemon --console=plain
```

Run the supported dedicated-server smoke gate:

```powershell
.\gradlew.bat smokeTestSupportedServers --no-daemon --console=plain
```

Run a selected smoke launch:

```powershell
.\gradlew.bat smokeTestSelectedServers "-Phcautopsy_smoke_profiles=1.21.11" "-Phcautopsy_smoke_game_versions=1.21.11" --no-daemon --console=plain
```

Dry-run the supported-profile Modrinth upload plan without publishing:

```powershell
.\gradlew.bat publishModrinthDryRun --no-daemon --console=plain
```

The current normal jar is written under:

```text
build/libs/
```

Profile release jars are collected under:

```text
build/release/<profile_id>/
```

The multi-version pipeline builds, smoke-tests, and dry-runs Modrinth upload
plans for the supported profiles. Candidate profiles should stay unpublished
until every exact claimed Minecraft runtime has a passing dedicated-server
smoke record.

## Project Structure

- `src/main/java/tempeststudios/hcautopsy/HCAutopsy.java` - mod entrypoint and
  event wiring
- `src/main/java/tempeststudios/hcautopsy/lifecycle/RunManager.java` - run
  lifecycle and wipe orchestration
- `src/main/java/tempeststudios/hcautopsy/mixin/ServerPlayerMixin.java` -
  server-player death hook
- `src/main/java/tempeststudios/hcautopsy/persistence/PersistenceManager.java`
  - disk I/O and aggregates
- `src/main/java/tempeststudios/hcautopsy/command/CommandRegistry.java` -
  `/hcautopsy` command tree
- `src/main/java/tempeststudios/hcautopsy/notification/DiscordNotifier.java` -
  webhook sender

## Project Docs

- `AGENTS.md` - fresh-agent workflow, verification ladder, and checkpoint rules
- `TODO.md` - current state, migration roadmap, and compatibility backlog
- `CHANGELOG.md` - repo-facing engineering history
- `COMPATIBILITY.md` - source-read compatibility risk map and profile strategy
- `gradle/compatibility-release-playbook.md` - reusable compatibility-group
  pipeline plan
- `gradle/version-profiles/README.md` - profile metadata model
- `gradle/smoke-tests.md` - launcher smoke-test gate
- `gradle/modrinth-publishing.md` - guarded publishing rules
- `gradle/modrinth-project-pages.md` - source copy for the Modrinth summary and
  description page

## License

The repository currently includes the CC0 1.0 Universal license text in
`LICENSE`. Fabric metadata should be reconciled with the intended release
license before publishing.
