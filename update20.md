# WorldReset v2.0 - Dziennik Aktualizacji (Dla dewelopera / AI)

Wydanie **2.0** dla wtyczki **WorldReset** wprowadza natywny system automatycznego wstrzykiwania datapacków (`Datapack Injection System`) do nowo generowanych lub resetowanych światów (funkcjonalność zaproponowana przez użytkownika **backermeisteru** ze społeczności Discord) oraz przeprowadza generalną modernizację pliku konfiguracyjnego `config.yml`, ujednolicając go do czystego, międzynarodowego standardu w języku angielskim.

---

## 🛠️ Zmiany Techniczne i Architektoniczne

### 1. Architektura cyklu życia świata i wstrzykiwanie datapacków (`Datapack Injection System`)
* **Problem architektoniczny i specyfika silnika Minecraft (1.21 & 26.x):**
  * W standardowej procedurze resetu wtyczka usuwała cały folder aktywnego świata (`deleteWorldFolder`), niszcząc podkatalog `<world>/datapacks/`.
  * W architekturze dedykowanego serwera Minecrafta (Spigot, Paper, Purpur) datapacki **nigdy nie są ładowane per-świat**. Rejestr paczek (`PackRepository`), receptury (`RecipeManager`), drzewo osiągnięć (`AdvancementTree`) i funkcje są jednolite, globalne dla całego serwera i wczytywane **wyłącznie z folderu głównego poziomu startowego** (`<mainWorld>/datapacks/`, domyślnie `world/datapacks/`).
  * W nowszych wersjach silnika Paper (1.20.6, 1.21, 26.x) światy dodatkowe zapisywane są w hierarchii wymiarów (`world/dimensions/minecraft/game_world`), więc próba wrzucenia plików wyłącznie do `server/game_world/datapacks` skutkowała utworzeniem osieroconego folderu całkowicie ignorowanego przez silnik gry.
  * Ponadto silnik Minecraft w czasie działania nie wykrywa automatycznie nowych plików na dysku – wymaga wywołania przeładowania zasobów (`Bukkit.reloadData()`).
* **Wdrożenie i Architektura Dual-Target:**
  * **Dynamiczny folder świata głównego (`getPrimaryWorldDatapacksFolder()`):** Rozwiązuje dynamicznie folder zerowego świata (`Bukkit.getWorlds().getFirst().getWorldFolder()`), kierując pliki do właściwego katalogu `datapacks/` bez sztywnego hardcodowania nazwy `"world"`.
  * **Dual-Target Pathing (Spójność backupów i wymiarów):** Metoda `getRootDatapackFolders()` pobiera właściwy katalog główny silnika (`<server_root>/world/datapacks/`) zamiast zwodniczej ścieżki wymiarów `getWorldFolder()`. Paczki kopiowane są do świata głównego oraz do podkatalogów świata gry: ścieżki klasycznej (`server/<gameWorld>/datapacks`) i ścieżki wymiarów Paper (`server/world/dimensions/minecraft/<gameWorld>/datapacks`), co zapewnia pełną integralność backupów tworzonych przez `performBackup()`.
  * **Obsługa blokad plików w systemie Windows & Bezpieczne Wyłączanie (Clean Un-injection):** 
    * Na systemach Windows pliki `.zip` otwarte przez serwer Minecraft (`ZipFile`) są zablokowane przed bezpośrednim usunięciem lub nadpisaniem.
    * Przy wyłączaniu (`disable`) wtyczka najpierw jawnie wyrejestrowuje paczkę w silniku (`datapack disable` oraz `p.setEnabled(false)` przez Paper `DatapackManager`), zwalniając uchwyt do pliku, a dopiero po wywołaniu reloadu bezpiecznie usuwa pliki z dysku (`deleteSafely`).
    * Zabezpieczono kopiowanie (`copyFileOrDirectory`) przed próbą niepotrzebnego nadpisywania identycznych plików o tym samym rozmiarze, eliminując błędy kolizji dostępu.
  * **Odświeżanie rejestrów (`Bukkit.reloadData()` + Paper `DatapackManager`):** Bezpieczna detekcja refleksyjna metody `getDatapackManager().refreshPacks()` na silnikach Paper 1.21+ / Purpur 26.3, automatyczna aktywacja paczek (`setEnabled(true)` i `datapack enable`) oraz wywołanie natywnego `Bukkit.reloadData()`, co natychmiastowo aktywuje receptury (np. w Stonecutterze dla MasterCuttera) i wysyła zaktualizowane rejestry do połączonych graczy.
  * **Synchronizacja przy restarcie:** Dodano weryfikację i synchronizację paczek na ticku 1 w `onEnable()`, dzięki czemu po restarcie serwera datapacki są natychmiast aktywne bez wymogu natychmiastowego wywoływania `/wr reset`.
* **Metody pomocnicze w `Main.java`:**
  * `private File getDatapacksFolder()` – rozwiązuje ścieżkę z klucza `datapacks.folder` (wspiera ścieżki bezwzględne oraz względne względem `getDataFolder()`).
  * `private List<File> getRootDatapackFolders()` – rozwiązuje rzeczywiste ścieżki główne serwera (`<server_root>/world/datapacks/` oraz foldery wymiarów).
  * `private void deleteSafely(File)` – bezpieczne usuwanie plików z obsługą blokad Windows i opóźnionego ponawiania.
  * `private File getInjectedHistoryFile()` & `getInjectedDatapacksHistory()` & `saveInjectedDatapacksHistory()` – trwały rejestr wstrzykniętych paczek.
  * `private void copyFileOrDirectory(File, File)` – uniwersalne kopiowanie plików i folderów z weryfikacją tożsamości.
  * `private void createDatapacksFolder()` – tworzy katalog `WorldReset_Datapacks/` oraz plik `README.txt` z instrukcją techniczną przy wywołaniu w `onEnable()`.
  * `private List<File> getAvailableDatapacks()` – filtruje katalog pod kątem poprawnych paczek: ignoruje `README.txt`, akceptuje archiwa `.zip` oraz katalogi zawierające `pack.mcmeta`.
  * `private int injectDatapacksToWorld(String targetWorldName)` – wielopoziomowe wstrzykiwanie, czyszczenie nieaktualnych paczek, synchronizacja i reload zasobów serwera.

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

### 6. Poprawka formatowania i elastyczności komendy `/wr death <limit>`
* **Problem:**
  * W plikach językowych `messages_en.yml`, `messages_pl.yml` i `messages_de.yml` wpis `death-mode-enabled` zawierał zakodowaną na stałe cyfrę `1` (np. `&aDeath limit set to &f1&a...`) zamiast znacznika zmiennej `{v1}`.
  * W efekcie po wpisaniu komendy definiującej dowolny inny limit (np. `/wr death 5`) konfiguracja prawidłowo zapisywała wartość `5`, lecz na czacie wyświetlał się mylący komunikat `Death limit set to 1`.
* **Rozwiązanie:**
  * Zaktualizowano definicje klucza `death-mode-enabled` we wszystkich plikach językowych (`en`, `pl`, `de`), zastępując statyczną cyfrę `1` zmienną `{v1}`.
  * W kodzie `Main.java` wdrożono wielopoziomowy mechanizm fallbacku (obsługa `{v1}`, `{count}` oraz dynamiczne zastępowanie sekwencji kolorów `&f1&a` / `§f1§a` i spacji ` 1 `), co gwarantuje pełną odporność na starsze pliki konfiguracyjne użytkowników bez konieczności resetowania ich plików wiadomości.

### 7. Wielosegmentowy Action Bar i Wskaźnik Obrażeń w Trybie Podwójnym (Dual-Segment Action Bar)
* **Problem architektoniczny:**
  * W protokole Minecrafta pakiet `ClientboundSetActionBarTextPacket` kontroluje pojedynczą linię tekstu nad ekwipunkiem gracza.
  * Ponieważ pętla stopera (`runTaskTimer`) odświeżała Action Bar w każdym ticku serwera (20 razy na sekundę), wszelkie zewnętrzne powiadomienia (np. z pluginu *SharedHealthAndHunger* o otrzymanych przez członka zespołu obrażeniach) były natychmiastowo nadpisywane i znikały z ekranu w ułamku sekundy.
* **Rozwiązanie architektoniczne:**
  * **Wielosegmentowy Kompozytor (`sendActionBar`):** Przekształcono metodę wysyłającą pasek w wielosegmentowy kompozytor składający się z trzech niezależnych segmentów łączonych separatorem ` &7| `:
    1. **Stoper:** `&e&l⏱ HH:MM:SS`
    2. **Wskaźnik obrażeń / Notyfikacja (Tryb Podwójny):** `&c{player} -{damage}❤`
    3. **AutoReset:** `&a⟳ MM:SS`
  * **Dynamiczne przełączanie trybów:**
    * Przy braku obrażeń pasek pracuje w **trybie pojedynczym** (wyłącznie stoper).
    * Po otrzymaniu obrażeń (`onPlayerDamageActionBar` nasłuchujący zdarzenia `EntityDamageEvent` z priorytetem `MONITOR`), pasek natychmiast przełącza się w **tryb podwójny** na konfigurowalny czas (`damage-indicator.duration`, domyślnie 3 sekundy).
    * Jeśli gracz odniesie kolejne obrażenia przed upływem tego czasu, licznik sekund zostaje automatycznie odnowiony, a treść zaktualizowana.
    * Po upływie czasu notyfikacji pasek płynnie i bez migania powraca do trybu pojedynczego.
  * **Publiczne API dla integracji między pluginami:** Dodano publiczną metodę statyczną `Main.setActionBarNotification(String message, long durationMs)`, umożliwiającą innym wtyczkom (w tym *SharedHealthAndHunger*) bezpośrednie wstrzykiwanie dowolnych powiadomień do wspólnego paska.
  * **Konfiguracja i komendy:**
    * Nowa sekcja w `config.yml`: `damage-indicator.enabled`, `duration`, `format`.
    * Nowa komenda w grze: `/wr damage [enable|disable|status|duration <sekundy>]` z uprawnieniem `worldreset.damage` (włączonym do węzła wildcard `worldreset.*`) i pełnym wsparciem TabCompletion oraz systemu pomocy `/wr help`.

---

## 📂 Modyfikowane Pliki w Wersji 2.0

| Ścieżka | Wprowadzone zmiany |
| :--- | :--- |
| [`src/main/java/org/example/worldreset/Main.java`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java) | Pełna implementacja systemu Datapack Injection w architekturze Dual-Target (`world/datapacks` + foldery wymiarów Paper), odświeżanie rejestrów przez `Bukkit.reloadData()` i Paper `DatapackManager`, trwałe śledzenie wstrzykniętych paczek (`.injected_datapacks.txt`), bezpieczny un-injection przy wyłączeniu/usunięciu, synchronizacja przy restarcie w `onEnable()`, natychmiastowe wstrzykiwanie w komendzie `/wr datapacks`, synchronizacja cyklu życia stopera z opóźnieniem Limbo, odporny parser placeholderów dla `/wr death <limit>`, implementacja wielosegmentowego paska Action Bar z dynamicznym trybem podwójnym dla wskaźnika obrażeń, obsługa komendy `/wr damage`. |
| [`Plan_Wdrozenia_Datapack_Injection.md`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/Plan_Wdrozenia_Datapack_Injection.md) | Szczegółowy dokument architektoniczny i plan naprawczy systemu wstrzykiwania datapacków dla Minecraft 1.21 oraz 26.x. |
| [`src/main/resources/config.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/config.yml) | Nagłówek v2.0, ujednolicenie wszystkich komentarzy do czystego angielskiego, dodanie sekcji `datapacks:` oraz `damage-indicator:`. |
| [`src/main/resources/plugin.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/plugin.yml) | Podbicie wersji do `2.0`, aktualizacja `usage`, dodanie uprawnień `worldreset.datapacks` oraz `worldreset.damage`, włączenie ich do węzła wildcard `worldreset.*`. |
| [`pom.xml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/pom.xml) | Podbicie wersji artefaktu Maven do `2.0`. |
| [`src/main/resources/messages_pl.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_pl.yml) | Dodanie polskich tłumaczeń dla modułów datapacks i damage indicator, poprawka `{v1}` w `death-mode-enabled`. |
| [`src/main/resources/messages_en.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_en.yml) | Dodanie angielskich tłumaczeń dla modułów datapacks i damage indicator, poprawka `{v1}` w `death-mode-enabled`. |
| [`src/main/resources/messages_de.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/messages_de.yml) | Dodanie niemieckich tłumaczeń dla modułów datapacks i damage indicator, poprawka `{v1}` w `death-mode-enabled`. |
| [`changelog20.md`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/changelog20.md) | Przystępny opis nowości w wersji 2.0 dla graczy i społeczności. |
| [`update20.md`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/update20.md) | Szczegółowy dziennik techniczny i architektoniczny wdrożenia wersji 2.0. |

---

## 🔍 Weryfikacja Kompilacji
Projekt został pomyślnie skompilowany i zacieniony (shaded) w środowisku Java 25 za pomocą polecenia:
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" clean package "-DskipTests"
```
Wynik: **`BUILD SUCCESS`** (utworzono poprawny artefakt `target/WorldReset-2.0.jar`).
