# Changelog - WorldReset v2.0

### ❤️ Special Thanks
* Thanks to **backermeisteru** from our [Discord](https://discord.gg/A7WVnYj3BP) for inspiring the Datapack Injection feature!
* Thanks to **carlosveny** for reporting the timer start desynchronization during Limbo delay-out countdowns!

---

### 🚀 What's New & Fixed in v2.0

* 📦 **Dynamic Datapack Management (`/wr datapacks`):**
  * Drop your datapacks (`.zip` files or folders) into `WorldReset_Datapacks/` and toggle, disable, or reload them on the fly anytime with `/wr datapacks <enable|disable>` without restarting the server.
  * Injected datapacks seamlessly persist across world resets and backups.
* ⏱️ **Timer Start Synchronization:**
  * Fixed stopwatch starting prematurely while waiting in Limbo; the timer now starts the exact moment players actually land in the new world.
* 🩸 **Dual Action Bar Damage Indicator (`/wr damage`):**
  * The timer smoothly switches into dual display mode (`⏱ 12:35 | Player -2.0❤`) for a few seconds whenever someone takes damage, preventing messages from being overwritten.
* 🧹 **Config Cleanup & Bug Fixes:**
  * Cleaned up `config.yml` into standardized English - removed cluttered multi-language comments to improve readability.
  * Fixed `/wr death <limit>` message displaying a hardcoded limit of 1.
