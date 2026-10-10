# Immortality

**Take control over death itself.**

Immortality is a lightweight, server-side Fabric mod that lets you decide who lives and who doesn't. Grant players the ability to survive fatal damage, manage immortality through simple commands, and configure how immortality works on your server.

**No client installation required.**

---

## Features

- **Per-player immortality** — Grant or revoke immortality for individual players or multiple players using selectors.
- **Timed immortality** — Grant immortality for a specific duration, or make it permanent.
- **Global immortality** — Enable or disable immortality server-wide.
- **Configurable defaults** — Choose whether players are immortal by default.
- **Status and player lists** — Check immortality status and list active grants.
- **Persistent configuration** — Settings and immortality grants are saved automatically.
- **Expiration options** — Configure expiration display and notifications.
- **Totem compatibility** — Totems of Undying continue to work normally.
- **Lightweight and server-side** — Players don't need to install the mod on their clients.

---

## Commands

All commands use the `/immortal` root.

### Player immortality

| Command                                           | Description                                 |
| ------------------------------------------------- | ------------------------------------------- |
| `/immortal`                                       | Toggle immortality for yourself             |
| `/immortal set <player> <true\|false> [duration]` | Grant or revoke immortality                 |
| `/immortal get <player>`                          | Check a player's immortality status         |
| `/immortal status [player]`                       | View your status or another player's status |
| `/immortal list`                                  | List players with immortality grants        |

The `player` argument supports Minecraft selectors such as `@a` and `@p` where applicable.

### Global controls

| Command                           | Description                                     |
| --------------------------------- | ----------------------------------------------- |
| `/immortal global`                | Toggle global immortality                       |
| `/immortal global <true\|false>`  | Explicitly enable or disable global immortality |
| `/immortal default <true\|false>` | Set the default immortality state for players   |
| `/immortal info`                  | View immortality configuration information      |
| `/immortal reload`                | Reload the configuration                        |

### Timed immortality

You can grant immortality for a limited duration by adding a duration to `/immortal set`.

Examples:

- `/immortal set Steve true 30m` — Immortality for 30 minutes.
- `/immortal set Steve true 2h` — Immortality for 2 hours.
- `/immortal set Steve true 1d` — Immortality for 1 day.
- `/immortal set Steve true 1h30m` — Immortality for 1 hour and 30 minutes.

Supported duration units include:

- `t` — Ticks
- `s` — Seconds
- `m` — Minutes
- `h` — Hours
- `d` — Days
- `w` — Weeks

Omitting the duration grants permanent immortality. Setting the value to `false` revokes the grant.

### Configuration controls

Use `/immortal gamerule` to view and change supported settings:

- `global` — Global immortality mode.
- `default` — Whether players are immortal by default.
- `allowTimed` — Allow timed immortality grants.
- `showExpiration` — Display grant expiration information.
- `notifyExpiration` — Notify players when their immortality expires.

For example:

`/immortal gamerule allowTimed false`

Disables timed immortality grants.

---

## How It Works

When an immortal player would die, Immortality prevents the death and keeps the player alive at **half a heart**.

Immortality does not prevent damage itself. Players can still take damage; the mod intervenes when that damage would otherwise kill them.

Totems of Undying retain their normal behavior.

---

## Permissions

Administrative commands are intended for server operators. The mod uses Minecraft's built-in operator permissions.

The server console can also run administrative commands.

---

## Use Cases

- Scripted Minecraft servers
- Adventure maps and custom events
- God mode for selected players
- Roleplay servers
- Testing and server administration
- Temporary protection during events

---

## Configuration and Data

The configuration file is stored at:

`config/immortality.json`

Settings and immortality grants are persisted so they can be restored after a server restart.

---

## Installation

1. Install Fabric Loader on your server.
2. Install the required Fabric API modules.
3. Place the Immortality JAR in your server's `mods` folder.
4. Start the server.

**Client installation is not required.**

---

## Compatibility

Immortality is a server-side Fabric mod. Check the supported Minecraft versions listed on this Modrinth project before installing.
