❤️ **If you enjoy this plugin, please consider leaving a like! It means a lot for me.** ❤️

> **⚠️ Compatibility Note:** This plugin is built for **Minecraft 1.21+ Purpur**. Tested on Purpur 1.21, 1.21.4, 26.1.2. It should work on Bukkit and Paper.

If you want to report a bug or suggest a new plugin, join my [Discord server](https://discord.gg/A7WVnYj3BP).

Also check my other plugin **[SharedHealthAndHunger](https://modrinth.com/plugin/sharedhealthandhunger)**

# 🌍 WorldReset

**Reset your world instantly without restarting the server! Perfect for Speedruns, Manhunts, and Challenges.**

WorldReset is a plugin designed to manage game worlds dynamically. Instead of kicking players and restarting the server to generate a new map, this plugin moves players to a "Limbo" world, deletes the old map, generates a fresh one, and teleports everyone back—all within seconds!

### ✨ Key Features

* **🔄 Instant Reset:** Regenerates Overworld, Nether, and End without shutting down the server.
* **🌱 Seed Control:** Choose between **Random Seed** for a fresh experience or **Fixed Seed** for practice/speedruns.
* **📁 World Templates (Custom Maps):** Load your own custom worlds instead of generating them! Put your map folders into `WorldReset_Templates`. The plugin automatically classifies dimensions (Overworld, Nether, End), auto-converts Singleplayer world structures (moving `DIM-1` and `DIM1` folders to Spigot standards), cleans up temporary files, and prevents UUID conflicts. Multiple overworld templates are randomly selected each reset. Includes a fail-safe backup seed.
* **⏰ AutoReset Scheduler:** Schedule automated periodic resets! Set loop intervals (e.g. `60s`, `5m`, `1h`) and control the countdown. The paused countdown remains on screen in gray to keep players informed.
* **🏁 Multi-Goal Speedrun Stopwatch:** Built-in Action Bar stopwatch! Race in **Global** mode (first to finish stops the timer) or **Individual** mode (personal timers). Support for RTA (Real-Time) and IGT (In-Game Time). Set triggers for Portal Entries, Entity Kills, Advancements, Block Breaking (`BLOCK`), and Item Collection (`ITEM`)! Dynamic autocomplete suggestions are loaded directly from the game registries.
* **📈 Leaderboards & Records Database:** Built-in `records.yml` database tracks attempts, completions, PBs, and average times. Tracks a **Top 10 Highscore Leaderboard** with player names, record times, dates, and world seeds.
* **📊 Vanilla Scoreboard Integration:** Plug-and-play synchronization with **39 vanilla scoreboard objectives**! Track and display speedrun metrics (attempts, completions, deaths, average times, personal bests, server records, timer live seconds/minutes, seed, world name, active players, difficulty, active goal, death reset, etc.) using built-in Minecraft commands, completely out of the box!
* **🧩 PlaceholderAPI (PAPI) Support:** Integrates with PlaceholderAPI to provide **29 dynamic placeholders** for your tab lists, sidebar plugins, or hologram displays (timer, goals, finished players, current seeds, active filters, difficulty, leaderboards, deaths).
* **🏹 Spawn Shifter:** Never reset for a good seed again! Configure a target **Structure** (e.g., Village) or **Biome** (e.g., Cherry Grove). The plugin scans the registry dynamically to move your spawn directly to your target. Underground structures (Stronghold, Ancient City, Mineshaft, Trial Chambers) spawn you inside the structure!
* **🏝️ Smart Land Seeker:** No more spawning in the middle of the ocean! After generating a new world (or shifting spawn), the plugin geometrically calculates the biome center and spirals outward to find dry land. It uses a strict 4-way water verification (32-block radius) to ensure ocean islands are actually surrounded by water (cutting off attached peninsulas). Spread across server ticks, it never blocks the main thread.
* **🎁 Auto Give:** Automatically gives a boat when spawning on actual water biomes, and wood when spawning underground (Y is below surface). Configurable via `/wr give boat <enable|disable>` and `/wr give wood <amount|enable|disable>`.
* **☁️ Seamless Limbo:** Players are moved to a waiting world ("Limbo") during generation. Configurable countdown delays with on-screen timer and sound effects give players a heads-up before teleports.
* **⏳ Limbo Countdown Delays:** Set automatic delays (in seconds) for entering and leaving Limbo. Players see a visual countdown with adaptive intervals and sounds, but can keep playing until it finishes. Use `/wr limbo <seconds>` for manual delayed toggle, or `/wr limbo delay <in> <out>` to configure global automatic delays.
* **🧭 Native Locator Bar:** Toggle Minecraft's built-in multiplayer **Locator Bar** (1.21.6+) directly with `/wr compass enable/disable`. No custom overlays — pure vanilla.
* **🌍 Multi-Language:** Full support for **English** and **Polish** (changeable via command).

---

### 📜 Commands and Permissions

Main command: `/worldreset` or `/wr`

| Command | Description | Permission |
| --- | --- | --- |
| `/wr reset` | Instantly resets the game: moves everyone to Limbo, regenerates the world, and starts a new game. | `worldreset.reset` |
| `/wr reset <in> <out>` | Reset with countdown before (delay-in) and countdown in Limbo before game starts (delay-out). | `worldreset.reset` |
| `/wr limbo` | Toggles Limbo mode for all players. Moves everyone to Limbo or back to game. Also skips active countdowns. | `worldreset.limbo.all` |
| `/wr limbo me` | Same but only for yourself. Aliases: `m`, `ja`, `j`. | `worldreset.limbo.self` |
| `/wr limbo <player>` | Toggle Limbo for a specific player. Also accepts `all`. | `worldreset.limbo.others` |
| `/wr limbo <seconds> [player]` | Toggle with countdown. Target defaults to all if omitted. | `.others` or `.all` |
| `/wr limbo delay <in> <out>` | Sets global automatic delays (seconds) for death-reset enter/leave Limbo. | `worldreset.limbo.all` |
| `/wr death` | Toggles **Reset on Death** mode (Hardcore) ON/OFF. | `worldreset.death` |
| `/wr silent` | Toggles **Silent Mode** (hides global chat messages) ON/OFF. | `worldreset.silent` |
| `/wr filter` | Shows current active filters and seed status. | `worldreset.filter.use` |
| `/wr filter structure <name>` | Sets a target structure (e.g., VILLAGE). Auto-clears biome filter. | `worldreset.filter.config` |
| `/wr filter biome <group/name> [specific_biome]` | Sets a target biome or group (e.g., OCEANS). Auto-clears structure filter. | `worldreset.filter.config` |
| `/wr filter clear` | Instantly clears and disables all biome/structure filters. | `worldreset.filter.config` |
| `/wr seed <value>` | Sets a fixed seed for future resets. | `worldreset.seed.config` |
| `/wr seed` | Disables fixed seed (enables Random Seed mode) or toggles state. | `worldreset.seed.use` |
| `/wr give boat <enable/disable>` | Automatically gives a boat when spawning on water. | `worldreset.give` |
| `/wr give wood <amount/enable/disable>` | Automatically gives wood when spawning underground. | `worldreset.give` |
| `/wr templates <enable/disable>` | Toggles whether custom maps should load from the templates folder. | `worldreset.templates` |
| `/wr autoreset <start/stop/disable>` | Controls automated periodic world resets toggles. | `worldreset.autoreset.use` |
| `/wr autoreset <loop/time>` | Configures automated periodic world resets rules. | `worldreset.autoreset.config` |
| `/wr autoreset status` | Shows current autoreset state, time, loop, visibility. | `worldreset.autoreset.use` |
| `/wr timer <enable/disable>` | Turns the speedrun stopwatch system ON/OFF. | `worldreset.timer.use` |
| `/wr timer <start/pause/reset>` | Controls the built-in speedrun stopwatch. | `worldreset.timer.use` |
| `/wr timer <mode/scope/goal>` | Configures timer settings (RTA/IGT, Global/Individual, end triggers). | `worldreset.timer.config` |
| `/wr compass` | Toggles the native Minecraft **Locator Bar** ON/OFF. | `worldreset.compass` |
| `/wr scoreboard [true/false/on/off/status]` | Manages scoreboard clearing on reset (toggle when no argument). | `worldreset.scoreboard.use` |
| `/wr scoreboard reset [player]` | Clears permanent stats and records for a player (or all). | `worldreset.scoreboard.admin` |
| `/wr language <en/pl>` | Changes the plugin language (English / Polish). | `worldreset.language` |
| `/wr backup <enable/disable>` | Toggle backup creation system. | `worldreset.backup.create` |
| `/wr backup <status/list>` | Manage world backups view status. | `worldreset.backup.list` |
| `/wr backup load <number>` | Load a backup (restores world + player states). | `worldreset.backup.admin` |
| `/wr backup clear [count]` | Delete all or N oldest backups. | `worldreset.backup.admin` |
| `/wr backup limit` | Configure backup retention limit. | `worldreset.backup.admin` |
| `/wr help [command]` | Shows help menu or detailed usage for a specific command. | — |
| `/wr reload` | Reloads configuration and language files instantly. | `worldreset.config.reload` |

**Command Aliases:** All `enable`/`disable` arguments also accept `on`/`off` and `true`/`false`.

**Wildcard Permissions:** 
For easier moderation, you can use wildcard nodes. For example, `worldreset.timer.*` grants all timer permissions. Available wildcards:
`worldreset.backup.*`, `worldreset.limbo.*`, `worldreset.timer.*`, `worldreset.autoreset.*`, `worldreset.seed.*`, `worldreset.filter.*`.

**Full Admin Permission:** `worldreset.*` (Inherits everything above).

---

### 🚀 Installation

1. Download the `.jar` file.
2. Place it in the `/plugins/` folder of your server (Spigot/Paper/Purpur **1.21+**).
3. **Restart** the server.
4. Done! The plugin will generate the `limbo` world and the `game_world`.

* *Tip: To use your own custom lobby, simply stop the server, delete the generated `limbo` folder, and upload your own world folder named `limbo`.*

---

### ❤️ Credits

The default Limbo map is **Floating Island Sanctuary**.
All credits go to the original creator.
**[Download Original Map Here](https://www.planetminecraft.com/project/floating-island-sanctuary/)**