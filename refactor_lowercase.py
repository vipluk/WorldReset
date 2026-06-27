import sys

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

old_tab_3 = '''                if (args[1].equalsIgnoreCase("biome")) {
                    List<String> list = new ArrayList<>(Arrays.asList("OCEANS", "FORESTS", "MOUNTAINS", "CAVES", "DESERTS", "TAIGAS", "clear"));
                    // Also include base biomes so they can still type them if they want
                    list.addAll(BIOME_NAMES);
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }'''

new_tab_3 = '''                if (args[1].equalsIgnoreCase("biome")) {
                    List<String> list = new ArrayList<>(Arrays.asList("oceans", "forests", "mountains", "caves", "deserts", "taigas", "clear"));
                    // Also include base biomes so they can still type them if they want
                    list.addAll(BIOME_NAMES);
                    return StringUtil.copyPartialMatches(args[2], list, new ArrayList<>());
                }'''

if old_tab_3 in code:
    code = code.replace(old_tab_3, new_tab_3)
    with open(JAVA_FILE, "w", encoding="utf-8") as f:
        f.write(code)
    print("Main.java refactored successfully for lowercase tab completions!")
else:
    print("Failed to replace onTabComplete args.length == 3 for lowercase.")
    sys.exit(1)
