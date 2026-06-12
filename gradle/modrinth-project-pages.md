# Modrinth Project Pages

This file is the source-of-truth copy for public Modrinth project summaries and
description pages. Update it before changing live Modrinth project metadata.

## HC Autopsy

- Modrinth project name: `HC Autopsy`
- Project ID: `4eBkeUAl`
- Summary:

Capture Hardcore wipes, player stat snapshots, leaderboards, and lifetime run history on Fabric servers.

### Description Markdown

```markdown
HC Autopsy is a lightweight Fabric server mod that turns Hardcore wipes into
durable run history, lifetime totals, and in-game postmortem tools.

Minecraft normally treats a Hardcore death as the end of the story. Servers get
reset, stat files move around, chat scrolls away, and the details of the run can
disappear. HC Autopsy keeps a separate server-side record in a fixed Tempest
Studios app-data folder, so each wipe leaves behind the death context, vanilla
stat snapshots, run totals, cached player names, and lifetime player and server
history.

It works server-side on dedicated Fabric servers. Players do not need to install
the mod. When the first player dies in a run, HC Autopsy records the wipe,
snapshots every player's vanilla stat JSON, aggregates the run, updates lifetime
totals, broadcasts an in-game wipe summary, and can optionally send a Discord
webhook summary.

## Features

- Persistent Hardcore wipe history across server resets
- First-death wipe detection for active runs
- Death message, damage source, attacker, player, and world metadata
- Raw vanilla player stat snapshots for complete stat fidelity
- Stores per-run metadata, per-player snapshots, aggregated run totals, and
  cached player names
- Maintains lifetime player and server totals across wiped runs
- In-game wipe summary after stat capture completes
- `/hcautopsy` commands for status, run history, cached players, player totals,
  server totals, and lifetime leaderboards
- Operator/console-safe tools for continuing a wiped run, recalculating
  lifetime totals, reloading config, and testing Discord webhooks
- Optional Discord webhook notifications for wipe summaries
- Launcher-agnostic app-data storage with first-launch migration from the old
  `config/hc-autopsy/` folder
- Defensive config/data loading and atomic writes for persisted HC Autopsy data
- Server-only install; no client mod required

## Good for

- Hardcore Fabric servers
- Challenge and limited-life runs
- Community worlds with seasonal resets
- Post-wipe stat review
- Comparing lifetime playtime, deaths, distance walked, and jumps
- Long-term server history
- Discord communities that want automatic wipe summaries
- Players who want the receipts after a run goes wrong

## Install note

Install Fabric Loader, Fabric API, and the HC Autopsy jar that matches your
Minecraft version.

HC Autopsy is designed for server-side use. Install it in the dedicated Fabric
server `mods` folder. It does not need to be installed on players' clients.

Choose the compatibility-group jar that matches your server:

- `1.20-1.21.11`
- `26.1-26.2-pre-3`

HC Autopsy stores config and run history outside launcher-local `.minecraft`
folders:

- Windows: `%APPDATA%\TempestStudios\HC-Autopsy\`
- macOS: `~/Library/Application Support/TempestStudios/HC-Autopsy/`
- Linux: `$XDG_DATA_HOME/tempest-studios/hc-autopsy/` or
  `~/.local/share/tempest-studios/hc-autopsy/`

On first launch, existing `config/hc-autopsy/` data is copied to the new
app-data folder when the new folder is empty. Old files stay in place as a
backup.
```
