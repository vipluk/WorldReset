package org.example.worldreset;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
    private BukkitTask compassTask;
    private final Map<UUID, BossBar> compassBars = new HashMap<>();
    private final Map<UUID, ChatColor> playerColors = new HashMap<>();
    private final ChatColor[] DOT_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.LIGHT_PURPLE, ChatColor.AQUA, ChatColor.GOLD, ChatColor.DARK_GREEN
    };
    private final Map<String, ChatColor> STRING_TO_COLOR = new HashMap<>() {{
        put("RED", ChatColor.RED);
        put("BLUE", ChatColor.BLUE);
        put("GREEN", ChatColor.GREEN);
        put("YELLOW", ChatColor.YELLOW);
        put("PURPLE", ChatColor.LIGHT_PURPLE);
        put("AQUA", ChatColor.AQUA);
        put("GOLD", ChatColor.GOLD);
        put("DARK_GREEN", ChatColor.DARK_GREEN);
        put("WHITE", ChatColor.WHITE);
    }};

    private final String[] COMPASS_BG = new String[120];

    // Structure list (Overworld only)
    private final List<String> STRUCTURE_NAMES = new ArrayList<>();

    // Biome list (Overworld popular)
    private final List<String> BIOME_NAMES = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        saveResource("messages_en.yml", false);
        saveResource("messages_pl.yml", false);
        saveResource("placeholderapi.yml", false);
        saveResource("scoreboard.yml", false);
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

        initCompassBg();
        startCompassTask();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new WorldResetExpansion().register();
            getLogger().info("Zarejestrowano integrację z PlaceholderAPI!");
        }

        getLogger().info("WorldReset v1.19 (Timer Toggle Update) enabled.");
    }

    @Override
    public void onDisable() {
        for (BossBar bar : compassBars.values()) {
            bar.removeAll();
        }
        compassBars.clear();
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            // USUNIĘTO: p.spigot().respawn() - to wywoływało błędy!
            p.closeInventory();
        }

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
        }.runTaskLater(this, 5L);
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

                        deleteWorldFolder(gameWorldName);
                        deleteWorldFolder(gameWorldName + "_nether");
                        deleteWorldFolder(gameWorldName + "_the_end");

                        generateGameWorlds();
                    }
                }.runTaskLater(Main.this, 20L);
            }
        }.runTaskLater(this, 20L);
    }

    private void generateGameWorlds() {
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
                for (File dir : subDirs) {
                    if (new File(dir, "level.dat").exists()) {
                        String name = dir.getName().toLowerCase();
                        if (name.contains("nether")) {
                            sourceNether = dir;
                        } else if (name.contains("end")) {
                            sourceEnd = dir;
                        } else {
                            if (sourceOverworld == null || name.equals("overworld")) {
                                sourceOverworld = dir;
                            }
                        }
                    }
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
        startGameForAll();
        resetAndStartTimer();
        isResetting = false;
    }

    // --- LOGIKA FILTRÓW (EXCLUSIVE) ---
    private void applyFiltersAndShiftSpawn(World w) {
        if (getConfig().getBoolean("seed.use-fixed")) return;

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
                getLogger().warning("Error searching structure " + struct.getKey().getKey() + ": " + e.getMessage());
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

    private void findSafeSpawn(World w) {
        Location spawn = w.getSpawnLocation();
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();

        int attempts = 0;
        Random rand = new Random();

        getLogger().info("Finalizing safe spawn around: " + x + ", " + z);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);

        while (attempts < 50) {
            int y = w.getHighestBlockYAt(x, z);
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() != Material.WATER && b.getType() != Material.LAVA) {
                Location safeLoc = new Location(w, x + 0.5, y + 1, z + 0.5);
                w.setSpawnLocation(safeLoc);
                return;
            }
            x += (rand.nextInt(20) - 10);
            z += (rand.nextInt(20) - 10);
            attempts++;
        }
        int y = w.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ());
        w.setSpawnLocation(new Location(w, spawn.getX(), y + 1, spawn.getZ()));
    }

    private void performBackup() {
        Bukkit.getScheduler().runTask(this, () -> broadcastInfo(getMsg("backup-start")));
        String timestamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
        File backupsDir = new File(getDataFolder().getParentFile().getParentFile(), getConfig().getString("backup.folder", "backups"));
        File currentBackupDir = new File(backupsDir, timestamp);
        if (!currentBackupDir.mkdirs()) {}
        copyWorldToBackup(gameWorldName, currentBackupDir);
        copyWorldToBackup(gameWorldName + "_nether", currentBackupDir);
        copyWorldToBackup(gameWorldName + "_the_end", currentBackupDir);
        manageBackupLimit(backupsDir);
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
            for (Structure s : Registry.STRUCTURE) {
                String name = s.getKey().getKey().toUpperCase();
                if (!STRUCTURE_NAMES.contains(name)) {
                    STRUCTURE_NAMES.add(name);
                }
            }
        } catch (Exception e) {
            STRUCTURE_NAMES.addAll(Arrays.asList(
                "DESERT_PYRAMID", "JUNGLE_PYRAMID", "PILLAGER_OUTPOST", "MANSION",
                "ANCIENT_CITY", "STRONGHOLD", "MONUMENT", "BURIED_TREASURE", "IGLOO", "SWAMP_HUT", "TRAIL_RUINS", "TRIAL_CHAMBERS"
            ));
        }

        BIOME_NAMES.clear();
        try {
            for (Biome b : Registry.BIOME) {
                String name = b.getKey().getKey().toUpperCase();
                if (!BIOME_NAMES.contains(name)) {
                    BIOME_NAMES.add(name);
                }
            }
        } catch (Exception e) {
            BIOME_NAMES.addAll(Arrays.asList(
                "PLAINS", "DESERT", "FOREST", "TAIGA", "SWAMP", "JUNGLE", "SPARSE_JUNGLE", "BAMBOO_JUNGLE",
                "BADLANDS", "SAVANNA", "WINDSWEPT_HILLS", "SNOWY_PLAINS", "ICE_SPIKES",
                "MUSHROOM_FIELDS", "CHERRY_GROVE", "MEADOW", "GROVE", "SNOWY_SLOPES", "JAGGED_PEAKS", "FROZEN_PEAKS",
                "STONY_PEAKS", "RIVER", "BEACH", "WARM_OCEAN", "LUKEWARM_OCEAN", "DEEP_LUKEWARM_OCEAN", "OCEAN",
                "DEEP_OCEAN", "COLD_OCEAN", "DEEP_COLD_OCEAN", "FROZEN_OCEAN", "DEEP_FROZEN_OCEAN",
                "DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK"
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
                                        String itemKey = item.getType().getKey().toString().toLowerCase(); // e.g. "minecraft:diamond"
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
        p.sendActionBar(Component.text(ChatColor.translateAlternateColorCodes('&', "&e&l⏱ " + text)));
    }

    // --- LOGIKA KOMPASU (RADARU) ---
    private void initCompassBg() {
        for (int i = 0; i < 120; i++) COMPASS_BG[i] = "&7·";
        COMPASS_BG[0] = "&eS";
        COMPASS_BG[15] = "&7SW";
        COMPASS_BG[30] = "&eW";
        COMPASS_BG[45] = "&7NW";
        COMPASS_BG[60] = "&eN";
        COMPASS_BG[75] = "&7NE";
        COMPASS_BG[90] = "&eE";
        COMPASS_BG[105] = "&7SE";
    }

    private void startCompassTask() {
        if (compassTask != null) compassTask.cancel();
        compassTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!compassEnabled) {
                    for (BossBar bar : compassBars.values()) bar.setVisible(false);
                    return;
                }
                updateCompass();
            }
        }.runTaskTimer(this, 0L, 2L); // Odswiezanie co 2 ticki
    }

    private void updateCompass() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = compassBars.get(p.getUniqueId());
            if (bar == null) continue;

            if (!bar.isVisible()) bar.setVisible(true);

            Location pLoc = p.getLocation();
            double pYaw = pLoc.getYaw();
            while (pYaw < 0) pYaw += 360;
            while (pYaw >= 360) pYaw -= 360;

            int centerIdx = (int) Math.round(pYaw / 3.0);
            if (centerIdx >= 120) centerIdx = 0;

            // Wyciagamy pole widzenia (41 znakow, czyli ok 120 stopni FOV)
            String[] view = new String[41];
            for (int i = 0; i < 41; i++) {
                int idx = centerIdx - 20 + i;
                if (idx < 0) idx += 120;
                if (idx >= 120) idx -= 120;
                view[i] = COMPASS_BG[idx];
            }

            // Nakladanie graczy na radar
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(p) || target.getGameMode() == GameMode.SPECTATOR || !target.getWorld().equals(p.getWorld())) continue;

                Location tLoc = target.getLocation();
                double dx = tLoc.getX() - pLoc.getX();
                double dz = tLoc.getZ() - pLoc.getZ();

                if (dx == 0 && dz == 0) continue;

                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double deltaYaw = targetYaw - pLoc.getYaw();

                while (deltaYaw < -180) deltaYaw += 360;
                while (deltaYaw > 180) deltaYaw -= 360;

                int offset = (int) Math.round(deltaYaw / 3.0);
                if (offset >= -20 && offset <= 20) {
                    int viewIdx = 20 + offset;
                    ChatColor color = playerColors.getOrDefault(target.getUniqueId(), ChatColor.WHITE);
                    view[viewIdx] = color + "●";
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("&8[ ");
            for (String s : view) {
                sb.append(s);
            }
            sb.append(" &8]");

            bar.setTitle(ChatColor.translateAlternateColorCodes('&', sb.toString()));
        }
    }

    // --- EVENTS ---
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (isResetting) {
            Location limbo = getLimboSpawn();
            if (limbo != null) e.setRespawnLocation(limbo);
            return;
        }

        World game = Bukkit.getWorld(gameWorldName);
        if (game != null && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            e.setRespawnLocation(game.getSpawnLocation());
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
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

        // Przydzielanie koloru i paska kompasu
        if (!playerColors.containsKey(p.getUniqueId())) {
            playerColors.put(p.getUniqueId(), DOT_COLORS[playerColors.size() % DOT_COLORS.length]);
        }
        if (!compassBars.containsKey(p.getUniqueId())) {
            BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
            bar.addPlayer(p);
            if (!compassEnabled) bar.setVisible(false);
            compassBars.put(p.getUniqueId(), bar);
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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar bar = compassBars.remove(e.getPlayer().getUniqueId());
        if (bar != null) bar.removeAll();
        Bukkit.getScheduler().runTaskLater(this, this::syncAllScoreboards, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!getConfig().getBoolean("reset-on-death", false)) return;
        if (isResetting) return;
        if (e.getEntity().getWorld().getName().equals(limboWorldName)) return;
        if (!e.getEntity().getWorld().getName().contains(gameWorldName)) return;

        String playerName = e.getEntity().getName();
        broadcastInfo(getMsg("death-reset-triggered").replace("{player}", playerName));
        startReset();
    }

    // --- TIMER TRIGGER EVENTS ---
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            String entityName = e.getEntity().getType().getKey().toString();
            checkTimerGoal(e.getEntity().getKiller(), "ENTITY", entityName.toLowerCase());
        }
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent e) {
        if (e.getAdvancement().getKey().getKey().startsWith("recipes/")) return;
        String advName = e.getAdvancement().getKey().toString();
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
            String blockKey = e.getBlock().getType().getKey().toString().toLowerCase(); // e.g. "minecraft:obsidian"
            checkTimerGoal(e.getPlayer(), "BLOCK", blockKey);
        }
    }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (e.getPlayer().getWorld().getName().equals(limboWorldName) && !e.getPlayer().isOp()) e.setCancelled(true); }

    // --- COMMANDS ---
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length > 0) {
            String arg = args[0].toLowerCase();

            switch (arg) {
                case "help", "?" -> {
                    sender.sendMessage(getMsg("command-usage"));
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
                    startReset();
                    return true;
                }
                case "limbo" -> {
                    if (hasPerm(sender, "worldreset.limbo")) return noPerm(sender, "worldreset.limbo");
                    if (!(sender instanceof Player p)) return true;
                    if (isResetting) {
                        sender.sendMessage(getMsg("already-resetting"));
                        return true;
                    }
                    if (p.getWorld().getName().equals(limboWorldName)) {
                        if (isGameReady) {
                            World game = Bukkit.getWorld(gameWorldName);
                            if (game != null) {
                                setupGamePlayer(p, game.getSpawnLocation());
                                p.sendMessage(getMsg("game-started"));
                            }
                        } else {
                            startReset();
                        }
                    } else {
                        Location loc = getLimboSpawn();
                        p.teleport(loc);
                        setupLimboPlayer(p);
                        p.sendMessage(getMsg("limbo-join"));
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
                    if (args.length > 1) {
                        getConfig().set("seed.use-fixed", true);
                        getConfig().set("seed.value", args[1]);
                        saveConfig();
                        sender.sendMessage(getMsg("seed-set").replace("{seed}", args[1]));
                    } else {
                        getConfig().set("seed.use-fixed", false);
                        saveConfig();
                        sender.sendMessage(getMsg("seed-random"));
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

                    if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
                        getConfig().set("filter.structure", "");
                        getConfig().set("filter.biome", "");
                        saveConfig();
                        sender.sendMessage(getMsg("filter-disabled"));
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
                    } else if (sub.equals("enable")) {
                        getConfig().set("timer.enabled", true);
                        saveConfig();
                        timerEnabled = true;
                        sender.sendMessage(getMsg("timer-enabled"));
                        return true;
                    } else if (sub.equals("disable")) {
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
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /wr compass <enable|disable|color>");
                        return true;
                    }

                    String sub = args[1].toLowerCase();

                    if (sub.equals("enable")) {
                        getConfig().set("compass.enabled", true);
                        saveConfig();
                        compassEnabled = true;
                        sender.sendMessage("§aCompass tracking enabled!");
                        return true;
                    } else if (sub.equals("disable")) {
                        getConfig().set("compass.enabled", false);
                        saveConfig();
                        compassEnabled = false;
                        for (BossBar bar : compassBars.values()) bar.setVisible(false);
                        sender.sendMessage("§cCompass tracking disabled.");
                        return true;
                    } else if (sub.equals("color")) {
                        if (args.length < 3) {
                            sender.sendMessage("§cUsage: /wr compass color [player] <color>");
                            return true;
                        }

                        Player targetPlayer = null;
                        String colorName;

                        if (args.length == 3) {
                            if (!(sender instanceof Player)) {
                                sender.sendMessage("§cConsole must specify a player!");
                                return true;
                            }
                            targetPlayer = (Player) sender;
                            colorName = args[2].toUpperCase();
                        } else {
                            targetPlayer = Bukkit.getPlayer(args[2]);
                            if (targetPlayer == null) {
                                sender.sendMessage("§cPlayer not found.");
                                return true;
                            }
                            colorName = args[3].toUpperCase();
                        }

                        if (!STRING_TO_COLOR.containsKey(colorName)) {
                            sender.sendMessage("§cInvalid color. Available: RED, BLUE, GREEN, YELLOW, PURPLE, AQUA, GOLD, DARK_GREEN, WHITE");
                            return true;
                        }

                        playerColors.put(targetPlayer.getUniqueId(), STRING_TO_COLOR.get(colorName));
                        sender.sendMessage("§aColor for " + targetPlayer.getName() + " set to " + STRING_TO_COLOR.get(colorName) + colorName);
                        return true;
                    }
                }
            }

        }
        sender.sendMessage(getMsg("command-usage"));
        return true;
    }

    private boolean hasPerm(CommandSender s, String node) { return !s.hasPermission(node) && !s.hasPermission(node + ".*") && !s.hasPermission("worldreset.*") && !s.isOp(); }
    private boolean noPerm(CommandSender s, String node) { s.sendMessage(getMsg("no-permission").replace("{permission}", node)); return true; }

    // --- DYNAMIC TAB COMPLETION ---
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("reset", "limbo", "seed", "language", "silent", "death", "filter", "timer", "compass", "reload"), new ArrayList<>());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("filter")) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("structure", "biome", "clear"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("language")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("en", "pl"), new ArrayList<>());
            // Dodane 'enable' i 'disable' do podpowiedzi timera
            if (args[0].equalsIgnoreCase("timer")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("start", "pause", "reset", "enable", "disable", "mode", "scope", "goal"), new ArrayList<>());
            if (args[0].equalsIgnoreCase("compass")) return StringUtil.copyPartialMatches(args[1], Arrays.asList("enable", "disable", "color"), new ArrayList<>());
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
            if (args[0].equalsIgnoreCase("compass") && args[1].equalsIgnoreCase("color")) {
                List<String> list = new ArrayList<>(STRING_TO_COLOR.keySet());
                for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
                return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
            }
        }
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("timer") && args[1].equalsIgnoreCase("goal")) {
                if (args[2].equalsIgnoreCase("PORTAL")) return StringUtil.copyPartialMatches(args[3], Arrays.asList("NETHER", "END", "OVERWORLD", "ANY"), new ArrayList<>());

                if (args[2].equalsIgnoreCase("ENTITY")) {
                    List<String> entities = new ArrayList<>();
                    for (EntityType type : EntityType.values()) {
                        if (type.isAlive()) entities.add(type.getKey().getKey());
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), entities, new ArrayList<>());
                }

                if (args[2].equalsIgnoreCase("ADVANCEMENT")) {
                    List<String> advs = new ArrayList<>();
                    Iterator<Advancement> it = Bukkit.advancementIterator();
                    while(it.hasNext()) {
                        NamespacedKey key = it.next().getKey();
                        if (!key.getKey().startsWith("recipes/")) advs.add(key.getKey());
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), advs, new ArrayList<>());
                }

                if (args[2].equalsIgnoreCase("BLOCK")) {
                    List<String> blocks = new ArrayList<>();
                    for (Material mat : Material.values()) {
                        if (mat.isBlock() && !mat.isLegacy()) {
                            blocks.add(mat.getKey().getKey());
                        }
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), blocks, new ArrayList<>());
                }

                if (args[2].equalsIgnoreCase("ITEM")) {
                    List<String> items = new ArrayList<>();
                    for (Material mat : Material.values()) {
                        if (mat.isItem() && !mat.isLegacy()) {
                            items.add(mat.getKey().getKey());
                        }
                    }
                    return StringUtil.copyPartialMatches(args[3].toLowerCase(), items, new ArrayList<>());
                }
            }
            if (args[0].equalsIgnoreCase("compass") && args[1].equalsIgnoreCase("color")) {
                List<String> colors = new ArrayList<>(STRING_TO_COLOR.keySet());
                return StringUtil.copyPartialMatches(args[3], colors, new ArrayList<>());
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
            obj = scoreboard.registerNewObjective(name, "dummy");
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