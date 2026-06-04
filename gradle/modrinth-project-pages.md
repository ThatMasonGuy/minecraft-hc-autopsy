# Modrinth Project Pages

This file is the source-of-truth copy for public Modrinth project summaries and
description pages. Update it before changing live Modrinth project metadata.

## HC Autopsy

- Modrinth project name: `HC Autopsy`
- Project ID: `TODO`
- Summary:

Capture Hardcore world wipes, player stat snapshots, and lifetime postmortems.

### Description Markdown

```markdown
HC Autopsy is a Fabric server mod for Hardcore Minecraft run postmortems.

When the first player dies in a run, HC Autopsy records the wipe, preserves the
death details, snapshots every player's vanilla stat JSON, aggregates run
totals, and updates lifetime player and server totals. Months later, you can
still answer who wiped the world, what happened, how long the run lasted, and
what the server achieved overall.

## Features

- Detects the first death of a run as the wipe
- Records wipe cause, death message, player, damage source, and attacker details
- Snapshots raw vanilla player stat JSON for complete stat fidelity
- Stores per-run metadata, per-player snapshots, and aggregated run totals
- Maintains lifetime player and server totals across wiped runs
- Provides `/hcautopsy` commands for run history and totals
- Supports continuing a wiped run and recalculating lifetime totals
- Optional Discord webhook notification for wipe summaries

## Good for

- Hardcore servers
- Challenge runs
- Community worlds with seasonal resets
- Post-run stat review
- Long-term server history
- Discord communities that want automatic wipe summaries

## Install note

Install Fabric Loader, Fabric API, and the HC Autopsy jar that matches your
Minecraft version.

HC Autopsy is designed for server-side use. Install it in the server `mods`
folder. It does not need to be installed on players' clients.
```
