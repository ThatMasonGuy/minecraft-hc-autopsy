# HC Autopsy

HC Autopsy is a Fabric server mod for Hardcore Minecraft run postmortems.

It detects the first player death in a run, records the wipe cause, snapshots
vanilla player stat JSON, stores per-run data, and rolls wiped runs into
lifetime player and server totals. Optional Discord webhook notifications can
post a concise wipe summary.

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

Current implementation:

- Minecraft: `1.21.11`
- Fabric Loader: `0.18.4+`
- Fabric API: required
- Java: `21+`
- Primary install: dedicated Fabric server
- Target environment: server-only

Planned migration target:

- Minecraft: `1.20` through `26.2-pre-3`, published as compatibility-group jars
- Initial profile plan: `1.20-1.20.4`, `1.20.5-1.21.10`, `1.21.11`, and
  `26.1-26.2-pre-3`
- Java: `17+` for `1.20-1.20.4`, `21+` for `1.20.5-1.21.11`, and `25+` for
  `26.x`

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Install the matching Fabric API in the server `mods` folder.
3. Place the HC Autopsy jar in the server `mods` folder.
4. Launch the server once to generate the config file.
5. Optionally configure a Discord webhook in `config/hc-autopsy/config.json`.

## How Tracking Works

On server start, HC Autopsy creates or resumes an active run for the current
world. The run id is based on the world name and a timestamp.

When a player dies, the server-side death mixin records the wipe cause and marks
the active run as wiped. After a short delay, HC Autopsy forces online player
stats to save, reads the world's vanilla stat files, saves per-player snapshots,
aggregates the run, and updates lifetime totals.

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

## Configuration

Generated config:

```json
{
  "discordWebhookUrl": "",
  "discordNotificationsEnabled": true,
  "statSaveDelayMs": 500
}
```

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

The current normal jar is written under:

```text
build/libs/
```

Profile release jars are collected under:

```text
build/release/<profile_id>/
```

The planned multi-version pipeline still needs candidate compile shims,
metadata verification, dedicated-server smoke validation, and guarded Modrinth
publishing.

## Project Docs

- `AGENTS.md` - fresh-agent workflow, verification ladder, and checkpoint rules
- `TODO.md` - current state, migration roadmap, and compatibility backlog
- `CHANGELOG.md` - repo-facing engineering history
- `COMPATIBILITY.md` - source-read compatibility risk map and profile strategy
- `gradle/compatibility-release-playbook.md` - reusable compatibility-group pipeline plan
- `gradle/version-profiles/README.md` - planned profile metadata model
- `gradle/smoke-tests.md` - planned launcher smoke-test gate
- `gradle/modrinth-publishing.md` - planned guarded publishing rules
- `gradle/modrinth-project-pages.md` - source copy for the Modrinth summary and description page

## License

The repository currently includes the CC0 1.0 Universal license text in
`LICENSE`. Fabric metadata should be reconciled with the intended release
license before publishing.
