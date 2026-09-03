# Changelog - WorldReset v1.8

### 🚀 New Features & Improvements
* ⌨️ **Dynamic Permission Tab Completion:** Auto-completion under the `Tab` key now dynamically checks player permissions. Regular players only see commands they can execute, while administrative commands remain hidden.
* 📖 **Filtered `/wr help`:** The in-game help menu now displays only the command categories and instructions that the player has permission to use.
* 🔓 **Root Command Access for Players:** Removed the global `worldreset.admin` requirement from `/wr`, allowing non-admin players to execute permitted sub-commands (such as `/wr limbo` or `/wr help`).
* 🌌 **Accurate Speedrun Portal Detection:** Portal timer goals now trigger upon actual dimension teleportation (`PlayerTeleportEvent`) rather than initial block contact. Includes safeguards against cancelled teleports, command teleportation (`/tp`), and correctly registers returning from The End (`OVERWORLD`).
* 💀 **Death Screen Limbo Protection:** Players on the death screen cannot be transferred to limbo, preventing respawn camera locks and inventory corruption.
* ⏳ **Persistent Limbo State Across Disconnects:** Players who disconnect while in Limbo now remain in Limbo upon reconnecting and safely preserve their saved game world inventory and position.
* 🚪 **New Players to Limbo (`/wr limbo newplayers`):** Configurable option (`limbo.new-players-to-limbo`) to prevent players joining mid-game from entering an active run; they are automatically sent to Limbo until the next game starts.
* 🛑 **Start Server in Limbo (`/wr limbo startup`):** Configurable option (`limbo.start-in-limbo`) allowing the server to boot up into Limbo waiting mode, keeping players in Limbo until an administrator starts the round.
* 🎮 **Start Game from Limbo (`/wr start`):** New command to immediately release players waiting in Limbo and launch the game without running a full world reset.
* 🌐 **Per-Player Language Settings (`/wr language` / `/wr lang`):** Players can now toggle or choose their personal language (`en`/`pl`) independently of other players and the server default. Administrators manage the server-wide default via `/wr languageall`.
* 🔕 **Per-Player Silent Mode (`/wr silent`):** Players can individually mute or unmute world reset broadcast messages on their own chat. Administrators toggle global broadcasts via `/wr silentall`.
* 💾 **Persistent User Data (`userdata.yml`):** Individual language and silent preferences are saved per UUID and protected against server-wide setting changes.
* 📈 **bStats Metrics Integration:** Added anonymous server metrics via [bStats.org](https://bstats.org) to monitor active installations and Minecraft versions.

---

### ❤️ Special Thanks
* A huge thank you to **[@yuzzzie](https://github.com/yuzzzie)** for contributing pull requests, highlighting important edge cases, and helping shape this release!
