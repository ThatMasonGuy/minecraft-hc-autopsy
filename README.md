# HC Autopsy

HC Autopsy is a **server-side Fabric mod** for Minecraft hardcore worlds.

It treats the **first death in a world as a wipe event**, captures full player stat snapshots, stores run metadata to disk, and builds lifetime aggregates so you can answer questions like:

- Who ended the run?
- How long did the world last?
- What did the server accomplish across all hardcore attempts?

---

## Features

- **Automatic run tracking per world**
  - Creates (or resumes) one active run for each world on server startup.
- **Wipe detection on first death**
  - Hooks player death events and marks a run as wiped exactly once.
- **Stat snapshot capture**
  - Saves raw per-player statistics at wipe time.
- **Run-level aggregation**
  - Produces aggregated stats for each individual wiped run.
- **Lifetime totals**
  - Maintains rolling player and server-wide lifetime aggregates.
- **Run continuation**
  - Admins can “strike” a wipe and continue a run; lifetime stats are recalculated.
- **Discord webhook notifications**
  - Optionally sends a wipe summary embed (cause, duration, headline stats).
- **Built-in commands**
  - Query run state, list prior runs, inspect details, and check totals in-game.

---

## Compatibility

From this repository’s Gradle/mod metadata:

- **Minecraft:** `1.21.11`
- **Fabric Loader:** `>=0.18.4`
- **Java:** `21+`
- **Fabric API:** required (`*` in mod metadata; built against `0.141.1+1.21.11`)

---

## Installation (Server)

1. Install a Fabric server for Minecraft `1.21.11`.
2. Ensure Java 21 is installed.
3. Build this mod (or use a prebuilt JAR).
4. Place the mod JAR in your server’s `mods/` folder.
5. Start the server once to generate default HC Autopsy config/data folders.

---

## Configuration

HC Autopsy writes config to:

```text
config/hc-autopsy/config.json
```

Default config values:

```json
{
  "discordWebhookUrl": "",
  "discordNotificationsEnabled": true,
  "statSaveDelayMs": 500
}
```

### Fields

- `discordWebhookUrl`
  - Discord incoming webhook URL. Leave blank to disable actual sends.
- `discordNotificationsEnabled`
  - Currently available in config; keep `true` when using Discord notifications.
- `statSaveDelayMs`
  - Delay before stat snapshot capture after wipe is triggered.

> Note: The current implementation checks whether a webhook URL is configured when sending notifications.

---

## Commands

Root command: `/hcautopsy`

- `/hcautopsy status`
  - Shows active run ID, world, state, duration, player count, and wipe summary if wiped.
- `/hcautopsy run last`
  - Shows details for the most recent wiped run.
- `/hcautopsy run list`
  - Lists runs (newest first), clickable entries in chat.
- `/hcautopsy run <id>`
  - Shows details for a specific run (supports partial ID matching).
- `/hcautopsy run continue <reason>` (**OP only**)
  - Continues a wiped run and recalculates lifetime totals.
- `/hcautopsy player <name> totals`
  - Shows lifetime totals for a player (currently best when player is online).
- `/hcautopsy server totals`
  - Shows server-wide lifetime totals.

---

## Data Layout

HC Autopsy stores everything under:

```text
config/hc-autopsy/
```

Structure:

```text
config/hc-autopsy/
├── config.json
├── runs/
│   ├── <world-name>__<timestamp>/
│   │   ├── metadata.json
│   │   ├── aggregated.json
│   │   └── players/
│   │       └── <uuid>.json
│   └── ...
└── lifetime/
    ├── server.json
    └── players/
        └── <uuid>.json
```

### Run ID format

Run IDs are generated as:

```text
<sanitized-world-name>__yyyyMMdd-HHmmss
```

---

## How wipe processing works

When a player dies:

1. Run manager confirms an active tracking run exists.
2. A lock prevents duplicate wipe processing from rapid near-simultaneous deaths.
3. Run is immediately marked `WIPED` with captured death metadata.
4. After a short delay, player stat files are captured and persisted.
5. Run-level and lifetime aggregates are updated.
6. Optional Discord notification is sent.

---

## Building from source

From the repo root:

```bash
./gradlew build
```

Artifacts are written to:

```text
build/libs/
```

---

## Project structure

- `src/main/java/tempeststudios/hcautopsy/HCAutopsy.java`
  - Mod entrypoint and event wiring.
- `.../lifecycle/RunManager.java`
  - Run lifecycle + wipe orchestration.
- `.../mixin/ServerPlayerEntityMixin.java`
  - Death hook.
- `.../persistence/PersistenceManager.java`
  - Disk I/O and aggregates.
- `.../command/CommandRegistry.java`
  - `/hcautopsy` command tree.
- `.../notification/DiscordNotifier.java`
  - Webhook embed sender.

---

## License

This repository currently declares **All Rights Reserved** in `fabric.mod.json`.

