# Changelog - WorldReset v1.7beta

### 🆕 New Features & Changes
* 📊 **Scoreboard Management:** From now on, your plugin scoreboard objectives (like deaths or attempts) **are not cleared** by default upon world reset! Perfect if you want to preserve your statistics between games.
* ⚙️ **New `/wr scoreboard` Command:** You can easily bring back the classic clearing behavior from previous versions using the `/wr scoreboard` command. It now safely only clears `wr_` objectives without touching other server scoreboards.
* 📋 **Status Check:** Type `/wr scoreboard status` to see if plugin objectives will be cleared during the next reset.
* 💀 **New Statistic - Deaths:** The plugin now persistently tracks the total number of deaths for every player in `records.yml`. You can display it using the new `wr_deaths` scoreboard objective or the `%worldreset_deaths%` PlaceholderAPI placeholder!
* 💀 **Death Limit System:** You can now configure the plugin to automatically reset the world if the total number of player deaths in the current run reaches a limit! Use the revamped `/wr death <number>` command to set the global pool of shared lives. Great for multiplayer hardcore challenges! Tracks via `wr_run_deaths` and `wr_lives_left`.
* 🧹 **Scoreboard Reset Command:** Added a new admin command `/wr scoreboard reset [player]` to instantly wipe all permanent speedrun statistics and personal bests from a player's record (or everyone's record).
* 🔒 **Granular Permissions:** I have completely rebuilt the permission system! You can now grant your moderators access to specific sub-commands instead of giving them full access to entire modules. For example, give someone `/wr timer start` but prevent them from changing the `/wr timer mode`!.
