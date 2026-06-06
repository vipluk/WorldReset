# WorldReset v1.6 — Update Notes

Version 1.6 introduces the AutoReset scheduler, Limbo countdown delays, a redesigned help system, parallel world generation, and full API compatibility with Minecraft 1.21 through 26.1.2.

---

## AutoReset Scheduler

Complete scheduled periodic reset system:
- **Config:** `autoreset.enabled`, `autoreset.time` (supports `30s`, `5m`, `1h`), `autoreset.loop`, `autoreset.paused`, `autoreset.visible`.
- **Commands:** `/wr autoreset <start|stop|disable|loop|visible|time>`. Without arguments shows current status.
- **Action Bar:** Displays remaining time alongside the speedrun timer (separated by `|`). Colors: green >2min, orange ≤2min, red ≤30s, gray when paused.
- **Last 5 seconds:** Title countdown with PLING sound on screen.
- **Scoreboard:** `wr_autoreset_sec`, `wr_autoreset_min`, `wr_autoreset_status`.
- **PlaceholderAPI:** `%worldreset_autoreset%`, `%worldreset_autoreset_sec%`, `%worldreset_autoreset_min%`, `%worldreset_autoreset_status%`, `%worldreset_autoreset_loop%`, `%worldreset_autoreset_enabled%`, `%worldreset_autoreset_total%`, `%worldreset_autoreset_total_sec%`.

## Limbo Pause & Countdown System

`/wr limbo` is now a full pause system with state preservation:

**Targeting:**
- `/wr limbo` — all online players
- `/wr limbo me` — only yourself (aliases: `m`, `ja`, `j`)
- `/wr limbo all` — all players (explicit)
- `/wr limbo <player>` — specific player by name

**Countdown:**
- `/wr limbo <seconds>` — all players with countdown
- `/wr limbo <seconds> <target>` — specific target with countdown (me/all/player)

**State preservation:**
- Entering limbo saves full player state: position, HP, food, saturation, XP, gamemode, inventory, armor, offhand, potion effects.
- Leaving limbo restores the exact state (not a fresh start on spawn).
- Reset clears saved states (world is new, old positions invalid).

**Automatic delays (for death-reset only):**
- **Config:** `limbo.delay-in` / `limbo.delay-out`
- **Set via:** `/wr limbo delay <in> <out>`
- Does NOT apply to manual `/wr reset` or autoreset (autoreset countdown is the warning).

**Countdown display:** Title on screen with adaptive intervals. PLING sound at ≤5s, higher pitch at ≤3s. Red/orange/yellow colors.

**Skip:** `/wr limbo` during active countdown instantly completes the teleport.

## `/wr reset [delay-in] [delay-out]`

Optional pre-reset countdown (`delay-in`) and post-reset countdown in limbo (`delay-out`). Both arguments optional. Without arguments — instant reset as before.

## Parallel Delay-Out

When `delay-out` is set, the countdown starts immediately after players enter Limbo — world generation runs in parallel. If generation finishes before the countdown, players teleport at countdown end. If generation takes longer, a "Teleporting..." title is shown until the world is ready.

## Help System Redesign

- `/wr help` — categorized command list (Game, Timer & AutoReset, World, System).
- `/wr help <command>` — detailed usage for a specific command with all sub-arguments.
- `/wr`, `/wr ?`, or any unknown command shows the full help.
- Tab completion for `/wr help <command>`.

## `/wr filter` Improvements

- `/wr filter` (no args) — shows current structure filter, biome filter, seed mode, and active world seed.
- `/wr filter clear` — clears both filters AND disables fixed seed.

## `/wr templates`

Full in-game template management: `/wr templates <enable|disable|folder|status>`.

## Random Template Selection

When multiple overworld/nether/end template folders exist in the templates directory, the plugin randomly selects one each reset. Logged to console when multiple candidates are detected.

## `/wr backup`

Full backup management in-game:
- `/wr backup` — toggle on/off.
- `/wr backup <enable|disable>` — explicit control.
- `/wr backup status` — shows enabled state, retention limit, and existing backup count.
- `/wr backup limit <number|all>` — set how many backups to keep.

## Standardized Command Aliases

All `enable`/`disable` arguments across every command also accept `on`/`off` and `true`/`false`. Tab completion always suggests `enable`/`disable`.

## API Compatibility (1.21 → 26.1.2)

- Replaced `EntityType.values()` → `Registry.ENTITY_TYPE` iteration.
- Replaced `Material.values()` → `Registry.MATERIAL` iteration.
- Replaced `getKey().getKey()` → `key().value()` (Adventure API).
- Replaced `registerNewObjective(name, "dummy")` → `registerNewObjective(name, Criteria.DUMMY, displayName)`.
- Removed unused BossBar/BarColor/BarStyle imports.
- `locator_bar` GameRule: graceful fallback for servers where it doesn't exist yet.

## Translation Keys Added

`messages_en.yml` / `messages_pl.yml`:
- `autoreset-triggered`, `autoreset-countdown`
- `limbo-countdown-in`, `limbo-countdown-out`, `limbo-waiting`
- `reset-countdown`
- Updated `command-usage`.
