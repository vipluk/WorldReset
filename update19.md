# WorldReset v1.9 - Dziennik Aktualizacji (Dla dewelopera / AI)

Wydanie **1.9** dla wtyczki **WorldReset** koncentruje się na zagwarantowaniu pełnej trwałości konfiguracji oraz reguł rozgrywki pomiędzy kolejnymi resetami świata. Eliminuje krytyczny błąd utraty ustawień limitu zgonów (`/wr death`), wprowadza automatyczny mechanizm utrwalania i synchronizacji zasad gry Minecrafta (`GameRules`), ujednolica poziom trudności (Difficulty) dla wymiarów Netheru i Endu oraz modernizuje odwołania do Bukkit API, eliminując przestarzałe metody.

---

## 🛠️ Zmiany Techniczne i Architektoniczne

### 1. Trwałość limitu śmierci i naprawa rozbieżności kluczy konfiguracyjnych (`death-limit`)
* **Problem:** 
  * W pliku `config.yml` oraz w procedurze wczytywania `loadConfigValues()` klucz limitu zgonów nosił nazwę `death-limit`.
  * W obsłudze komendy `/wr death` (`onCommand`) wartość była omyłkowo zapisywana pod kluczem `death.limit` (podsekcja YAML).
  * Przy każdym resecie świata metoda `startReset()` wywoływała `loadConfigValues()`, która wykonywała `reloadConfig()`. Ponieważ klucz `death-limit` na dysku wciąż miał wartość `1`, wyłączenie limitu (`deathLimit = 0`) było natychmiast nadpisywane z powrotem wartością `1`.
* **Wdrożenie:**
  * W [`Main.java`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java#L5075-L5085) zaktualizowano zapis na właściwy klucz:
    ```java
    getConfig().set("death-limit", deathLimit);
    getConfig().set("reset-on-death", deathLimit > 0);
    saveConfig();
    ```
  * Zachowano klucz pomocniczy `reset-on-death` (boolean) w celu zachowania pełnej kompatybilności z PlaceholderAPI (`%worldreset_death_reset%`) oraz tablicami wyników.

### 2. Architektura zachowywania i przywracania reguł gry (`preserve-gamerules`)
* **Problem:**
  * Standardowa procedura resetu usuwa foldery świata (`deleteWorldFolder`) i tworzy nowe instancje za pomocą `Bukkit.createWorld(...)`. Powodowało to bezpowrotną utratę wszystkich reguł gry vanilla (m.in. `/gamerule keepInventory`, `/gamerule doDaylightCycle`, `/gamerule mobGriefing`, `/gamerule randomTickSpeed`).
* **Wdrożenie:**
  * W [`config.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/config.yml#L29-L34) dodano przełącznik `preserve-gamerules: true` (wspierający opisy PL/EN/DE).
  * Wprowadzono pole `private boolean preserveGamerules = true;` oraz mapę buforującą `private final Map<String, String> savedGameRules = new HashMap<>();`.
  * W procedurze inicjalizującej reset dodano metodę `captureGameRules()` wywoływaną bezpośrednio przed usunięciem świata we wszystkich ścieżkach resetu:
    * `startReset()` (ręczny reset natychmiastowy)
    * `startResetWithDelayOut(...)` (reset z odliczaniem w limbo)
    * `startAutoTriggeredReset()` (reset automatyczny po zgonie)
    * `startAutoResetReset()` (reset z cyklicznego timera autoresetu)
  * Dodano procedurę `applySavedGameRules(World world)` aplikującą zapamiętane reguły na wszystkie trzy wygenerowane światy (`normal`, `nether`, `the_end`). Obsługuje ona typy `Boolean` oraz `Integer`, ignorując regułę `locator_bar` (która posiada osobną logikę w `applyLocatorBarGamerule()`).

### 3. Wymiarowa synchronizacja poziomu trudności (Overworld, Nether, The End)
* **Problem:**
  * W poprzednich wersjach wtyczka zapisywała trudność ze starego świata i przywracała ją wyłącznie dla instancji Overworldu (`normal.setDifficulty(difficulty);`).
  * Światy `game_world_nether` oraz `game_world_the_end` były generowane bez ustawiania trudności, dziedzicząc domyślną wartość z pliku `server.properties`. W efekcie, gdy Overworld był na poziomie `HARD`, Nether i End mogły działać na poziomie `NORMAL` lub `EASY`.
* **Wdrożenie:**
  * W procedurze generowania światów [[generateGameWorldsInternal()]](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java#L1675-L1685) rozszerzono aplikację trudności na wszystkie wymiary:
    ```java
    if (normal != null) normal.setDifficulty(difficulty);
    if (nether != null) nether.setDifficulty(difficulty);
    if (end != null) end.setDifficulty(difficulty);
    ```
  * W procedurze wczytywania kopii zapasowych [[loadGameWorlds()]](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java#L2760-L2780) wprowadzono identyczną synchronizację trudności oraz wywołanie `applySavedGameRules` dla wszystkich trzech wymiarów.

### 4. Modernizacja wywołań Bukkit GameRule API (eliminacja ostrzeżeń `deprecated`)
* **Problem:**
  * Metoda `world.getGameRuleValue(String)` została oznaczona jako przestarzała w Paper/Bukkit API na rzecz typowanego wywołania obiektowego.
* **Wdrożenie:**
  * W metodzie `captureGameRules()` zastąpiono pobieranie wartości z tekstu wywołaniem obiektowym:
    ```java
    org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(ruleName);
    if (rule != null) {
        Object val = game.getGameRuleValue(rule);
        if (val != null) {
            savedGameRules.put(ruleName, String.valueOf(val));
        }
    }
    ```
  * Zapewniono pełną zgodność z API Paper 1.20 - 1.21+ bez jakichkolwiek ostrzeżeń kompilatora.

### 5. Refaktoryzacja listenera zgonu gracza (`onPlayerDeath`)
* **Problem:**
  * Listener zawierał martwy fragment kodu z warunkiem `if (!getConfig().getBoolean("deathLimit", false)) return;` będący pozostałością po wczesnych wersjach pluginu przed wprowadzeniem puli zgonów `deathLimit`.
* **Wdrożenie:**
  * Usunięto martwy blok kodu i zduplikowane wywołania.
  * Przeniesiono czyszczenie gracza z listy przydzielonych łódek (`boatGivenPlayers.remove(dead.getUniqueId())`) bezpośrednio po synchronizacji statystyk zgonu, gwarantując poprawny stan przed ewentualnym wywołaniem resetu.

---

## 📂 Modyfikowane Pliki w Wersji 1.9

| Ścieżka | Wprowadzone zmiany |
| :--- | :--- |
| [`src/main/java/org/example/worldreset/Main.java`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/java/org/example/worldreset/Main.java) | Naprawa zapisu `death-limit`, implementacja `captureGameRules` i `applySavedGameRules`, synchronizacja trudności Netheru/Endu, modernizacja GameRule API, refaktoryzacja `onPlayerDeath`. |
| [`src/main/resources/config.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/config.yml) | Aktualizacja nagłówka do v1.9, dodanie opcji `preserve-gamerules: true` z trójjęzycznymi komentarzami. |
| [`src/main/resources/plugin.yml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/src/main/resources/plugin.yml) | Podbicie wersji pluginu do `1.9`. |
| [`pom.xml`](file:///c:/Users/vipluk/.gemini/antigravity-ide/scratch/WorldRest/pom.xml) | Podbicie wersji projektu Maven do `1.9`. |
