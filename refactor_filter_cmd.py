import sys
import re

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Update onCommand for /wr filter biome
old_biome_cmd = '''                    } else if (type.equals("biome")) {
                        if (value.equals("CLEAR")) {
                            getConfig().set("filter.biome", "");
                            saveConfig();
                            sender.sendMessage(getMsg("auto-msg-54"));
                        } else {
                            Set<String> META_BIOMES = Set.of("OCEAN_ALL", "FOREST_ALL", "MOUNTAIN_ALL", "CAVE_ALL", "DESERT_ALL", "TAIGA_ALL");
                            if (!BIOME_NAMES.contains(value) && !META_BIOMES.contains(value)) {
                                sender.sendMessage(getMsg("auto-msg-55"));
                            }
                            getConfig().set("filter.biome", value);
                            getConfig().set("filter.structure", ""); // AUTO CLEAR STRUCTURE
                            saveConfig();
                            sender.sendMessage(getMsg("filter-biome-set").replace("{biome}", value));
                        }
                    }'''

new_biome_cmd = '''                    } else if (type.equals("biome")) {
                        if (value.equals("CLEAR")) {
                            getConfig().set("filter.biome", "");
                            saveConfig();
                            sender.sendMessage(getMsg("auto-msg-54"));
                        } else {
                            String finalBiome = value;
                            if (args.length >= 4) {
                                finalBiome = args[3].toUpperCase();
                            } else {
                                switch (value) {
                                    case "OCEANS" -> finalBiome = "OCEAN_ALL";
                                    case "FORESTS" -> finalBiome = "FOREST_ALL";
                                    case "MOUNTAINS" -> finalBiome = "MOUNTAIN_ALL";
                                    case "CAVES" -> finalBiome = "CAVE_ALL";
                                    case "DESERTS" -> finalBiome = "DESERT_ALL";
                                    case "TAIGAS" -> finalBiome = "TAIGA_ALL";
                                }
                            }

                            Set<String> META_BIOMES = Set.of("OCEAN_ALL", "FOREST_ALL", "MOUNTAIN_ALL", "CAVE_ALL", "DESERT_ALL", "TAIGA_ALL");
                            if (!BIOME_NAMES.contains(finalBiome) && !META_BIOMES.contains(finalBiome)) {
                                sender.sendMessage(getMsg("auto-msg-55"));
                                return true;
                            }
                            getConfig().set("filter.biome", finalBiome);
                            getConfig().set("filter.structure", ""); // AUTO CLEAR STRUCTURE
                            saveConfig();
                            sender.sendMessage(getMsg("filter-biome-set").replace("{biome}", finalBiome));
                        }
                    }'''

if old_biome_cmd in code:
    code = code.replace(old_biome_cmd, new_biome_cmd)
else:
    print("Failed to replace onCommand logic.")
    sys.exit(1)


# 2. Update onTabComplete for args.length == 3 (filter biome)
old_tab_3 = '''                if (args[1].equalsIgnoreCase("biome")) {
                    List<String> list = new ArrayList<>(BIOME_NAMES);
                    list.add(0, "OCEAN_ALL");
                    list.add(1, "FOREST_ALL");
                    list.add(2, "MOUNTAIN_ALL");
                    list.add(3, "CAVE_ALL");
                    list.add(4, "DESERT_ALL");
                    list.add(5, "TAIGA_ALL");
                    list.add("clear");
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }'''

new_tab_3 = '''                if (args[1].equalsIgnoreCase("biome")) {
                    List<String> list = new ArrayList<>(Arrays.asList("OCEANS", "FORESTS", "MOUNTAINS", "CAVES", "DESERTS", "TAIGAS", "clear"));
                    // Also include base biomes so they can still type them if they want
                    list.addAll(BIOME_NAMES);
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }'''

if old_tab_3 in code:
    code = code.replace(old_tab_3, new_tab_3)
else:
    print("Failed to replace onTabComplete args.length == 3.")
    sys.exit(1)


# 3. Add to onTabComplete for args.length == 4
old_tab_4_start = '''        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("limbo") && args[1].equalsIgnoreCase("delay")) {'''

new_tab_4_start = '''        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("filter") && args[1].equalsIgnoreCase("biome")) {
                List<String> list = new ArrayList<>();
                switch (args[2].toUpperCase()) {
                    case "OCEANS" -> list.addAll(Arrays.asList("ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "warm_ocean"));
                    case "FORESTS" -> list.addAll(Arrays.asList("forest", "birch_forest", "dark_forest", "old_growth_birch_forest", "old_growth_spruce_taiga", "flower_forest"));
                    case "MOUNTAINS" -> list.addAll(Arrays.asList("stony_peaks", "jagged_peaks", "frozen_peaks", "meadow", "grove", "snowy_slopes", "windswept_hills"));
                    case "CAVES" -> list.addAll(Arrays.asList("dripstone_caves", "lush_caves", "deep_dark"));
                    case "DESERTS" -> list.addAll(Arrays.asList("desert", "badlands", "eroded_badlands", "wooded_badlands"));
                    case "TAIGAS" -> list.addAll(Arrays.asList("taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga"));
                }
                return StringUtil.copyPartialMatches(args[3], list, new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("limbo") && args[1].equalsIgnoreCase("delay")) {'''

if old_tab_4_start in code:
    code = code.replace(old_tab_4_start, new_tab_4_start)
else:
    print("Failed to replace onTabComplete args.length == 4.")
    sys.exit(1)


# 4. Update the help text
old_help_pl = '''§e/wr filter biome §6<§enazwa§6> §8- §7Filtr biomu'''
new_help_pl = '''§e/wr filter biome §6<§egrupa/nazwa§6> §6[§ekonkretny_biom§6] §8- §7Filtr biomu'''

old_help_en = '''§e/wr filter biome §6<§ename§6> §8- §7Set biome filter'''
new_help_en = '''§e/wr filter biome §6<§egroup/name§6> §6[§especific_biome§6] §8- §7Set biome filter'''

code = code.replace(old_help_pl, new_help_pl)
code = code.replace(old_help_en, new_help_en)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

print("Main.java refactored successfully for /wr filter biome autocomplete and execution!")
