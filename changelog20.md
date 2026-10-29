# Changelog - WorldReset v2.0

### ❤️ Special Thanks
* A big thank you to **backermeisteru** from our Discord community for suggesting the automated Datapack Injection feature and inspiring this update!
* Thanks to **carlosveny** for reporting the timer start desynchronization during Limbo delay-out countdowns!

---

### 🚀 What's New & Fixed in v2.0

* 📦 **Automatic Datapack Injection:**
  * You can now automatically include datapacks in every newly generated or reset world!
  * Simply drop your datapacks (either as `.zip` files or uncompressed folders) into the new `WorldReset_Datapacks/` directory.
  * Every time a new world is generated, WorldReset automatically injects them before the world starts, ensuring all custom mechanics, visualizers, recipes, and challenges work immediately from the very first second.
* ⏱️ **Timer Delay-Out Synchronization Fix:**
  * Fixed an issue where the speedrun stopwatch started counting immediately as soon as world generation finished, even while players were still waiting out a countdown delay in Limbo.
  * Worlds continue to generate seamlessly in the background for zero-lag transitions, but the timer now precisely starts the exact millisecond players actually spawn into the new world.
* 🛠️ **In-Game Datapack Management (`/wr datapacks`):**
  * Toggle datapack injection on or off anytime with `/wr datapacks <enable|disable>`.
  * Check which datapacks are currently installed with `/wr datapacks list`.
  * View your setup status with `/wr datapacks status` or customize the folder location with `/wr datapacks folder [path]`.
* 🧹 **Clean & Readable Configuration:**
  * Completely revamped `config.yml` into clean, concise English.
  * Removed cluttered multi-language comment tags for a modern, standardized configuration experience.
* ⌨️ **Smart Tab Completion & Full Permission Integration:**
  * Seamless auto-completion under the `Tab` key for all new commands.
  * Fully integrated into `/wr help` under the **World** category.
