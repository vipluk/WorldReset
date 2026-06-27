import sys
import re

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Replace resolveMetaBiome with resolveMetaBiomes
old_resolve = '''    private String resolveMetaBiome(String biome) {
        List<String> options = switch (biome) {
            case "OCEAN_ALL" -> List.of("OCEAN", "DEEP_OCEAN", "COLD_OCEAN", "DEEP_COLD_OCEAN", "FROZEN_OCEAN", "DEEP_FROZEN_OCEAN", "LUKEWARM_OCEAN", "DEEP_LUKEWARM_OCEAN", "WARM_OCEAN");
            case "FOREST_ALL" -> List.of("FOREST", "BIRCH_FOREST", "DARK_FOREST", "OLD_GROWTH_BIRCH_FOREST", "OLD_GROWTH_SPRUCE_TAIGA", "FLOWER_FOREST");
            case "MOUNTAIN_ALL" -> List.of("STONY_PEAKS", "JAGGED_PEAKS", "FROZEN_PEAKS", "MEADOW", "GROVE", "SNOWY_SLOPES", "WINDSWEPT_HILLS");
            case "CAVE_ALL" -> List.of("DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK");
            case "DESERT_ALL" -> List.of("DESERT", "BADLANDS", "ERODED_BADLANDS", "WOODED_BADLANDS");
            case "TAIGA_ALL" -> List.of("TAIGA", "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA", "SNOWY_TAIGA");
            default -> null;
        };
        if (options == null) return biome;
        String selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        getLogger().info("Meta-biome " + biome + " resolved to: " + selected);
        return selected;
    }'''

new_resolve = '''    private java.util.List<String> resolveMetaBiomes(String biome) {
        if (biome == null || biome.isEmpty()) return java.util.List.of();
        java.util.List<String> options = switch (biome) {
            case "OCEAN_ALL" -> java.util.List.of("OCEAN", "DEEP_OCEAN", "COLD_OCEAN", "DEEP_COLD_OCEAN", "FROZEN_OCEAN", "DEEP_FROZEN_OCEAN", "LUKEWARM_OCEAN", "DEEP_LUKEWARM_OCEAN", "WARM_OCEAN");
            case "FOREST_ALL" -> java.util.List.of("FOREST", "BIRCH_FOREST", "DARK_FOREST", "OLD_GROWTH_BIRCH_FOREST", "OLD_GROWTH_SPRUCE_TAIGA", "FLOWER_FOREST");
            case "MOUNTAIN_ALL" -> java.util.List.of("STONY_PEAKS", "JAGGED_PEAKS", "FROZEN_PEAKS", "MEADOW", "GROVE", "SNOWY_SLOPES", "WINDSWEPT_HILLS");
            case "CAVE_ALL" -> java.util.List.of("DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK");
            case "DESERT_ALL" -> java.util.List.of("DESERT", "BADLANDS", "ERODED_BADLANDS", "WOODED_BADLANDS");
            case "TAIGA_ALL" -> java.util.List.of("TAIGA", "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA", "SNOWY_TAIGA");
            default -> null;
        };
        if (options == null) return java.util.List.of(biome);
        getLogger().info("Meta-biome " + biome + " resolved to " + options.size() + " biomes: " + options);
        return options;
    }'''

code = code.replace(old_resolve, new_resolve)

# 2. Update generateGameWorldsInternal to use List<String>
old_gen_call_1 = '''                // Resolve meta-biomes (virtual groups) to a random specific biome
                biomeReq = resolveMetaBiome(biomeReq);
                
                // Async biome search for ALL biome filters (spreads work across ticks)
                if (getConfig().getBoolean("filter.enabled", true) && !biomeReq.isEmpty() && structReq.isEmpty()) {
                    int attempts = getConfig().getInt("filter.attempts", 5);
                    if (attempts <= 0) {
                        // 0 attempts = find biome, basic land check (few blocks), fallback water+boat
                        Biome targetBiome = Registry.BIOME.get(NamespacedKey.minecraft(biomeReq.toLowerCase()));
                        if (targetBiome != null) {
                            BiomeSearchResult result = normal.locateNearestBiome(new Location(normal, 0, 62, 0), 2500, targetBiome);'''

new_gen_call_1 = '''                // Resolve meta-biomes (virtual groups) to all target biomes
                java.util.List<String> biomeReqs = resolveMetaBiomes(biomeReq);
                
                // Async biome search for ALL biome filters (spreads work across ticks)
                if (getConfig().getBoolean("filter.enabled", true) && !biomeReqs.isEmpty() && structReq.isEmpty()) {
                    int attempts = getConfig().getInt("filter.attempts", 5);
                    if (attempts <= 0) {
                        // 0 attempts = find biome, basic land check (few blocks), fallback water+boat
                        java.util.List<Biome> targetBiomes = new java.util.ArrayList<>();
                        for (String bReq : biomeReqs) {
                            Biome b = Registry.BIOME.get(NamespacedKey.minecraft(bReq.toLowerCase()));
                            if (b != null) targetBiomes.add(b);
                        }
                        if (!targetBiomes.isEmpty()) {
                            org.bukkit.util.BiomeSearchResult result = normal.locateNearestBiome(new Location(normal, 0, 62, 0), 2500, targetBiomes.toArray(new Biome[0]));'''

code = code.replace(old_gen_call_1, new_gen_call_1)

old_gen_call_2 = '''                                                    // Verify biome at spawn position
                                                    Biome spawnBiome = normal.getBiome(x, y + 1, z);
                                                    if (spawnBiome != null && spawnBiome.key().value().equals(reqBiomeLower)) {
                                                        landSpot = new Location(normal, x + 0.5, y + 1, z + 0.5);
                                                    }'''

new_gen_call_2 = '''                                                    // Verify biome at spawn position
                                                    Biome spawnBiome = normal.getBiome(x, y + 1, z);
                                                    if (spawnBiome != null) {
                                                        for (String bReq : biomeReqs) {
                                                            if (spawnBiome.key().value().equalsIgnoreCase(bReq)) {
                                                                landSpot = new Location(normal, x + 0.5, y + 1, z + 0.5);
                                                                break;
                                                            }
                                                        }
                                                    }'''
code = code.replace(old_gen_call_2, new_gen_call_2)

old_gen_call_3 = '''                    } else {
                        final World fw = normal;
                        final boolean fUseDelayOut = useDelayOut;
                        broadcastInfo(getMsg("auto-msg-1"));
                        startAsyncBiomeSpawnSearch(fw, biomeReq.toLowerCase(), () -> finishResetProcess(fw, fUseDelayOut));
                        return; // Async — rest handled in callback
                    }'''

new_gen_call_3 = '''                    } else {
                        final World fw = normal;
                        final boolean fUseDelayOut = useDelayOut;
                        broadcastInfo(getMsg("auto-msg-1"));
                        startAsyncBiomeSpawnSearch(fw, biomeReqs, () -> finishResetProcess(fw, fUseDelayOut));
                        return; // Async — rest handled in callback
                    }'''

code = code.replace(old_gen_call_3, new_gen_call_3)

# 3. Completely replace startAsyncBiomeSpawnSearch

match = re.search(r'    private void startAsyncBiomeSpawnSearch\(World w, String biomeName, Runnable onComplete\) \{.*?\n    \}\n', code, re.DOTALL)
if match:
    old_method = match.group(0)
    print("Found old startAsyncBiomeSpawnSearch!")
else:
    print("Could not find startAsyncBiomeSpawnSearch!")
    sys.exit(1)

new_method = '''    private <T> java.util.concurrent.CompletableFuture<T> anyOfNonNull(java.util.List<java.util.concurrent.CompletableFuture<T>> futures) {
        java.util.concurrent.CompletableFuture<T> result = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger finished = new java.util.concurrent.atomic.AtomicInteger(0);
        if (futures.isEmpty()) {
            result.complete(null);
            return result;
        }
        for (java.util.concurrent.CompletableFuture<T> f : futures) {
            f.thenAccept(val -> {
                if (val != null) {
                    result.complete(val);
                } else {
                    if (finished.incrementAndGet() == futures.size()) {
                        result.complete(null);
                    }
                }
            }).exceptionally(ex -> {
                if (finished.incrementAndGet() == futures.size()) {
                    result.complete(null);
                }
                return null;
            });
        }
        return result;
    }

    private void startAsyncBiomeSpawnSearch(World w, java.util.List<String> biomeNames, Runnable onComplete) {
        java.util.List<Biome> targetBiomesList = new java.util.ArrayList<>();
        boolean isWaterBiomeTemp = false;
        boolean isCaveBiomeTemp = false;
        boolean isRiverLikeTemp = false;

        for (String bName : biomeNames) {
            Biome b = Registry.BIOME.get(NamespacedKey.minecraft(bName.toLowerCase()));
            if (b != null) targetBiomesList.add(b);
            if (WATER_BIOMES.contains(bName.toUpperCase())) isWaterBiomeTemp = true;
            if (CAVE_BIOMES.contains(bName.toUpperCase())) isCaveBiomeTemp = true;
            if (bName.equalsIgnoreCase("river") || bName.equalsIgnoreCase("frozen_river")) isRiverLikeTemp = true;
        }

        if (targetBiomesList.isEmpty()) {
            getLogger().warning("No valid biomes found in registry for: " + biomeNames);
            broadcastInfo(getMsg("filter-failed"));
            onComplete.run();
            return;
        }

        final Biome[] targetBiomes = targetBiomesList.toArray(new Biome[0]);
        final boolean isWaterBiome = isWaterBiomeTemp;
        final boolean isCaveBiome = isCaveBiomeTemp;
        final boolean isRiverLike = isRiverLikeTemp;
        final int MAX_BIOME_ATTEMPTS = Math.max(1, getConfig().getInt("filter.attempts", 5));
        final int SCAN_RADIUS_CHUNKS = isRiverLike ? 60 : 30; // Max chunks radius

        new BukkitRunnable() {
            int biomeAttempt = 0;
            int searchOffsetX = 0;
            int searchOffsetZ = 0;
            
            // Chunk scanning state
            Location biomePoint = null;
            int chunkRadius = 0;
            boolean waitingForAsync = false;

            @Override
            public void run() {
                if (waitingForAsync) return;

                if (biomePoint != null) {
                    waitingForAsync = true;
                    int currentChunkRadius = chunkRadius;
                    
                    // Collect chunks for the current ring
                    java.util.Set<Long> requiredChunks = new java.util.HashSet<>();
                    int cx = biomePoint.getBlockX() >> 4;
                    int cz = biomePoint.getBlockZ() >> 4;
                    for (int dx = -currentChunkRadius; dx <= currentChunkRadius; dx++) {
                        for (int dz = -currentChunkRadius; dz <= currentChunkRadius; dz++) {
                            if (currentChunkRadius > 0 && Math.abs(dx) != currentChunkRadius && Math.abs(dz) != currentChunkRadius) continue;
                            requiredChunks.add(((long) (cx + dx) << 32) | ((cz + dz) & 0xFFFFFFFFL));
                        }
                    }
                    
                    java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> futures = new java.util.ArrayList<>();
                    for (Long chunkKey : requiredChunks) {
                        futures.add(w.getChunkAtAsync((int)(chunkKey >> 32), chunkKey.intValue()));
                    }
                    
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> {
                        Bukkit.getScheduler().runTask(Main.this, () -> {
                            // Safely capture snapshots on main thread
                            java.util.Map<Long, org.bukkit.ChunkSnapshot> snapshots = new java.util.HashMap<>();
                            for (Long chunkKey : requiredChunks) {
                                org.bukkit.Chunk chunk = w.getChunkAt((int)(chunkKey >> 32), chunkKey.intValue());
                                snapshots.put(chunkKey, chunk.getChunkSnapshot(true, true, false));
                            }
                            
                            // Offload processing to async thread
                            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                                return scanChunkSnapshots(snapshots, targetBiomes, isRiverLike, isCaveBiome, w.getMinHeight());
                            }).thenAccept(resultLoc -> {
                                Bukkit.getScheduler().runTask(Main.this, () -> {
                                    waitingForAsync = false;
                                    if (resultLoc != null) {
                                        setSpawnAndFinish(resultLoc);
                                        return;
                                    }
                                    
                                    chunkRadius++;
                                    if (chunkRadius > SCAN_RADIUS_CHUNKS) {
                                        getLogger().info("  No valid spawn near " + biomePoint.toVector() + ". Trying next...");
                                        biomePoint = null;
                                        searchOffsetX += ((biomeAttempt % 2 == 0) ? 6000 : -6000);
                                        searchOffsetZ += ((biomeAttempt % 3 == 0) ? 4000 : -4000);
                                    }
                                });
                            });
                        });
                    });
                    return;
                }

                if (biomeAttempt >= MAX_BIOME_ATTEMPTS) {
                    if (isWaterBiome) {
                        waitingForAsync = true;
                        java.util.concurrent.CompletableFuture.supplyAsync(() -> w.locateNearestBiome(new Location(w, 0, 62, 0), 2000, targetBiomes)).thenAccept(fallback -> {
                            Bukkit.getScheduler().runTask(Main.this, () -> {
                                waitingForAsync = false;
                                if (fallback != null) {
                                    Location waterLoc = new Location(w, fallback.getLocation().getBlockX() + 0.5, 63, fallback.getLocation().getBlockZ() + 0.5);
                                    w.setSpawnLocation(waterLoc);
                                    waterSpawnActive = true;
                                    boatGivenPlayers.clear();
                                    broadcastInfo(getMsg("filter-shifted").replace("{target}", biomeNames.get(0).toUpperCase() + " (water)"));
                                } else {
                                    broadcastInfo(getMsg("filter-failed"));
                                }
                                skipFindSafeSpawn = true;
                                cancel();
                                onComplete.run();
                            });
                        });
                    } else {
                        broadcastInfo(getMsg("filter-failed"));
                        skipFindSafeSpawn = true;
                        cancel();
                        onComplete.run();
                    }
                    return;
                }

                biomeAttempt++;
                getLogger().info("Biome search " + biomeAttempt + "/" + MAX_BIOME_ATTEMPTS + " for " + biomeNames + " around " + searchOffsetX + "," + searchOffsetZ);
                waitingForAsync = true;
                
                // Launch multi-directional search (5 parallel threads: center + 4 quadrants)
                int dist = 3000;
                Location[] searchPoints = new Location[]{
                    new Location(w, searchOffsetX, 62, searchOffsetZ),
                    new Location(w, searchOffsetX + dist, 62, searchOffsetZ + dist),
                    new Location(w, searchOffsetX - dist, 62, searchOffsetZ + dist),
                    new Location(w, searchOffsetX + dist, 62, searchOffsetZ - dist),
                    new Location(w, searchOffsetX - dist, 62, searchOffsetZ - dist)
                };

                java.util.List<java.util.concurrent.CompletableFuture<org.bukkit.util.BiomeSearchResult>> searchFutures = new java.util.ArrayList<>();
                for (Location loc : searchPoints) {
                    searchFutures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> w.locateNearestBiome(loc, 5000, targetBiomes)));
                }

                anyOfNonNull(searchFutures).thenAccept(found -> {
                    Bukkit.getScheduler().runTask(Main.this, () -> {
                        waitingForAsync = false;
                        if (found == null) {
                            searchOffsetX += 5000;
                            searchOffsetZ += 3000;
                            return;
                        }
                        biomePoint = found.getLocation();
                        biomePoint.setY(62);
                        chunkRadius = 0;
                        getLogger().info("  Found matching biome at " + biomePoint.toVector() + ". Starting fast parallel chunk scan...");
                    });
                });
            }

            private Location scanChunkSnapshots(java.util.Map<Long, org.bukkit.ChunkSnapshot> snapshots, Biome[] targetBiomes, boolean isRiverLike, boolean isCaveBiome, int minHeight) {
                for (java.util.Map.Entry<Long, org.bukkit.ChunkSnapshot> entry : snapshots.entrySet()) {
                    long key = entry.getKey();
                    int cx = (int)(key >> 32);
                    int cz = (int)key;
                    org.bukkit.ChunkSnapshot snap = entry.getValue();

                    for (int rx = 0; rx < 16; rx++) {
                        for (int rz = 0; rz < 16; rz++) {
                            // Quick biome check
                            Biome quickCheck = snap.getBiome(rx, 62, rz);
                            boolean match = false;
                            for (Biome b : targetBiomes) {
                                if (b.equals(quickCheck)) { match = true; break; }
                            }
                            if (!match && !isCaveBiome) continue; // For caves we check per-Y later

                            int globalX = (cx << 4) + rx;
                            int globalZ = (cz << 4) + rz;

                            if (isRiverLike) {
                                Material ground = snap.getBlockType(rx, 62, rz);
                                if (ground.isSolid() && ground != Material.WATER && ground != Material.LAVA) {
                                    if (snap.getBlockType(rx, 63, rz).isAir() && snap.getBlockType(rx, 64, rz).isAir()) {
                                        return new Location(w, globalX + 0.5, 63, globalZ + 0.5);
                                    }
                                }
                            } else {
                                int startY = isCaveBiome ? 60 : 58;
                                int endY = isCaveBiome ? minHeight + 3 : 90;
                                int step = isCaveBiome ? -1 : 1;
                                
                                for (int y = startY; isCaveBiome ? (y >= endY) : (y <= endY); y += step) {
                                    if (isCaveBiome) {
                                        Biome b = snap.getBiome(rx, y, rz);
                                        boolean yMatch = false;
                                        for (Biome tb : targetBiomes) {
                                            if (tb.equals(b)) { yMatch = true; break; }
                                        }
                                        if (!yMatch) continue;
                                    }

                                    Material blockHere = snap.getBlockType(rx, y, rz);
                                    if (blockHere.isAir()) {
                                        Material below = snap.getBlockType(rx, y - 1, rz);
                                        if (below.isSolid() && below != Material.WATER && below != Material.LAVA) {
                                            if (snap.getBlockType(rx, y + 1, rz).isAir()) {
                                                return new Location(w, globalX + 0.5, y, globalZ + 0.5);
                                            }
                                        }
                                    } else if (blockHere.isSolid() && blockHere != Material.WATER) {
                                        if (snap.getBlockType(rx, y + 1, rz).isAir() && snap.getBlockType(rx, y + 2, rz).isAir()) {
                                            return new Location(w, globalX + 0.5, y + 1, globalZ + 0.5);
                                        }
                                    }
                                    if (!isCaveBiome) break; // surface biome: only first hit per column
                                }
                            }
                        }
                    }
                }
                return null;
            }

            private void setSpawnAndFinish(Location loc) {
                // Safety: ensure spawn is not inside a solid block
                Block atSpawn = w.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if (atSpawn.getType().isSolid()) {
                    for (int y = loc.getBlockY(); y < loc.getBlockY() + 10; y++) {
                        if (w.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isAir()) {
                            loc.setY(y);
                            break;
                        }
                    }
                }
                w.setSpawnLocation(loc);
                w.setGameRule(GameRule.SPAWN_RADIUS, 0);
                skipFindSafeSpawn = true;
                broadcastInfo(getMsg("filter-shifted").replace("{target}", biomeNames.get(0).toUpperCase()));
                getLogger().info("Spawn set to matching biome at " + loc.toVector());
                cancel();
                onComplete.run();
            }
        }.runTaskTimer(this, 1L, 2L);
    }
'''

code = code.replace(old_method, new_method)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

print("Main.java refactored successfully for multi-biome and multi-core!")
