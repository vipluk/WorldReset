# WorldReset v2.0 - Dziennik Aktualizacji (Dla dewelopera / AI)

Wydanie **2.0** dla wtyczki **WorldReset** wprowadza natywny system automatycznego wstrzykiwania datapacków (`Datapack Injection System`) do nowo generowanych lub resetowanych światów (funkcjonalność zaproponowana przez użytkownika **backermeisteru** ze społeczności Discord) oraz przeprowadza generalną modernizację pliku konfiguracyjnego `config.yml`, ujednolicając go do czystego, międzynarodowego standardu w języku angielskim.

---

## 🛠️ Zmiany Techniczne i Architektoniczne

### 1. Architektura cyklu życia świata i wstrzykiwanie datapacków (`Datapack Injection System`)
* **Problem architektoniczny:**
  * W standardowej procedurze resetu wtyczka usuwa cały folder aktywnego świata (`deleteWorldFolder`), niszcząc podkatalog `<world>/datapacks/`.
  * Wgranie datapacku po zainicjalizowaniu świata (`Bukkit.createWorld`) nie aktywuje mechanik generowania świata (biomy, struktury, tagi) bez restartu serwera lub przeładowania rejestrów.
* **Wdrożenie i Timing:**
  * Wstrzyknięcie następuje w procedurze [`generateGameWorldsInternal(boolean useDelayOut)`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java#L1690-L1696) **tuż przed** pierwszym wywołaniem `Bukkit.createWorld(new WorldCreator(gameWorldName)...)`.
  * Silnik Paper/Spigot podczas tworzenia instancji świata natywnie skanuje folder `datapacks/`, ładuje zasoby do rejestrów i aplikuje ich logikę do fazy generowania chunków startowych.
* **Metody pomocnicze w `Main.java`:**
  * `private File getDatapacksFolder()` – rozwiązuje ścieżkę z klucza `datapacks.folder` (wspiera ścieżki bezwzględne oraz względne względem `getDataFolder()`).
  * `private void createDatapacksFolder()` – tworzy katalog `WorldReset_Datapacks/` oraz plik `README.txt` z instrukcją techniczną przy wywołaniu w `onEnable()`.
  * `private List<File> getAvailableDatapacks()` – filtruje katalog pod kątem poprawnych paczek: ignoruje `README.txt`, akceptuje archiwa `.zip` oraz katalogi zawierające `pack.mcmeta`.
  * `private int injectDatapacksToWorld(String targetWorldName)` – tworzy docelowy folder `<targetWorldName>/datapacks/` i kopiuje wszystkie wykryte pakiety za pomocą `Files.copy(..., StandardCopyOption.REPLACE_EXISTING)` (dla plików) lub rekurencyjnego `copyDirectory(...)` (dla katalogów, z wcześniejszym usunięciem istniejącej zawartości).

### 2. Implementacja komendy `/wr datapacks`, uprawnień i bezpieczeństwa
* **Rejestracja uprawnień w `plugin.yml`:**
  * `worldreset.datapacks` (domyślnie `op`, dziedziczone przez węzeł wildcard `worldreset.*`).
* **Obsługa w `onCommand` (`Main.java`):**
  * Zarejestrowano obsługę aliasów `case "datapacks", "datapack"`.
  * Bezpieczna weryfikacja uprawnień: `if (hasPerm(sender, "worldreset.datapacks")) return noPerm(...)`.
  * Obsługa szybkiego przełącznika (toggle przy wywołaniu bez argumentów).
  * Obsługa podkomend:
    * `enable` / `disable` (z wykorzystaniem aliasów `isEnableAlias` / `isDisableAlias`).
    * `folder [ścieżka]` – odczyt lub zmiana ścieżki w konfiguracji w czasie działania serwera.
    * `status` – sformatowany ekran statusu (stan włączenia, folder bazowy, liczba wykrytych paczek).
    * `list` – wylistowanie wszystkich wykrytych paczek z oznaczeniem typu (`.zip` / `folder`).

### 3. Dynamiczne podpowiedzi (TabCompletion) oraz integracja z `/wr help`
* **TabCompletion (`Main.java`):**
  * W gałęzi głównej (`args.length == 1`) dodano podpowiedź `datapacks`, widoczną wyłącznie dla graczy posiadających `worldreset.datapacks`.
  * Dla podkomendy (`args.length == 2` przy `args[0].equalsIgnoreCase("datapacks")`) dynamicznie podpowiadane są: `enable`, `disable`, `folder`, `status`, `list`.
  * W `/wr help <tab>` dodano sugerowanie modułu `datapacks`.
* **System Pomocy (`/wr help`):**
  * Wpis komendy został włączony do kategorii **Świat (`world`)** obok `templates`, `seed`, `filter`, `compass`.
  * W `getHelpTopicTitle(...)` zdefiniowano zlokalizowane tytuły modułu (`Datapacki` / `Datapacks`).
  * W `getHelpForCommand(...)` zaimplementowano pełne opisy pomocy w 3 językach (`pl`, `en`, `de`) dla `/wr help datapacks`.

### 4. Refaktoryzacja pliku `config.yml`
* **Uproszczenie językowe:**
  * Cały plik konfiguracyjny został oczyszczony ze zbędnych, potrójnych tagów językowych (`[PL]`, `[DE]`, `[EN]`) i ujednolicony do czystego, czytelnego języka angielskiego.
* **Nowa sekcja konfiguracyjna (umieszczona nad `# --- Backup ---`):**
  ```yaml
  # --- Datapacks ---
  datapacks:
    # Automatically inject datapacks into every newly generated/reset world? (true/false)
    enabled: false

    # Directory path containing datapacks (.zip files or directories with pack.mcmeta).
    # Supports relative and absolute paths.
    folder: "WorldReset_Datapacks"
  ```

### 5. Synchronizacja cyklu życia stopera ze spawnowaniem graczy (Zgłoszenie `carlosveny`)
* **Problem:**
  * W metodzie `finalizeGameStart()` wywołanie `resetAndStartTimer()` oraz `isResetting = false` następowało natychmiast po ukończeniu asynchronicznego generowania świata.
  * Jeśli skonfigurowano opóźnienie wyjścia z Limbo (`delay-out`, np. 60 sekund), stoper speedrunowy odmierzał czas podczas gdy gracze tkwili w poczekalni Limbo, co powodowało start gry z naliczonym już opóźnieniem.
* **Rozwiązanie architektoniczne:**
  * Świat nadal generuje się natychmiast w tle w celu eliminacji lagów przy teleportacji.
  * Z `finalizeGameStart()` usunięto przedwczesne uruchamianie stopera dla ścieżek z aktywnym odliczaniem (`parallelDelayOutRunning` oraz `limboDelayOut > 0`).
  * Wywołanie `resetAndStartTimer()` oraz przestawienie `isResetting = false` przeniesiono bezpośrednio do momentu faktycznego lądowania graczy na spawnie nowego świata:
    1. W `doTeleportAllToGame()` (dla ścieżki równoległej, po zakończeniu odliczania w Limbo).
    2. W callbacku `startCountdown` w `startGameForAllWithDelay()` (dla ścieżki sekwencyjnej).

---

## 📂 Modyfikowane Pliki w Wersji 2.0

| Ścieżka | Wprowadzone zmiany |
| :--- | :--- |
| [`src/main/java/org/example/worldreset/Main.java`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java) | Implementacja metod `getDatapacksFolder`, `createDatapacksFolder`, `getAvailableDatapacks`, `injectDatapacksToWorld`, wstrzyknięcie przed `Bukkit.createWorld`, obsługa komendy `/wr datapacks`, obsługa TabCompletion, integracja z `/wr help`, synchronizacja cyklu życia stopera (przeniesienie startu do momentu po odliczaniu delay-out w Limbo). |
| [`src/main/resources/config.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/config.yml) | Nagłówek v2.0, ujednolicenie wszystkich komentarzy do czystego angielskiego, dodanie sekcji `datapacks:` nad `backup:`. |
| [`src/main/resources/plugin.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/plugin.yml) | Podbicie wersji do `2.0`, aktualizacja `usage`, dodanie uprawnienia `worldreset.datapacks` oraz dodanie go do węzła wildcard `worldreset.*`. |
| [`pom.xml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/pom.xml) | Podbicie wersji artefaktu Maven do `2.0`. |
| [`src/main/resources/messages_pl.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_pl.yml) | Dodanie polskich tłumaczeń dla modułu datapacks (`usage_wr_datapacks`, status, list, opisy w menu pomocy). |
| [`src/main/resources/messages_en.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_en.yml) | Dodanie angielskich tłumaczeń dla modułu datapacks. |
| [`src/main/resources/messages_de.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_de.yml) | Dodanie niemieckich tłumaczeń dla modułu datapacks. |
| [`changelog20.md`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/changelog20.md) | Przystępny opis nowości w wersji 2.0 dla graczy i społeczności. |
| [`update20.md`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/update20.md) | Szczegółowy dziennik techniczny i architektoniczny wdrożenia wersji 2.0. |

---

## 🔍 Weryfikacja Kompilacji
Projekt został pomyślnie skompilowany i zacieniony (shaded) w środowisku Java 25 za pomocą polecenia:
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" clean package "-DskipTests"
```
Wynik: **`BUILD SUCCESS`** (utworzono poprawny artefakt `target/WorldReset-2.0.jar`).
