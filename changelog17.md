# Changelog - WorldReset v1.7

### 🆕 New Features & Changes
* 📊 **Scoreboard Management:** From now on, your scoreboard (stats on the right side of the screen, hidden objectives) **is not cleared** by default upon world reset! Perfect if you want to preserve your statistics between games. 
* ⚙️ **New `/wr scoreboard` Command:** You can easily bring back the classic clearing behavior from previous versions using the `/wr scoreboard` command. It acts as a classic toggle, exactly the same way as `/wr compass`.
* 📋 **Status Check:** Type `/wr scoreboard status` to see if your scoreboard will be cleared during the next reset.
* 💀 **New Statistic - Deaths:** The plugin now persistently tracks the total number of deaths for every player in `records.yml`. You can display it using the new `wr_deaths` scoreboard objective or the `%worldreset_deaths%` PlaceholderAPI placeholder!
* 🧹 **Scoreboard Reset Command:** Added a new admin command `/wr scoreboard reset [player]` to instantly wipe all permanent speedrun statistics and personal bests from a player's record (or everyone's record).
* 🔒 **Granular Permissions:** I have completely rebuilt the permission system! You can now grant your moderators access to specific sub-commands instead of giving them full access to entire modules. For example, give someone `/wr timer start` but prevent them from changing the `/wr timer mode`!.