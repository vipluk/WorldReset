# Plan Architektoniczny i Wdrożeniowy: Datapack Injection System
**Projekt:** WorldReset v2.0  
**Środowisko:** Java 21+ / Java 25 (Paper / Purpur 1.21 - 26.x)  
**Status:** Przygotowany do wdrożenia  

---

## 1. Geneza Problemu i Diagnoza Błędu

Podczas testów na serwerze (`Purpur 26.3`) stwierdzono, że mimo włączenia opcji `datapacks.enabled: true` oraz umieszczenia paczki `MasterCutter+v1.8.1-mc26.3.zip` w folderze wtyczki, po wykonaniu komendy `/wr reset` datapack nie działa (nie jest widoczny pod `/datapack list` i receptury w Stonecutterze nie są aktywne).

### Przyczyny techniczne:
1. **Globalność rejestru `PackRepository` w silniku Minecraft:**
   * W silniku Minecraft Dedicated Server (zarówno Vanilla, Spigot, jak i Paper) datapacki **nie są ładowane niezależnie dla światów podrzędnych**.
   * Rejestr paczek (`PackRepository`), menedżer receptur (`RecipeManager`), drzewo osiągnięć (`AdvancementTree`) oraz funkcje są wspólne dla całego serwera i wczytywane **wyłącznie z folderu głównego świata startowego** (`<server-root>/world/datapacks/`).
2. **Błędna/osierocona ścieżka docelowa:**
   * Kod w `Main.java` kopiował pliki wyłącznie do `new File(worldContainer, "game_world/datapacks")`.
   * Silnik serwera nigdy nie zagląda do katalogów dynamicznie tworzonych światów w poszukiwaniu datapacków. Ponadto w nowszych wersjach Paper/Purpur świat `game_world` znajduje się w hierarchii wymiarów (`world/dimensions/minecraft/game_world`), więc folder `game_world/` w katalogu głównym był całkowicie ignorowany.
3. **Brak odświeżenia rejestrów zasobów w locie:**
   * Minecraft w trakcie pracy serwera nie wykrywa automatycznie nowych plików wrzuconych do folderu `datapacks/`.
   * Konieczne jest wywołanie procedury przeładowania (`Bukkit.reloadData()`), która uruchamia `ReloadCommand.reload()`, wykrywa nowo dodane archiwa `.zip` i kompiluje receptury dla graczy.

---

## 2. Analiza Różnic Między Systemami (Minecraft 1.21 vs 26.x)

Wdrożenie zostało zaprojektowane tak, aby obsłużyć oba systemy i zachować 100% kompatybilności wstecznej oraz w przód:

| Aspekt | Wersje 1.20 / 1.21 (Spigot/Paper) | Wersja 26.x (Nowoczesny Paper/Purpur) | Rozwiązanie w WorldReset |
| :--- | :--- | :--- | :--- |
| **Struktura katalogów światów** | Płaski katalog w root (`server/game_world`) | Struktura wymiarów (`world/dimensions/minecraft/game_world`) | **Dual-Target Pathing**: Kopiowanie do obu ścieżek naraz dla pełnej spójności kopii zapasowych. |
| **Lokalizacja live datapacków** | Główny świat serwera (`<mainWorld>/datapacks/`) | Główny świat serwera (`<mainWorld>/datapacks/`) | Dynamiczne pobranie katalogu świata zerowego: `Bukkit.getWorlds().getFirst().getWorldFolder()`. |
| **Mechanizm odświeżania** | `Bukkit.reloadData()` | `Bukkit.reloadData()` + `DatapackManager` | Wykorzystanie natywnego `Bukkit.reloadData()` z opcjonalnym `refreshPacks()` przez detekcję API Paper. |
| **Format paczek (`pack_format`)** | Format 48 / 57 | Nowy format wersji 26 | Walidacja natywna silnika podczas `PackRepository.reload()`. |

---

## 3. Szczegółowa Architektura Wdrożenia

### A. Komponenty i Pliki do Modyfikacji
* [`src/main/java/org/example/worldreset/Main.java`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java):
  * Dodanie metod pomocniczych do zarządzania folderami świata głównego i historią wstrzyknięć.
  * Przebudowa metody `injectDatapacksToWorld(String targetWorldName)`.
  * Integracja z cyklem startowym `onEnable()` oraz komendą `/wr datapacks`.
* [`src/main/resources/messages_pl.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_pl.yml) (oraz `messages_en.yml`, `messages_de.yml`):
  * Opcjonalne rozszerzenie komunikatów statusu o synchronizację.

---

### B. Nowe Metody i Sygnatury w `Main.java`

#### 1. Dynamiczne pobieranie folderu datapacków świata głównego
```java
private File getPrimaryWorldDatapacksFolder() {
    if (!Bukkit.getWorlds().isEmpty()) {
        return new File(Bukkit.getWorlds().getFirst().getWorldFolder(), "datapacks");
    }
    // Fallback dla wczesnego startu
    String mainName = Bukkit.getServer().getWorldContainer().getName();
    return new File(Bukkit.getWorldContainer(), "world/datapacks");
}
```

#### 2. Śledzenie wstrzykniętych paczek (Clean Un-injection)
Aby nie usuwać prywatnych datapacków użytkownika umieszczonych ręcznie w `world/datapacks/`, wtyczka zapisuje historię własnych wstrzyknięć w pliku `.injected_datapacks.txt`:
```java
private File getInjectedHistoryFile() {
    return new File(getDataFolder(), ".injected_datapacks.txt");
}

private Set<String> getInjectedDatapacksHistory() {
    Set<String> set = new HashSet<>();
    File file = getInjectedHistoryFile();
    if (!file.exists()) return set;
    try {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
    } catch (IOException e) {
        getLogger().warning("Could not read injected datapacks history: " + e.getMessage());
    }
    return set;
}

private void saveInjectedDatapacksHistory(Set<String> injectedNames) {
    File file = getInjectedHistoryFile();
    try {
        Files.write(file.toPath(), injectedNames, StandardCharsets.UTF_8);
    } catch (IOException e) {
        getLogger().warning("Could not save injected datapacks history: " + e.getMessage());
    }
}
```

#### 3. Kopiowanie i bezpieczne czyszczenie plików
```java
private void copyFileOrDirectory(File source, File destination) throws IOException {
    if (source.isDirectory()) {
        if (destination.exists()) {
            deleteDirectoryRecursive(destination);
        }
        copyDirectory(source.toPath(), destination.toPath());
    } else {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
```

#### 4. Główna procedura wstrzykiwania i synchronizacji: `injectDatapacksToWorld`
```java
private int injectDatapacksToWorld(String targetWorldName) {
    File primaryDatapacks = getPrimaryWorldDatapacksFolder();
    boolean isEnabled = getConfig().getBoolean("datapacks.enabled", false);
    Set<String> previousInjected = getInjectedDatapacksHistory();

    // Scenariusz A: Wstrzykiwanie wyłączone
    if (!isEnabled) {
        if (!previousInjected.isEmpty()) {
            for (String oldPack : previousInjected) {
                File target = new File(primaryDatapacks, oldPack);
                if (target.exists()) deleteDirectoryRecursive(target);
            }
            saveInjectedDatapacksHistory(Collections.emptySet());
            try {
                Bukkit.reloadData();
                getLogger().info("WorldReset cleaned up previously injected datapacks from primary world.");
            } catch (Exception e) {
                getLogger().warning("Failed to reload data packs after cleanup: " + e.getMessage());
            }
        }
        return 0;
    }

    // Scenariusz B: Wstrzykiwanie włączone
    List<File> sourcePacks = getAvailableDatapacks();
    if (sourcePacks.isEmpty()) {
        if (!previousInjected.isEmpty()) {
            for (String oldPack : previousInjected) {
                File target = new File(primaryDatapacks, oldPack);
                if (target.exists()) deleteDirectoryRecursive(target);
            }
            saveInjectedDatapacksHistory(Collections.emptySet());
            try { Bukkit.reloadData(); } catch (Exception ignored) {}
        }
        return 0;
    }

    if (!primaryDatapacks.exists()) {
        primaryDatapacks.mkdirs();
    }

    // 1. Usuń paczki usunięte z folderu wtyczki od ostatniego resetu
    Set<String> currentPackNames = new HashSet<>();
    for (File f : sourcePacks) currentPackNames.add(f.getName());

    for (String oldPack : previousInjected) {
        if (!currentPackNames.contains(oldPack)) {
            File toDelete = new File(primaryDatapacks, oldPack);
            if (toDelete.exists()) deleteDirectoryRecursive(toDelete);
        }
    }

    // 2. Kopiuj paczki do głównego świata serwera (Live Execution)
    int copiedCount = 0;
    Set<String> newlyInjected = new HashSet<>();
    for (File dp : sourcePacks) {
        try {
            File targetFile = new File(primaryDatapacks, dp.getName());
            copyFileOrDirectory(dp, targetFile);
            newlyInjected.add(dp.getName());
            copiedCount++;
        } catch (IOException e) {
            getLogger().warning("Failed to inject datapack " + dp.getName() + " into primary world: " + e.getMessage());
        }
    }

    // 3. Kopiuj również do folderu game_world (dla celów kopii zapasowych / backupów)
    File legacyGameDatapacks = new File(Bukkit.getWorldContainer(), targetWorldName + "/datapacks");
    if (!legacyGameDatapacks.exists()) legacyGameDatapacks.mkdirs();
    for (File dp : sourcePacks) {
        try { copyFileOrDirectory(dp, new File(legacyGameDatapacks, dp.getName())); } catch (IOException ignored) {}
    }

    if (!Bukkit.getWorlds().isEmpty()) {
        String mainWorldName = Bukkit.getWorlds().getFirst().getName();
        File migratedGameDatapacks = new File(Bukkit.getWorldContainer(), mainWorldName + "/dimensions/minecraft/" + targetWorldName + "/datapacks");
        if (migratedGameDatapacks.getParentFile().exists()) {
            if (!migratedGameDatapacks.exists()) migratedGameDatapacks.mkdirs();
            for (File dp : sourcePacks) {
                try { copyFileOrDirectory(dp, new File(migratedGameDatapacks, dp.getName())); } catch (IOException ignored) {}
            }
        }
    }

    saveInjectedDatapacksHistory(newlyInjected);

    // 4. Przeładuj datapacki serwera (Bukkit.reloadData)
    try {
        Bukkit.reloadData();
        getLogger().info("Successfully injected " + copiedCount + " datapack(s) and reloaded server resources.");
    } catch (Exception e) {
        getLogger().warning("Failed to reload server datapacks: " + e.getMessage());
    }

    return copiedCount;
}
```

---

## 4. Punkty Wywołania w Cyklu Życia

1. **Procedura Resetu (`generateGameWorldsInternal` w `Main.java`):**
   * Wywołanie `injectDatapacksToWorld(gameWorldName)` tuż przed `Bukkit.createWorld(...)`.
   * Przeładowanie zasobów następuje w momencie, gdy gracze przebywają w Limbo, co gwarantuje zerowy wpływ na płynność gry.
2. **Procedura Startu Serwera (`onEnable` w `Main.java`):**
   * Jeśli `datapacks.enabled: true`, wtyczka weryfikuje zgodność paczek w `world/datapacks/` i synchronizuje je, zapobiegając konieczności natychmiastowego wywoływania `/wr reset` po restarcie maszyny.
3. **Komenda `/wr datapacks <enable|disable>`:**
   * Po przełączeniu flagi w konfiguracji informujemy gracza:
     * Włączenie: `Wstrzykiwanie datapacków włączone. Zostaną zastosowane przy najbliższym /wr reset.`
     * Wyłączenie: `Wstrzykiwanie datapacków wyłączone. Zostaną usunięte przy najbliższym /wr reset.`

---

## 5. Procedura Wdrożenia i Weryfikacja

### Krok 1: Wprowadzenie zmian w kodzie
Edycja pliku `src/main/java/org/example/worldreset/Main.java`.

### Krok 2: Kompilacja i weryfikacja kodu
Polecenie w terminalu:
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" clean package "-DskipTests"
```
Wymagany wynik: `BUILD SUCCESS` bez błędów kompilacji.

### Krok 3: Instalacja na serwerze testowym
Skopiowanie pliku `target/WorldReset-2.0.jar` do:
`C:\Users\vipluk\Downloads\Serwer Minecraft\test\26.3 purpur\plugins\WorldReset-2.0.jar`

### Krok 4: Weryfikacja funkcjonalna w grze
1. Sprawdzenie statusu wstrzykiwania: `/wr datapacks status`.
2. Uruchomienie procedury resetu: `/wr reset`.
3. Weryfikacja poleceniem konsoli/gry: `/datapack list` – potwierdzenie, że paczka `MasterCutter+v1.8.1-mc26.3.zip` jest oznaczona jako aktywna (`[file/MasterCutter+v1.8.1-mc26.3.zip (world)]`).
4. Test receptury: włożenie drewna (np. Acacia / Oak) do Stonecuttera.

