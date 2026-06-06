# Changelog - WorldReset v1.6

### 🆕 New Features
* ⏳ **Limbo Pause & Countdown System:** `/wr limbo` pauses all players (saves full state). Target with `me`/`<player>`/`all`. Optional countdown with `/wr limbo <seconds> [player]`. `/wr limbo delay <in> <out>` for automatic delays.
* ⏱️ **`/wr reset [delay-in] [delay-out]`:** Optional countdown before reset and countdown in limbo before game starts. Both arguments optional.
* 💾 **`/wr backup <enable|disable|status|limit>`:** Full backup management in-game. Status shows enabled state, limit, and existing backup count.
* 📁 **`/wr templates <enable|disable|folder|status>`:** Full command for managing world templates in-game.
* 🎲 **Random Template Selection:** Multiple overworld/nether/end templates in the templates folder are now randomly selected each reset.
* 📋 **`/wr help` Redesign:** Categorized layout (Game, Timer & AutoReset, World, System). `/wr help <command>` shows detailed usage for a specific command.
* 🔤 **`on`/`off` Aliases:** All enable/disable commands also accept `on`/`off` and `true`/`false`.

### 🐛 Fixed Bugs & Technical Improvements
* 🧹 **`/wr filter clear`:** Now also disables fixed seed.
* 🗑️ Removed unused BossBar imports.
