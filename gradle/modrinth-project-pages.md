# Modrinth Project Pages

This file is the source-of-truth copy for public Modrinth project summaries and
description pages. Update it before changing live Modrinth project metadata.

## HC Autopsy

- Modrinth project name: `HC Autopsy`
- Project ID: `4eBkeUAl`
- Summary:

Capture Hardcore world wipes, full player stat snapshots, and lifetime run history on Fabric servers.

### Description Markdown

```markdown
HC Autopsy is a lightweight Fabric server mod that turns Hardcore wipes into
durable run history.

Minecraft normally treats a Hardcore death as the end of the story. Servers get
reset, stat files move around, chat scrolls away, and the details of the run can
disappear. HC Autopsy keeps a separate server-side record in your config folder,
so each wipe leaves behind the death context, vanilla stat snapshots, run
totals, and lifetime player and server history.

It works server-side on dedicated Fabric servers. Players do not need to install
the mod. When the first player dies in a run, HC Autopsy records the wipe,
snapshots every player's vanilla stat JSON, aggregates the run, updates lifetime
totals, and can optionally send a Discord webhook summary.

## Features

- Persistent Hardcore wipe history across server resets
- First-death wipe detection for active runs
- Death message, damage source, attacker, player, and world metadata
- Raw vanilla player stat snapshots for complete stat fidelity
- Stores per-run metadata, per-player snapshots, and aggregated run totals
- Maintains lifetime player and server totals across wiped runs
- `/hcautopsy` commands for status, run history, player totals, and server totals
- Console-safe tools for continuing a wiped run and recalculating lifetime totals
- Optional Discord webhook notifications for wipe summaries
- Server-only install; no client mod required

## Good for

- Hardcore Fabric servers
- Challenge and limited-life runs
- Community worlds with seasonal resets
- Post-wipe stat review
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
```
