# Changelog - WorldReset v1.6fix

### 🐛 Fixed Bugs & Technical Improvements
* 🏆 **Achievements & Stats Full Reset:** Online players get stats/achievements cleared in memory; offline players' files in `stats/` and `advancements/` are wiped on disk.
* 🚪 **Offline Players Join Fix:** Wiping `playerdata/` files on reset forces returning offline players to be treated as new, preventing inventory leaks and spawn location bugs.
* 💼 **Ender Chest Clearing:** Wipes Ender Chest inventories during resets (both in memory and in offline player data files).
* 🗺️ **Vanilla Map & Raid Resets:** Wipes `world/data/` contents on reset, starting new map IDs from 0 and clearing active raids.
* 📊 **RAM Scoreboard Cleanse:** Unregisters all objectives and teams from the server's scoreboard upon reset to prevent RAM bloat.
* 💾 **Integrated Metadata Backups:** Backups now include `playerdata`, `stats`, `advancements`, and `data` folders.
* 📦 **Ender Chest Backup & Restore:** Online players' Ender Chests are saved in `players.yml` and restored in RAM upon loading a backup without kicking players.
* 🛡️ **Compilation Error Fix:** Added missing `AdvancementProgress` import to fix build issues.
* ⏱️ **Translatable Delay-Out Countdown:** Replaced hardcoded English text in `/wr reset` with the `reset-scheduled-with-delayout` translation key for Polish and English.

