# Changelog - WorldReset v1.8

### 🚀 New Features & Improvements
* ⌨️ **Dynamic Permission Tab Completion:** Auto-completion under the `Tab` key now dynamically checks player permissions. Regular players only see commands they can execute, while administrative commands remain hidden.
* 📖 **Filtered `/wr help`:** The in-game help menu now displays only the command categories and instructions that the player has permission to use.
* 🔓 **Root Command Access for Players:** Removed the global `worldreset.admin` requirement from `/wr`, allowing non-admin players to execute permitted sub-commands (such as `/wr limbo` or `/wr help`).
* 🌌 **Accurate Speedrun Portal Detection:** Portal timer goals now trigger upon actual dimension teleportation (`PlayerTeleportEvent`) rather than initial block contact. Includes safeguards against cancelled teleports, command teleportation (`/tp`), and correctly registers returning from The End (`OVERWORLD`).
* 💀 **Death Screen Limbo Protection:** Players on the death screen cannot be transferred to limbo, preventing respawn camera locks and inventory corruption.
* ⏳ **Persistent Limbo State Across Disconnects:** Players who disconnect while in Limbo now remain in Limbo upon reconnecting and safely preserve their saved game world inventory and position.

---

### ❤️ Special Thanks
* A huge thank you to **[@yuzzzie](https://github.com/yuzzzie)** for contributing pull requests, highlighting important edge cases, and helping shape this release!
