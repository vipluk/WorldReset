import re

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Replace findSafeSpawn with startAsyncSafeSpawnSearch
old_find_safe_spawn = '''    private void findSafeSpawn(World w) {
        Location spawn = w.getSpawnLocation();
        waterSpawnActive = false;
        boatGivenPlayers.clear();

        // Step 1: Try immediate area (32 block radius) for safe ground
        Location safe = getSafeLocation(spawn);
        if (safe != null) {
            w.setSpawnLocation(safe);
            getLogger().info("Safe spawn found nearby at: " + safe.toVector());
            w.setGameRule(GameRule.SPAWN_RADIUS, 0);
            return;
        }

        // Step 2: Spiral search for solid land in wider radius (up to 100 blocks)
        Location nearby = findLandNear(w, spawn, 100);
        if (nearby != null) {
            w.setSpawnLocation(nearby);
            getLogger().info("Safe spawn found via spiral search at: " + nearby.toVector());
            w.setGameRule(GameRule.SPAWN_RADIUS, 0);
            return;
        }

        // Step 3: Use locateNearestBiome to find land biomes (BEACH, STONY_SHORE, PLAINS, FOREST)
        // Much faster than block-by-block — queries the biome noise map directly
        Location landLoc = findLandViaBiomeSearch(w, spawn, 10000);
        if (landLoc != null) {
            Location safeLand = getSafeLocation(landLoc);
            if (safeLand != null) {
                w.setSpawnLocation(safeLand);
                getLogger().info("Safe spawn found via biome search at: " + safeLand.toVector() + " (" + (int) safeLand.distance(spawn) + " blocks from target)");
                w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                return;
            }
            // Biome found but exact spot not safe — try wider spiral around it
            Location nearBiome = findLandNear(w, landLoc, 64);
            if (nearBiome != null) {
                w.setSpawnLocation(nearBiome);
                getLogger().info("Safe spawn found near biome hit at: " + nearBiome.toVector());
                w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                return;
            }
        }

        // Step 4: Fallback — no land found. Spawn on water, give boat
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int y = w.getHighestBlockYAt(x, z);
        w.setSpawnLocation(new Location(w, x + 0.5, y + 1, z + 0.5));
        getLogger().info("No land found via biome search. Spawning on water (boat will be given).");
        waterSpawnActive = true;
        boatGivenPlayers.clear();
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        getLogger().info("Spawn finalized at: " + w.getSpawnLocation().toVector());
    }'''

new_find_safe_spawn = '''    private void startAsyncSafeSpawnSearch(World w, Runnable onComplete) {
        Location spawn = w.getSpawnLocation();
        waterSpawnActive = false;
        boatGivenPlayers.clear();

        int cx = spawn.getBlockX() >> 4;
        int cz = spawn.getBlockZ() >> 4;
        
        java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> initialFutures = new java.util.ArrayList<>();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                initialFutures.add(w.getChunkAtAsync(cx + dx, cz + dz));
            }
        }

        java.util.concurrent.CompletableFuture.allOf(initialFutures.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> {
            Bukkit.getScheduler().runTask(Main.this, () -> {
                Location safe = getSafeLocation(spawn);
                if (safe != null) {
                    w.setSpawnLocation(safe);
                    w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                    getLogger().info("Safe spawn found nearby at: " + safe.toVector());
                    onComplete.run();
                    return;
                }

                Location nearby = findLandNear(w, spawn, 100);
                if (nearby != null) {
                    w.setSpawnLocation(nearby);
                    w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                    getLogger().info("Safe spawn found via spiral search at: " + nearby.toVector());
                    onComplete.run();
                    return;
                }

                java.util.concurrent.CompletableFuture.supplyAsync(() -> findLandViaBiomeSearch(w, spawn, 10000)).thenAccept(landLoc -> {
                    Bukkit.getScheduler().runTask(Main.this, () -> {
                        if (landLoc != null) {
                            int bcx = landLoc.getBlockX() >> 4;
                            int bcz = landLoc.getBlockZ() >> 4;
                            java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> bFutures = new java.util.ArrayList<>();
                            for (int dx = -4; dx <= 4; dx++) {
                                for (int dz = -4; dz <= 4; dz++) {
                                    bFutures.add(w.getChunkAtAsync(bcx + dx, bcz + dz));
                                }
                            }
                            java.util.concurrent.CompletableFuture.allOf(bFutures.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> {
                                Bukkit.getScheduler().runTask(Main.this, () -> {
                                    Location safeLand = getSafeLocation(landLoc);
                                    if (safeLand != null) {
                                        w.setSpawnLocation(safeLand);
                                        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                                        getLogger().info("Safe spawn found via biome search at: " + safeLand.toVector());
                                        onComplete.run();
                                        return;
                                    }
                                    
                                    Location nearBiome = findLandNear(w, landLoc, 64);
                                    if (nearBiome != null) {
                                        w.setSpawnLocation(nearBiome);
                                        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                                        getLogger().info("Safe spawn found near biome hit at: " + nearBiome.toVector());
                                        onComplete.run();
                                        return;
                                    }
                                    
                                    finalizeSafeSpawnFallback(w, spawn);
                                    onComplete.run();
                                });
                            });
                        } else {
                            finalizeSafeSpawnFallback(w, spawn);
                            onComplete.run();
                        }
                    });
                });
            });
        });
    }

    private void finalizeSafeSpawnFallback(World w, Location spawn) {
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int y = w.getHighestBlockYAt(x, z);
        w.setSpawnLocation(new Location(w, x + 0.5, y + 1, z + 0.5));
        getLogger().info("No land found via biome search. Spawning on water (boat will be given).");
        waterSpawnActive = true;
        boatGivenPlayers.clear();
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        getLogger().info("Spawn finalized at: " + w.getSpawnLocation().toVector());
    }'''

code = code.replace(old_find_safe_spawn, new_find_safe_spawn)


# 2. Add finishResetProcess helper method 
# Inject it right before resetWorld (around line 980)

finish_method = '''
    private void finishResetProcess(World w, boolean useDelayOut) {
        if (!skipFindSafeSpawn) {
            startAsyncSafeSpawnSearch(w, () -> {
                preGenerateSpawnChunks(w, w.getSpawnLocation());
                broadcastInfo(getMsg("generation-complete"));
                isGameReady = true;
                finalizeGameStart(useDelayOut);
            });
        } else {
            preGenerateSpawnChunks(w, w.getSpawnLocation());
            broadcastInfo(getMsg("generation-complete"));
            isGameReady = true;
            finalizeGameStart(useDelayOut);
        }
    }
'''

code = code.replace("private void resetWorld(String seed) {", finish_method + "\n    private void resetWorld(String seed) {")

# 3. Update resetWorld usages
# Usage 1: startAsyncBiomeSpawnSearch
old_biome_usage = '''                        startAsyncBiomeSpawnSearch(fw, biomeReq.toLowerCase(), () -> {
                            if (!skipFindSafeSpawn) {
                                findSafeSpawn(fw);
                            }
                            preGenerateSpawnChunks(fw, fw.getSpawnLocation());
                            broadcastInfo(getMsg("generation-complete"));
                            isGameReady = true;
                            finalizeGameStart(fUseDelayOut);
                        });'''
new_biome_usage = '''                        startAsyncBiomeSpawnSearch(fw, biomeReq.toLowerCase(), () -> finishResetProcess(fw, fUseDelayOut));'''
code = code.replace(old_biome_usage, new_biome_usage)


# Usage 2: startAsyncStructureSpawnSearch
old_struct_usage = '''                        startAsyncStructureSpawnSearch(normal, structReq, () -> {
                            if (!skipFindSafeSpawn) {
                                findSafeSpawn(normal);
                            }
                            preGenerateSpawnChunks(normal, normal.getSpawnLocation());
                            broadcastInfo(getMsg("generation-complete"));
                            isGameReady = true;
                            finalizeGameStart(useDelayOut);
                        });'''
new_struct_usage = '''                        startAsyncStructureSpawnSearch(normal, structReq, () -> finishResetProcess(normal, useDelayOut));'''
code = code.replace(old_struct_usage, new_struct_usage)


# Usage 3: Normal fallback path
old_normal_usage = '''                    } else {
                        applyFiltersAndShiftSpawn(normal);
                        if (!skipFindSafeSpawn) {
                            findSafeSpawn(normal);
                        }
                    }
                }
            }
            // Pre-generate 5x5 chunk grid around spawn asynchronously
            preGenerateSpawnChunks(normal, normal.getSpawnLocation());
        }

        broadcastInfo(getMsg("generation-complete"));
        isGameReady = true;
        finalizeGameStart(useDelayOut);'''

new_normal_usage = '''                    } else {
                        applyFiltersAndShiftSpawn(normal);
                        finishResetProcess(normal, useDelayOut);
                    }
                }
            } else {
                broadcastInfo(getMsg("generation-complete"));
                isGameReady = true;
                finalizeGameStart(useDelayOut);
            }
        }'''

code = code.replace(old_normal_usage, new_normal_usage)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

print("Java refactored for findSafeSpawn!")
