# Changelog - WorldReset v1.9

### 🚀 New Features & Improvements
* 🛡️ **GameRules Preservation Across Resets (`preserve-gamerules`):** You no longer need to re-type game rules like `/gamerule keepInventory true`, `/gamerule doDaylightCycle false`, or `/gamerule mobGriefing false` every time the world resets! The plugin now automatically captures all active game rules before resetting and reapplies them to your freshly generated Overworld, Nether, and The End. Can be toggled in `config.yml` via `preserve-gamerules`.
* 💀 **Death Limit Persistence Fix (`/wr death`):** Fixed an issue where disabling the death limit (`/wr death disable` / `/wr death off`) or setting custom lives reverted back to 1 after every reset. Your death limit preferences are now permanently saved to configuration.
* 🌍 **Dimension Difficulty Synchronization:** Worlds in the Nether and The End now strictly synchronize their difficulty level with the Overworld across resets and backup restorations, ensuring consistent gameplay difficulty across all dimensions.
