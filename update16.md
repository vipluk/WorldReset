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


## Async Biome Spawn System (Rewrite)

Complete rewrite of the biome spawn finding logic:

**How it works:**
- Uses `locateNearestBiome` to find the biome, then spirals outward checking for dry land with the exact biome at player height.
- Spread across server ticks (BukkitRunnable every 2 ticks) — never blocks the main thread.
- Pre-filters columns by biome at Y=62 to skip irrelevant areas quickly.
- For ocean biomes: finds islands by detecting land above sea level.
- For river/frozen_river: special border-detection logic finds solid ground at biome edges.
- Fallback: spawns on water with boat if no land found.

**Supported biomes (async path):** All oceans (7+5 deep), river, frozen_river, mushroom_fields, mangrove_swamp, swamp, beach, snowy_beach, stony_shore.

**Config:** `filter.attempts` — number of biome instances to try before giving up (default 5). Configurable via `/wr filter attempts <number>`.

**Biome accuracy:** Final spawn position is verified with `getBiome` at exact player coordinates. Biome grid alignment (4×4) handled automatically.

## `/wr seed copy`

Copies the current game world seed to the fixed seed config. Equivalent to `/wr seed <currentSeed>` + `/wr seed enable`.

## `/wr give`

Auto-give system for survival convenience:
- `/wr give boat <enable|disable>` — Toggle automatic boat on water spawn.
- `/wr give wood <amount|enable|disable>` — Toggle/set automatic wood on underground spawn. Set to `0` or `disable` to turn off.

**Config:**
```yaml
give:
  boat-if-water: true
  wood-if-underground: true
  wood-amount: 5
```

**Wood trigger:** Given when player spawn Y is more than 3 blocks below the surface (highest block). Works for cave biomes AND underground structures.

## Underground Structure Spawn

Structures with characteristic blocks are now searched in full 3D:
- **Stronghold:** stone_bricks, mossy_stone_bricks, cracked_stone_bricks, end_portal_frame
- **Ancient City:** deepslate_bricks, deepslate_tiles, sculk
- **Mineshaft:** oak_planks, rail, oak_fence
- **Trail Ruins:** mud_bricks, packed_mud
- **Trial Chambers:** tuff_bricks, oxidized_copper, trial_spawner
- **Buried Treasure:** chest

The plugin searches ±16 blocks XZ and full Y range for these blocks, then finds a safe air pocket (2 air blocks + solid floor) within ±3 blocks. Fallback: any air pocket in the structure column, then surface spawn.

## Paper API Multi-Version Compatibility (1.21 - 1.21.4+)

The plugin compiles against **Paper 1.21 API** and supports all Minecraft 1.21 revisions (including 1.21.0, 1.21.1, 1.21.2, 1.21.3, and 1.21.4+) natively at runtime. The previous `IncompatibleClassChangeError` when running on older 1.21.0–1.21.3 servers is completely resolved using smart runtime mappings and version-safe abstractions.

## Full Async Overworld Spawn Finder

To eliminate server frame freezes (lag spikes of up to 10+ seconds) on world startups or during bad seed generations, the final fallback safety land-seeker (`findSafeSpawn`) has been rewritten to run fully asynchronously. Slices of world chunks are loaded and scanned across server ticks using `CompletableFuture` and `getChunkAtAsync`, keeping the server main thread perfectly fluid.

## Bilingual Translation Migration

Moved 231 hardcoded bilingual messages from the Java source file directly into external config files: `messages_en.yml` and `messages_pl.yml`. Server administrators can now translate every single output string, broadcast message, action bar message, and GUI item, with dynamic variable replacement supporting `{time}`, `{seed}`, `{player}`, etc.

## Dedicated Error Logging

Errors and console stack traces are now directed to a dedicated `errorlogs.yml` file to keep the server log clean. Comments in the config file are fully translated.

## Resolved Deprecations & Modern API Standards

To guarantee optimal performance and future compatibility, we refactored old Bukkit APIs:
- **Title Displays:** Replaced deprecated `Player.sendTitle` with modern Kyori Adventure `Player.showTitle(Title)` and `Player.clearTitle()`.
- **Formatting Colors:** Replaced deprecated `ChatColor` with Kyori's `LegacyComponentSerializer`.
- **Registry Access:** Replaced deprecated static registries (`Registry.STRUCTURE`, `Registry.BIOME`) with `RegistryAccess.registryAccess()`.
- **Scoreboard Objectives:** Modernized `Scoreboard.registerNewObjective` using adventure `Component` structures.
- **Max Health Getter:** Replaced deprecated `Damageable.getMaxHealth()` with attribute getters (`Attribute.GENERIC_MAX_HEALTH`).
- **Plugin Metadata:** Replaced deprecated `JavaPlugin.getDescription()` calls with `getPluginMeta()`.

