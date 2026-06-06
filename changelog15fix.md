# Changelog - WorldReset v1.5fix

### 🐛 Fixed Bugs & Technical Improvements
* 🌍 **Paper/Purpur 1.21+ Multi-World Support:** Redesigned world unloading, deleting, and backing up to fully support modern Paper directory structures (`.\world\dimensions\`) in addition to legacy Spigot structures.
* 🌬️ **Watchdog & Windows File Lock Delay:** Added a 20-tick (1-second) cooldown before deleting folders to let Spigot's async threads complete chunk saving and release OS file locks cleanly (fully compatible with Aikar's Flags).
* 🛡️ **Defensive Limbo Loading:** Wrapped limbo world loading in a fallback block that automatically generates a safe flat world in case of server migration conflicts, preventing server start crashes.
* 🔑 **Granular Permissions Fix:** Removed global `worldreset.admin` block in commands, allowing players to use their granular permissions (e.g. `worldreset.limbo`), and added missing checks for `reload`, `silent`, and `death`.
* 🚪 **Adventure Mode Offline Join Fix:** Solved the bug where offline players joining after game start were stuck in Limbo's Adventure mode instead of starting in Survival.
* ⏱️ **Individual Timer Freeze Fix:** Resolved stuck individual timers (`0:00`) by ensuring player start times are correctly initialized when they first tick.
* 🏹 **Spawn Shifter Search Crash Protection:** Prevented world generation tasks from crashing in case a structure search throws registry exceptions.
* ⚙️ **Difficulty Persistence:** World difficulty is now permanently saved to `config.yml` on world resets so it is correctly loaded after server reboots.
* 📦 **Fixed Seed NPE Protection:** Prevented server startup crashes when `use-fixed` seed is enabled but no value is specified in configuration.
