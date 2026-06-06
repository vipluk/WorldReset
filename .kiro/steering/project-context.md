# WorldReset — Project Context

## Overview
Minecraft Bukkit/Paper plugin for dynamic world management. Resets game worlds without server restart. Used for speedruns, manhunts, and challenges.

- **Author:** vipluk
- **Version:** 1.6
- **API:** Paper 1.21.4+ (compatible 1.21 → 26.1.2)
- **Java:** 21
- **Build:** Maven (`pom.xml`), shade plugin. Build command in file `tym budowac na PC`.
- **Main class:** `src/main/java/org/example/worldreset/Main.java` (~2900 lines, single file)
- **Plugin page:** https://modrinth.com/project/worldreset

## Architecture
Everything is in one `Main.java` file (intentional — single-dev plugin). Logical sections:
1. Config loading & helpers
2. Limbo logic (teleport, setup players, countdown system)
3. Reset logic (`startReset`, `startAutoTriggeredReset`, `startAutoResetReset`, `startResetWithDelayOut`)
4. World generation (`generateGameWorldsInternal`), filters, safe spawn, templates
5. Timer (speedrun stopwatch — RTA/IGT, GLOBAL/INDIVIDUAL, goal triggers)
6. AutoReset (scheduled periodic resets with action bar display)
7. Events (respawn, death, portal, join, quit, block break/place)
8. Commands (`onCommand` switch) & Tab completion (`onTabComplete`)
9. Help system (`sendFullHelp`, `getHelpForCommand`)
10. Scoreboard sync (38+ objectives)
11. PlaceholderAPI expansion (inner class `WorldResetExpansion`)
12. Records/Leaderboard persistence (`records.yml`)

## Key Systems

### Limbo Delays
- `limbo.delay-in` / `limbo.delay-out` in config — for death-reset automatic transitions only
- `/wr reset <in> <out>` — manual reset with custom delays (parallel delay-out)
- `/wr limbo <sec>` — manual countdown, `/wr limbo <in> <out>` — set delays
- `/wr limbo` during countdown — skips it (tracked via `activeCountdowns` map)
- `startParallelDelayOut()` — countdown runs parallel with world generation

### AutoReset
- Last 5 seconds show title + sound
- Uses instant-to-limbo (autoreset IS the countdown), but delay-out for returning
- Action bar shows alongside timer separated by `|`

### Command Aliases
All enable/disable accept: `enable`, `on`, `true` / `disable`, `off`, `false`
Helpers: `isEnableAlias(s)`, `isDisableAlias(s)`
Tab always suggests `enable`/`disable`.

### Templates
Multiple worlds in templates folder → randomly selected each reset.
Folders with "nether"/"end" in name → dimension-specific. Others → overworld candidates.

## Conventions
- Messages: `messages_en.yml` / `messages_pl.yml`. Use `getMsg(key)` (with prefix) or `getSubtitle(key, fallback)` (no prefix, for titles).
- Config: bilingual comments `[PL]`/`[EN]` in `config.yml`.
- Tab completion uses `StringUtil.copyPartialMatches`.
- Scoreboard uses `Criteria.DUMMY` via `getOrRegisterObjective()`.
- API: Use `key().value()` not deprecated `getKey().getKey()`. Use `Registry.X` iteration not `.values()`.

## Resource Files
- `config.yml` — all plugin settings
- `plugin.yml` — command registration, permissions
- `messages_en.yml` / `messages_pl.yml` — translations
- `placeholderapi.yml` — PAPI documentation (not loaded by code, just docs)
- `scoreboard.yml` — scoreboard objectives documentation
- `internal_map/` — default limbo world bundled in JAR

## Documentation Files
- `description.md` — Modrinth page description
- `changelog16.md` — short changelog (like changelog15fix.md format)
- `update16.md` — detailed update notes
- `README.md` — basic readme

## Important Notes
- `saveResource("file.yml", false)` does NOT overwrite — users keep old files. New keys need fallback logic (`getSubtitle` pattern).
- World name cannot be "world" or "limbo".
- `isResetting` flag blocks commands/events during reset.
- `isDelayingReset` flag allows respawn in game world during delay-in phase.
- `parallelDelayOutRunning` flag prevents `generateGameWorldsInternal` from calling `startGameForAll` when parallel countdown handles it.
