# Changelog - WorldReset v1.5

### ✨ New Features
* 📁 **World Templates Loader:** Place your custom maps into `WorldReset_Templates/`. The plugin automatically detects singleplayer format, converts dimension folders, prevents UUID conflicts, and loads them safely. Supports relative and absolute custom template paths.
* 💾 **Records Database & Highscores:** Built-in `records.yml` database tracks attempts, completions, PBs, and average times. Manages a server-wide Top 10 Leaderboard.
* 📋 **Native Scoreboard Integration:** Real-time synchronization with **38 native scoreboard objectives** (PBs, records, active players, seed, world name, etc.) completely out of the box.
* 🧩 **PlaceholderAPI Support:** Dynamically registers **28 placeholders** for timer states, seeds, active filters, and top leaderboards.
* 🏁 **Block & Item Stopwatch Goals:** Stopwatch can now stop on block breaking (`BLOCK`) or item collection (`ITEM`), with automatic registry-based TAB autocompletion.

### 📜 New Commands
* 🧹 **`/wr filter clear`:** Instantly clears all structure/biome spawn filters.
* ⚙️ **`/wr timer <enable/disable>`:** Turns the speedrun stopwatch system ON/OFF.
* 📚 **Overhauled Help Menu:** Redesigned and fully localized in-game command usage instructions (`/wr`).

### 🐛 Fixed Bugs & Optimizations
* 🌬️ **Windows File Lock Fix:** Solved the Java world folder locking issue (`session.lock` / region files) on Windows systems.
* 🚪 **Safe Portal Travel:** Fixed dimension coordinate math in portals to prevent player suffocation in netherrack or falling in lava.
* 🚀 **Portal Entity Support:** Non-player entities, items, and Ender Pearls now pass through portal dimensions cleanly.
* ⚙️ **Difficulty Path Fix:** Corrected server difficulty reading path from `server.properties`.
