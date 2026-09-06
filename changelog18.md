# Changelog - WorldReset v1.8
### ❤️ Special Thanks
* A huge thank you to **[@yuzzzie](https://github.com/yuzzzie)** for contributing pull requests, highlighting important edge cases, and helping shape this release!
---

### 🚀 New Features
* 🏳️ **German Language Support:** Added full German translation across the entire plugin. Thanks to data from [FastStats.dev](https://faststats.dev).
* 🌐 **Per-Player Language Settings (`/wr language` / `/wr lang`):** Players can now choose their personal language (`en`/`de`/`pl`) independently of other players and the server default. Administrators manage the server-wide default via `/wr languageall`.
* 🔕 **Per-Player Silent Mode (`/wr silent`):** Players can individually mute or unmute world reset broadcast messages on their own chat. Administrators toggle global broadcasts via `/wr silentall`.
* 🎯 **Dynamic Timer Goal Announcements:** Starting the timer now shows a clean on-screen title and subtitle with the exact objective (e.g. "Enter the Nether first", "Kill: Ender Dragon") and announces it in chat in each player's chosen language.
* 📈 **bStats Metrics Integration:** Added anonymous server metrics via [bStats.org](https://bstats.org) to monitor active installations and Minecraft versions. I want to compare it with [FastStats.dev](https://faststats.dev).
* 🚪 **New Players to Limbo (`/wr limbo newplayers`):** Configurable option (`new-players-to-limbo`) to prevent players joining mid-game from entering an active run; they are automatically sent to Limbo until the next game starts.
* 🛑 **Start Server in Limbo (`/wr limbo startup`):** Configurable option (`start-in-limbo`) allowing the server to boot up into Limbo waiting mode, keeping players in Limbo until an administrator starts the round.
* 🎮 **Start Game from Limbo (`/wr start`):** New command to immediately release players waiting in Limbo and launch the game without running a full world reset.
* 🚪 **Selective & Global Limbo (`/wr limbo` / `/wr limboall`):** Separated Limbo management into selective `/wr limbo [player]` (toggle self or specific player) and global `/wr limboall [seconds]` (dedicated switch to send active players into Limbo). Added intelligent, permission-aware and silent-mode-aware welcome notices.

### 🐛 Improvements
* ⌨️ **Dynamic Permission Tab Completion:** Auto-completion under the `Tab` key now dynamically checks player permissions. Regular players only see commands they can execute, while administrative commands remain hidden.
* 📖 **Filtered `/wr help`:** The in-game help menu now displays only the command categories and instructions that the player has permission to use.
* 🔓 **Root Command Access for Players:** Removed the global `worldreset.admin` requirement from `/wr`, allowing non-admin players to execute permitted sub-commands (such as `/wr limbo` or `/wr help`).
* 🌌 **Accurate Speedrun Portal Detection:** Portal timer goals now trigger upon actual dimension teleportation (`PlayerTeleportEvent`) rather than initial block contact. Includes safeguards against cancelled teleports, command teleportation (`/tp`), and correctly registers returning from The End (`OVERWORLD`).
* ⏳ **Smart Limbo Reconnect Handling & Persistent Storage:** Comprehensive reconnect management and state preservation across all gameplay flows:
  * **Persistent Disk Storage (`limbo_players.yml`):** Player states saved in Limbo (coordinates, inventory, armor, offhand, enderchest, potion effects, stats) are saved to YAML on disk, ensuring total protection across server crashes or reboots.
  * **Active Game Protection (Scenario A):** Returning participants are never blocked by `new-players-to-limbo`. Disconnecting in the Nether or The End keeps players exactly where they were with their full equipment.
  * **Global Limbo Hold (Scenario B):** Offline players reconnecting while the server is in Limbo (after `/wr limboall`) have their in-game state safely preserved to disk and join Limbo until `/wr start` releases everyone.
  * **New Game Clean Slate (Scenario C):** Resetting a world cleanly purges old session states, preventing item bleeding into new games.
  * **Death Disconnect Safeguard:** Players disconnecting on the death screen cleanly respawn at full health and hunger upon reconnecting.
* ⚡ **Difficulty Cache & Console Optimization:** Eliminated redundant console spam (`Loaded difficulty from server.properties`) by reading world difficulty directly from the active world in RAM instead of reading disk on every scoreboard update.
* 🔄 **Safe Language Updates (`.yml.old`):** If a language file on disk lacks newly introduced keys, the plugin automatically archives the old file to `<file>.old` and deploys the fresh default from the JAR without data loss.


