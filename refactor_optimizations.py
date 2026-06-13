import re

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Update resetWorld to call startAsyncStructureSpawnSearch instead of applyFiltersAndShiftSpawn for structures
# Lines around 1253-1259:
old_sync_path = '''                } else {
                    // Synchronous path: structures or no filter
                    applyFiltersAndShiftSpawn(normal);
                    if (!skipFindSafeSpawn) {
                        findSafeSpawn(normal);
                    }
                }'''

new_sync_path = '''                } else {
                    // Structures or no filter
                    String structReq = getConfig().getString("filter.structure", "").toUpperCase();
                    if (!structReq.isEmpty() && getConfig().getBoolean("filter.enabled", true)) {
                        broadcastInfo(getMsg("auto-msg-1"));
                        startAsyncStructureSpawnSearch(normal, structReq, () -> {
                            if (!skipFindSafeSpawn) {
                                findSafeSpawn(normal);
                            }
                            preGenerateSpawnChunks(normal, normal.getSpawnLocation());
                            broadcastInfo(getMsg("generation-complete"));
                            isGameReady = true;
                            finalizeGameStart(useDelayOut);
                        });
                        return; // Async
                    } else {
                        applyFiltersAndShiftSpawn(normal);
                        if (!skipFindSafeSpawn) {
                            findSafeSpawn(normal);
                        }
                    }
                }'''

code = code.replace(old_sync_path, new_sync_path)

# 2. In applyFiltersAndShiftSpawn, REMOVE the cave handling, water biome handling, and structure handling, because they are either handled async or are obsolete.
# Actually, applyFiltersAndShiftSpawn is only called now if structReq is empty AND biomeReq is empty. 
# So applyFiltersAndShiftSpawn doesn't even need to exist, but let's keep it harmless just in case.

# 3. Add startAsyncStructureSpawnSearch method before applyFiltersAndShiftSpawn
async_struct = '''
    private void startAsyncStructureSpawnSearch(World w, String structName, Runnable onComplete) {
        getLogger().info("Starting async search for structure: " + structName);
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            return findStructureLocation(w, structName);
        }).thenAccept(bestLoc -> {
            Bukkit.getScheduler().runTask(Main.this, () -> {
                if (bestLoc != null) {
                    Location structSpawn = findSafeSpawnInStructure(w, bestLoc, structName);
                    Location finalLoc = structSpawn != null ? structSpawn : new Location(w, bestLoc.getX(), w.getHighestBlockYAt(bestLoc) + 1, bestLoc.getZ());
                    w.setSpawnLocation(finalLoc);
                    w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                    skipFindSafeSpawn = true;
                    broadcastInfo(getMsg("filter-shifted").replace("{target}", structName));
                    getLogger().info("Spawn shifted to structure " + structName + " at " + finalLoc.toVector());
                } else {
                    broadcastInfo(getMsg("filter-failed"));
                }
                onComplete.run();
            });
        });
    }
'''

code = code.replace('private void applyFiltersAndShiftSpawn(World w) {', async_struct + '\n    private void applyFiltersAndShiftSpawn(World w) {')

# 4. Modify startAsyncBiomeSpawnSearch to properly handle CAVE_BIOMES
# Find "final boolean isWaterBiome = WATER_BIOMES.contains(biomeName.toUpperCase());"
cave_var_code = '''        final boolean isWaterBiome = WATER_BIOMES.contains(biomeName.toUpperCase());
        final boolean isCaveBiome = CAVE_BIOMES.contains(biomeName.toUpperCase());
        final boolean isRiverLike = biomeName.equals("river") || biomeName.equals("frozen_river");'''
code = code.replace('''        final boolean isWaterBiome = WATER_BIOMES.contains(biomeName.toUpperCase());
        final boolean isRiverLike = biomeName.equals("river") || biomeName.equals("frozen_river");''', cave_var_code)

# Inside scanSpiralFragment, replace the STANDARD Y loop with cave aware logic
old_spiral_scan = '''                                // STANDARD: search Y from 58 upward
                                for (int y = 58; y <= 90; y++) {
                                    Biome b = w.getBiome(x, y, z);
                                    if (b == null || !b.key().value().equals(biomeName)) continue;

                                    Block blockHere = w.getBlockAt(x, y, z);
                                    if (blockHere.getType().isAir()) {
                                        Block below = w.getBlockAt(x, y - 1, z);
                                        if (below.getType().isSolid() && below.getType() != Material.WATER 
                                                && below.getType() != Material.LAVA) {
                                            // Verify air above too (not in solid)
                                            Block head = w.getBlockAt(x, y + 1, z);
                                            if (head.getType().isAir()) {
                                                return new Location(w, x + 0.5, y, z + 0.5);
                                            }
                                        }
                                    } else if (blockHere.getType().isSolid() && blockHere.getType() != Material.WATER) {
                                        Block above = w.getBlockAt(x, y + 1, z);
                                        Block above2 = w.getBlockAt(x, y + 2, z);
                                        if (above.getType().isAir() && above2.getType().isAir()) {
                                            return new Location(w, x + 0.5, y + 1, z + 0.5);
                                        }
                                    }
                                    break; // Only check first matching Y per column
                                }'''

new_spiral_scan = '''                                // STANDARD / CAVE:
                                int startY, endY, step;
                                if (isCaveBiome) {
                                    startY = 60;
                                    endY = w.getMinHeight() + 3;
                                    step = -1;
                                } else {
                                    startY = 58;
                                    endY = 90;
                                    step = 1;
                                }
                                
                                for (int y = startY; isCaveBiome ? (y >= endY) : (y <= endY); y += step) {
                                    Biome b = w.getBiome(x, y, z);
                                    if (b == null || !b.key().value().equals(biomeName)) continue;

                                    Block blockHere = w.getBlockAt(x, y, z);
                                    if (blockHere.getType().isAir()) {
                                        Block below = w.getBlockAt(x, y - 1, z);
                                        if (below.getType().isSolid() && below.getType() != Material.WATER 
                                                && below.getType() != Material.LAVA) {
                                            // Verify air above too (not in solid)
                                            Block head = w.getBlockAt(x, y + 1, z);
                                            if (head.getType().isAir()) {
                                                return new Location(w, x + 0.5, y, z + 0.5);
                                            }
                                        }
                                    } else if (blockHere.getType().isSolid() && blockHere.getType() != Material.WATER) {
                                        Block above = w.getBlockAt(x, y + 1, z);
                                        Block above2 = w.getBlockAt(x, y + 2, z);
                                        if (above.getType().isAir() && above2.getType().isAir()) {
                                            return new Location(w, x + 0.5, y + 1, z + 0.5);
                                        }
                                    }
                                    if (!isCaveBiome) break; // Only check first matching Y per column for surface biomes
                                }'''

code = code.replace(old_spiral_scan, new_spiral_scan)

# Finally, let's wrap scanSpiralFragment's execution with CompletableFuture chunk loading to avoid lag
# Wait, this requires refactoring `run()` method in the BukkitRunnable.
# Currently it is:
# Location result = scanSpiralFragment();
# Let's change it to pre-load chunks async.

old_run_method = '''                // Currently scanning around a biome point
                if (biomePoint != null) {
                    Location result = scanSpiralFragment();
                    if (result != null) {
                        // Found valid spawn!
                        setSpawnAndFinish(result);
                        return;
                    }
                    if (scanRadius > SCAN_RADIUS) {
                        // Exhausted this biome point — try next instance
                        getLogger().info("  No valid spawn in " + biomeName + " near " + biomePoint.toVector() + ". Trying next...");
                        biomePoint = null;
                        // Jump far away to find different instance
                        searchOffsetX += ((biomeAttempt % 2 == 0) ? 5000 : -5000);
                        searchOffsetZ += ((biomeAttempt % 3 == 0) ? 3000 : -3000);
                    }
                    return;
                }'''

new_run_method = '''                // Currently scanning around a biome point
                if (biomePoint != null) {
                    waitingForAsync = true;
                    int currentRadius = scanRadius;
                    
                    // Collect chunks to load for the current ring
                    java.util.Set<Long> requiredChunks = new java.util.HashSet<>();
                    int cx = biomePoint.getBlockX();
                    int cz = biomePoint.getBlockZ();
                    for (int dx = -currentRadius; dx <= currentRadius; dx++) {
                        for (int dz = -currentRadius; dz <= currentRadius; dz++) {
                            if (currentRadius > 0 && Math.abs(dx) != currentRadius && Math.abs(dz) != currentRadius) continue;
                            int cx_chunk = (cx + dx) >> 4;
                            int cz_chunk = (cz + dz) >> 4;
                            requiredChunks.add(((long) cx_chunk << 32) | (cz_chunk & 0xFFFFFFFFL));
                        }
                    }
                    
                    java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> futures = new java.util.ArrayList<>();
                    for (Long chunkKey : requiredChunks) {
                        futures.add(w.getChunkAtAsync((int)(chunkKey >> 32), chunkKey.intValue()));
                    }
                    
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> {
                        Bukkit.getScheduler().runTask(Main.this, () -> {
                            waitingForAsync = false;
                            Location result = scanSpiralFragment();
                            if (result != null) {
                                setSpawnAndFinish(result);
                                return;
                            }
                            if (scanRadius > SCAN_RADIUS) {
                                getLogger().info("  No valid spawn in " + biomeName + " near " + biomePoint.toVector() + ". Trying next...");
                                biomePoint = null;
                                searchOffsetX += ((biomeAttempt % 2 == 0) ? 5000 : -5000);
                                searchOffsetZ += ((biomeAttempt % 3 == 0) ? 3000 : -3000);
                            }
                        });
                    });
                    return;
                }'''

code = code.replace(old_run_method, new_run_method)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

print("Java refactored for structures, caves and chunks!")
