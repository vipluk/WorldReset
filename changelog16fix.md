# Changelog - WorldReset v1.6fix

WorldReset v1.6fix focuses on ensuring a complete reset of all player data, statistics, achievements, Ender Chests, maps, and scoreboards, while integrating them fully with the backup and restore system.

### 🐛 Fixed Bugs & Technical Improvements
* 🏆 **Achievements & Stats Full Reset:** Online players get their achievements and statistics fully reset in RAM upon world reset, while files in `playerdata/`, `stats/`, and `advancements/` are wiped on disk to clean up offline players.
* 🚪 **Offline Players Inventory & Join Fix:** Solved a critical bug where players logging off in `game_world` before a reset would bypass spawn teleportation and initialization on their next join. Wiping `playerdata/` files on reset forces Spigot to treat them as new players, placing them safely at spawn.
* 💼 **Ender Chest Clearing:** Ender chest inventories are now completely wiped during resets (both in-memory for online players and on disk inside player data files for offline players).
* 🗺️ **Vanilla Map & Raid Resets:** Wipes `world/data/` directory contents on reset, clearing written maps (`map_*.dat`), map counters (`idcounts.dat`), and active raids (`raids.dat`). New maps correctly start from ID `0`.
* 📊 **RAM Scoreboard Cleanse:** Unregisters all teams and objectives from the server's main scoreboard on reset, preventing leftover speedrun objectives or database bloat.
* 💾 **Integrated Metadata Backups & Ender Chest Hotfix:** Enhanced the backup (`performBackup`) and restore (`/wr backup load`) systems to include `playerdata`, `stats`, `advancements`, and `data` directories. Online players' Ender Chests are now saved in-memory within the player state snapshots (`players.yml`) and restored in RAM upon backup loading, completely eliminating the need to kick players. Additionally, `player.saveData()` is called before backing up to ensure disk sync.
* 🛡️ **Compilation Error Fix:** Resolved a compilation error by adding the missing import for `org.bukkit.advancement.AdvancementProgress` in `Main.java`.
* ⏱️ **Translatable Delay-Out Countdown:** Replaced the hardcoded English `(delay-out: Xs)` suffix in `/wr reset [delay-in] [delay-out]` with a dedicated translatable message key `reset-scheduled-with-delayout` in both English and Polish, enabling custom formats like `Reset scheduled in X seconds, exit in Y seconds.`.
