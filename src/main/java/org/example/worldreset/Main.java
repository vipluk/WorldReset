package org.example.worldreset;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BiomeSearchResult;
import org.bukkit.util.StringUtil;
import org.bukkit.util.StructureSearchResult;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings("deprecation")
public class Main extends JavaPlugin implements Listener, TabCompleter {

    private String gameWorldName;
    private final String limboWorldName = "limbo";
    private FileConfiguration langConfig;
    private FileConfiguration recordsConfig;
    private File recordsFile;

    private boolean isResetting = false;
    private boolean isDelayingReset = false; // true during delay-in countdown before actual reset
    private boolean parallelDelayOutRunning = false; // true when delay-out countdown started parallel with generation
    private boolean isGameReady = false;
    private Difficulty lastSavedDifficulty = Difficulty.NORMAL;

    // --- ZMIENNE TIMERA ---
    private boolean timerEnabled;
    private String timerMode;
    private String timerScope;
    private String timerGoalType;
    private String timerGoalValue;

    private boolean timerRunning = false;
    private boolean goalReachedPause = false;
    private long globalStartTime = 0;
    private long globalElapsedTime = 0;
    private int globalElapsedTicks = 0;
    private BukkitTask timerTask;

    private final Map<UUID, Long> playerStartTimes = new HashMap<>();
    private final Map<UUID, Long> playerElapsedTimes = new HashMap<>();
    private final Map<UUID, Integer> playerElapsedTicks = new HashMap<>();
    private final Set<UUID> playersFinished = new HashSet<>();

    // --- ZMIENNE KOMPASU (RADARU) ---
    private boolean compassEnabled;

    // --- ZMIENNE AUTORESETU ---
    private boolean autoResetEnabled;
    private boolean autoResetVisible;
    private boolean autoResetLoop;
    private boolean autoResetPaused;
    private long autoResetTotalSeconds;
    private long autoResetRemainingSeconds;
    private BukkitTask autoResetTask;

    // --- ZMIENNE DELAY LIMBO ---
    private int limboDelayIn;  // sekundy opóźnienia wejścia do limbo (automatyczne)
    private int limboDelayOut; // sekundy opóźnienia wyjścia z limbo (automatyczne)
    private final Map<UUID, BukkitTask> activeCountdowns = new HashMap<>();
    private final Map<UUID, Map<String, Object>> limboSavedStates = new HashMap<>(); // Player states saved when entering limbo
    private final Set<UUID> boatGivenPlayers = new HashSet<>(); // Track who got a boat for water spawn
    private boolean waterSpawnActive = false; // True if current spawn is on water
    private FileConfiguration lastPlayerSnapshot; // Saved before reset, used in backup

    // Structure list (Overworld only)
    private final List<String> STRUCTURE_NAMES = new ArrayList<>();

    // Biome list (Overworld popular)
    private final List<String> BIOME_NAMES = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadConfigValues();
        updateResourceFile("messages_en.yml");
        updateResourceFile("messages_pl.yml");
        saveResource("placeholderapi.yml", true);
        saveResource("scoreboard.yml", true);
        loadLanguage();
        createTemplateFolders();
        loadRecordsFile();
        initDynamicNames();

        if (!limboWorldName.equals(Bukkit.getWorlds().getFirst().getName())) {
            String mainWorldName = Bukkit.getWorlds().getFirst().getName();
            File limboDir = new File(Bukkit.getWorldContainer(), limboWorldName);
            File migratedLimboDir = new File(Bukkit.getWorldContainer(), mainWorldName + "/dimensions/minecraft/" + limboWorldName);
            
            if (!limboDir.exists() && !migratedLimboDir.exists()) {
                copyDirectoryFromJar(limboDir);
                File uidFile = new File(limboDir, "uid.dat");
                if (uidFile.exists()) {
                    uidFile.delete();
                }
            }
            
            try {
                new WorldCreator(limboWorldName).createWorld();
            } catch (Exception e) {
                getLogger().severe("Failed to load limbo world: " + e.getMessage());
                getLogger().info("Attempting to load limbo in safe flat fallback mode...");
                try {
                    if (limboDir.exists()) {
                        deleteDirectoryRecursive(limboDir);
                    }
                    new WorldCreator(limboWorldName).type(WorldType.FLAT).createWorld();
                } catch (Exception ex) {
                    getLogger().severe("Could not generate fallback limbo: " + ex.getMessage());
                }
            }
            configureLimboWorld(Bukkit.getWorld(limboWorldName));
        } else {
            configureLimboWorld(Bukkit.getWorlds().getFirst());
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("worldreset")).setTabCompleter(this);

        if (Bukkit.getWorld(gameWorldName) == null) {
            loadGameWorlds();
        }
        isGameReady = true;
        applyLocatorBarGamerule();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new WorldResetExpansion().register();
            getLogger().info("Zarejestrowano integrację z PlaceholderAPI!");
        }

        getLogger().info("WorldReset v" + getDescription().getVersion() + " enabled.");

        // Start autoreset timer if enabled and not paused
        if (autoResetEnabled && !autoResetPaused) {
            autoResetRemainingSeconds = autoResetTotalSeconds;
            startAutoResetTimer();
        }

        // Initialize autoreset scoreboard values (0 if disabled)
        Bukkit.getScheduler().runTaskLater(this, this::syncAutoResetScoreboard, 5L);
    }

    @Override
    public void onDisable() {
        stopAutoResetTimer();
    }

    private void loadConfigValues() {
        reloadConfig();
        gameWorldName = getConfig().getString("game-world-name", "game_world");
        if (gameWorldName.equalsIgnoreCase("world")) {
            getLogger().warning("You cannot use 'world' as game-world-name! Changing to 'game_world'.");
            gameWorldName = "game_world";
            getConfig().set("game-world-name", "game_world");
            saveConfig();
        }

        timerEnabled = getConfig().getBoolean("timer.enabled", false);
        timerMode = getConfig().getString("timer.mode", "RTA").toUpperCase();
        timerScope = getConfig().getString("timer.scope", "GLOBAL").toUpperCase();
        timerGoalType = getConfig().getString("timer.goal.type", "NONE").toUpperCase();

        timerGoalValue = getConfig().getString("timer.goal.value", "");
        if (timerGoalType.equals("ENTITY") || timerGoalType.equals("ADVANCEMENT")) {
            timerGoalValue = timerGoalValue.toLowerCase();
        } else {
            timerGoalValue = timerGoalValue.toUpperCase();
        }

        compassEnabled = getConfig().getBoolean("compass.enabled", true);

        // AutoReset
        autoResetEnabled = getConfig().getBoolean("autoreset.enabled", false);
        autoResetVisible = getConfig().getBoolean("autoreset.visible", true);
        autoResetLoop = getConfig().getBoolean("autoreset.loop", true);
        autoResetPaused = getConfig().getBoolean("autoreset.paused", true);
        autoResetTotalSeconds = parseTimeToSeconds(getConfig().getString("autoreset.time", "1h"));
        if (autoResetRemainingSeconds <= 0) {
            autoResetRemainingSeconds = autoResetTotalSeconds;
        }

        // Limbo delay
        limboDelayIn = getConfig().getInt("limbo.delay-in", 0);
        limboDelayOut = getConfig().getInt("limbo.delay-out", 0);
    }

    // --- HELPER METHODS ---

    private File getTemplateFolder() {
        String folderName = getConfig().getString("template.folder", "WorldReset_Templates");
        File templatesFolder = new File(folderName);
        if (!templatesFolder.isAbsolute()) {
            templatesFolder = new File(getDataFolder(), folderName);
        }
        return templatesFolder;
    }

    private void broadcastInfo(String message) {
        if (getConfig().getBoolean("broadcast-messages", true)) {
            Bukkit.broadcast(Component.text(message));
        }
    }

    private void configureLimboWorld(World w) {
        if (w != null) {
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
            w.setTime(6000);
        }
    }

    private void copyDirectoryFromJar(File targetDir) {
        try {
            URL jarUrl = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            try (JarFile jarFile = new JarFile(new File(jarUrl.toURI()))) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith("internal_map" + "/")) {
                        String fileName = entryName.substring("internal_map".length() + 1);
                        if (fileName.isEmpty()) continue;
                        File destFile = new File(targetDir, fileName);
                        if (entry.isDirectory()) {
                            if (!destFile.mkdirs()) {}
                        }
                        else {
                            if (!destFile.getParentFile().mkdirs()) {}
                            try (InputStream is = jarFile.getInputStream(entry);
                                 FileOutputStream fos = new FileOutputStream(destFile)) {
                                byte[] buffer = new byte[1024]; int length;
                                while ((length = is.read(buffer)) > 0) fos.write(buffer, 0, length);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { getLogger().severe("Error extracting map: " + e.getMessage()); }
    }

    private void loadLanguage() {
        String lang = getConfig().getString("language", "en");
        File langFile = new File(getDataFolder(), "messages_" + lang + ".yml");
        if (!langFile.exists()) langFile = new File(getDataFolder(), "messages_en.yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private String getMsg(String key) {
        String prefix = langConfig.getString("prefix", "");
        String msg = langConfig.getString(key, key);
        return (prefix + msg).replace("&", "§");
    }

    private String getSubtitle(String key, String fallback) {
        String value = langConfig.getString(key);
        if (value == null || value.isEmpty()) return fallback;
        return value.replace("&", "§");
    }

    /**
     * Updates a resource file by adding any missing keys from the JAR default
     * without overwriting existing user values.
     */
    private void updateResourceFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            try { saveResource(fileName, false); } catch (Exception ignored) {}
            return;
        }

        try (InputStream defaultStream = getResource(fileName)) {
            if (defaultStream == null) return;

            FileConfiguration userConfig = YamlConfiguration.loadConfiguration(file);
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defaultStream));

            boolean updated = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!userConfig.contains(key)) {
                    userConfig.set(key, defaultConfig.get(key));
                    updated = true;
                }
            }

            if (updated) {
                userConfig.save(file);
                getLogger().info("Updated " + fileName + " with new keys.");
            }
        } catch (Exception e) {
            // File may contain YAML-incompatible characters (like % in placeholderapi.yml)
            // Just overwrite it with the latest version
            try { saveResource(fileName, true); getLogger().info("Replaced " + fileName + " (format incompatible with YAML parser)."); } catch (Exception ignored) {}
        }
    }

    private Difficulty getServerDifficulty() {
        try {
            File serverProps = new File("server.properties");
            if (!serverProps.exists()) {
                return Difficulty.NORMAL;
            }
            
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(serverProps)) {
                props.load(fis);
            }
            
            String diffValue = props.getProperty("difficulty", "2").toLowerCase().trim();
            Difficulty difficulty;
            
            // Try parsing as integer first (0-3)
            try {
                int diffInt = Integer.parseInt(diffValue);
                difficulty = switch(diffInt) {
                    case 0 -> Difficulty.PEACEFUL;
                    case 1 -> Difficulty.EASY;
                    case 2 -> Difficulty.NORMAL;
                    case 3 -> Difficulty.HARD;
                    default -> Difficulty.NORMAL;
                };
            } catch (NumberFormatException e) {
                // If not an integer, try parsing as string name
                difficulty = switch(diffValue) {
                    case "peaceful" -> Difficulty.PEACEFUL;
                    case "easy" -> Difficulty.EASY;
                    case "normal" -> Difficulty.NORMAL;
                    case "hard" -> Difficulty.HARD;
                    default -> Difficulty.NORMAL;
                };
            }
            
            getLogger().info("Loaded difficulty from server.properties: " + difficulty.name().toLowerCase());
            return difficulty;
        } catch (Exception e) {
            getLogger().warning("Error reading server.properties: " + e.getMessage());
            return Difficulty.NORMAL;
        }
    }

    // --- LIMBO LOGIC ---

    private Location getLimboSpawn() {
        World limbo = Bukkit.getWorld(limboWorldName);
        if (limbo == null) {
            new WorldCreator(limboWorldName).createWorld();
            limbo = Bukkit.getWorld(limboWorldName);
        }
        return limbo != null ? limbo.getSpawnLocation() : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private void sendAllToLimboForReset() {
        stopTimer();
        limboSavedStates.clear(); // Reset clears saved states — world is being regenerated

        // Save all game worlds to disk before backup (preserves player modifications)
        saveGameWorlds();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
        }
        doSendAllToLimbo();
    }

    /**
     * Sends all players to limbo with delay-in countdown (used for death-reset and automatic triggers).
     */
    private void sendAllToLimboWithDelay() {
        stopTimer();
        saveGameWorlds();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
        }

        if (limboDelayIn > 0) {
            List<Player> playersToMove = new ArrayList<>(Bukkit.getOnlinePlayers());
            String subtitle = getSubtitle("limbo-countdown-in", "Teleport to Limbo...");
            startCountdown(playersToMove, limboDelayIn, subtitle, this::doSendAllToLimbo);
        } else {
            doSendAllToLimbo();
        }
    }

    private void doSendAllToLimbo() {
        isDelayingReset = false;
        new BukkitRunnable() {
            @Override
            public void run() {
                Location spawn = getLimboSpawn();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.isDead()) p.spigot().respawn();
                    if (!p.getWorld().getName().equals(limboWorldName)) {
                        p.teleport(spawn);
                    }
                    setupLimboPlayer(p);
                }
            }
        }.runTaskLater(Main.this, 5L);
    }

    private void setupLimboPlayer(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setRemainingAir(p.getMaximumAir());
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        p.setInvulnerable(false);
    }

    private void saveLimboState(Player p) {
        Map<String, Object> state = new HashMap<>();
        state.put("world", p.getWorld().getName());
        state.put("location", p.getLocation().clone());
        state.put("health", p.getHealth());
        state.put("food", p.getFoodLevel());
        state.put("saturation", p.getSaturation());
        state.put("xp-level", p.getLevel());
        state.put("xp-progress", p.getExp());
        state.put("gamemode", p.getGameMode());
        state.put("fire-ticks", p.getFireTicks());
        state.put("inventory", p.getInventory().getContents().clone());
        state.put("armor", p.getInventory().getArmorContents().clone());
        state.put("offhand", p.getInventory().getItemInOffHand().clone());
        List<PotionEffect> effects = new ArrayList<>(p.getActivePotionEffects());
        state.put("effects", effects);
        limboSavedStates.put(p.getUniqueId(), state);
    }

    @SuppressWarnings("unchecked")
    private void restoreLimboState(Player p) {
        Map<String, Object> state = limboSavedStates.remove(p.getUniqueId());
        if (state == null) return;

        Location loc = (Location) state.get("location");
        if (loc != null && loc.getWorld() != null) {
            p.teleport(loc);
        } else {
            World game = Bukkit.getWorld(gameWorldName);
            if (game != null) p.teleport(game.getSpawnLocation());
        }

        p.setGameMode((GameMode) state.get("gamemode"));
        p.getInventory().setContents((org.bukkit.inventory.ItemStack[]) state.get("inventory"));
        p.getInventory().setArmorContents((org.bukkit.inventory.ItemStack[]) state.get("armor"));
        p.getInventory().setItemInOffHand((org.bukkit.inventory.ItemStack) state.get("offhand"));
        p.setHealth(Math.min((double) state.get("health"), p.getMaxHealth()));
        p.setFoodLevel((int) state.get("food"));
        p.setSaturation((float) state.get("saturation"));
        p.setLevel((int) state.get("xp-level"));
        p.setExp((float) state.get("xp-progress"));
        p.setFireTicks((int) state.get("fire-ticks"));

        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        List<PotionEffect> effects = (List<PotionEffect>) state.get("effects");
        if (effects != null) {
            for (PotionEffect effect : effects) p.addPotionEffect(effect);
        }
    }

    private void toggleLimboForPlayer(Player p, int delay) {
        // Skip active countdown
        if (activeCountdowns.containsKey(p.getUniqueId())) {
            skipCountdown(p);
            p.sendTitle("", "", 0, 1, 0);
        }

        if (p.getWorld().getName().equals(limboWorldName)) {
            // Leave limbo
            if (limboSavedStates.containsKey(p.getUniqueId())) {
                if (delay > 0) {
                    String subtitle = getSubtitle("limbo-countdown-out", "Teleport to Game...");
                    startCountdown(p, delay, subtitle, () -> {
                        if (p.isOnline()) { restoreLimboState(p); p.sendMessage(getMsg("limbo-leave")); }
                    });
                } else {
                    restoreLimboState(p);
                    p.sendMessage(getMsg("limbo-leave"));
                }
            } else if (isGameReady) {
                World game = Bukkit.getWorld(gameWorldName);
                if (game != null) {
                    if (delay > 0) {
                        String subtitle = getSubtitle("limbo-countdown-out", "Teleport to Game...");
                        Location spawn = game.getSpawnLocation();
                        startCountdown(p, delay, subtitle, () -> {
                            if (p.isOnline()) { setupGamePlayer(p, spawn); p.sendMessage(getMsg("game-started")); }
                        });
                    } else {
                        setupGamePlayer(p, game.getSpawnLocation());
                        p.sendMessage(getMsg("game-started"));
                    }
                }
            }
        } else {
            // Enter limbo
            if (delay > 0) {
                String subtitle = getSubtitle("limbo-countdown-in", "Teleport to Limbo...");
                startCountdown(p, delay, subtitle, () -> {
                    if (p.isOnline()) {
                        saveLimboState(p);
                        p.teleport(getLimboSpawn());
                        setupLimboPlayer(p);
                        p.sendMessage(getMsg("limbo-join"));
                    }
                });
            } else {
                saveLimboState(p);
                p.teleport(getLimboSpawn());
                setupLimboPlayer(p);
                p.sendMessage(getMsg("limbo-join"));
            }
        }
    }

    private void setupGamePlayer(Player p, Location spawn) {
        if (p.isDead()) p.spigot().respawn();
        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setExp(0);
        p.setLevel(0);
        p.setRemainingAir(p.getMaximumAir());
        p.setFireTicks(0);
        p.setFallDistance(0);
        for(PotionEffect pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());

        p.setInvulnerable(true);
        new BukkitRunnable() { @Override public void run() { if (p.isOnline()) p.setInvulnerable(false); } }.runTaskLater(this, 40L);

        // Give boat if water spawn or player is in/above water
        if (waterSpawnActive && !boatGivenPlayers.contains(p.getUniqueId())) {
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.OAK_BOAT));
            boatGivenPlayers.add(p.getUniqueId());
        } else if (!boatGivenPlayers.contains(p.getUniqueId())) {
            // Extra check: if player's spawn location is on water, give boat anyway
            Block below = p.getLocation().getBlock().getRelative(0, -1, 0);
            if (below.getType() == Material.WATER) {
                p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.OAK_BOAT));
                boatGivenPlayers.add(p.getUniqueId());
                waterSpawnActive = true;
            }
        }
    }

    // --- COUNTDOWN SYSTEM ---

    /**
     * Starts a countdown displayed as titles on screen with sound.
     * After countdown finishes, executes the provided action.
     * Tracks per-player so it can be skipped with /wr limbo.
     */
    private void startCountdown(Collection<Player> players, int seconds, String subtitleMsg, Runnable onComplete) {
        if (seconds <= 0) {
            onComplete.run();
            return;
        }

        Set<Integer> displayAt = getDisplaySeconds(seconds);

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    this.cancel();
                    for (Player p : players) {
                        activeCountdowns.remove(p.getUniqueId());
                    }
                    onComplete.run();
                    return;
                }

                if (displayAt.contains(remaining)) {
                    String color = remaining <= 3 ? "§c§l" : (remaining <= 5 ? "§6§l" : "§e§l");
                    for (Player p : players) {
                        if (p.isOnline()) {
                            p.sendTitle(color + remaining, "§7" + subtitleMsg, 0, 25, 5);
                            if (remaining <= 5) {
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, remaining <= 3 ? 1.5f : 1.0f);
                            } else {
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.0f);
                            }
                        }
                    }
                }

                remaining--;
            }
        }.runTaskTimer(this, 0L, 20L);

        // Register for each player so /wr limbo can skip
        for (Player p : players) {
            activeCountdowns.put(p.getUniqueId(), task);
        }
    }

    private void startCountdown(Player player, int seconds, String subtitleMsg, Runnable onComplete) {
        startCountdown(Collections.singletonList(player), seconds, subtitleMsg, onComplete);
    }

    /**
     * Skips an active countdown for a player — immediately runs the completion action.
     * Returns true if a countdown was skipped.
     */
    private boolean skipCountdown(Player player) {
        BukkitTask task = activeCountdowns.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
            return true;
        }
        return false;
    }

    private Set<Integer> getDisplaySeconds(int total) {
        Set<Integer> set = new HashSet<>();
        if (total <= 5) {
            for (int i = 1; i <= total; i++) set.add(i);
        } else if (total <= 15) {
            set.addAll(Arrays.asList(1, 2, 3, 4, 5, 10));
        } else if (total <= 30) {
            set.addAll(Arrays.asList(1, 2, 3, 4, 5, 10, 15, 20));
        } else {
            set.addAll(Arrays.asList(1, 2, 3, 4, 5, 10, 15, 20, 30));
        }
        // Always include the start number
        set.add(total);
        return set;
    }

    // --- RESET LOGIC ---

    public void startReset() {
        if (isResetting) return;
        loadConfigValues(); // Dynamically reload config.yml edits from disk before starting reset!
        if (gameWorldName.equals(limboWorldName) || gameWorldName.equals("world")) {
            getLogger().severe("CRITICAL: game-world-name cannot be 'limbo' or 'world'!");
            return;
        }

        World currentWorld = Bukkit.getWorld(gameWorldName);
        lastSavedDifficulty = currentWorld != null ? currentWorld.getDifficulty() : getServerDifficulty();
        getConfig().set("world.difficulty", lastSavedDifficulty.name());
        saveConfig();

        isResetting = true;
        isGameReady = false;

        lastPlayerSnapshot = capturePlayerStates();
        broadcastInfo(getMsg("reset-started"));
        sendAllToLimboForReset();

        // Step 1: Unload worlds after 20 ticks (allowing players to leave)
        new BukkitRunnable() {
            @Override
            public void run() {
                unloadGameWorlds();
                
                // Step 2: Wait 20 more ticks for Spigot background saving threads and file system to settle
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (getConfig().getBoolean("backup.enabled")) performBackup();

                        // Next tick: delete old worlds and generate new
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                deleteWorldFolder(gameWorldName);
                                deleteWorldFolder(gameWorldName + "_nether");
                                deleteWorldFolder(gameWorldName + "_the_end");

                                generateGameWorlds();
                            }
                        }.runTaskLater(Main.this, 1L);
                    }
                }.runTaskLater(Main.this, 20L);
            }
        }.runTaskLater(this, 20L);
    }

    /**
     * Manual reset with a custom delay-out (countdown in limbo before game starts).
     */
    private void startResetWithDelayOut(int customDelayOut) {
        if (isResetting) return;
        loadConfigValues();
        if (gameWorldName.equals(limboWorldName) || gameWorldName.equals("world")) {
            getLogger().severe("CRITICAL: game-world-name cannot be 'limbo' or 'world'!");
            return;
        }

        World currentWorld = Bukkit.getWorld(gameWorldName);
        lastSavedDifficulty = currentWorld != null ? currentWorld.getDifficulty() : getServerDifficulty();
        getConfig().set("world.difficulty", lastSavedDifficulty.name());
        saveConfig();

        isResetting = true;
        isGameReady = false;

        lastPlayerSnapshot = capturePlayerStates();
        broadcastInfo(getMsg("reset-started"));
        sendAllToLimboForReset();

        // Start parallel delay-out after players are in limbo (10 ticks buffer)
        final int delayOut = customDelayOut;
        new BukkitRunnable() {
            @Override
            public void run() {
                parallelDelayOutRunning = true;
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!players.isEmpty()) {
                    String subtitle = getSubtitle("limbo-countdown-out", "Game starts...");
                    startCountdown(players, delayOut, subtitle, () -> {
                        if (isGameReady) {
                            doTeleportAllToGame();
                        } else {
                            String waitMsg = getSubtitle("limbo-waiting", "Teleporting...");
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendTitle("§e⏳", "§7" + waitMsg, 0, 60, 20);
                            }
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (isGameReady) {
                                        this.cancel();
                                        doTeleportAllToGame();
                                    }
                                }
                            }.runTaskTimer(Main.this, 10L, 10L);
                        }
                    });
                }
            }
        }.runTaskLater(this, 10L);

        new BukkitRunnable() {
            @Override
            public void run() {
                unloadGameWorlds();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (getConfig().getBoolean("backup.enabled")) performBackup();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                deleteWorldFolder(gameWorldName);
                                deleteWorldFolder(gameWorldName + "_nether");
                                deleteWorldFolder(gameWorldName + "_the_end");

                                generateGameWorldsInternal(false); // false — parallel handles teleport
                            }
                        }.runTaskLater(Main.this, 1L);
                    }
                }.runTaskLater(Main.this, 20L);
            }
        }.runTaskLater(Main.this, 20L);
    }

    /**
     * Reset triggered by automatic events (death, autoreset).
     * Uses limboDelayIn/limboDelayOut for countdown before/after.
     */
    public void startAutoTriggeredReset() {
        if (isResetting) return;
        loadConfigValues();
        if (gameWorldName.equals(limboWorldName) || gameWorldName.equals("world")) {
            getLogger().severe("CRITICAL: game-world-name cannot be 'limbo' or 'world'!");
            return;
        }

        World currentWorld = Bukkit.getWorld(gameWorldName);
        lastSavedDifficulty = currentWorld != null ? currentWorld.getDifficulty() : getServerDifficulty();
        getConfig().set("world.difficulty", lastSavedDifficulty.name());
        saveConfig();

        isResetting = true;
        isDelayingReset = limboDelayIn > 0;
        isGameReady = false;

        lastPlayerSnapshot = capturePlayerStates();
        sendAllToLimboWithDelay();

        // Unload after delay-in + buffer
        long delayTicks = (limboDelayIn > 0 ? (limboDelayIn * 20L + 20L) : 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                unloadGameWorlds();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (getConfig().getBoolean("backup.enabled")) performBackup();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                deleteWorldFolder(gameWorldName);
                                deleteWorldFolder(gameWorldName + "_nether");
                                deleteWorldFolder(gameWorldName + "_the_end");

                                generateAutoTriggeredGameWorlds();
                            }
                        }.runTaskLater(Main.this, 1L);
                    }
                }.runTaskLater(Main.this, 20L);
            }
        }.runTaskLater(Main.this, delayTicks);
    }

    private void generateAutoTriggeredGameWorlds() {
        // Death-reset: start delay-out parallel, generate world without triggering another delay-out
        if (limboDelayOut > 0) {
            startParallelDelayOut();
        }
        generateGameWorldsInternal(false);
    }

    /**
     * Starts the delay-out countdown immediately (parallel with world generation).
     * When countdown finishes:
     *   - If world is ready → teleport players
     *   - If world not ready → show "Teleporting..." and wait until ready
     */
    private void startParallelDelayOut() {
        parallelDelayOutRunning = true;

        // Delay 10 ticks to ensure players have been teleported to limbo
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (players.isEmpty()) {
                    parallelDelayOutRunning = false;
                    return;
                }

                String subtitle = getSubtitle("limbo-countdown-out", "Game starts...");
                startCountdown(players, limboDelayOut, subtitle, () -> {
                    if (isGameReady) {
                        doTeleportAllToGame();
                    } else {
                        String waitMsg = getSubtitle("limbo-waiting", "Teleporting...");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendTitle("§e⏳", "§7" + waitMsg, 0, 60, 20);
                        }
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (isGameReady) {
                                    this.cancel();
                                    doTeleportAllToGame();
                                }
                            }
                        }.runTaskTimer(Main.this, 10L, 10L);
                    }
                });
            }
        }.runTaskLater(Main.this, 10L);
    }

    private void doTeleportAllToGame() {
        parallelDelayOutRunning = false;
        World game = Bukkit.getWorld(gameWorldName);
        if (game == null) return;
        Location spawn = game.getSpawnLocation();

        broadcastInfo(getMsg("game-started"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            setupGamePlayer(p, spawn);
        }
        incrementAttempts();
        isResetting = false;
    }

    /**
     * Reset triggered by autoreset timer.
     * Instant to limbo (autoreset countdown itself serves as the warning),
     * but uses delay-out when returning players to the new world.
     */
    private void startAutoResetReset() {
        if (isResetting) return;
        loadConfigValues();
        if (gameWorldName.equals(limboWorldName) || gameWorldName.equals("world")) {
            getLogger().severe("CRITICAL: game-world-name cannot be 'limbo' or 'world'!");
            return;
        }

        World currentWorld = Bukkit.getWorld(gameWorldName);
        lastSavedDifficulty = currentWorld != null ? currentWorld.getDifficulty() : getServerDifficulty();
        getConfig().set("world.difficulty", lastSavedDifficulty.name());
        saveConfig();

        isResetting = true;
        isGameReady = false;

        lastPlayerSnapshot = capturePlayerStates();
        sendAllToLimboForReset(); // Instant — autoreset countdown was the warning

        // Start delay-out countdown IMMEDIATELY (parallel with world generation)
        if (limboDelayOut > 0) {
            startParallelDelayOut();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                unloadGameWorlds();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (getConfig().getBoolean("backup.enabled")) performBackup();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                deleteWorldFolder(gameWorldName);
                                deleteWorldFolder(gameWorldName + "_nether");
                                deleteWorldFolder(gameWorldName + "_the_end");

                                generateGameWorldsInternal(false); // false = don't start another delay-out (it's already running)
                            }
                        }.runTaskLater(Main.this, 1L);
                    }
                }.runTaskLater(Main.this, 20L);
            }
        }.runTaskLater(Main.this, 20L);
    }

    private void generateGameWorlds() {
        generateGameWorldsInternal(false);
    }

    private void generateGameWorldsInternal(boolean useDelayOut) {
        // Zapisz trudność przed usunięciem świata
        Difficulty difficulty = lastSavedDifficulty != null ? lastSavedDifficulty : getServerDifficulty();

        long seed;
        if (getConfig().getBoolean("seed.use-fixed")) {
            String s = getConfig().getString("seed.value");
            if (s == null || s.trim().isEmpty()) {
                seed = ThreadLocalRandom.current().nextLong();
                getLogger().warning("Fixed seed is enabled but seed.value is empty! Using random seed instead.");
            } else {
                try {
                    seed = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    seed = s.hashCode();
                }
            }
        } else {
            seed = ThreadLocalRandom.current().nextLong();
        }

        boolean useTemplate = getConfig().getBoolean("template.enabled", false);
        File templatesFolder = getTemplateFolder();

        File sourceOverworld = null;
        File sourceNether = null;
        File sourceEnd = null;

        if (useTemplate && templatesFolder.exists() && templatesFolder.isDirectory()) {
            File[] subDirs = templatesFolder.listFiles(File::isDirectory);
            if (subDirs != null) {
                List<File> overworldCandidates = new ArrayList<>();
                List<File> netherCandidates = new ArrayList<>();
                List<File> endCandidates = new ArrayList<>();

                for (File dir : subDirs) {
                    if (new File(dir, "level.dat").exists()) {
                        String name = dir.getName().toLowerCase();
                        if (name.contains("nether")) {
                            netherCandidates.add(dir);
                        } else if (name.contains("end")) {
                            endCandidates.add(dir);
                        } else {
                            overworldCandidates.add(dir);
                        }
                    }
                }

                // Random selection from candidates
                if (!overworldCandidates.isEmpty()) {
                    sourceOverworld = overworldCandidates.get(ThreadLocalRandom.current().nextInt(overworldCandidates.size()));
                }
                if (!netherCandidates.isEmpty()) {
                    sourceNether = netherCandidates.get(ThreadLocalRandom.current().nextInt(netherCandidates.size()));
                }
                if (!endCandidates.isEmpty()) {
                    sourceEnd = endCandidates.get(ThreadLocalRandom.current().nextInt(endCandidates.size()));
                }

                if (overworldCandidates.size() > 1) {
                    getLogger().info("Multiple overworld templates found (" + overworldCandidates.size() + "). Randomly selected: " + sourceOverworld.getName());
                }
            }
        }

        boolean templateApplied = false;

        if (useTemplate && sourceOverworld != null && new File(sourceOverworld, "level.dat").exists()) {
            try {
                getLogger().info("Loading Overworld from template: " + sourceOverworld.getName() + "...");
                File destOverworld = new File(Bukkit.getWorldContainer(), gameWorldName);
                copyTemplateFolder(sourceOverworld, destOverworld);
                templateApplied = true;

                File dimNether = new File(destOverworld, "DIM-1");
                File dimEnd = new File(destOverworld, "DIM1");

                // --- Nether handling ---
                if (sourceNether != null && new File(sourceNether, "level.dat").exists()) {
                    copyTemplateFolder(sourceNether, new File(Bukkit.getWorldContainer(), gameWorldName + "_nether"));
                    getLogger().info("Loaded Nether from separate template: " + sourceNether.getName());
                    if (dimNether.exists()) {
                        deleteDirectoryRecursive(dimNether);
                    }
                } else if (dimNether.exists() && new File(dimNether, "region").exists()) {
                    File destNether = new File(Bukkit.getWorldContainer(), gameWorldName + "_nether");
                    if (!destNether.exists()) {
                        destNether.mkdirs();
                    }
                    Files.copy(new File(destOverworld, "level.dat").toPath(), new File(destNether, "level.dat").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copyDirectory(dimNether.toPath(), destNether.toPath());
                    deleteDirectoryRecursive(dimNether);
                    getLogger().info("Successfully extracted and loaded Nether from Singleplayer template (DIM-1).");
                } else {
                    getLogger().info("No Nether template found. Generating standard Nether...");
                }

                // --- End handling ---
                if (sourceEnd != null && new File(sourceEnd, "level.dat").exists()) {
                    copyTemplateFolder(sourceEnd, new File(Bukkit.getWorldContainer(), gameWorldName + "_the_end"));
                    getLogger().info("Loaded End from separate template: " + sourceEnd.getName());
                    if (dimEnd.exists()) {
                        deleteDirectoryRecursive(dimEnd);
                    }
                } else if (dimEnd.exists() && new File(dimEnd, "region").exists()) {
                    File destEnd = new File(Bukkit.getWorldContainer(), gameWorldName + "_the_end");
                    if (!destEnd.exists()) {
                        destEnd.mkdirs();
                    }
                    Files.copy(new File(destOverworld, "level.dat").toPath(), new File(destEnd, "level.dat").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copyDirectory(dimEnd.toPath(), destEnd.toPath());
                    deleteDirectoryRecursive(dimEnd);
                    getLogger().info("Successfully extracted and loaded End from Singleplayer template (DIM1).");
                } else {
                    getLogger().info("No End template found. Generating standard End...");
                }

                // Cleanup uid.dat files to prevent Spigot world UUID conflicts
                File uidNether = new File(Bukkit.getWorldContainer(), gameWorldName + "_nether/uid.dat");
                if (uidNether.exists()) uidNether.delete();
                File uidEnd = new File(Bukkit.getWorldContainer(), gameWorldName + "_the_end/uid.dat");
                if (uidEnd.exists()) uidEnd.delete();

            } catch (Exception e) {
                getLogger().severe("Error copying templates! Falling back to standard seed generation: " + e.getMessage());
                templateApplied = false;
            }
        } else if (useTemplate) {
            getLogger().warning("Templates option is enabled but no valid world template folder containing 'level.dat' was found in templates folder. Generating standard worlds...");
        }

        if (!templateApplied) {
            getLogger().info("Generating standard world with seed: " + seed);
        }

        World normal = Bukkit.createWorld(new WorldCreator(gameWorldName).environment(World.Environment.NORMAL).seed(seed));
        Bukkit.createWorld(new WorldCreator(gameWorldName + "_nether").environment(World.Environment.NETHER).seed(seed));
        Bukkit.createWorld(new WorldCreator(gameWorldName + "_the_end").environment(World.Environment.THE_END).seed(seed));
        applyLocatorBarGamerule();

        if (normal != null) {
            // Przywróć trudność
            normal.setDifficulty(difficulty);
            if (!templateApplied) {
                applyFiltersAndShiftSpawn(normal);
                findSafeSpawn(normal);
            }
        }

        broadcastInfo(getMsg("generation-complete"));
        isGameReady = true;
        if (parallelDelayOutRunning) {
            // Parallel delay-out countdown is handling the teleport — don't start game here
            resetAndStartTimer();
            isResetting = false;
        } else if (useDelayOut && limboDelayOut > 0) {
            startGameForAllWithDelay();
            resetAndStartTimer();
            isResetting = false;
        } else {
            startGameForAll();
            resetAndStartTimer();
            isResetting = false;
        }
    }

    // --- LOGIKA FILTRÓW (EXCLUSIVE) ---
    private void applyFiltersAndShiftSpawn(World w) {
        if (!getConfig().getBoolean("filter.enabled", true)) return;

        String structReq = getConfig().getString("filter.structure", "").toUpperCase();
        String biomeReq = getConfig().getString("filter.biome", "").toUpperCase();

        if (structReq.isEmpty() && biomeReq.isEmpty()) return;

        Location bestLoc;
        String foundType;

        if (!structReq.isEmpty()) {
            bestLoc = findStructureLocation(w, structReq);
            foundType = structReq;
        }
        else {
            bestLoc = findBiomeLocation(w, biomeReq);
            foundType = biomeReq;
        }

        if (bestLoc != null) {
            bestLoc.setY(w.getHighestBlockYAt(bestLoc) + 1);
            w.setSpawnLocation(bestLoc);
            broadcastInfo(getMsg("filter-shifted").replace("{target}", foundType));
            getLogger().info("Spawn shifted to " + foundType + " at " + bestLoc.toVector());
        } else {
            broadcastInfo(getMsg("filter-failed"));
        }
    }

    private Location findBiomeLocation(World w, String biomeName) {
        try {
            Registry<@NotNull Biome> biomeRegistry = Registry.BIOME;
            NamespacedKey key = NamespacedKey.minecraft(biomeName.toLowerCase());
            Biome targetBiome = biomeRegistry.get(key);

            if (targetBiome == null) {
                getLogger().warning("Biome not found in registry: " + biomeName);
                return null;
            }

            Location center = new Location(w, 0, 64, 0);
            BiomeSearchResult result = w.locateNearestBiome(center, 2500, targetBiome);
            if (result != null) return result.getLocation();
        } catch (Exception e) {
            getLogger().warning("Error searching biome: " + e.getMessage());
        }
        return null;
    }

    private Location findStructureLocation(World w, String structName) {
        List<Structure> targets = getStructuresFromName(structName);
        if (targets.isEmpty()) return null;

        Location center = new Location(w, 0, 64, 0);
        int searchRadiusInChunks = 2500 / 16;

        Location bestLoc = null;
        double nearestDist = Double.MAX_VALUE;

        for (Structure struct : targets) {
            try {
                StructureSearchResult result = w.locateNearestStructure(center, struct, searchRadiusInChunks, false);
                if (result != null) {
                    double dist = result.getLocation().distance(center);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        bestLoc = result.getLocation();
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Error searching structure: " + e.getMessage());
            }
        }
        return bestLoc;
    }

    private List<Structure> getStructuresFromName(String name) {
        List<Structure> list = new ArrayList<>();
        Registry<@NotNull Structure> reg = Registry.STRUCTURE;

        switch (name) {
            case "VILLAGE" -> {
                addReg(list, reg, "village_plains");
                addReg(list, reg, "village_desert");
                addReg(list, reg, "village_savanna");
                addReg(list, reg, "village_snowy");
                addReg(list, reg, "village_taiga");
            }
            case "MINESHAFT" -> {
                addReg(list, reg, "mineshaft");
                addReg(list, reg, "mineshaft_mesa");
            }
            case "RUINED_PORTAL" -> {
                addReg(list, reg, "ruined_portal");
                addReg(list, reg, "ruined_portal_desert");
                addReg(list, reg, "ruined_portal_jungle");
                addReg(list, reg, "ruined_portal_swamp");
                addReg(list, reg, "ruined_portal_mountain");
                addReg(list, reg, "ruined_portal_ocean");
            }
            case "SHIPWRECK" -> {
                addReg(list, reg, "shipwreck");
                addReg(list, reg, "shipwreck_beached");
            }
            case "MANSION" -> addReg(list, reg, "mansion");
            case "STRONGHOLD" -> addReg(list, reg, "stronghold");
            case "ANCIENT_CITY" -> addReg(list, reg, "ancient_city");
            case "DESERT_PYRAMID" -> addReg(list, reg, "desert_pyramid");
            case "JUNGLE_PYRAMID" -> addReg(list, reg, "jungle_pyramid");
            case "SWAMP_HUT" -> addReg(list, reg, "swamp_hut");
            case "IGLOO" -> addReg(list, reg, "igloo");
            case "PILLAGER_OUTPOST" -> addReg(list, reg, "pillager_outpost");
            case "MONUMENT" -> addReg(list, reg, "monument");
            case "TRAIL_RUINS" -> addReg(list, reg, "trail_ruins");
            case "VILLAGE_PLAINS" -> addReg(list, reg, "village_plains");
            case "VILLAGE_DESERT" -> addReg(list, reg, "village_desert");
            case "VILLAGE_TAIGA" -> addReg(list, reg, "village_taiga");
            case "VILLAGE_SNOWY" -> addReg(list, reg, "village_snowy");
            case "VILLAGE_SAVANNA" -> addReg(list, reg, "village_savanna");

            default -> addReg(list, reg, name.toLowerCase());
        }
        return list;
    }

    private void addReg(List<Structure> list, Registry<@NotNull Structure> reg, String key) {
        Structure s = reg.get(NamespacedKey.minecraft(key));
        if (s != null) list.add(s);
    }

    private void loadGameWorlds() {
        World normal = new WorldCreator(gameWorldName).environment(World.Environment.NORMAL).createWorld();
        new WorldCreator(gameWorldName + "_nether").environment(World.Environment.NETHER).createWorld();
        new WorldCreator(gameWorldName + "_the_end").environment(World.Environment.THE_END).createWorld();
        applyLocatorBarGamerule();
        
        // Ustaw trudność: najpierw ze starego świata (config), potem z server.properties, fallback na NORMAL
        if (normal != null) {
            String diffStr = getConfig().getString("world.difficulty", "").toLowerCase();
            
            if (!diffStr.isEmpty()) {
                // Użyj zapisanej trudności ze starego świata
                try {
                    normal.setDifficulty(Difficulty.valueOf(diffStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    normal.setDifficulty(getServerDifficulty());
                }
            } else {
                // Nie ma zapisanej - pobierz z server.properties
                normal.setDifficulty(getServerDifficulty());
            }
        }
    }

    private void saveGameWorlds() {
        World overworld = Bukkit.getWorld(gameWorldName);
        World nether = Bukkit.getWorld(gameWorldName + "_nether");
        World end = Bukkit.getWorld(gameWorldName + "_the_end");
        if (overworld != null) overworld.save();
        if (nether != null) nether.save();
        if (end != null) end.save();
    }

    private void unloadGameWorlds() {
        unloadWorld(gameWorldName + "_the_end");
        unloadWorld(gameWorldName + "_nether");
        unloadWorld(gameWorldName);
        System.gc(); // Force Garbage Collector to release locked file handles on Windows
    }

    private void unloadWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) {
            boolean success = Bukkit.unloadWorld(w, false);
            if (!success) {
                getLogger().warning("Could not unload world: " + name + "! Force-saving and trying again...");
                w.save();
                Bukkit.unloadWorld(w, false);
            } else {
                getLogger().info("Successfully unloaded world: " + name);
            }
        }
    }

    public void startGameForAll() {
        World game = Bukkit.getWorld(gameWorldName);
        if (game == null) {
            loadGameWorlds();
            game = Bukkit.getWorld(gameWorldName);
            if(game == null) return;
        }
        Location spawn = game.getSpawnLocation();

        broadcastInfo(getMsg("game-started"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            setupGamePlayer(p, spawn);
        }
        incrementAttempts();
    }

    private void startGameForAllWithDelay() {
        World game = Bukkit.getWorld(gameWorldName);
        if (game == null) {
            loadGameWorlds();
            game = Bukkit.getWorld(gameWorldName);
            if (game == null) return;
        }
        Location spawn = game.getSpawnLocation();

        List<Player> playersInLimbo = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(limboWorldName)) {
                playersInLimbo.add(p);
            }
        }

        if (!playersInLimbo.isEmpty()) {
            String subtitle = getSubtitle("limbo-countdown-out", "Game starts...");
            Location finalSpawn = spawn;
            startCountdown(playersInLimbo, limboDelayOut, subtitle, () -> {
                broadcastInfo(getMsg("game-started"));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    setupGamePlayer(p, finalSpawn);
                }
                incrementAttempts();
            });
        } else {
            broadcastInfo(getMsg("game-started"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                setupGamePlayer(p, spawn);
            }
            incrementAttempts();
        }
    }

    private void findSafeSpawn(World w) {
        Location spawn = w.getSpawnLocation();
        waterSpawnActive = false;

        // Step 1: Try immediate area (8 block radius) for safe ground
        Location safe = getSafeLocation(spawn);
        if (safe != null) {
            w.setSpawnLocation(safe);
            getLogger().info("Safe spawn found nearby at: " + safe.toVector());
            w.setGameRule(GameRule.SPAWN_RADIUS, 0);
            return;
        }

        // Step 2: Use locateNearestBiome to find land biomes (BEACH, STONY_SHORE, PLAINS, FOREST)
        // Much faster than block-by-block — queries the biome noise map directly
        Location landLoc = findLandViaBiomeSearch(w, spawn, 500);
        if (landLoc != null) {
            Location safeLand = getSafeLocation(landLoc);
            if (safeLand != null) {
                w.setSpawnLocation(safeLand);
                getLogger().info("Safe spawn found via biome search at: " + safeLand.toVector() + " (" + (int) safeLand.distance(spawn) + " blocks from target)");
                w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                return;
            }
            // Biome found but exact spot not safe — try small radius around it
            Location nearby = findLandNear(w, landLoc, 32);
            if (nearby != null) {
                w.setSpawnLocation(nearby);
                getLogger().info("Safe spawn found near biome hit at: " + nearby.toVector());
                w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                return;
            }
        }

        // Step 3: Fallback — no land found. Spawn on water, give boat
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int y = w.getHighestBlockYAt(x, z);
        w.setSpawnLocation(new Location(w, x + 0.5, y + 1, z + 0.5));
        getLogger().info("No land found via biome search. Spawning on water (boat will be given).");
        waterSpawnActive = true;
        boatGivenPlayers.clear();
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        getLogger().info("Spawn finalized at: " + w.getSpawnLocation().toVector());
    }

    /**
     * Uses locateNearestBiome API to find land biomes near a point.
     * Much faster than block-by-block iteration — queries noise maps directly.
     */
    private Location findLandViaBiomeSearch(World w, Location center, int radius) {
        try {
            // Search for coastal/land biomes from the given center point
            Biome[] landBiomes = {
                Registry.BIOME.get(NamespacedKey.minecraft("beach")),
                Registry.BIOME.get(NamespacedKey.minecraft("stony_shore")),
                Registry.BIOME.get(NamespacedKey.minecraft("plains")),
                Registry.BIOME.get(NamespacedKey.minecraft("forest")),
                Registry.BIOME.get(NamespacedKey.minecraft("snowy_beach")),
                Registry.BIOME.get(NamespacedKey.minecraft("mushroom_fields"))
            };

            // Filter nulls (in case registry doesn't have some on older versions)
            List<Biome> validBiomes = new ArrayList<>();
            for (Biome b : landBiomes) {
                if (b != null) validBiomes.add(b);
            }
            if (validBiomes.isEmpty()) return null;

            // Use large horizontal interval (32) for speed — we don't need block precision here
            for (Biome target : validBiomes) {
                BiomeSearchResult result = w.locateNearestBiome(center, radius, target);
                if (result != null) {
                    Location loc = result.getLocation();
                    loc.setY(w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1);
                    return loc;
                }
            }
        } catch (Throwable e) {
            getLogger().warning("Biome search failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Searches for solid land near a location, spiraling outward.
     */
    private Location findLandNear(World w, Location center, int maxRadius) {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        for (int radius = 4; radius <= maxRadius; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    if (Math.abs(dx) < radius - 2 && Math.abs(dz) < radius - 2) continue;
                    int x = cx + dx;
                    int z = cz + dz;
                    int y = w.getHighestBlockYAt(x, z);
                    if (y > w.getMinHeight() + 1 && isLocationSafe(w, x, y + 1, z)) {
                        return new Location(w, x + 0.5, y + 1, z + 0.5, center.getYaw(), center.getPitch());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns a safe location near the given location.
     * Checks: not void, not in lava, not in fire, not in mid-air (>4 blocks fall),
     * not on damaging blocks (cactus, magma, campfire, berry bush).
     * Searches upward first, then in a spiral around.
     */
    private Location getSafeLocation(Location original) {
        if (original == null || original.getWorld() == null) return null;
        World w = original.getWorld();
        int ox = original.getBlockX();
        int oz = original.getBlockZ();

        // Try the original position first
        if (isLocationSafe(w, ox, original.getBlockY(), oz)) {
            return new Location(w, ox + 0.5, original.getBlockY(), oz + 0.5, original.getYaw(), original.getPitch());
        }

        // Try directly above (highest block at same X/Z)
        int highY = w.getHighestBlockYAt(ox, oz);
        if (highY > w.getMinHeight() && isLocationSafe(w, ox, highY + 1, oz)) {
            return new Location(w, ox + 0.5, highY + 1, oz + 0.5, original.getYaw(), original.getPitch());
        }

        // Spiral search around original position
        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue; // Only border
                    int x = ox + dx;
                    int z = oz + dz;
                    int y = w.getHighestBlockYAt(x, z) + 1;
                    if (y > w.getMinHeight() + 1 && isLocationSafe(w, x, y, z)) {
                        return new Location(w, x + 0.5, y, z + 0.5, original.getYaw(), original.getPitch());
                    }
                }
            }
        }

        // Fallback: highest block at original position
        if (highY > w.getMinHeight()) {
            return new Location(w, ox + 0.5, highY + 1, oz + 0.5, original.getYaw(), original.getPitch());
        }

        return null; // Truly no safe spot found
    }

    private boolean isLocationSafe(World w, int x, int y, int z) {
        if (y <= w.getMinHeight() + 1) return false; // Void
        Block feet = w.getBlockAt(x, y, z);
        Block below = w.getBlockAt(x, y - 1, z);
        Block head = w.getBlockAt(x, y + 1, z);

        Material belowType = below.getType();
        Material feetType = feet.getType();
        Material headType = head.getType();

        // Must have solid ground below (no water — player should spawn on land)
        if (!belowType.isSolid()) return false;

        // Feet and head must be passable (air, water — not lava, not solid)
        if (feetType == Material.LAVA || headType == Material.LAVA) return false;
        if (feetType == Material.FIRE || headType == Material.FIRE) return false;
        if (feetType.isSolid() || headType.isSolid()) return false;

        // Dangerous blocks below
        if (belowType == Material.LAVA || belowType == Material.MAGMA_BLOCK
                || belowType == Material.CACTUS || belowType == Material.CAMPFIRE
                || belowType == Material.SOUL_CAMPFIRE || belowType == Material.SWEET_BERRY_BUSH
                || belowType == Material.FIRE || belowType == Material.POINTED_DRIPSTONE) return false;

        return true;
    }

    private void performBackup() {
        broadcastInfo(getMsg("backup-start"));
        String timestamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
        File backupsDir = new File(getDataFolder().getParentFile().getParentFile(), getConfig().getString("backup.folder", "backups"));
        File currentBackupDir = new File(backupsDir, timestamp);
        if (!currentBackupDir.mkdirs()) {}
        copyWorldToBackup(gameWorldName, currentBackupDir);
        copyWorldToBackup(gameWorldName + "_nether", currentBackupDir);
        copyWorldToBackup(gameWorldName + "_the_end", currentBackupDir);

        // Save player states from snapshot (captured before limbo teleport)
        if (lastPlayerSnapshot != null) {
            try {
                lastPlayerSnapshot.save(new File(currentBackupDir, "players.yml"));
            } catch (Exception e) {
                getLogger().warning("Failed to save player states to backup: " + e.getMessage());
            }
        }

        manageBackupLimit(backupsDir);
    }

    private void savePlayerStates(File backupDir) {
        try {
            FileConfiguration playersYml = capturePlayerStates();
            if (playersYml != null) {
                playersYml.save(new File(backupDir, "players.yml"));
            }
        } catch (Exception e) {
            getLogger().warning("Failed to save player states: " + e.getMessage());
        }
    }

    /**
     * Captures current player states into a YamlConfiguration (in memory).
     * Called before players are moved to limbo.
     */
    private FileConfiguration capturePlayerStates() {
        FileConfiguration playersYml = new YamlConfiguration();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(limboWorldName)) continue;
            if (!p.getWorld().getName().contains(gameWorldName)) continue;

            String path = p.getUniqueId().toString();
            playersYml.set(path + ".name", p.getName());
            playersYml.set(path + ".world", p.getWorld().getName());
            playersYml.set(path + ".x", p.getLocation().getX());
            playersYml.set(path + ".y", p.getLocation().getY());
            playersYml.set(path + ".z", p.getLocation().getZ());
            playersYml.set(path + ".yaw", (double) p.getLocation().getYaw());
            playersYml.set(path + ".pitch", (double) p.getLocation().getPitch());
            playersYml.set(path + ".health", p.getHealth());
            playersYml.set(path + ".max-health", p.getMaxHealth());
            playersYml.set(path + ".food", p.getFoodLevel());
            playersYml.set(path + ".saturation", (double) p.getSaturation());
            playersYml.set(path + ".xp-level", p.getLevel());
            playersYml.set(path + ".xp-progress", (double) p.getExp());
            playersYml.set(path + ".gamemode", p.getGameMode().name());
            playersYml.set(path + ".fire-ticks", p.getFireTicks());

            // Inventory (Bukkit serialization)
            playersYml.set(path + ".inventory", Arrays.asList(p.getInventory().getContents()));
            playersYml.set(path + ".armor", Arrays.asList(p.getInventory().getArmorContents()));
            playersYml.set(path + ".offhand", p.getInventory().getItemInOffHand());

            // Potion effects
            List<Map<String, Object>> effects = new ArrayList<>();
            for (PotionEffect effect : p.getActivePotionEffects()) {
                effects.add(effect.serialize());
            }
            playersYml.set(path + ".effects", effects);
        }

        return playersYml;
    }

    @SuppressWarnings("unchecked")
    private void restorePlayerStates(File backupDir) {
        File playersFile = new File(backupDir, "players.yml");
        if (!playersFile.exists()) return;

        try {
            FileConfiguration playersYml = YamlConfiguration.loadConfiguration(playersFile);

            for (Player p : Bukkit.getOnlinePlayers()) {
                String path = p.getUniqueId().toString();
                if (!playersYml.contains(path)) continue;

                // Restore location
                String worldName = playersYml.getString(path + ".world", gameWorldName);
                World world = Bukkit.getWorld(worldName);
                if (world == null) world = Bukkit.getWorld(gameWorldName);
                if (world == null) continue;

                double x = playersYml.getDouble(path + ".x");
                double y = playersYml.getDouble(path + ".y");
                double z = playersYml.getDouble(path + ".z");
                float yaw = (float) playersYml.getDouble(path + ".yaw");
                float pitch = (float) playersYml.getDouble(path + ".pitch");
                Location loc = new Location(world, x, y, z, yaw, pitch);

                if (p.isDead()) p.spigot().respawn();

                // Delay restore by 2 ticks if player was dead (let respawn process first)
                boolean wasDead = p.getHealth() <= 0 || p.isDead();
                final Player fp = p;
                final Location floc = loc;
                final double fhealth = Math.max(playersYml.getDouble(path + ".health", 20), fp.getMaxHealth() / 2); // Min half HP
                final int ffood = Math.max(playersYml.getInt(path + ".food", 20), 10); // Min half food (10/20)
                final float fsat = (float) playersYml.getDouble(path + ".saturation", 5);
                final int flevel = playersYml.getInt(path + ".xp-level", 0);
                final float fxp = (float) playersYml.getDouble(path + ".xp-progress", 0);
                final int ffire = playersYml.getInt(path + ".fire-ticks", 0);
                final String fgm = playersYml.getString(path + ".gamemode", "SURVIVAL");
                final List<?> finvList = playersYml.getList(path + ".inventory");
                final List<?> farmorList = playersYml.getList(path + ".armor");
                final Object foffhand = playersYml.get(path + ".offhand");
                final List<Map<?, ?>> feffects = playersYml.getMapList(path + ".effects");

                Runnable restoreAction = () -> {
                    if (!fp.isOnline()) return;
                    Location safeLoc = getSafeLocation(floc);
                    fp.teleport(safeLoc != null ? safeLoc : floc);

                    try { fp.setGameMode(GameMode.valueOf(fgm)); } catch (Exception ignored) { fp.setGameMode(GameMode.SURVIVAL); }
                    fp.setHealth(Math.min(fhealth, fp.getMaxHealth()));
                    fp.setFoodLevel(ffood);
                    fp.setSaturation(fsat);
                    fp.setLevel(flevel);
                    fp.setExp(fxp);
                    fp.setFireTicks(Math.max(ffire, 0));

                    // Restore inventory
                    fp.getInventory().clear();
                    if (finvList != null) {
                        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[finvList.size()];
                        for (int i = 0; i < finvList.size(); i++) {
                            Object item = finvList.get(i);
                            if (item instanceof org.bukkit.inventory.ItemStack) contents[i] = (org.bukkit.inventory.ItemStack) item;
                        }
                        fp.getInventory().setContents(contents);
                    }
                    if (farmorList != null) {
                        org.bukkit.inventory.ItemStack[] armor = new org.bukkit.inventory.ItemStack[farmorList.size()];
                        for (int i = 0; i < farmorList.size(); i++) {
                            Object item = farmorList.get(i);
                            if (item instanceof org.bukkit.inventory.ItemStack) armor[i] = (org.bukkit.inventory.ItemStack) item;
                        }
                        fp.getInventory().setArmorContents(armor);
                    }
                    if (foffhand instanceof org.bukkit.inventory.ItemStack) {
                        fp.getInventory().setItemInOffHand((org.bukkit.inventory.ItemStack) foffhand);
                    }

                    // Restore potion effects
                    for (PotionEffect effect : fp.getActivePotionEffects()) fp.removePotionEffect(effect.getType());
                    if (feffects != null) {
                        for (Map<?, ?> map : feffects) {
                            try {
                                Map<String, Object> effectMap = new HashMap<>();
                                for (Map.Entry<?, ?> entry : map.entrySet()) {
                                    effectMap.put(entry.getKey().toString(), entry.getValue());
                                }
                                fp.addPotionEffect(new PotionEffect(effectMap));
                            } catch (Exception ignored) {}
                        }
                    }

                    // Grant brief invulnerability
                    fp.setInvulnerable(true);
                    new BukkitRunnable() { @Override public void run() { if (fp.isOnline()) fp.setInvulnerable(false); } }.runTaskLater(Main.this, 60L);
                };

                if (wasDead) {
                    new BukkitRunnable() { @Override public void run() { restoreAction.run(); } }.runTaskLater(Main.this, 2L);
                } else {
                    restoreAction.run();
                }
            }
        } catch (Exception e) {
            getLogger().warning("Failed to restore player states: " + e.getMessage());
        }
    }

    private void copyWorldToBackup(String worldName, File backupDir) {
        File legacyFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (legacyFolder.exists()) {
            try { copyDirectory(legacyFolder.toPath(), new File(backupDir, worldName).toPath()); } catch (IOException ignored) {}
        }
        
        if (!Bukkit.getWorlds().isEmpty()) {
            String mainWorldName = Bukkit.getWorlds().getFirst().getName();
            File migratedFolder = new File(Bukkit.getWorldContainer(), mainWorldName + "/dimensions/minecraft/" + worldName);
            if (migratedFolder.exists()) {
                try { copyDirectory(migratedFolder.toPath(), new File(backupDir, worldName).toPath()); } catch (IOException ignored) {}
            }
        }

        // Ensure level.dat is included (Paper keeps it only in main world folder)
        File backupWorldDir = new File(backupDir, worldName);
        if (backupWorldDir.exists() && !new File(backupWorldDir, "level.dat").exists()) {
            String mainWorldName = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().getFirst().getName();
            File mainLevelDat = new File(Bukkit.getWorldContainer(), mainWorldName + "/level.dat");
            if (mainLevelDat.exists()) {
                try {
                    Files.copy(mainLevelDat.toPath(), new File(backupWorldDir, "level.dat").toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }
        }
    }
    private void copyDirectory(Path source, Path target) throws IOException { Files.walkFileTree(source, new SimpleFileVisitor<>() {
        @Override
        public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
            Path targetDir = target.resolve(source.relativize(dir));
            if (!Files.exists(targetDir)) Files.createDirectory(targetDir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
            if (!file.getFileName().toString().equals("session.lock")) {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
            }
            return FileVisitResult.CONTINUE;
        }
    }); }
    private void deleteWorldFolder(String worldName) {
        File legacyFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (legacyFolder.exists()) {
            deleteDirectoryRecursive(legacyFolder);
        }
        
        if (!Bukkit.getWorlds().isEmpty()) {
            String mainWorldName = Bukkit.getWorlds().getFirst().getName();
            File migratedFolder = new File(Bukkit.getWorldContainer(), mainWorldName + "/dimensions/minecraft/" + worldName);
            if (migratedFolder.exists()) {
                deleteDirectoryRecursive(migratedFolder);
            }
        }
    }
    private void deleteDirectoryRecursive(File file) {
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteDirectoryRecursive(f);
                    } else {
                        if (!f.delete()) {
                            getLogger().warning("Failed to delete file: " + f.getAbsolutePath());
                        }
                    }
                }
            }
            if (!file.delete()) {
                getLogger().warning("Failed to delete directory: " + file.getAbsolutePath());
            }
        }
    }
    private void manageBackupLimit(File backupsDir) { String limitStr = getConfig().getString("backup.limit", "all"); if(limitStr.equalsIgnoreCase("all")) return; try { int limit = Integer.parseInt(limitStr); File[] backups = backupsDir.listFiles(File::isDirectory); if (backups != null && backups.length > limit) { Arrays.sort(backups, Comparator.comparingLong(File::lastModified)); int toDelete = backups.length - limit; for(int i=0; i<toDelete; i++) deleteDirectoryRecursive(backups[i]); } } catch(Exception ignored) {} }

    private void createTemplateFolders() {
        try {
            File templatesDir = getTemplateFolder();
            if (!templatesDir.exists()) {
                templatesDir.mkdirs();
            }

            File readme = new File(templatesDir, "README.txt");
            if (!readme.exists()) {
                try (java.io.FileWriter writer = new java.io.FileWriter(readme)) {
                    writer.write("=== WorldReset Templates Instruction / Instrukcja Szablonów ===\n\n");
                    writer.write("[EN] How to use:\n");
                    writer.write("1. Set 'template.enabled: true' in config.yml.\n");
                    writer.write("2. Drop your world folder(s) directly into this templates directory.\n");
                    writer.write("   - Singleplayer save: Drop one folder (e.g. 'my_world' containing 'level.dat', 'region', 'DIM-1', 'DIM1'). The plugin automatically extracts Nether and End.\n");
                    writer.write("   - Multiplayer layout: Drop separate folders for dimensions. Any folder containing 'nether' in its name becomes Nether, 'end' becomes End, and any other folder becomes Overworld.\n");
                    writer.write("3. During reset, the plugin will load these maps instead of generating them from seed.\n\n");
                    writer.write("[PL] Jak używać:\n");
                    writer.write("1. Ustaw 'template.enabled: true' w pliku config.yml.\n");
                    writer.write("2. Wrzuć folder(y) swojego świata bezpośrednio do tego katalogu szablonów.\n");
                    writer.write("   - Świat z Singleplayer: Wrzuć jeden folder (np. 'moj_swiat' zawierający 'level.dat', 'region', 'DIM-1', 'DIM1'). Plugin automatycznie wyodrębni Nether i End.\n");
                    writer.write("   - Świat z Multiplayer: Wrzuć osobne foldery dla wymiarów. Folder z 'nether' w nazwie staje się Netherem, z 'end' Endem, a każdy inny Overworldem.\n");
                    writer.write("3. Podczas resetu, plugin skopiuje te mapy zamiast generować je na nowo z seeda.\n");
                }
            }
        } catch (Exception e) {
            getLogger().warning("Failed to create template folders: " + e.getMessage());
        }
    }

    private void copyTemplateFolder(File source, File target) throws IOException {
        if (!source.exists()) return;
        copyDirectory(source.toPath(), target.toPath());
        File uidFile = new File(target, "uid.dat");
        if (uidFile.exists()) {
            uidFile.delete();
        }
    }

    private void initDynamicNames() {
        STRUCTURE_NAMES.clear();
        STRUCTURE_NAMES.addAll(Arrays.asList("VILLAGE", "MINESHAFT", "RUINED_PORTAL", "SHIPWRECK"));

        try {
            @SuppressWarnings({"deprecation", "removal"})
            Registry<Structure> structRegistry = Registry.STRUCTURE;
            for (Structure s : structRegistry) {
                @SuppressWarnings({"deprecation", "removal"})
                String name = s.key().value().toUpperCase();
                if (!STRUCTURE_NAMES.contains(name)) {
                    STRUCTURE_NAMES.add(name);
                }
            }
        } catch (Throwable e) {
            STRUCTURE_NAMES.addAll(Arrays.asList(
                "DESERT_PYRAMID", "JUNGLE_PYRAMID", "PILLAGER_OUTPOST", "MANSION",
                "ANCIENT_CITY", "STRONGHOLD", "MONUMENT", "BURIED_TREASURE", "IGLOO",
                "SWAMP_HUT", "TRAIL_RUINS", "TRIAL_CHAMBERS", "OCEAN_RUIN", "OCEAN_RUIN_WARM"
            ));
        }

        BIOME_NAMES.clear();
        try {
            for (Biome b : Registry.BIOME) {
                String name = b.key().value().toUpperCase();
                if (!BIOME_NAMES.contains(name)) {
                    BIOME_NAMES.add(name);
                }
            }
        } catch (Throwable e) {
            BIOME_NAMES.addAll(Arrays.asList(
                "PLAINS", "SUNFLOWER_PLAINS", "DESERT", "FOREST", "FLOWER_FOREST", "BIRCH_FOREST",
                "OLD_GROWTH_BIRCH_FOREST", "DARK_FOREST", "TAIGA", "OLD_GROWTH_PINE_TAIGA",
                "OLD_GROWTH_SPRUCE_TAIGA", "SNOWY_TAIGA", "SWAMP", "MANGROVE_SWAMP",
                "JUNGLE", "SPARSE_JUNGLE", "BAMBOO_JUNGLE", "BADLANDS", "WOODED_BADLANDS",
                "ERODED_BADLANDS", "SAVANNA", "SAVANNA_PLATEAU", "WINDSWEPT_SAVANNA",
                "WINDSWEPT_HILLS", "WINDSWEPT_GRAVELLY_HILLS", "WINDSWEPT_FOREST",
                "SNOWY_PLAINS", "ICE_SPIKES", "SNOWY_SLOPES", "FROZEN_PEAKS", "JAGGED_PEAKS",
                "STONY_PEAKS", "MUSHROOM_FIELDS", "CHERRY_GROVE", "PALE_GARDEN",
                "MEADOW", "GROVE", "STONY_SHORE", "RIVER", "FROZEN_RIVER",
                "BEACH", "SNOWY_BEACH", "WARM_OCEAN", "LUKEWARM_OCEAN", "DEEP_LUKEWARM_OCEAN",
                "OCEAN", "DEEP_OCEAN", "COLD_OCEAN", "DEEP_COLD_OCEAN", "FROZEN_OCEAN",
                "DEEP_FROZEN_OCEAN", "DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK"
            ));
        }

        Collections.sort(STRUCTURE_NAMES);
        Collections.sort(BIOME_NAMES);
    }

    // --- LOGIKA TIMERA ---

    private void resetTimerData() {
        globalElapsedTime = 0;
        globalElapsedTicks = 0;
        playerStartTimes.clear();
        playerElapsedTimes.clear();
        playerElapsedTicks.clear();
        playersFinished.clear();
    }

    private void startTimer() {
        if (!timerEnabled) return;
        if (timerRunning) return;

        if (goalReachedPause) {
            resetTimerData();
            goalReachedPause = false;
        }

        timerRunning = true;
        long now = System.currentTimeMillis();

        if (timerScope.equals("GLOBAL")) {
            globalStartTime = now - globalElapsedTime;
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!playersFinished.contains(p.getUniqueId())) {
                    long elapsed = playerElapsedTimes.getOrDefault(p.getUniqueId(), 0L);
                    playerStartTimes.put(p.getUniqueId(), now - elapsed);
                }
            }
        }

        startTimerTask();
        broadcastInfo(getMsg("timer-started"));
        syncAllScoreboards();
    }

    private void stopTimer() {
        stopTimer(false);
    }

    private void stopTimer(boolean silent) {
        if (!timerEnabled) return;
        if (timerRunning) {
            timerRunning = false;
            goalReachedPause = false;
            if (timerTask != null) {
                timerTask.cancel();
                timerTask = null;
            }
            if (!silent) {
                broadcastInfo(getMsg("timer-paused"));
            }
            syncAllScoreboards();
        }
    }

    private void resetAndStartTimer() {
        if (!timerEnabled) return;

        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        resetTimerData();
        goalReachedPause = false;

        long now = System.currentTimeMillis();
        if (timerScope.equals("GLOBAL")) {
            globalStartTime = now;
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerStartTimes.put(p.getUniqueId(), now);
            }
        }

        if (!timerRunning) {
            timerRunning = true;
            broadcastInfo(getMsg("timer-started"));
        }
        startTimerTask();
        syncAllScoreboards();
    }

    private void startTimerTask() {
        if (timerTask != null) timerTask.cancel();

        timerTask = new BukkitRunnable() {
            private int tickCount = 0;

            @Override
            public void run() {
                if (!timerRunning) {
                    this.cancel();
                    return;
                }

                // Check ITEM goal and update live run scoreboard once every 10 ticks (0.5s)
                tickCount++;
                if (tickCount >= 10) {
                    tickCount = 0;
                    
                    try {
                        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                        org.bukkit.scoreboard.Objective runMsObj = getOrRegisterObjective(sb, "wr_run_ms", "Run (Millis)");
                        org.bukkit.scoreboard.Objective runSecObj = getOrRegisterObjective(sb, "wr_run_sec", "Run (Seconds)");
                        org.bukkit.scoreboard.Objective runMinObj = getOrRegisterObjective(sb, "wr_run_min", "Run (Minutes)");
                        org.bukkit.scoreboard.Objective runTicksObj = getOrRegisterObjective(sb, "wr_run_ticks", "Run (Ticks)");

                        org.bukkit.scoreboard.Objective playersActiveObj = sb.getObjective("wr_players_active");
                        org.bukkit.scoreboard.Objective timerStatusObj = sb.getObjective("wr_timer_status");
                        org.bukkit.scoreboard.Objective playerFinishedObj = sb.getObjective("wr_player_finished");

                        World w = Bukkit.getWorld(gameWorldName);
                        int activePlayers = w != null ? w.getPlayers().size() : 0;
                        int timerStatusVal = goalReachedPause ? 2 : (timerRunning ? 1 : 0);

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.getWorld().getName().equals(limboWorldName)) {
                                long elapsed = getRawLiveTime(p.getUniqueId());
                                runMsObj.getScore(p.getName()).setScore((int) elapsed);
                                runSecObj.getScore(p.getName()).setScore((int) (elapsed / 1000L));
                                runMinObj.getScore(p.getName()).setScore((int) (elapsed / 60000L));
                                runTicksObj.getScore(p.getName()).setScore((int) (elapsed / 50L));

                                if (playersActiveObj != null) {
                                    playersActiveObj.getScore(p.getName()).setScore(activePlayers);
                                }
                                if (timerStatusObj != null) {
                                    timerStatusObj.getScore(p.getName()).setScore(timerStatusVal);
                                }
                                if (playerFinishedObj != null) {
                                    int playerFinishedVal = playersFinished.contains(p.getUniqueId()) ? 1 : 0;
                                    playerFinishedObj.getScore(p.getName()).setScore(playerFinishedVal);
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (timerGoalType.equals("ITEM")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.getWorld().getName().equals(limboWorldName)) {
                                for (org.bukkit.inventory.ItemStack item : p.getInventory().getContents()) {
                                    if (item != null) {
                                        String itemKey = item.getType().key().asString().toLowerCase(); // e.g. "minecraft:diamond"
                                        checkTimerGoal(p, "ITEM", itemKey);
                                    }
                                }
                            }
                        }
                    }
                }

                if (timerScope.equals("GLOBAL")) {
                    if (timerMode.equals("IGT")) {
                        globalElapsedTicks++;
                        updateActionBarGlobal(formatTime(globalElapsedTicks * 50L, false));
                    } else {
                        globalElapsedTime = System.currentTimeMillis() - globalStartTime;
                        updateActionBarGlobal(formatTime(globalElapsedTime, false));
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (playersFinished.contains(p.getUniqueId())) {
                            long finalTime = playerElapsedTimes.getOrDefault(p.getUniqueId(), 0L);
                            if (timerMode.equals("IGT")) finalTime = playerElapsedTicks.getOrDefault(p.getUniqueId(), 0) * 50L;
                            sendActionBar(p, getMsg("timer-finished-action").replace("{time}", formatTime(finalTime, true)));
                        } else {
                            if (timerMode.equals("IGT")) {
                                int ticks = playerElapsedTicks.getOrDefault(p.getUniqueId(), 0) + 1;
                                playerElapsedTicks.put(p.getUniqueId(), ticks);
                                sendActionBar(p, formatTime(ticks * 50L, false));
                            } else {
                                if (!playerStartTimes.containsKey(p.getUniqueId())) {
                                    playerStartTimes.put(p.getUniqueId(), System.currentTimeMillis());
                                }
                                long start = playerStartTimes.get(p.getUniqueId());
                                long elapsed = System.currentTimeMillis() - start;
                                playerElapsedTimes.put(p.getUniqueId(), elapsed);
                                sendActionBar(p, formatTime(elapsed, false));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void checkTimerGoal(Player p, String triggerType, String triggerValue) {
        if (!timerEnabled || !timerRunning) return;
        if (timerGoalType.equals("NONE")) return;

        if (timerGoalType.equals(triggerType)) {
            boolean isMatch = false;
            if (triggerType.equals("PORTAL")) {
                if (timerGoalValue.equals("ANY") || timerGoalValue.equals(triggerValue)) isMatch = true;
            } else {
                String goal = timerGoalValue.toLowerCase();
                String trigger = triggerValue.toLowerCase();
                if (goal.equals(trigger)) {
                    isMatch = true;
                } else if (goal.contains(":") && !trigger.contains(":")) {
                    if (goal.endsWith(":" + trigger)) isMatch = true;
                } else if (!goal.contains(":") && trigger.contains(":")) {
                    if (trigger.endsWith(":" + goal)) isMatch = true;
                }
            }

            if (isMatch) handleTimerWin(p);
        }
    }

    private void handleTimerWin(Player winner) {
        long finalTime = 0;
        if (timerScope.equals("GLOBAL")) {
            timerRunning = false;
            goalReachedPause = true;

            if (timerTask != null) {
                timerTask.cancel();
                timerTask = null;
            }
            finalTime = timerMode.equals("IGT") ? globalElapsedTicks * 50L : globalElapsedTime;
            String timeStr = formatTime(finalTime, true);
            broadcastInfo(getMsg("timer-win-global").replace("{player}", winner.getName()).replace("{time}", timeStr));
            updateActionBarGlobal(getMsg("timer-finished-action").replace("{time}", timeStr));
        } else {
            if (!playersFinished.contains(winner.getUniqueId())) {
                playersFinished.add(winner.getUniqueId());
                finalTime = timerMode.equals("IGT") ? playerElapsedTicks.getOrDefault(winner.getUniqueId(), 0) * 50L : playerElapsedTimes.getOrDefault(winner.getUniqueId(), 0L);
                String timeStr = formatTime(finalTime, true);
                broadcastInfo(getMsg("timer-win-individual").replace("{player}", winner.getName()).replace("{time}", timeStr));
                sendActionBar(winner, getMsg("timer-finished-action").replace("{time}", timeStr));
            }
        }

        if (finalTime > 0) {
            try {
                UUID uuid = winner.getUniqueId();
                String path = "players." + uuid.toString();
                
                int completions = recordsConfig.getInt(path + ".completions", 0) + 1;
                recordsConfig.set(path + ".completions", completions);
                recordsConfig.set(path + ".name", winner.getName());
                recordsConfig.set(path + ".last_time", finalTime);

                long currentTotal = recordsConfig.getLong(path + ".total_completion_time", -1);
                if (currentTotal == -1) {
                    long currentPb = recordsConfig.getLong(path + ".pb", -1);
                    if (currentPb > 0 && completions > 1) {
                        currentTotal = currentPb * (completions - 1);
                    } else {
                        currentTotal = 0;
                    }
                }
                recordsConfig.set(path + ".total_completion_time", currentTotal + finalTime);

                long currentPb = recordsConfig.getLong(path + ".pb", -1);
                if (currentPb == -1 || finalTime < currentPb) {
                    recordsConfig.set(path + ".pb", finalTime);
                    String dateStr = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new java.util.Date());
                    recordsConfig.set(path + ".pb_date", dateStr);
                    winner.sendMessage(ChatColor.translateAlternateColorCodes('&', getMsg("timer-new-pb").replace("{time}", formatTime(finalTime, true))));
                }

                World w = Bukkit.getWorld(gameWorldName);
                long seed = w != null ? w.getSeed() : 0;
                updateLeaderboard(winner.getName(), uuid, finalTime, seed);
                saveRecordsFile();
                syncAllScoreboards();
            } catch (Exception e) {
                getLogger().warning("Failed to save player records: " + e.getMessage());
            }
        }
    }

    private String formatTime(long millis, boolean showMs) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;

        if (showMs) {
            if (hours > 0) return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms);
            return String.format("%d:%02d.%03d", minutes, seconds, ms);
        } else {
            if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    private void updateActionBarGlobal(String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendActionBar(p, text);
        }
    }

    private void sendActionBar(Player p, String text) {
        StringBuilder sb = new StringBuilder();

        // Timer part - only show if timer is enabled and has content
        if (timerEnabled && text != null && !text.isEmpty()) {
            sb.append("&e&l⏱ ").append(text);
        }

        // AutoReset part - hide when at 0 (reset in progress or just finished)
        if (autoResetEnabled && autoResetVisible && autoResetRemainingSeconds > 0) {
            String autoResetText;
            if (autoResetPaused) {
                autoResetText = "&7⟳ " + formatAutoResetTime(autoResetRemainingSeconds) + " &7(||)";
            } else {
                // Color based on remaining time
                String color = autoResetRemainingSeconds <= 30 ? "&c&l" : (autoResetRemainingSeconds <= 120 ? "&6" : "&a");
                autoResetText = color + "⟳ " + formatAutoResetTime(autoResetRemainingSeconds);
            }

            if (!sb.isEmpty()) {
                sb.append(" &7| ");
            }
            sb.append(autoResetText);
        }

        if (sb.isEmpty()) return;

        p.sendActionBar(Component.text(ChatColor.translateAlternateColorCodes('&', sb.toString())));
    }

    /**
     * Sends only the autoreset action bar when the timer is NOT running
     * (so autoreset still refreshes the display independently).
     */
    private void refreshAutoResetActionBar() {
        if (!autoResetEnabled || !autoResetVisible) return;
        if (timerEnabled && timerRunning) return; // Timer task already handles display

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().getName().equals(limboWorldName)) {
                sendActionBar(p, "");
            }
        }
    }

    // --- LOGIKA KOMPASU (RADARU) ---
    private void applyLocatorBarGamerule() {
        try {
            org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName("locator_bar");
            if (rule != null) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().contains(gameWorldName)) {
                        @SuppressWarnings("unchecked")
                        org.bukkit.GameRule<Boolean> boolRule = (org.bukkit.GameRule<Boolean>) rule;
                        w.setGameRule(boolRule, compassEnabled);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Could not set locator_bar game rule: " + e.getMessage());
        }
    }

    // --- LOGIKA AUTORESETU ---

    private long parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 3600;
        timeStr = timeStr.trim().toLowerCase();
        try {
            if (timeStr.endsWith("s")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
            } else if (timeStr.endsWith("m")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 60;
            } else if (timeStr.endsWith("h")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 3600;
            } else {
                return Long.parseLong(timeStr);
            }
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid autoreset time format: " + timeStr + ". Using default 1h.");
            return 3600;
        }
    }

    private void startAutoResetTimer() {
        stopAutoResetTimer();
        if (!autoResetEnabled) return;

        autoResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!autoResetEnabled) {
                    this.cancel();
                    return;
                }
                if (autoResetPaused) return;

                autoResetRemainingSeconds--;

                // Last 5 seconds - show title countdown with sound
                if (autoResetRemainingSeconds <= 5 && autoResetRemainingSeconds > 0) {
                    int remaining = (int) autoResetRemainingSeconds;
                    String color = remaining <= 3 ? "§c§l" : "§6§l";
                    String subtitle = getSubtitle("autoreset-countdown", "World Reset...");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.getWorld().getName().equals(limboWorldName)) {
                            p.sendTitle(color + remaining, "§7" + subtitle, 0, 25, 5);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, remaining <= 3 ? 1.5f : 1.0f);
                        }
                    }
                }

                if (autoResetRemainingSeconds <= 0) {
                    this.cancel();
                    autoResetTask = null;
                    broadcastInfo(getMsg("autoreset-triggered"));
                    startAutoResetReset();

                    // Po resecie restartujemy autoreset jeśli loop
                    if (autoResetLoop) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                autoResetRemainingSeconds = autoResetTotalSeconds;
                                startAutoResetTimer();
                            }
                        }.runTaskLater(Main.this, 100L);
                    }
                }

                // Refresh action bar display for autoreset (when timer is not running)
                refreshAutoResetActionBar();
                syncAutoResetScoreboard();
            }
        }.runTaskTimer(this, 20L, 20L); // Co 1 sekundę (20 ticków)
    }

    private void stopAutoResetTimer() {
        if (autoResetTask != null) {
            autoResetTask.cancel();
            autoResetTask = null;
        }
    }

    private void syncAutoResetScoreboard() {
        try {
            org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Objective arSecObj = getOrRegisterObjective(sb, "wr_autoreset_sec", "AutoReset (Sec)");
            org.bukkit.scoreboard.Objective arMinObj = getOrRegisterObjective(sb, "wr_autoreset_min", "AutoReset (Min)");
            org.bukkit.scoreboard.Objective arStatusObj = getOrRegisterObjective(sb, "wr_autoreset_status", "AutoReset Status");

            int secVal = (int) autoResetRemainingSeconds;
            int minVal = (int) (autoResetRemainingSeconds / 60);
            // Status: 0 = disabled, 1 = running, 2 = paused
            int statusVal = !autoResetEnabled ? 0 : (autoResetPaused ? 2 : 1);

            for (Player p : Bukkit.getOnlinePlayers()) {
                arSecObj.getScore(p.getName()).setScore(secVal);
                arMinObj.getScore(p.getName()).setScore(minVal);
                arStatusObj.getScore(p.getName()).setScore(statusVal);
            }
        } catch (Exception ignored) {}
    }

    private String formatAutoResetTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    // --- EVENTS ---
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (isResetting && !isDelayingReset) {
            // Reset in progress and delay is over — respawn in limbo
            Location limbo = getLimboSpawn();
            if (limbo != null) e.setRespawnLocation(limbo);
            return;
        }

        // Normal game respawn (or during delay-in phase) - send to game world spawn
        String playerWorld = e.getPlayer().getWorld().getName();
        if (playerWorld.contains(gameWorldName) || playerWorld.equals(limboWorldName)) {
            World game = Bukkit.getWorld(gameWorldName);
            if (game != null && !e.isBedSpawn() && !e.isAnchorSpawn()) {
                e.setRespawnLocation(game.getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (isResetting) return; // Don't reset HP during backup load
        if (event.getNewGameMode() == GameMode.SURVIVAL && !event.getPlayer().getWorld().getName().equals(limboWorldName)) {
            event.getPlayer().setHealth(event.getPlayer().getMaxHealth());
            event.getPlayer().setFoodLevel(20);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (timerEnabled && timerRunning && timerScope.equals("INDIVIDUAL") && !playersFinished.contains(p.getUniqueId()) && !playerStartTimes.containsKey(p.getUniqueId())) {
            playerStartTimes.put(p.getUniqueId(), System.currentTimeMillis());
        }


        if (isGameReady && !isResetting) {
            String wName = p.getWorld().getName();
            boolean shouldTeleport = !p.hasPlayedBefore() || wName.equals("world") || wName.equals(limboWorldName);
            if (shouldTeleport) {
                World game = Bukkit.getWorld(gameWorldName);
                if (game != null) {
                    setupGamePlayer(p, game.getSpawnLocation());
                }
            }
        } else {
            Location loc = getLimboSpawn();
            p.teleport(loc);
            setupLimboPlayer(p);
        }

        syncAllScoreboards();

        // Give boat if water spawn is active and player hasn't received one
        if (waterSpawnActive && !boatGivenPlayers.contains(p.getUniqueId())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline() && !boatGivenPlayers.contains(p.getUniqueId())) {
                        p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.OAK_BOAT));
                        boatGivenPlayers.add(p.getUniqueId());
                    }
                }
            }.runTaskLater(Main.this, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Bukkit.getScheduler().runTaskLater(this, this::syncAllScoreboards, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!getConfig().getBoolean("reset-on-death", false)) return;
        if (isResetting) return;
        if (e.getEntity().getWorld().getName().equals(limboWorldName)) return;
        if (!e.getEntity().getWorld().getName().contains(gameWorldName)) return;

        // Capture player states BEFORE death clears them (use pre-death snapshot for backup)
        // The dying player's inventory is in getDrops(), other players are alive
        lastPlayerSnapshot = capturePlayerStates();
        // Override the dead player's data with pre-death state
        Player dead = e.getEntity();
        String path = dead.getUniqueId().toString();
        if (lastPlayerSnapshot != null) {
            lastPlayerSnapshot.set(path + ".health", Math.max(dead.getMaxHealth() / 2, 1)); // Half health on restore
            // Reconstruct inventory from drops
            org.bukkit.inventory.ItemStack[] inv = new org.bukkit.inventory.ItemStack[41];
            int i = 0;
            for (org.bukkit.inventory.ItemStack item : e.getDrops()) {
                if (i < inv.length) inv[i++] = item;
            }
            lastPlayerSnapshot.set(path + ".inventory", Arrays.asList(inv));
        }

        String playerName = dead.getName();
        broadcastInfo(getMsg("death-reset-triggered").replace("{player}", playerName));
        startAutoTriggeredReset();
    }

    // --- TIMER TRIGGER EVENTS ---
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            String entityName = e.getEntity().getType().key().asMinimalString();
            checkTimerGoal(e.getEntity().getKiller(), "ENTITY", entityName.toLowerCase());
        }
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent e) {
        if (e.getAdvancement().key().value().startsWith("recipes/")) return;
        String advName = e.getAdvancement().key().asMinimalString();
        checkTimerGoal(e.getPlayer(), "ADVANCEMENT", advName.toLowerCase());
    }

    // Dodatkowy w pełni precyzyjny event speedrunnerski
    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent e) {
        if (isResetting || !timerEnabled || !timerRunning) return;
        if (!(e.getEntity() instanceof Player p)) return;

        String n = e.getLocation().getWorld().getName();
        if (!n.contains(gameWorldName)) return;

        Material m = e.getLocation().getBlock().getType();
        String portalType = null;

        if (m == Material.NETHER_PORTAL) {
            portalType = "NETHER";
        } else if (m == Material.END_PORTAL) {
            if (n.endsWith("_the_end")) {
                portalType = "OVERWORLD";
            } else {
                portalType = "END";
            }
        }

        if (portalType != null) {
            checkTimerGoal(p, "PORTAL", portalType);
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if(isResetting) return;
        String n = e.getFrom().getWorld().getName();

        // Fallback w razie opoznienia EntityPortalEnterEvent
        if (timerEnabled && timerRunning) {
            String portalType = "ANY";
            if (e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                portalType = "NETHER";
            } else if (e.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
                if (n.endsWith("_the_end")) {
                    portalType = "OVERWORLD";
                } else {
                    portalType = "END";
                }
            }
            checkTimerGoal(e.getPlayer(), "PORTAL", portalType);
        }

        if(!n.contains(gameWorldName)) return;

        if (n.equals(gameWorldName)) {
            if(e.getCause()==PlayerTeleportEvent.TeleportCause.NETHER_PORTAL){
                World nether = Bukkit.getWorld(gameWorldName+"_nether");
                if(nether!=null && e.getTo() != null) {
                    Location to = e.getTo().clone();
                    to.setWorld(nether);
                    e.setTo(to);
                }
            } else if(e.getCause()==PlayerTeleportEvent.TeleportCause.END_PORTAL){
                World end = Bukkit.getWorld(gameWorldName+"_the_end");
                if(end!=null) e.setTo(new Location(end, 100, 50, 0));
            }
        } else if (n.equals(gameWorldName+"_nether")) {
            if(e.getCause()==PlayerTeleportEvent.TeleportCause.NETHER_PORTAL){
                World over = Bukkit.getWorld(gameWorldName);
                if(over!=null && e.getTo() != null) {
                    Location to = e.getTo().clone();
                    to.setWorld(over);
                    e.setTo(to);
                }
            }
        } else if (n.equals(gameWorldName+"_the_end")) {
            if(e.getCause()==PlayerTeleportEvent.TeleportCause.END_PORTAL){
                World over = Bukkit.getWorld(gameWorldName);
                if(over!=null) e.setTo(over.getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent e) {
        if (isResetting) return;
        if (e.getTo() == null || e.getTo().getWorld() == null) return;

        String n = e.getFrom().getWorld().getName();
        if (!n.contains(gameWorldName)) return;

        if (n.equals(gameWorldName)) {
            if (e.getTo().getWorld().getEnvironment() == World.Environment.NETHER) {
                World nether = Bukkit.getWorld(gameWorldName + "_nether");
                if (nether != null) {
                    Location to = e.getTo().clone();
                    to.setWorld(nether);
                    e.setTo(to);
                }
            } else if (e.getTo().getWorld().getEnvironment() == World.Environment.THE_END) {
                World end = Bukkit.getWorld(gameWorldName + "_the_end");
                if (end != null) e.setTo(new Location(end, 100, 50, 0));
            }
        } else if (n.equals(gameWorldName + "_nether")) {
            if (e.getTo().getWorld().getEnvironment() == World.Environment.NORMAL) {
                World over = Bukkit.getWorld(gameWorldName);
                if (over != null) {
                    Location to = e.getTo().clone();
                    to.setWorld(over);
                    e.setTo(to);
                }
            }
        } else if (n.equals(gameWorldName + "_the_end")) {
            if (e.getTo().getWorld().getEnvironment() == World.Environment.NORMAL) {
                World over = Bukkit.getWorld(gameWorldName);
                if (over != null) e.setTo(over.getSpawnLocation());
            }
        }
    }

    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p) {
        if (p.getWorld().getName().equals(limboWorldName)) { e.setDamage(0); if (e.getCause() == EntityDamageEvent.DamageCause.VOID) { Location loc = getLimboSpawn();
            p.teleport(loc);
            e.setCancelled(true); } } } }
    @EventHandler public void onHit(EntityDamageByEntityEvent e) { if (e.getDamager().getWorld().getName().equals(limboWorldName)) e.setDamage(0); }
    @EventHandler public void onHunger(FoodLevelChangeEvent e) { if (e.getEntity().getWorld().getName().equals(limboWorldName)) { e.setCancelled(true); e.setFoodLevel(20); } }
    @EventHandler public void onBreak(BlockBreakEvent e) { 
        if (e.getPlayer().getWorld().getName().equals(limboWorldName) && !e.getPlayer().isOp()) {
            e.setCancelled(true); 
            return;
        }
        if (timerEnabled && timerRunning && timerGoalType.equals("BLOCK") && !e.getPlayer().getWorld().getName().equals(limboWorldName)) {
            String blockKey = e.getBlock().getType().key().asString().toLowerCase(); // e.g. "minecraft:obsidian"
            checkTimerGoal(e.getPlayer(), "BLOCK", blockKey);
        }
    }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (e.getPlayer().getWorld().getName().equals(limboWorldName) && !e.getPlayer().isOp()) e.setCancelled(true); }

    // --- COMMANDS ---
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length > 0) {
            String arg = args[0].toLowerCase();

            // /wr <command> help — show help for that command
            if (args.length >= 2 && (args[1].equalsIgnoreCase("help") || args[1].equals("?"))) {
                String helpLine = getHelpForCommand(arg);
                if (helpLine != null) {
                    sender.sendMessage("§8§m------§8[ §b§lWorldReset §8]§m------");
                    sender.sendMessage(helpLine);
                    sender.sendMessage("§8§m----------------------------");
                    return true;
                }
            }

            switch (arg) {
                case "help", "?" -> {
                    if (args.length >= 2) {
                        String topic = args[1].toLowerCase();
                        String helpLine = getHelpForCommand(topic);
                        if (helpLine != null) {
                            sender.sendMessage("§8§m------§8[ §b§lWorldReset §8]§m------");
                            sender.sendMessage(helpLine);
                            sender.sendMessage("§8§m----------------------------");
                        } else {
                            sender.sendMessage("§cUnknown command: §e" + topic + "§c. Use §e/wr help §cfor full list.");
                        }
                    } else {
                        sendFullHelp(sender);
                    }
                    return true;
                }
                case "reload" -> {
                    if (hasPerm(sender, "worldreset.admin")) return noPerm(sender, "worldreset.admin");
                    loadConfigValues();
                    loadLanguage();
                    sender.sendMessage("§aConfiguration and languages reloaded!");
                    return true;
                }
                case "reset" -> {
                    if (hasPerm(sender, "worldreset.reset")) return noPerm(sender, "worldreset.reset");
                    if (isResetting) {
                        sender.sendMessage(getMsg("already-resetting"));
                        return true;
                    }

                    if (args.length >= 2) {
                        try {
                            int delayIn = Integer.parseInt(args[1]);
                            int delayOut = args.length >= 3 ? Integer.parseInt(args[2]) : 0;

                            if (delayIn <= 0 && delayOut <= 0) {
                                startReset();
                            } else if (delayIn <= 0) {
                                startResetWithDelayOut(delayOut);
                            } else {
                                List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
                                String subtitle = getSubtitle("reset-countdown", "World Reset...");
                                final int finalDelayOut = delayOut;
                                startCountdown(allPlayers, delayIn, subtitle, () -> {
                                    if (finalDelayOut > 0) {
                                        startResetWithDelayOut(finalDelayOut);
                                    } else {
                                        startReset();
                                    }
                                });
                                String msg = "§eReset scheduled in §c" + delayIn + "s§e...";
                                if (delayOut > 0) msg += " §7(delay-out: " + delayOut + "s)";
                                sender.sendMessage(msg);
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§cUsage: /wr reset [delay-in] [delay-out]");
                        }
                    } else {
                        startReset();
                    }
                    return true;
                }
                case "limbo" -> {
                    if (hasPerm(sender, "worldreset.limbo")) return noPerm(sender, "worldreset.limbo");
                    if (isResetting) {
                        sender.sendMessage(getMsg("already-resetting"));
                        return true;
                    }

                    // /wr limbo delay <in> <out>
                    if (args.length >= 2 && args[1].equalsIgnoreCase("delay")) {
                        if (args.length < 4) {
                            sender.sendMessage("§eCurrent delays: §fIn=" + limboDelayIn + "s §7| §fOut=" + limboDelayOut + "s");
                            sender.sendMessage("§cUsage: /wr limbo delay <in_seconds> <out_seconds>");
                            return true;
                        }
                        try {
                            int delayIn = Integer.parseInt(args[2]);
                            int delayOut = Integer.parseInt(args[3]);
                            if (delayIn < 0 || delayOut < 0) { sender.sendMessage("§cDelay values must be >= 0!"); return true; }
                            limboDelayIn = delayIn; limboDelayOut = delayOut;
                            getConfig().set("limbo.delay-in", delayIn); getConfig().set("limbo.delay-out", delayOut); saveConfig();
                            sender.sendMessage("§aLimbo delays set: §eIn=" + delayIn + "s §7| §eOut=" + delayOut + "s");
                        } catch (NumberFormatException e) { sender.sendMessage("§cInvalid number!"); }
                        return true;
                    }

                    // Determine target players and delay
                    List<Player> targets = new ArrayList<>();
                    int manualDelay = 0;

                    if (args.length == 1) {
                        // /wr limbo — all players
                        targets.addAll(Bukkit.getOnlinePlayers());
                    } else if (args.length >= 2) {
                        String arg1 = args[1];
                        // Try as number (delay)
                        try {
                            manualDelay = Integer.parseInt(arg1);
                            if (manualDelay < 0) manualDelay = 0;

                            // Check if args[2] specifies a target
                            if (args.length >= 3) {
                                String arg2 = args[2];
                                if (arg2.equalsIgnoreCase("me") || arg2.equalsIgnoreCase("m") || arg2.equalsIgnoreCase("ja") || arg2.equalsIgnoreCase("j")) {
                                    if (sender instanceof Player p) targets.add(p);
                                } else if (arg2.equalsIgnoreCase("all")) {
                                    targets.addAll(Bukkit.getOnlinePlayers());
                                } else {
                                    Player target = Bukkit.getPlayerExact(arg2);
                                    if (target != null) {
                                        targets.add(target);
                                    } else {
                                        sender.sendMessage("§cPlayer not found: §e" + arg2);
                                        return true;
                                    }
                                }
                            } else {
                                // No target specified with delay — all players
                                targets.addAll(Bukkit.getOnlinePlayers());
                            }
                        } catch (NumberFormatException e) {
                            // It's a player name or "me"
                            if (arg1.equalsIgnoreCase("me") || arg1.equalsIgnoreCase("m") || arg1.equalsIgnoreCase("ja") || arg1.equalsIgnoreCase("j")) {
                                if (sender instanceof Player p) targets.add(p);
                            } else if (arg1.equalsIgnoreCase("all")) {
                                targets.addAll(Bukkit.getOnlinePlayers());
                            } else {
                                Player target = Bukkit.getPlayerExact(arg1);
                                if (target != null) {
                                    targets.add(target);
                                } else {
                                    sender.sendMessage("§cPlayer not found: §e" + arg1);
                                    return true;
                                }
                            }
                        }
                    }

                    if (targets.isEmpty()) return true;

                    // Execute toggle for each target
                    for (Player target : targets) {
                        toggleLimboForPlayer(target, manualDelay);
                    }
                    return true;
                }
                case "silent" -> {
                    if (hasPerm(sender, "worldreset.silent")) return noPerm(sender, "worldreset.silent");
                    boolean n = !getConfig().getBoolean("broadcast-messages");
                    getConfig().set("broadcast-messages", n);
                    saveConfig();
                    sender.sendMessage(getMsg(n ? "silent-off" : "silent-on"));
                    return true;
                }
                case "death" -> {
                    if (hasPerm(sender, "worldreset.death")) return noPerm(sender, "worldreset.death");
                    boolean n = !getConfig().getBoolean("reset-on-death");
                    getConfig().set("reset-on-death", n);
                    saveConfig();
                    sender.sendMessage(getMsg(n ? "death-reset-on" : "death-reset-off"));
                    return true;
                }
                case "seed" -> {
                    if (hasPerm(sender, "worldreset.seed")) return noPerm(sender, "worldreset.seed");

                    if (args.length == 1) {
                        // Toggle
                        boolean current = getConfig().getBoolean("seed.use-fixed", false);
                        boolean newVal = !current;
                        getConfig().set("seed.use-fixed", newVal);
                        saveConfig();
                        if (newVal) {
                            String val = getConfig().getString("seed.value", "");
                            sender.sendMessage(getMsg("seed-set").replace("{seed}", val.isEmpty() ? "(empty)" : val));
                        } else {
                            sender.sendMessage(getMsg("seed-random"));
                        }
                        return true;
                    }

                    String sub = args[1].toLowerCase();

                    if (isEnableAlias(sub)) {
                        getConfig().set("seed.use-fixed", true);
                        saveConfig();
                        String val = getConfig().getString("seed.value", "");
                        sender.sendMessage(getMsg("seed-set").replace("{seed}", val.isEmpty() ? "(empty)" : val));
                    } else if (isDisableAlias(sub)) {
                        getConfig().set("seed.use-fixed", false);
                        saveConfig();
                        sender.sendMessage(getMsg("seed-random"));
                    } else if (sub.equals("clear")) {
                        getConfig().set("seed.use-fixed", false);
                        getConfig().set("seed.value", "");
                        saveConfig();
                        sender.sendMessage("§aSeed cleared and set to random.");
                    } else if (sub.equals("status")) {
                        boolean fixed = getConfig().getBoolean("seed.use-fixed", false);
                        String val = getConfig().getString("seed.value", "");
                        sender.sendMessage("§e--- Seed Status ---");
                        sender.sendMessage("§7Mode: " + (fixed ? "§eFixed" : "§aRandom"));
                        if (!val.isEmpty()) sender.sendMessage("§7Value: §f" + val);
                        World game = Bukkit.getWorld(gameWorldName);
                        if (game != null) sender.sendMessage("§7Active world seed: §f" + game.getSeed());
                    } else {
                        // Treat as seed value
                        getConfig().set("seed.use-fixed", true);
                        getConfig().set("seed.value", args[1]);
                        saveConfig();
                        sender.sendMessage(getMsg("seed-set").replace("{seed}", args[1]));
                    }
                    return true;
                }
                case "language" -> {
                    if (hasPerm(sender, "worldreset.language")) return noPerm(sender, "worldreset.language");
                    if (args.length < 2) {
                        String current = getConfig().getString("language", "en");
                        String target = current.equals("en") ? "pl" : "en";
                        getConfig().set("language", target);
                        saveConfig();
                        loadLanguage();
                        sender.sendMessage(getMsg("language-changed").replace("{lang}", target));
                        return true;
                    }
                    String l = args[1].toLowerCase();
                    if (l.equals("en") || l.equals("pl")) {
                        getConfig().set("language", l);
                        saveConfig();
                        loadLanguage();
                        sender.sendMessage(getMsg("language-changed").replace("{lang}", l));
                    } else sender.sendMessage(getMsg("language-invalid"));
                    return true;
                }
                case "filter" -> {
                    if (hasPerm(sender, "worldreset.filter")) return noPerm(sender, "worldreset.filter");

                    if (args.length == 1) {
                        // Toggle filter enabled state
                        boolean current = getConfig().getBoolean("filter.enabled", true);
                        boolean newVal = !current;
                        getConfig().set("filter.enabled", newVal);
                        saveConfig();
                        sender.sendMessage(newVal ? "§aFilters enabled." : "§cFilters disabled (values preserved).");
                        return true;
                    }

                    String filterSub = args[1].toLowerCase();

                    if (filterSub.equals("status")) {
                        boolean filterEnabled = getConfig().getBoolean("filter.enabled", true);
                        String filterStruct = getConfig().getString("filter.structure", "");
                        String filterBiome = getConfig().getString("filter.biome", "");
                        boolean fixedSeed = getConfig().getBoolean("seed.use-fixed", false);
                        String seedVal = getConfig().getString("seed.value", "");

                        sender.sendMessage("§e--- Filter Status ---");
                        sender.sendMessage("§7Enabled: " + (filterEnabled ? "§aYes" : "§cNo"));
                        sender.sendMessage("§7Structure: " + (filterStruct.isEmpty() ? "§8None" : "§a" + filterStruct));
                        sender.sendMessage("§7Biome: " + (filterBiome.isEmpty() ? "§8None" : "§a" + filterBiome));
                        sender.sendMessage("§7Seed: " + (fixedSeed ? "§e" + seedVal + " §7(fixed)" : "§aRandom"));
                        World game = Bukkit.getWorld(gameWorldName);
                        if (game != null) {
                            sender.sendMessage("§7Active world seed: §f" + game.getSeed());
                        }
                        return true;
                    }

                    if (isEnableAlias(filterSub)) {
                        getConfig().set("filter.enabled", true);
                        saveConfig();
                        sender.sendMessage("§aFilters enabled.");
                        return true;
                    }
                    if (isDisableAlias(filterSub)) {
                        getConfig().set("filter.enabled", false);
                        saveConfig();
                        sender.sendMessage("§cFilters disabled (values preserved).");
                        return true;
                    }

                    // /wr filter clear — clear filters AND seed
                    if (filterSub.equals("clear")) {
                        getConfig().set("filter.structure", "");
                        getConfig().set("filter.biome", "");
                        getConfig().set("seed.use-fixed", false);
                        saveConfig();
                        sender.sendMessage(getMsg("filter-disabled"));
                        sender.sendMessage("§7Fixed seed disabled. Next reset will use a random seed.");
                        return true;
                    }

                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /wr filter <structure|biome> <name|clear> or /wr filter clear");
                        return true;
                    }

                    String type = args[1].toLowerCase();
                    String value = args[2].toUpperCase();

                    if (type.equals("structure")) {
                        if (value.equals("CLEAR")) {
                            getConfig().set("filter.structure", "");
                            saveConfig();
                            sender.sendMessage("§aStructure filter cleared.");
                        } else {
                            if (getStructuresFromName(value).isEmpty()) {
                                sender.sendMessage("§cInvalid structure name. Try: VILLAGE, SHIPWRECK, etc.");
                                return true;
                            }
                            getConfig().set("filter.structure", value);
                            getConfig().set("filter.biome", ""); // AUTO CLEAR BIOME
                            saveConfig();
                            sender.sendMessage(getMsg("filter-struct-set").replace("{struct}", value));
                        }
                    } else if (type.equals("biome")) {
                        if (value.equals("CLEAR")) {
                            getConfig().set("filter.biome", "");
                            saveConfig();
                            sender.sendMessage("§aBiome filter cleared.");
                        } else {
                            if (!BIOME_NAMES.contains(value)) {
                                sender.sendMessage("§cInvalid biome name (or rare). Try: PLAINS, DESERT, etc.");
                            }
                            getConfig().set("filter.biome", value);
                            getConfig().set("filter.structure", ""); // AUTO CLEAR STRUCTURE
                            saveConfig();
                            sender.sendMessage(getMsg("filter-biome-set").replace("{biome}", value));
                        }
                    } else {
                        sender.sendMessage("§cUsage: /wr filter <structure|biome> <name|clear> or /wr filter clear");
                    }

                    if (getConfig().getString("filter.structure", "").isEmpty() && getConfig().getString("filter.biome", "").isEmpty()) {
                        sender.sendMessage(getMsg("filter-disabled"));
                    }

                    return true;
                }
                case "timer" -> {
                    if (hasPerm(sender, "worldreset.timer")) return noPerm(sender, "worldreset.timer");
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /wr timer <start|pause|reset|enable|disable|mode|scope|goal>");
                        return true;
                    }

                    String sub = args[1].toLowerCase();

                    if (sub.equals("start")) {
                        if (!timerEnabled) {
                            getConfig().set("timer.enabled", true);
                            saveConfig();
                            timerEnabled = true;
                            sender.sendMessage(getMsg("timer-auto-enabled"));
                        }
                        startTimer();
                        return true;
                    } else if (sub.equals("pause")) {
                        stopTimer();
                        return true;
                    } else if (sub.equals("reset")) {
                        if (!timerEnabled) {
                            getConfig().set("timer.enabled", true);
                            saveConfig();
                            timerEnabled = true;
                            sender.sendMessage(getMsg("timer-auto-enabled"));
                        }
                        resetAndStartTimer();
                        broadcastInfo("§eTimer manually reset to 0.");
                        return true;
                    } else if (isEnableAlias(sub)) {
                        getConfig().set("timer.enabled", true);
                        saveConfig();
                        timerEnabled = true;
                        sender.sendMessage(getMsg("timer-enabled"));
                        return true;
                    } else if (isDisableAlias(sub)) {
                        stopTimer(true); // Najpierw pauzujemy, żeby zabić ewentualne taski
                        getConfig().set("timer.enabled", false);
                        saveConfig();
                        timerEnabled = false;
                        // Czyszczenie Action Bara dla wszystkich po wyłączeniu
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendActionBar(Component.empty());
                        }
                        sender.sendMessage(getMsg("timer-disabled"));
                        return true;
                    } else if (sub.equals("mode")) {
                        if (args.length < 3) return true;
                        String mode = args[2].toUpperCase();
                        if (mode.equals("RTA") || mode.equals("IGT")) {
                            getConfig().set("timer.mode", mode);
                            saveConfig();
                            timerMode = mode;
                            sender.sendMessage("§aTimer mode set to: §e" + mode);
                        }
                        return true;
                    } else if (sub.equals("scope")) {
                        if (args.length < 3) return true;
                        String scope = args[2].toUpperCase();
                        if (scope.equals("GLOBAL") || scope.equals("INDIVIDUAL")) {
                            getConfig().set("timer.scope", scope);
                            saveConfig();
                            timerScope = scope;
                            sender.sendMessage("§aTimer scope set to: §e" + scope);
                        }
                        return true;
                    } else if (sub.equals("goal")) {
                        if (args.length < 3) return true;
                        String goalType = args[2].toUpperCase();

                        if (goalType.equals("NONE")) {
                            getConfig().set("timer.goal.type", "NONE");
                            getConfig().set("timer.goal.value", "");
                            saveConfig();
                            timerGoalType = "NONE";
                            timerGoalValue = "";
                            sender.sendMessage("§aTimer goal removed. It will run indefinitely.");
                            return true;
                        }

                        if (args.length < 4) {
                            sender.sendMessage("§cProvide a value for the goal.");
                            return true;
                        }

                        String goalValue = args[3];

                        if (goalType.equals("ENTITY")) {
                            goalValue = goalValue.toLowerCase();
                            if (!goalValue.contains(":")) goalValue = "minecraft:" + goalValue;

                            try {
                                NamespacedKey key = NamespacedKey.fromString(goalValue);
                                EntityType t = Registry.ENTITY_TYPE.get(key);
                                if (t == null || !t.isAlive()) {
                                    sender.sendMessage("§cInvalid entity type: " + args[3]);
                                    return true;
                                }
                            } catch (Exception e) {
                                sender.sendMessage("§cInvalid entity format.");
                                return true;
                            }
                        } else if (goalType.equals("ADVANCEMENT")) {
                            goalValue = goalValue.toLowerCase();
                            if (!goalValue.contains(":")) goalValue = "minecraft:" + goalValue;

                            try {
                                NamespacedKey key = NamespacedKey.fromString(goalValue);
                                if (Bukkit.getAdvancement(key) == null) {
                                    sender.sendMessage("§cInvalid advancement: " + args[3]);
                                    return true;
                                }
                            } catch (Exception e) {
                                sender.sendMessage("§cInvalid advancement format.");
                                return true;
                            }
                        } else if (goalType.equals("PORTAL")) {
                            goalValue = goalValue.toUpperCase();
                            if (!Arrays.asList("NETHER", "END", "OVERWORLD", "ANY").contains(goalValue)) {
                                sender.sendMessage("§cInvalid portal! Use: NETHER, END, OVERWORLD, ANY");
                                return true;
                            }
                        } else if (goalType.equals("BLOCK")) {
                            goalValue = goalValue.toLowerCase();
                            if (!goalValue.contains(":")) goalValue = "minecraft:" + goalValue;
                            try {
                                Material mat = Material.matchMaterial(goalValue);
                                if (mat == null || !mat.isBlock()) {
                                    sender.sendMessage("§cInvalid block material: " + args[3]);
                                    return true;
                                }
                            } catch (Exception e) {
                                sender.sendMessage("§cInvalid block format.");
                                return true;
                            }
                        } else if (goalType.equals("ITEM")) {
                            goalValue = goalValue.toLowerCase();
                            if (!goalValue.contains(":")) goalValue = "minecraft:" + goalValue;
                            try {
                                Material mat = Material.matchMaterial(goalValue);
                                if (mat == null || !mat.isItem()) {
                                    sender.sendMessage("§cInvalid item material: " + args[3]);
                                    return true;
                                }
                            } catch (Exception e) {
                                sender.sendMessage("§cInvalid item format.");
                                return true;
                            }
                        }

                        getConfig().set("timer.goal.type", goalType);
                        getConfig().set("timer.goal.value", goalValue);
                        saveConfig();
                        timerGoalType = goalType;
                        timerGoalValue = goalValue;
                        sender.sendMessage("§aTimer goal set: §e" + goalType + " -> " + goalValue);
                        return true;
                    }
                }
                case "compass" -> {
                    if (hasPerm(sender, "worldreset.compass")) return noPerm(sender, "worldreset.compass");

                    boolean newState;
                    if (args.length < 2) {
                        // Toggle
                        newState = !compassEnabled;
                    } else {
                        String sub = args[1].toLowerCase();
                        if (isEnableAlias(sub)) {
                            newState = true;
                        } else if (isDisableAlias(sub)) {
                            newState = false;
                        } else {
                            sender.sendMessage("§cUsage: /wr compass <enable|disable>");
                            return true;
                        }
                    }

                    compassEnabled = newState;
                    getConfig().set("compass.enabled", newState);
                    saveConfig();
                    applyLocatorBarGamerule();
                    sender.sendMessage(newState ? "§aLocator bar enabled!" : "§cLocator bar disabled.");
                    return true;
                }
                case "templates" -> {
                    if (hasPerm(sender, "worldreset.templates")) return noPerm(sender, "worldreset.templates");

                    if (args.length < 2) {
                        // Toggle
                        boolean current = getConfig().getBoolean("template.enabled", false);
                        boolean newVal = !current;
                        getConfig().set("template.enabled", newVal);
                        saveConfig();
                        sender.sendMessage(newVal ? "§aTemplates enabled." : "§cTemplates disabled.");
                        return true;
                    }

                    String sub = args[1].toLowerCase();
                    if (isEnableAlias(sub)) {
                        getConfig().set("template.enabled", true);
                        saveConfig();
                        sender.sendMessage("§aTemplates enabled.");
                    } else if (isDisableAlias(sub)) {
                        getConfig().set("template.enabled", false);
                        saveConfig();
                        sender.sendMessage("§cTemplates disabled.");
                    } else if (sub.equals("folder")) {
                        if (args.length < 3) {
                            String currentFolder = getConfig().getString("template.folder", "WorldReset_Templates");
                            sender.sendMessage("§eCurrent templates folder: §f" + currentFolder);
                        } else {
                            String newFolder = args[2];
                            getConfig().set("template.folder", newFolder);
                            saveConfig();
                            sender.sendMessage("§aTemplates folder set to: §f" + newFolder);
                        }
                    } else if (sub.equals("status")) {
                        boolean enabled = getConfig().getBoolean("template.enabled", false);
                        String folder = getConfig().getString("template.folder", "WorldReset_Templates");
                        File templatesDir = getTemplateFolder();
                        int worldCount = 0;
                        if (templatesDir.exists()) {
                            File[] dirs = templatesDir.listFiles(File::isDirectory);
                            if (dirs != null) {
                                for (File d : dirs) {
                                    if (new File(d, "level.dat").exists()) worldCount++;
                                }
                            }
                        }
                        sender.sendMessage("§e--- Templates Status ---");
                        sender.sendMessage("§7Enabled: " + (enabled ? "§aYes" : "§cNo"));
                        sender.sendMessage("§7Folder: §f" + folder);
                        sender.sendMessage("§7Detected worlds: §f" + worldCount);
                    } else {
                        sender.sendMessage("§cUsage: /wr templates <enable|disable|folder|status>");
                    }
                    return true;
                }
                case "autoreset" -> {
                    if (hasPerm(sender, "worldreset.autoreset")) return noPerm(sender, "worldreset.autoreset");

                    if (args.length < 2) {
                        // Toggle autoreset
                        if (autoResetEnabled && !autoResetPaused) {
                            // Running → pause
                            autoResetPaused = true;
                            getConfig().set("autoreset.paused", true);
                            saveConfig();
                            sender.sendMessage("§6AutoReset paused.");
                        } else if (autoResetEnabled && autoResetPaused) {
                            // Paused → resume
                            autoResetPaused = false;
                            getConfig().set("autoreset.paused", false);
                            saveConfig();
                            if (autoResetRemainingSeconds <= 0) autoResetRemainingSeconds = autoResetTotalSeconds;
                            startAutoResetTimer();
                            sender.sendMessage("§aAutoReset resumed! Time: §e" + formatAutoResetTime(autoResetRemainingSeconds));
                        } else {
                            // Disabled → enable and start
                            autoResetEnabled = true;
                            autoResetPaused = false;
                            getConfig().set("autoreset.enabled", true);
                            getConfig().set("autoreset.paused", false);
                            saveConfig();
                            autoResetRemainingSeconds = autoResetTotalSeconds;
                            startAutoResetTimer();
                            sender.sendMessage("§aAutoReset started! Time: §e" + formatAutoResetTime(autoResetRemainingSeconds));
                        }
                        syncAutoResetScoreboard();
                        return true;
                    }

                    String sub = args[1].toLowerCase();

                    switch (sub) {
                        case "status" -> {
                            String statusStr = !autoResetEnabled ? "§cDisabled" : (autoResetPaused ? "§6Paused" : "§aRunning");
                            sender.sendMessage("§e--- AutoReset Status ---");
                            sender.sendMessage("§7Status: " + statusStr);
                            sender.sendMessage("§7Time: §f" + formatAutoResetTime(autoResetRemainingSeconds) + " §7/ §f" + formatAutoResetTime(autoResetTotalSeconds));
                            sender.sendMessage("§7Loop: " + (autoResetLoop ? "§aYes" : "§cNo"));
                            sender.sendMessage("§7Visible: " + (autoResetVisible ? "§aYes" : "§cNo"));
                        }
                        case "start" -> {
                            autoResetEnabled = true;
                            autoResetPaused = false;
                            getConfig().set("autoreset.enabled", true);
                            getConfig().set("autoreset.paused", false);
                            saveConfig();
                            if (autoResetRemainingSeconds <= 0) {
                                autoResetRemainingSeconds = autoResetTotalSeconds;
                            }
                            startAutoResetTimer();
                            sender.sendMessage("§aAutoReset started! Time remaining: §e" + formatAutoResetTime(autoResetRemainingSeconds));
                            syncAutoResetScoreboard();
                        }
                        case "stop", "pause" -> {
                            autoResetPaused = true;
                            getConfig().set("autoreset.paused", true);
                            saveConfig();
                            sender.sendMessage("§6AutoReset paused. Time remaining: §e" + formatAutoResetTime(autoResetRemainingSeconds));
                            syncAutoResetScoreboard();
                        }
                        case "disable" -> {
                            stopAutoResetTimer();
                            autoResetEnabled = false;
                            autoResetPaused = true;
                            autoResetRemainingSeconds = autoResetTotalSeconds;
                            getConfig().set("autoreset.enabled", false);
                            getConfig().set("autoreset.paused", true);
                            saveConfig();
                            sender.sendMessage("§cAutoReset disabled and timer reset.");
                            syncAutoResetScoreboard();
                        }
                        case "loop" -> {
                            if (args.length < 3) {
                                autoResetLoop = !autoResetLoop;
                            } else {
                                autoResetLoop = isEnableAlias(args[2].toLowerCase());
                            }
                            getConfig().set("autoreset.loop", autoResetLoop);
                            saveConfig();
                            sender.sendMessage("§aAutoReset loop: " + (autoResetLoop ? "§aEnabled" : "§cDisabled"));
                        }
                        case "visible" -> {
                            if (args.length < 3) {
                                autoResetVisible = !autoResetVisible;
                            } else {
                                autoResetVisible = isEnableAlias(args[2].toLowerCase());
                            }
                            getConfig().set("autoreset.visible", autoResetVisible);
                            saveConfig();
                            sender.sendMessage("§aAutoReset visibility: " + (autoResetVisible ? "§aVisible" : "§cHidden"));
                        }
                        case "time" -> {
                            if (args.length < 3) {
                                sender.sendMessage("§cUsage: /wr autoreset time <value> (e.g. 60s, 5m, 1h)");
                                return true;
                            }
                            long newTime = parseTimeToSeconds(args[2]);
                            autoResetTotalSeconds = newTime;
                            autoResetRemainingSeconds = newTime;
                            getConfig().set("autoreset.time", args[2]);
                            saveConfig();
                            sender.sendMessage("§aAutoReset time set to: §e" + formatAutoResetTime(newTime));

                            // Restart the timer if it's running
                            if (autoResetEnabled && !autoResetPaused) {
                                startAutoResetTimer();
                            }
                            syncAutoResetScoreboard();
                        }
                        default -> {
                            sender.sendMessage("§cUsage: /wr autoreset <start|stop|disable|loop|visible|time>");
                        }
                    }
                    return true;
                }
                case "backup" -> {
                    if (hasPerm(sender, "worldreset.admin")) return noPerm(sender, "worldreset.admin");

                    // /wr backup — toggle
                    if (args.length == 1) {
                        boolean current = getConfig().getBoolean("backup.enabled", true);
                        boolean newVal = !current;
                        getConfig().set("backup.enabled", newVal);
                        saveConfig();
                        sender.sendMessage(newVal ? "§aBackups enabled." : "§cBackups disabled.");
                        return true;
                    }

                    String sub = args[1].toLowerCase();

                    switch (sub) {
                        case "enable", "on", "true" -> {
                            getConfig().set("backup.enabled", true);
                            saveConfig();
                            sender.sendMessage("§aBackups enabled.");
                        }
                        case "disable", "off", "false" -> {
                            getConfig().set("backup.enabled", false);
                            saveConfig();
                            sender.sendMessage("§cBackups disabled.");
                        }
                        case "status" -> {
                            boolean enabled = getConfig().getBoolean("backup.enabled", true);
                            String limitStr = getConfig().getString("backup.limit", "all");
                            String folder = getConfig().getString("backup.folder", "WorldReset_BackUps");
                            File backupsDir = new File(getDataFolder().getParentFile().getParentFile(), folder);
                            int backupCount = 0;
                            long totalSize = 0;
                            if (backupsDir.exists()) {
                                File[] dirs = backupsDir.listFiles(File::isDirectory);
                                if (dirs != null) {
                                    backupCount = dirs.length;
                                    for (File dir : dirs) totalSize += getDirSize(dir);
                                }
                            }
                            sender.sendMessage("§e--- Backup Status ---");
                            sender.sendMessage("§7Enabled: " + (enabled ? "§aYes" : "§cNo"));
                            sender.sendMessage("§7Limit: §f" + limitStr);
                            sender.sendMessage("§7Existing backups: §f" + backupCount);
                            sender.sendMessage("§7Total size: §f" + formatFileSize(totalSize));
                            sender.sendMessage("§7Folder: §f" + folder);
                        }
                        case "list" -> {
                            String folder = getConfig().getString("backup.folder", "WorldReset_BackUps");
                            File backupsDir = new File(getDataFolder().getParentFile().getParentFile(), folder);
                            if (!backupsDir.exists() || backupsDir.listFiles(File::isDirectory) == null) {
                                sender.sendMessage("§7No backups found.");
                                return true;
                            }
                            File[] dirs = backupsDir.listFiles(File::isDirectory);
                            if (dirs == null || dirs.length == 0) {
                                sender.sendMessage("§7No backups found.");
                                return true;
                            }
                            Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());

                            int perPage = 10;
                            int totalPages = (int) Math.ceil(dirs.length / (double) perPage);
                            int page = 1;
                            if (args.length >= 3) {
                                try { page = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                            }
                            page = Math.max(1, Math.min(page, totalPages));
                            int start = (page - 1) * perPage;
                            int end = Math.min(start + perPage, dirs.length);

                            sender.sendMessage("§e--- Backups (" + dirs.length + ") §7— Page " + page + "/" + totalPages + " §e---");
                            for (int i = start; i < end; i++) {
                                long size = getDirSize(dirs[i]);
                                sender.sendMessage("§7 " + (i + 1) + ". §f" + dirs[i].getName() + " §8(§7" + formatFileSize(size) + "§8)");
                            }
                            if (page < totalPages) {
                                sender.sendMessage("§7Use §e/wr backup list " + (page + 1) + " §7for next page.");
                            }
                        }
                        case "load" -> {
                            if (args.length < 3) {
                                sender.sendMessage("§cUsage: /wr backup load <number>");
                                return true;
                            }
                            String folder = getConfig().getString("backup.folder", "WorldReset_BackUps");
                            File backupsDir = new File(getDataFolder().getParentFile().getParentFile(), folder);
                            File[] dirs = backupsDir.exists() ? backupsDir.listFiles(File::isDirectory) : null;
                            if (dirs == null || dirs.length == 0) {
                                sender.sendMessage("§cNo backups available.");
                                return true;
                            }
                            Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());
                            try {
                                int index = Integer.parseInt(args[2]) - 1;
                                if (index < 0 || index >= dirs.length) {
                                    sender.sendMessage("§cInvalid number! Use 1-" + dirs.length);
                                    return true;
                                }
                                File selectedBackup = dirs[index];
                                sender.sendMessage("§eLoading backup: §f" + selectedBackup.getName() + "§e...");

                                isResetting = true;
                                isGameReady = false;
                                sendAllToLimboForReset();

                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        unloadGameWorlds();
                                        // Extra delay for Windows file lock release
                                        new BukkitRunnable() {
                                            @Override
                                            public void run() {
                                                deleteWorldFolder(gameWorldName);
                                                deleteWorldFolder(gameWorldName + "_nether");
                                                deleteWorldFolder(gameWorldName + "_the_end");

                                                // Full copy of backup (including playerdata, entities, etc.)
                                                File backupOverworld = new File(selectedBackup, gameWorldName);
                                                File backupNether = new File(selectedBackup, gameWorldName + "_nether");
                                                File backupEnd = new File(selectedBackup, gameWorldName + "_the_end");

                                                getLogger().info("Backup load - copying from: " + selectedBackup.getAbsolutePath());
                                                getLogger().info("  Overworld exists: " + backupOverworld.exists());
                                                getLogger().info("  level.dat exists: " + new File(backupOverworld, "level.dat").exists());

                                                try {
                                                    if (backupOverworld.exists()) {
                                                        copyDirectory(backupOverworld.toPath(), new File(Bukkit.getWorldContainer(), gameWorldName).toPath());
                                                        // Remove uid.dat to prevent UUID conflicts
                                                        File uid = new File(Bukkit.getWorldContainer(), gameWorldName + "/uid.dat");
                                                        if (uid.exists()) uid.delete();
                                                    }
                                                    if (backupNether.exists()) {
                                                        copyDirectory(backupNether.toPath(), new File(Bukkit.getWorldContainer(), gameWorldName + "_nether").toPath());
                                                        File uid = new File(Bukkit.getWorldContainer(), gameWorldName + "_nether/uid.dat");
                                                        if (uid.exists()) uid.delete();
                                                    }
                                                    if (backupEnd.exists()) {
                                                        copyDirectory(backupEnd.toPath(), new File(Bukkit.getWorldContainer(), gameWorldName + "_the_end").toPath());
                                                        File uid = new File(Bukkit.getWorldContainer(), gameWorldName + "_the_end/uid.dat");
                                                        if (uid.exists()) uid.delete();
                                                    }
                                                } catch (IOException e) {
                                                    getLogger().severe("Failed to load backup: " + e.getMessage());
                                                }

                                                loadGameWorlds();
                                                applyLocatorBarGamerule();
                                                isGameReady = true;

                                                // Restore player states from backup
                                                World game = Bukkit.getWorld(gameWorldName);
                                                if (game != null) {
                                                    broadcastInfo("§aBackup loaded successfully!");
                                                    restorePlayerStates(selectedBackup);

                                                    // If no players.yml existed (old backup), just teleport to spawn
                                                    File playersFile = new File(selectedBackup, "players.yml");
                                                    if (!playersFile.exists()) {
                                                        for (Player p : Bukkit.getOnlinePlayers()) {
                                                            if (p.isDead()) p.spigot().respawn();
                                                            p.teleport(game.getSpawnLocation());
                                                            p.setGameMode(GameMode.SURVIVAL);
                                                        }
                                                    }
                                                }
                                                isResetting = false;
                                            }
                                        }.runTaskLater(Main.this, 40L);
                                    }
                                }.runTaskLater(Main.this, 20L);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cUsage: /wr backup load <number>");
                            }
                        }
                        case "clear" -> {
                            String folder = getConfig().getString("backup.folder", "WorldReset_BackUps");
                            File backupsDir = new File(getDataFolder().getParentFile().getParentFile(), folder);
                            File[] dirs = backupsDir.exists() ? backupsDir.listFiles(File::isDirectory) : null;
                            if (dirs == null || dirs.length == 0) {
                                sender.sendMessage("§7No backups to clear.");
                                return true;
                            }

                            if (args.length >= 3) {
                                // /wr backup clear <number> — delete N oldest
                                try {
                                    int count = Integer.parseInt(args[2]);
                                    if (count <= 0) {
                                        sender.sendMessage("§cNumber must be at least 1!");
                                        return true;
                                    }
                                    Arrays.sort(dirs, Comparator.comparingLong(File::lastModified));
                                    int toDelete = Math.min(count, dirs.length);
                                    for (int i = 0; i < toDelete; i++) {
                                        deleteDirectoryRecursive(dirs[i]);
                                    }
                                    sender.sendMessage("§aDeleted §e" + toDelete + " §aoldest backup(s).");
                                } catch (NumberFormatException e) {
                                    sender.sendMessage("§cUsage: /wr backup clear [number]");
                                }
                            } else {
                                // /wr backup clear — delete all
                                int count = dirs.length;
                                for (File dir : dirs) {
                                    deleteDirectoryRecursive(dir);
                                }
                                sender.sendMessage("§aAll §e" + count + " §abackup(s) deleted.");
                            }
                        }
                        case "limit" -> {
                            if (args.length < 3) {
                                String limitStr = getConfig().getString("backup.limit", "all");
                                sender.sendMessage("§7Current backup limit: §f" + limitStr);
                                return true;
                            }
                            String value = args[2].toLowerCase();
                            if (value.equals("all")) {
                                getConfig().set("backup.limit", "all");
                                saveConfig();
                                sender.sendMessage("§aBackup limit set to: §eall §7(keep all backups)");
                            } else {
                                try {
                                    int limit = Integer.parseInt(value);
                                    if (limit < 1) {
                                        sender.sendMessage("§cLimit must be at least 1 or 'all'!");
                                        return true;
                                    }
                                    getConfig().set("backup.limit", String.valueOf(limit));
                                    saveConfig();
                                    sender.sendMessage("§aBackup limit set to: §e" + limit + " §7(keep last " + limit + " backups)");
                                } catch (NumberFormatException e) {
                                    sender.sendMessage("§cUsage: /wr backup limit <number|all>");
                                }
                            }
                        }
                        default -> {
                            // Try parsing as a number for quick limit set
                            try {
                                int limit = Integer.parseInt(sub);
                                if (limit < 1) {
                                    sender.sendMessage("§cLimit must be at least 1!");
                                    return true;
                                }
                                getConfig().set("backup.limit", String.valueOf(limit));
                                saveConfig();
                                sender.sendMessage("§aBackup limit set to: §e" + limit);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cUsage: /wr backup <enable|disable|status|limit> or /wr backup <number>");
                            }
                        }
                    }
                    return true;
                }
            }

        }
        sendFullHelp(sender);
        return true;
    }

    private boolean hasPerm(CommandSender s, String node) { return !s.hasPermission(node) && !s.hasPermission(node + ".*") && !s.hasPermission("worldreset.*") && !s.isOp(); }
    private boolean noPerm(CommandSender s, String node) { s.sendMessage(getMsg("no-permission").replace("{permission}", node)); return true; }
    private boolean isEnableAlias(String s) { return s.equals("enable") || s.equals("on") || s.equals("true"); }
    private boolean isDisableAlias(String s) { return s.equals("disable") || s.equals("off") || s.equals("false"); }

    private long getDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.isDirectory() ? getDirSize(f) : f.length();
            }
        }
        return size;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void sendFullHelp(CommandSender sender) {
        boolean isPl = getConfig().getString("language", "en").equalsIgnoreCase("pl");
        sender.sendMessage("§8§m------§8[ §b§lWorldReset §8]§m------");
        sender.sendMessage(isPl ? "§7§oWpisz §e/wr help §6<§ekomenda§6> §7§opo szczegóły." : "§7§oUse §e/wr help §6<§ecommand§6> §7§ofor details.");
        sender.sendMessage("");
        sender.sendMessage(isPl ? "§f§l⚙ Gra" : "§f§l⚙ Game");
        sender.sendMessage(isPl ? "  §e/wr reset §6[§esekundy§6] §8- §7Zresetuj świat" : "  §e/wr reset §6[§eseconds§6] §8- §7Reset the world");
        sender.sendMessage(isPl ? "  §e/wr limbo §6[§egracz§6|§eme§6] §8- §7Przenieś do/z Limbo" : "  §e/wr limbo §6[§eplayer§6|§eme§6] §8- §7Toggle Limbo");
        sender.sendMessage(isPl ? "  §e/wr death §8- §7Reset po śmierci" : "  §e/wr death §8- §7Toggle reset-on-death");
        sender.sendMessage("");
        sender.sendMessage(isPl ? "§f§l⏱ Timer i AutoReset" : "§f§l⏱ Timer & AutoReset");
        sender.sendMessage(isPl ? "  §e/wr timer §6<§eakcja§6> §8- §7Stoper speedrunowy" : "  §e/wr timer §6<§eaction§6> §8- §7Speedrun stopwatch");
        sender.sendMessage(isPl ? "  §e/wr autoreset §6<§eakcja§6> §8- §7Zaplanowane resety" : "  §e/wr autoreset §6<§eaction§6> §8- §7Scheduled resets");
        sender.sendMessage("");
        sender.sendMessage(isPl ? "§f§l🌍 Świat" : "§f§l🌍 World");
        sender.sendMessage(isPl ? "  §e/wr filter §6[§etyp§6] §6[§enazwa§6] §8- §7Filtry spawnu i seed" : "  §e/wr filter §6[§etype§6] §6[§ename§6] §8- §7Spawn filters & seed");
        sender.sendMessage(isPl ? "  §e/wr seed §6[§ewartość§6] §8- §7Stały/losowy seed" : "  §e/wr seed §6[§evalue§6] §8- §7Fixed/random seed");
        sender.sendMessage(isPl ? "  §e/wr templates §6<§eakcja§6> §8- §7Szablony map" : "  §e/wr templates §6<§eaction§6> §8- §7World templates");
        sender.sendMessage(isPl ? "  §e/wr compass §6[§eenable§6|§edisable§6] §8- §7Locator bar" : "  §e/wr compass §6[§eenable§6|§edisable§6] §8- §7Locator bar");
        sender.sendMessage("");
        sender.sendMessage(isPl ? "§f§l🛠 System" : "§f§l🛠 System");
        sender.sendMessage(isPl ? "  §e/wr backup §6[§eakcja§6] §8- §7Zarządzanie kopiami" : "  §e/wr backup §6[§eaction§6] §8- §7Backup management");
        sender.sendMessage(isPl ? "  §e/wr language §6<§een§6|§epl§6> §8- §7Zmień język" : "  §e/wr language §6<§een§6|§epl§6> §8- §7Change language");
        sender.sendMessage(isPl ? "  §e/wr silent §8- §7Wycisz komunikaty" : "  §e/wr silent §8- §7Toggle broadcasts");
        sender.sendMessage(isPl ? "  §e/wr reload §8- §7Przeładuj config" : "  §e/wr reload §8- §7Reload config");
        sender.sendMessage("§8§m----------------------------");
    }

    private String getHelpForCommand(String cmd) {
        boolean isPl = getConfig().getString("language", "en").equalsIgnoreCase("pl");
        return switch (cmd) {
            case "reset" -> isPl
                    ? "§e/wr reset §6[§edelay-in§6] §6[§edelay-out§6] §8- §7Zresetuj świat.\n§7  delay-in: odliczanie przed resetem. delay-out: odliczanie w limbo przed startem."
                    : "§e/wr reset §6[§edelay-in§6] §6[§edelay-out§6] §8- §7Reset the world.\n§7  delay-in: countdown before reset. delay-out: countdown in limbo before game starts.";
            case "limbo" -> isPl
                    ? "§e/wr limbo §8- §7Przenieś wszystkich do/z Limbo\n§e/wr limbo me §8- §7Przenieś tylko siebie\n§e/wr limbo §6<§egracz§6> §8- §7Przenieś wybranego gracza\n§e/wr limbo §6<§esekundy§6> §6[§egracz§6] §8- §7Z odliczaniem (domyślnie wszyscy)\n§e/wr limbo delay §6<§ein§6> §6<§eout§6> §8- §7Ustaw automatyczne opóźnienia"
                    : "§e/wr limbo §8- §7Toggle all players to/from Limbo\n§e/wr limbo me §8- §7Toggle only yourself\n§e/wr limbo §6<§eplayer§6> §8- §7Toggle specific player\n§e/wr limbo §6<§eseconds§6> §6[§eplayer§6] §8- §7With countdown (default: all)\n§e/wr limbo delay §6<§ein§6> §6<§eout§6> §8- §7Set automatic delays";
            case "death" -> isPl
                    ? "§e/wr death §8- §7Przełącz reset po śmierci.\n§7  Gdy włączony, śmierć gracza resetuje świat."
                    : "§e/wr death §8- §7Toggle Reset-on-Death mode.\n§7  When enabled, any player death resets the world.";
            case "timer" -> isPl
                    ? "§e/wr timer §6<§estart§6|§epause§6|§ereset§6> §8- §7Steruj stoperem\n§e/wr timer §6<§eenable§6|§edisable§6> §8- §7Włącz/wyłącz system\n§e/wr timer mode §6<§eRTA§6|§eIGT§6> §8- §7Tryb liczenia\n§e/wr timer scope §6<§eGLOBAL§6|§eINDIVIDUAL§6> §8- §7Zasięg\n§e/wr timer goal §6<§etyp§6> §6<§ewartość§6> §8- §7Ustaw cel"
                    : "§e/wr timer §6<§estart§6|§epause§6|§ereset§6> §8- §7Control stopwatch\n§e/wr timer §6<§eenable§6|§edisable§6> §8- §7Turn system on/off\n§e/wr timer mode §6<§eRTA§6|§eIGT§6> §8- §7Set counting mode\n§e/wr timer scope §6<§eGLOBAL§6|§eINDIVIDUAL§6> §8- §7Set scope\n§e/wr timer goal §6<§etype§6> §6<§evalue§6> §8- §7Set goal trigger";
            case "autoreset" -> isPl
                    ? "§e/wr autoreset §8- §7Pokaż status\n§e/wr autoreset §6<§estart§6|§estop§6|§edisable§6> §8- §7Steruj odliczaniem\n§e/wr autoreset time §6<§ewartość§6> §8- §7Ustaw interwał §6(§e30s§6, §e5m§6, §e1h§6)\n§e/wr autoreset loop §6[§eenable§6|§edisable§6] §8- §7Pętla\n§e/wr autoreset visible §6[§eenable§6|§edisable§6] §8- §7Widoczność HUD"
                    : "§e/wr autoreset §8- §7Show status\n§e/wr autoreset §6<§estart§6|§estop§6|§edisable§6> §8- §7Control countdown\n§e/wr autoreset time §6<§evalue§6> §8- §7Set interval §6(§e30s§6, §e5m§6, §e1h§6)\n§e/wr autoreset loop §6[§eenable§6|§edisable§6] §8- §7Toggle loop\n§e/wr autoreset visible §6[§eenable§6|§edisable§6] §8- §7Toggle HUD";
            case "filter" -> isPl
                    ? "§e/wr filter §8- §7Przełącz filtry (włącz/wyłącz)\n§e/wr filter §6<§eenable§6|§edisable§6> §8- §7Włącz/wyłącz filtry\n§e/wr filter status §8- §7Pokaż status filtrów\n§e/wr filter structure §6<§enazwa§6> §8- §7Filtr struktury\n§e/wr filter biome §6<§enazwa§6> §8- §7Filtr biomu\n§e/wr filter clear §8- §7Wyczyść filtry i seed"
                    : "§e/wr filter §8- §7Toggle filters (enable/disable)\n§e/wr filter §6<§eenable§6|§edisable§6> §8- §7Enable/disable filters\n§e/wr filter status §8- §7Show filter status\n§e/wr filter structure §6<§ename§6> §8- §7Set structure filter\n§e/wr filter biome §6<§ename§6> §8- §7Set biome filter\n§e/wr filter clear §8- §7Clear all filters & seed";
            case "seed" -> isPl
                    ? "§e/wr seed §8- §7Przełącz stały/losowy seed\n§e/wr seed §6<§eenable§6|§edisable§6> §8- §7Włącz/wyłącz stały seed\n§e/wr seed §6<§ewartość§6> §8- §7Ustaw seed\n§e/wr seed status §8- §7Pokaż status\n§e/wr seed clear §8- §7Wyczyść i ustaw losowy"
                    : "§e/wr seed §8- §7Toggle fixed/random seed\n§e/wr seed §6<§eenable§6|§edisable§6> §8- §7Enable/disable fixed seed\n§e/wr seed §6<§evalue§6> §8- §7Set seed value\n§e/wr seed status §8- §7Show status\n§e/wr seed clear §8- §7Clear and set random";
            case "templates" -> isPl
                    ? "§e/wr templates §6<§eenable§6|§edisable§6> §8- §7Przełącz szablony\n§e/wr templates folder §6[§eścieżka§6] §8- §7Podgląd/zmiana folderu\n§e/wr templates status §8- §7Info o szablonach"
                    : "§e/wr templates §6<§eenable§6|§edisable§6> §8- §7Toggle templates\n§e/wr templates folder §6[§epath§6] §8- §7View/set folder\n§e/wr templates status §8- §7Show template info";
            case "compass" -> isPl
                    ? "§e/wr compass §6<§eenable§6|§edisable§6> §8- §7Przełącz Locator Bar"
                    : "§e/wr compass §6<§eenable§6|§edisable§6> §8- §7Toggle Locator Bar";
            case "language", "lang" -> isPl
                    ? "§e/wr language §6<§een§6|§epl§6> §8- §7Zmień język pluginu"
                    : "§e/wr language §6<§een§6|§epl§6> §8- §7Switch plugin language";
            case "silent" -> isPl
                    ? "§e/wr silent §8- §7Przełącz globalne komunikaty czatu"
                    : "§e/wr silent §8- §7Toggle global broadcast messages on/off";
            case "backup" -> isPl
                    ? "§e/wr backup §6<§eenable§6|§edisable§6> §8- §7Przełącz kopie zapasowe\n§e/wr backup status §8- §7Status (limit, liczba, rozmiar)\n§e/wr backup list §8- §7Lista kopii zapasowych\n§e/wr backup load §6<§enumer§6> §8- §7Wczytaj kopię\n§e/wr backup clear §6[§eilość§6] §8- §7Usuń kopie (najstarsze)\n§e/wr backup limit §6<§eliczba§6|§eall§6> §8- §7Ustaw limit"
                    : "§e/wr backup §6<§eenable§6|§edisable§6> §8- §7Toggle backups\n§e/wr backup status §8- §7Show info (limit, count, size)\n§e/wr backup list §8- §7List all backups\n§e/wr backup load §6<§enumber§6> §8- §7Load a backup\n§e/wr backup clear §6[§ecount§6] §8- §7Delete backups (oldest first)\n§e/wr backup limit §6<§enumber§6|§eall§6> §8- §7Set backup limit";
            case "reload" -> isPl
                    ? "§e/wr reload §8- §7Przeładuj konfigurację i pliki językowe"
                    : "§e/wr reload §8- §7Reload config and language files";
            default -> null;
        };
    }

    // --- DYNAMIC TAB COMPLETION ---
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("reset", "limbo", "seed", "language", "silent", "death", "filter", "timer", "compass", "templates", "autoreset", "backup", "reload", "help"), new ArrayList<>());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("filter")) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("structure", "biome", "enable", "disable", "status", "clear"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("language")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("en", "pl"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("timer")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("start", "pause", "reset", "enable", "disable", "mode", "scope", "goal"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("compass")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("enable", "disable"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("templates")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("enable", "disable", "folder", "status"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("autoreset")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("start", "stop", "disable", "status", "loop", "visible", "time"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("backup")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("enable", "disable", "status", "list", "load", "clear", "limit"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("limbo")) {
                List<String> suggestions = new ArrayList<>(Arrays.asList("me", "all", "delay", "3", "5", "10"));
                for (Player p : Bukkit.getOnlinePlayers()) suggestions.add(p.getName());
                return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("reset")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("3", "5", "10", "15", "30"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("seed")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("enable", "disable", "clear", "status"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("help") || args[0].equals("?")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("reset", "limbo", "death", "timer", "autoreset", "filter", "seed", "templates", "compass", "backup", "language", "silent", "reload"), new ArrayList<>());
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("filter")) {
                if (args[1].equalsIgnoreCase("structure")) {
                    List<String> list = new ArrayList<>(STRUCTURE_NAMES);
                    list.add("clear");
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }
                if (args[1].equalsIgnoreCase("biome")) {
                    List<String> list = new ArrayList<>(BIOME_NAMES);
                    list.add("clear");
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }
            }
            if (args[0].equalsIgnoreCase("timer")) {
                if (args[1].equalsIgnoreCase("mode")) {
                    List<String> list = new ArrayList<>(Arrays.asList("RTA", "IGT"));
                    if (timerMode != null && list.contains(timerMode)) { list.remove(timerMode); list.add(0, timerMode); }
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }
                if (args[1].equalsIgnoreCase("scope")) {
                    List<String> list = new ArrayList<>(Arrays.asList("GLOBAL", "INDIVIDUAL"));
                    if (timerScope != null && list.contains(timerScope)) { list.remove(timerScope); list.add(0, timerScope); }
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }
                if (args[1].equalsIgnoreCase("goal")) {
                    List<String> list = new ArrayList<>(Arrays.asList("ENTITY", "PORTAL", "ADVANCEMENT", "BLOCK", "ITEM", "NONE"));
                    if (timerGoalType != null && list.contains(timerGoalType)) { list.remove(timerGoalType); list.add(0, timerGoalType); }
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }
            }
            if (args[0].equalsIgnoreCase("autoreset")) {
                if (args[1].equalsIgnoreCase("loop") || args[1].equalsIgnoreCase("visible")) {
                    return StringUtil.copyPartialMatches(args[2], Arrays.asList("enable", "disable"), new ArrayList<>());
                }
                if (args[1].equalsIgnoreCase("time")) {
                    return StringUtil.copyPartialMatches(args[2], Arrays.asList("30s", "60s", "5m", "10m", "30m", "1h", "2h"), new ArrayList<>());
                }
            }
            if (args[0].equalsIgnoreCase("limbo") && args[1].equalsIgnoreCase("delay")) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("0", "3", "5", "10", "15"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("limbo")) {
                try {
                    Integer.parseInt(args[1]); // It's a delay number — suggest targets for args[2]
                    List<String> suggestions = new ArrayList<>(Arrays.asList("me", "all"));
                    for (Player p : Bukkit.getOnlinePlayers()) suggestions.add(p.getName());
                    return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>());
                } catch (NumberFormatException ignored) {}
            }
            if (args[0].equalsIgnoreCase("backup") && args[1].equalsIgnoreCase("limit")) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("all", "3", "5", "10", "20"), new ArrayList<>());
            }
        }
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("limbo") && args[1].equalsIgnoreCase("delay")) {
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("0", "3", "5", "10", "15"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("timer") && args[1].equalsIgnoreCase("goal")) {
                if (args[2].equalsIgnoreCase("PORTAL")) return StringUtil.copyPartialMatches(args[3], Arrays.asList("NETHER", "END", "OVERWORLD", "ANY"), new ArrayList<>());

                if (args[2].equalsIgnoreCase("ENTITY")) {
                    List<String> entities = new ArrayList<>();
                    for (EntityType type : Registry.ENTITY_TYPE) {
                        if (type.isAlive()) entities.add(type.key().value());
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), entities, new ArrayList<>());
                }

                if (args[2].equalsIgnoreCase("ADVANCEMENT")) {
                    List<String> advs = new ArrayList<>();
                    Iterator<Advancement> it = Bukkit.advancementIterator();
                    while(it.hasNext()) {
                        Advancement adv = it.next();
                        String keyValue = adv.key().value();
                        if (!keyValue.startsWith("recipes/")) advs.add(keyValue);
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), advs, new ArrayList<>());
                }

                if (args[2].equalsIgnoreCase("BLOCK")) {
                    List<String> blocks = new ArrayList<>();
                    for (Material mat : Registry.MATERIAL) {
                        if (mat.isBlock()) {
                            blocks.add(mat.key().value());
                        }
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), blocks, new ArrayList<>());
                }

                if (args[2].equalsIgnoreCase("ITEM")) {
                    List<String> items = new ArrayList<>();
                    for (Material mat : Registry.MATERIAL) {
                        if (mat.isItem()) {
                            items.add(mat.key().value());
                        }
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), items, new ArrayList<>());
                }
            }
        }
        return Collections.emptyList();
    }

    private long getRawLiveTime(UUID uuid) {
        if (!timerEnabled) return 0;
        if (timerScope.equals("GLOBAL")) {
            if (timerMode.equals("IGT")) {
                return globalElapsedTicks * 50L;
            } else {
                if (timerRunning) {
                    return System.currentTimeMillis() - globalStartTime;
                } else {
                    return globalElapsedTime;
                }
            }
        } else {
            if (playersFinished.contains(uuid)) {
                if (timerMode.equals("IGT")) {
                    return playerElapsedTicks.getOrDefault(uuid, 0) * 50L;
                } else {
                    return playerElapsedTimes.getOrDefault(uuid, 0L);
                }
            } else {
                if (timerMode.equals("IGT")) {
                    return playerElapsedTicks.getOrDefault(uuid, 0) * 50L;
                } else {
                    if (timerRunning) {
                        if (!playerStartTimes.containsKey(uuid)) {
                            playerStartTimes.put(uuid, System.currentTimeMillis());
                        }
                        long start = playerStartTimes.get(uuid);
                        return System.currentTimeMillis() - start;
                    } else {
                        return playerElapsedTimes.getOrDefault(uuid, 0L);
                    }
                }
            }
        }
    }

    private void loadRecordsFile() {
        recordsFile = new File(getDataFolder(), "records.yml");
        if (!recordsFile.exists()) {
            try {
                recordsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create records.yml: " + e.getMessage());
            }
        }
        recordsConfig = YamlConfiguration.loadConfiguration(recordsFile);
    }

    private void saveRecordsFile() {
        try {
            recordsConfig.save(recordsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save records.yml: " + e.getMessage());
        }
    }

    private void incrementAttempts() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            String path = "players." + uuid.toString();
            int attempts = recordsConfig.getInt(path + ".attempts", 0) + 1;
            recordsConfig.set(path + ".attempts", attempts);
            recordsConfig.set(path + ".name", p.getName());
            syncScoreboard(p);
        }
        saveRecordsFile();
    }

    private void syncScoreboard(Player player) {
        try {
            org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            
            String lang = getConfig().getString("language", "en");
            boolean isPl = lang.equalsIgnoreCase("pl");

            org.bukkit.scoreboard.Objective attemptsObj = getOrRegisterObjective(scoreboard, "wr_attempts", isPl ? "Proby" : "Attempts");
            org.bukkit.scoreboard.Objective completionsObj = getOrRegisterObjective(scoreboard, "wr_completions", isPl ? "Ukonczenia" : "Completions");
            org.bukkit.scoreboard.Objective winRatioObj = getOrRegisterObjective(scoreboard, "wr_win_ratio", isPl ? "Procent Wygranych (%)" : "Win Ratio (%)");
            org.bukkit.scoreboard.Objective topRankObj = getOrRegisterObjective(scoreboard, "wr_top_rank", isPl ? "Pozycja" : "Top Rank");
            
            // PB
            org.bukkit.scoreboard.Objective pbMsObj = getOrRegisterObjective(scoreboard, "wr_pb_ms", isPl ? "PB (Milisekundy)" : "PB (Millis)");
            org.bukkit.scoreboard.Objective pbSecObj = getOrRegisterObjective(scoreboard, "wr_pb", isPl ? "PB (Sekundy)" : "PB (Seconds)");
            org.bukkit.scoreboard.Objective pbSecExplicitObj = getOrRegisterObjective(scoreboard, "wr_pb_sec", isPl ? "PB (Sekundy)" : "PB (Seconds)");
            org.bukkit.scoreboard.Objective pbMinObj = getOrRegisterObjective(scoreboard, "wr_pb_min", isPl ? "PB (Minuty)" : "PB (Minutes)");
            org.bukkit.scoreboard.Objective pbTicksObj = getOrRegisterObjective(scoreboard, "wr_pb_ticks", isPl ? "PB (Ticki)" : "PB (Ticks)");
            
            // Record
            org.bukkit.scoreboard.Objective top1MsObj = getOrRegisterObjective(scoreboard, "wr_top1_ms", isPl ? "Rekord (Milisekundy)" : "Record (Millis)");
            org.bukkit.scoreboard.Objective top1SecObj = getOrRegisterObjective(scoreboard, "wr_top1_sec", isPl ? "Rekord (Sekundy)" : "Record (Seconds)");
            org.bukkit.scoreboard.Objective top1MinObj = getOrRegisterObjective(scoreboard, "wr_top1_min", isPl ? "Rekord (Minuty)" : "Record (Minutes)");
            org.bukkit.scoreboard.Objective top1TicksObj = getOrRegisterObjective(scoreboard, "wr_top1_ticks", isPl ? "Rekord (Ticki)" : "Record (Ticks)");
            
            // Last
            org.bukkit.scoreboard.Objective lastMsObj = getOrRegisterObjective(scoreboard, "wr_last_ms", isPl ? "Ostatni (Milisekundy)" : "Last (Millis)");
            org.bukkit.scoreboard.Objective lastSecObj = getOrRegisterObjective(scoreboard, "wr_last_sec", isPl ? "Ostatni (Sekundy)" : "Last (Seconds)");
            org.bukkit.scoreboard.Objective lastMinObj = getOrRegisterObjective(scoreboard, "wr_last_min", isPl ? "Ostatni (Minuty)" : "Last (Minutes)");
            org.bukkit.scoreboard.Objective lastTicksObj = getOrRegisterObjective(scoreboard, "wr_last_ticks", isPl ? "Ostatni (Ticki)" : "Last (Ticks)");

            // Average
            org.bukkit.scoreboard.Objective avgMsObj = getOrRegisterObjective(scoreboard, "wr_avg_ms", isPl ? "Srednia (Milisekundy)" : "Avg (Millis)");
            org.bukkit.scoreboard.Objective avgSecObj = getOrRegisterObjective(scoreboard, "wr_avg_sec", isPl ? "Srednia (Sekundy)" : "Avg (Seconds)");
            org.bukkit.scoreboard.Objective avgObj = getOrRegisterObjective(scoreboard, "wr_avg", isPl ? "Srednia (Sekundy)" : "Avg (Seconds)");
            org.bukkit.scoreboard.Objective avgMinObj = getOrRegisterObjective(scoreboard, "wr_avg_min", isPl ? "Srednia (Minuty)" : "Avg (Minutes)");
            org.bukkit.scoreboard.Objective avgTicksObj = getOrRegisterObjective(scoreboard, "wr_avg_ticks", isPl ? "Srednia (Ticki)" : "Avg (Ticks)");

            // Total
            org.bukkit.scoreboard.Objective totalMsObj = getOrRegisterObjective(scoreboard, "wr_total_time_ms", isPl ? "Suma (Milisekundy)" : "Total (Millis)");
            org.bukkit.scoreboard.Objective totalSecObj = getOrRegisterObjective(scoreboard, "wr_total_time_sec", isPl ? "Suma (Sekundy)" : "Total (Seconds)");
            org.bukkit.scoreboard.Objective totalMinObj = getOrRegisterObjective(scoreboard, "wr_total_time_min", isPl ? "Suma (Minuty)" : "Total (Minutes)");
            org.bukkit.scoreboard.Objective totalTicksObj = getOrRegisterObjective(scoreboard, "wr_total_time_ticks", isPl ? "Suma (Ticki)" : "Total (Ticks)");

            // --- NEW OBJECTIVES ---
            World w = Bukkit.getWorld(gameWorldName);
            long seedVal = w != null ? w.getSeed() : 0L;
            int seedLower32 = (int) seedVal;
            
            org.bukkit.scoreboard.Objective seedObj = getOrRegisterObjective(scoreboard, "wr_seed", isPl ? "Ziarno" : "Seed");

            int activePlayers = w != null ? w.getPlayers().size() : 0;
            org.bukkit.scoreboard.Objective playersActiveObj = getOrRegisterObjective(scoreboard, "wr_players_active", isPl ? "Aktywni Gracze" : "Active Players");

            int resetOnDeathVal = getConfig().getBoolean("reset-on-death", false) ? 1 : 0;
            org.bukkit.scoreboard.Objective deathResetObj = getOrRegisterObjective(scoreboard, "wr_death_reset", isPl ? "Reset po Smierci" : "Death Reset");

            int timerStatusVal = goalReachedPause ? 2 : (timerRunning ? 1 : 0);
            org.bukkit.scoreboard.Objective timerStatusObj = getOrRegisterObjective(scoreboard, "wr_timer_status", isPl ? "Status Stopera" : "Timer Status");

            int timerModeVal = timerMode != null && timerMode.equalsIgnoreCase("IGT") ? 2 : 1;
            org.bukkit.scoreboard.Objective timerModeObj = getOrRegisterObjective(scoreboard, "wr_timer_mode", isPl ? "Tryb Stopera" : "Timer Mode");

            int timerScopeVal = timerScope != null && timerScope.equalsIgnoreCase("INDIVIDUAL") ? 2 : 1;
            org.bukkit.scoreboard.Objective timerScopeObj = getOrRegisterObjective(scoreboard, "wr_timer_scope", isPl ? "Zasieg Stopera" : "Timer Scope");

            UUID uuid = player.getUniqueId();
            int playerFinishedVal = playersFinished.contains(uuid) ? 1 : 0;
            org.bukkit.scoreboard.Objective playerFinishedObj = getOrRegisterObjective(scoreboard, "wr_player_finished", isPl ? "Ukonczono" : "Finished");

            int goalTypeVal = 0;
            if (timerGoalType != null) {
                switch (timerGoalType.toUpperCase()) {
                    case "PORTAL": goalTypeVal = 1; break;
                    case "ENTITY": goalTypeVal = 2; break;
                    case "ADVANCEMENT": goalTypeVal = 3; break;
                    case "BLOCK": goalTypeVal = 4; break;
                    case "ITEM": goalTypeVal = 5; break;
                }
            }
            org.bukkit.scoreboard.Objective goalTypeObj = getOrRegisterObjective(scoreboard, "wr_goal_type", isPl ? "Typ Celu" : "Goal Type");

            int difficultyVal = 2;
            Difficulty bukkitDiff = getServerDifficulty();
            if (bukkitDiff != null) {
                switch (bukkitDiff) {
                    case PEACEFUL: difficultyVal = 0; break;
                    case EASY: difficultyVal = 1; break;
                    case NORMAL: difficultyVal = 2; break;
                    case HARD: difficultyVal = 3; break;
                }
            }
            org.bukkit.scoreboard.Objective difficultyObj = getOrRegisterObjective(scoreboard, "wr_difficulty", isPl ? "Trudnosc" : "Difficulty");

            String filterStruct = getConfig().getString("filter.structure", "");
            String filterBiome = getConfig().getString("filter.biome", "");
            int filterActiveVal = (filterStruct != null && !filterStruct.isEmpty()) || (filterBiome != null && !filterBiome.isEmpty()) ? 1 : 0;
            org.bukkit.scoreboard.Objective filterActiveObj = getOrRegisterObjective(scoreboard, "wr_filter_active", isPl ? "Filtr Aktywny" : "Filter Active");

            String path = "players." + uuid.toString();
            int attempts = recordsConfig.getInt(path + ".attempts", 0);
            int completions = recordsConfig.getInt(path + ".completions", 0);
            int winRatio = attempts > 0 ? (completions * 100) / attempts : 0;
            
            long pb = recordsConfig.getLong(path + ".pb", -1);
            int pbMs = pb > 0 ? (int) pb : 0;
            int pbSec = pb > 0 ? (int) (pb / 1000L) : 0;
            int pbMin = pb > 0 ? (int) (pb / 60000L) : 0;
            int pbTicks = pb > 0 ? (int) (pb / 50L) : 0;

            long lastTime = recordsConfig.getLong(path + ".last_time", -1);
            int lastMs = lastTime > 0 ? (int) lastTime : 0;
            int lastSec = lastTime > 0 ? (int) (lastTime / 1000L) : 0;
            int lastMin = lastTime > 0 ? (int) (lastTime / 60000L) : 0;
            int lastTicks = lastTime > 0 ? (int) (lastTime / 50L) : 0;

            long totalCompletionTime = recordsConfig.getLong(path + ".total_completion_time", -1);
            if (totalCompletionTime == -1) {
                if (completions > 0 && pb > 0) {
                    totalCompletionTime = pb * completions;
                } else {
                    totalCompletionTime = 0;
                }
            }
            int totalMs = (int) totalCompletionTime;
            int totalSec = (int) (totalCompletionTime / 1000L);
            int totalMin = (int) (totalCompletionTime / 60000L);
            int totalTicks = (int) (totalCompletionTime / 50L);

            long avgTime = completions > 0 ? totalCompletionTime / completions : 0;
            int avgMs = (int) avgTime;
            int avgSec = (int) (avgTime / 1000L);
            int avgMin = (int) (avgTime / 60000L);
            int avgTicks = (int) (avgTime / 50L);

            int topRank = 0;
            List<Map<?, ?>> list = (List<Map<?, ?>>) recordsConfig.getMapList("leaderboard");
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    if (uuid.toString().equals(list.get(i).get("uuid"))) {
                        topRank = i + 1;
                        break;
                    }
                }
            }

            long top1 = -1;
            if (list != null && !list.isEmpty()) {
                top1 = ((Number) list.getFirst().get("time")).longValue();
            }
            int top1Ms = top1 > 0 ? (int) top1 : 0;
            int top1Sec = top1 > 0 ? (int) (top1 / 1000L) : 0;
            int top1Min = top1 > 0 ? (int) (top1 / 60000L) : 0;
            int top1Ticks = top1 > 0 ? (int) (top1 / 50L) : 0;

            String name = player.getName();
            attemptsObj.getScore(name).setScore(attempts);
            completionsObj.getScore(name).setScore(completions);
            winRatioObj.getScore(name).setScore(winRatio);
            topRankObj.getScore(name).setScore(topRank);
            
            pbMsObj.getScore(name).setScore(pbMs);
            pbSecObj.getScore(name).setScore(pbSec);
            pbSecExplicitObj.getScore(name).setScore(pbSec);
            pbMinObj.getScore(name).setScore(pbMin);
            pbTicksObj.getScore(name).setScore(pbTicks);
            
            top1MsObj.getScore(name).setScore(top1Ms);
            top1SecObj.getScore(name).setScore(top1Sec);
            top1MinObj.getScore(name).setScore(top1Min);
            top1TicksObj.getScore(name).setScore(top1Ticks);
            
            lastMsObj.getScore(name).setScore(lastMs);
            lastSecObj.getScore(name).setScore(lastSec);
            lastMinObj.getScore(name).setScore(lastMin);
            lastTicksObj.getScore(name).setScore(lastTicks);

            avgMsObj.getScore(name).setScore(avgMs);
            avgSecObj.getScore(name).setScore(avgSec);
            avgObj.getScore(name).setScore(avgSec);
            avgMinObj.getScore(name).setScore(avgMin);
            avgTicksObj.getScore(name).setScore(avgTicks);

            totalMsObj.getScore(name).setScore(totalMs);
            totalSecObj.getScore(name).setScore(totalSec);
            totalMinObj.getScore(name).setScore(totalMin);
            totalTicksObj.getScore(name).setScore(totalTicks);

            // Set scores for new objectives
            seedObj.getScore(name).setScore(seedLower32);
            playersActiveObj.getScore(name).setScore(activePlayers);
            deathResetObj.getScore(name).setScore(resetOnDeathVal);
            timerStatusObj.getScore(name).setScore(timerStatusVal);
            timerModeObj.getScore(name).setScore(timerModeVal);
            timerScopeObj.getScore(name).setScore(timerScopeVal);
            playerFinishedObj.getScore(name).setScore(playerFinishedVal);
            goalTypeObj.getScore(name).setScore(goalTypeVal);
            difficultyObj.getScore(name).setScore(difficultyVal);
            filterActiveObj.getScore(name).setScore(filterActiveVal);

        } catch (Exception e) {
            getLogger().warning("Failed to sync player scoreboard: " + e.getMessage());
        }
    }

    private org.bukkit.scoreboard.Objective getOrRegisterObjective(org.bukkit.scoreboard.Scoreboard scoreboard, String name, String displayName) {
        org.bukkit.scoreboard.Objective obj = scoreboard.getObjective(name);
        if (obj == null) {
            obj = scoreboard.registerNewObjective(name, org.bukkit.scoreboard.Criteria.DUMMY, displayName);
        }
        if (!obj.getDisplayName().equals(displayName)) {
            obj.setDisplayName(displayName);
        }
        return obj;
    }

    private void syncAllScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            syncScoreboard(p);
        }
    }

    private void updateLeaderboard(String name, UUID uuid, long time, long seed) {
        List<Map<?, ?>> list = (List<Map<?, ?>>) recordsConfig.getMapList("leaderboard");
        if (list == null) list = new ArrayList<>();

        Map<String, Object> existingEntry = null;
        for (Map<?, ?> entry : list) {
            if (uuid.toString().equals(entry.get("uuid"))) {
                existingEntry = (Map<String, Object>) entry;
                break;
            }
        }

        if (existingEntry != null) {
            long oldTime = ((Number) existingEntry.get("time")).longValue();
            if (time < oldTime) {
                existingEntry.put("time", time);
                existingEntry.put("date", new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new java.util.Date()));
                existingEntry.put("seed", String.valueOf(seed));
                existingEntry.put("player", name);
            }
        } else {
            Map<String, Object> entry = new HashMap<>();
            entry.put("player", name);
            entry.put("uuid", uuid.toString());
            entry.put("time", time);
            entry.put("date", new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new java.util.Date()));
            entry.put("seed", String.valueOf(seed));
            list.add(entry);
        }

        list.sort((m1, m2) -> Long.compare(((Number) m1.get("time")).longValue(), ((Number) m2.get("time")).longValue()));

        if (list.size() > 10) {
            list = new ArrayList<>(list.subList(0, 10));
        }

        recordsConfig.set("leaderboard", list);
    }

    public class WorldResetExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        @Override
        public @NotNull String getAuthor() {
            return "vipluk";
        }

        @Override
        public @NotNull String getIdentifier() {
            return "worldreset";
        }

        @Override
        public @NotNull String getVersion() {
            return Main.this.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            if (player == null) return "";
            UUID uuid = player.getUniqueId();
            String lang = getConfig().getString("language", "en");
            boolean isPl = lang.equalsIgnoreCase("pl");

            if (params.equalsIgnoreCase("timer")) {
                long elapsed = getRawLiveTime(uuid);
                return formatTime(elapsed, false);
            }
            if (params.equalsIgnoreCase("timer_ms")) {
                long elapsed = getRawLiveTime(uuid);
                return formatTime(elapsed, true);
            }
            if (params.equalsIgnoreCase("timer_status")) {
                if (goalReachedPause) return isPl ? "§6Ukończony" : "§6Finished";
                return timerRunning ? (isPl ? "§aBiegnie" : "§aRunning") : (isPl ? "§cZatrzymany" : "§cPaused");
            }
            if (params.equalsIgnoreCase("timer_mode")) {
                return timerMode != null ? timerMode : "RTA";
            }
            if (params.equalsIgnoreCase("timer_scope")) {
                return timerScope != null ? timerScope : "GLOBAL";
            }
            if (params.equalsIgnoreCase("timer_raw")) {
                return String.valueOf(getRawLiveTime(uuid));
            }
            if (params.equalsIgnoreCase("timer_raw_ms")) {
                return String.valueOf(getRawLiveTime(uuid));
            }
            if (params.equalsIgnoreCase("timer_raw_sec")) {
                return String.valueOf(getRawLiveTime(uuid) / 1000L);
            }
            if (params.equalsIgnoreCase("timer_raw_min")) {
                return String.valueOf(getRawLiveTime(uuid) / 60000L);
            }
            if (params.equalsIgnoreCase("timer_raw_ticks")) {
                return String.valueOf(getRawLiveTime(uuid) / 50L);
            }
            if (params.equalsIgnoreCase("goal_type")) {
                return timerGoalType != null ? timerGoalType : "NONE";
            }
            if (params.equalsIgnoreCase("goal_value")) {
                return timerGoalValue != null ? timerGoalValue : "";
            }
            if (params.equalsIgnoreCase("player_finished")) {
                return playersFinished.contains(uuid) ? (isPl ? "§aUkończono" : "§aFinished") : (isPl ? "§cBiegnie" : "§cRunning");
            }
            if (params.equalsIgnoreCase("seed")) {
                World w = Bukkit.getWorld(gameWorldName);
                return w != null ? String.valueOf(w.getSeed()) : "0";
            }
            if (params.equalsIgnoreCase("death_reset")) {
                return getConfig().getBoolean("reset-on-death", false) ? (isPl ? "§cWłączony" : "§cEnabled") : (isPl ? "§aWyłączony" : "§aDisabled");
            }
            if (params.equalsIgnoreCase("world_name")) {
                return gameWorldName;
            }
            if (params.equalsIgnoreCase("players_active")) {
                World w = Bukkit.getWorld(gameWorldName);
                return w != null ? String.valueOf(w.getPlayers().size()) : "0";
            }
            if (params.equalsIgnoreCase("filter_active")) {
                String filterStruct = getConfig().getString("filter.structure", "");
                String filterBiome = getConfig().getString("filter.biome", "");
                boolean active = (filterStruct != null && !filterStruct.isEmpty()) || (filterBiome != null && !filterBiome.isEmpty());
                return active ? (isPl ? "Tak" : "Yes") : (isPl ? "Nie" : "No");
            }
            if (params.equalsIgnoreCase("filter_biome")) {
                String filterBiome = getConfig().getString("filter.biome", "");
                return (filterBiome != null && !filterBiome.isEmpty()) ? filterBiome : (isPl ? "Brak" : "None");
            }
            if (params.equalsIgnoreCase("filter_structure")) {
                String filterStruct = getConfig().getString("filter.structure", "");
                return (filterStruct != null && !filterStruct.isEmpty()) ? filterStruct : (isPl ? "Brak" : "None");
            }
            if (params.equalsIgnoreCase("difficulty")) {
                Difficulty diff = getServerDifficulty();
                if (diff == null) return isPl ? "Normalny" : "NORMAL";
                return isPl ? switch(diff) {
                    case PEACEFUL -> "Pokojowy";
                    case EASY -> "Łatwy";
                    case NORMAL -> "Normalny";
                    case HARD -> "Trudny";
                } : diff.name();
            }
            if (params.equalsIgnoreCase("goal")) {
                if (timerGoalType == null || timerGoalType.equalsIgnoreCase("NONE")) {
                    return isPl ? "Brak" : "None";
                }
                String typeStr = timerGoalType;
                if (isPl) {
                    typeStr = switch(timerGoalType.toUpperCase()) {
                        case "PORTAL" -> "Portal";
                        case "ENTITY" -> "Zabicie";
                        case "ADVANCEMENT" -> "Osiągnięcie";
                        case "BLOCK" -> "Zniszczenie Bloku";
                        case "ITEM" -> "Przedmiot";
                        default -> timerGoalType;
                    };
                }
                return typeStr + ": " + (timerGoalValue != null ? timerGoalValue : "");
            }

            // ---- PERSONAL BESTS (PB) ----
            if (params.equalsIgnoreCase("pb")) {
                long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                return pb == -1 ? "Brak" : formatTime(pb, true);
            }
            if (params.equalsIgnoreCase("pb_raw") || params.equalsIgnoreCase("pb_ms")) {
                return String.valueOf(recordsConfig.getLong("players." + uuid.toString() + ".pb", 0));
            }
            if (params.equalsIgnoreCase("pb_sec")) {
                long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                return pb == -1 ? "0" : String.valueOf(pb / 1000L);
            }
            if (params.equalsIgnoreCase("pb_min")) {
                long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                return pb == -1 ? "0" : String.valueOf(pb / 60000L);
            }
            if (params.equalsIgnoreCase("pb_ticks")) {
                long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                return pb == -1 ? "0" : String.valueOf(pb / 50L);
            }
            if (params.equalsIgnoreCase("pb_date")) {
                return recordsConfig.getString("players." + uuid.toString() + ".pb_date", "Brak");
            }
            if (params.equalsIgnoreCase("attempts")) {
                return String.valueOf(recordsConfig.getInt("players." + uuid.toString() + ".attempts", 0));
            }
            if (params.equalsIgnoreCase("completions")) {
                return String.valueOf(recordsConfig.getInt("players." + uuid.toString() + ".completions", 0));
            }
            if (params.equalsIgnoreCase("win_ratio")) {
                int attempts = recordsConfig.getInt("players." + uuid.toString() + ".attempts", 0);
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                int winRatio = attempts > 0 ? (completions * 100) / attempts : 0;
                return String.valueOf(winRatio);
            }

            // ---- LAST RUNS ----
            if (params.equalsIgnoreCase("last_ms")) {
                long lastTime = recordsConfig.getLong("players." + uuid.toString() + ".last_time", -1);
                return lastTime == -1 ? "0" : String.valueOf(lastTime);
            }
            if (params.equalsIgnoreCase("last_sec")) {
                long lastTime = recordsConfig.getLong("players." + uuid.toString() + ".last_time", -1);
                return lastTime == -1 ? "0" : String.valueOf(lastTime / 1000L);
            }
            if (params.equalsIgnoreCase("last_min")) {
                long lastTime = recordsConfig.getLong("players." + uuid.toString() + ".last_time", -1);
                return lastTime == -1 ? "0" : String.valueOf(lastTime / 60000L);
            }
            if (params.equalsIgnoreCase("last_ticks")) {
                long lastTime = recordsConfig.getLong("players." + uuid.toString() + ".last_time", -1);
                return lastTime == -1 ? "0" : String.valueOf(lastTime / 50L);
            }

            // ---- AVERAGE COMPLETION ----
            if (params.equalsIgnoreCase("avg") || params.equalsIgnoreCase("avg_formatted")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                if (completions <= 0) return "Brak";
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                long avg = total / completions;
                return formatTime(avg, true);
            }
            if (params.equalsIgnoreCase("avg_ms")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                if (completions <= 0) return "0";
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf(total / completions);
            }
            if (params.equalsIgnoreCase("avg_sec")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                if (completions <= 0) return "0";
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf((total / completions) / 1000L);
            }
            if (params.equalsIgnoreCase("avg_min")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                if (completions <= 0) return "0";
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf((total / completions) / 60000L);
            }
            if (params.equalsIgnoreCase("avg_ticks")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                if (completions <= 0) return "0";
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf((total / completions) / 50L);
            }

            // ---- TOTAL CUMULATIVE TIME ----
            if (params.equalsIgnoreCase("total_time") || params.equalsIgnoreCase("total_time_formatted")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return total <= 0 ? "Brak" : formatTime(total, true);
            }
            if (params.equalsIgnoreCase("total_time_ms")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf(total);
            }
            if (params.equalsIgnoreCase("total_time_sec")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf(total / 1000L);
            }
            if (params.equalsIgnoreCase("total_time_min")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf(total / 60000L);
            }
            if (params.equalsIgnoreCase("total_time_ticks")) {
                int completions = recordsConfig.getInt("players." + uuid.toString() + ".completions", 0);
                long total = recordsConfig.getLong("players." + uuid.toString() + ".total_completion_time", -1);
                if (total == -1) {
                    long pb = recordsConfig.getLong("players." + uuid.toString() + ".pb", -1);
                    total = pb > 0 ? pb * completions : 0;
                }
                return String.valueOf(total / 50L);
            }

            // ---- AUTORESET PLACEHOLDERS ----
            if (params.equalsIgnoreCase("autoreset")) {
                if (!autoResetEnabled) return "0:00";
                return formatAutoResetTime(autoResetRemainingSeconds);
            }
            if (params.equalsIgnoreCase("autoreset_sec")) {
                if (!autoResetEnabled) return "0";
                return String.valueOf(autoResetRemainingSeconds);
            }
            if (params.equalsIgnoreCase("autoreset_min")) {
                if (!autoResetEnabled) return "0";
                return String.valueOf(autoResetRemainingSeconds / 60);
            }
            if (params.equalsIgnoreCase("autoreset_ticks")) {
                if (!autoResetEnabled) return "0";
                return String.valueOf(autoResetRemainingSeconds * 20);
            }
            if (params.equalsIgnoreCase("autoreset_total")) {
                if (!autoResetEnabled) return "0:00";
                return formatAutoResetTime(autoResetTotalSeconds);
            }
            if (params.equalsIgnoreCase("autoreset_total_sec")) {
                if (!autoResetEnabled) return "0";
                return String.valueOf(autoResetTotalSeconds);
            }
            if (params.equalsIgnoreCase("autoreset_status")) {
                if (!autoResetEnabled) return isPl ? "§cWyłączony" : "§cDisabled";
                if (autoResetPaused) return isPl ? "§6Wstrzymany" : "§6Paused";
                return isPl ? "§aAktywny" : "§aRunning";
            }
            if (params.equalsIgnoreCase("autoreset_loop")) {
                return autoResetLoop ? (isPl ? "Tak" : "Yes") : (isPl ? "Nie" : "No");
            }
            if (params.equalsIgnoreCase("autoreset_enabled")) {
                return autoResetEnabled ? (isPl ? "Tak" : "Yes") : (isPl ? "Nie" : "No");
            }

            // ---- SERVER LEADERBOARDS ----
            if (params.toLowerCase().startsWith("top_")) {
                String[] parts = params.split("_");
                if (parts.length == 4) {
                    String type = parts[1].toLowerCase(); // "player", "time", "date", "seed", "ticks", "ms", "sec", "min"
                    try {
                        int rank = Integer.parseInt(parts[3]);
                        List<Map<?, ?>> list = (List<Map<?, ?>>) recordsConfig.getMapList("leaderboard");
                        if (list != null && rank > 0 && rank <= list.size()) {
                            Map<?, ?> entry = list.get(rank - 1);
                            long rawTime = ((Number) entry.get("time")).longValue();
                            switch (type) {
                                case "player": return String.valueOf(entry.get("player"));
                                case "time": return formatTime(rawTime, true);
                                case "ticks": return String.valueOf(rawTime / 50L);
                                case "ms": return String.valueOf(rawTime);
                                case "sec": return String.valueOf(rawTime / 1000L);
                                case "min": return String.valueOf(rawTime / 60000L);
                                case "date": return String.valueOf(entry.get("date"));
                                case "seed": return String.valueOf(entry.get("seed"));
                            }
                        } else {
                            return "Brak";
                        }
                    } catch (Exception ignored) {}
                }
            }

            return null;
        }
    }
}
