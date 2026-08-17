# WorldReset v1.7beta - Dziennik Aktualizacji (Dla dewelopera)

Wydanie **1.7beta** dla wtyczki **WorldReset** wprowadza nową kontrolę nad cyklem życia tablic wyników (scoreboard) podczas resetowania świata, zachowując przy tym wsteczną kompatybilność i standardy zachowania wtyczki.

---

## 🛠️ Zmiany Architektoniczne i Logiczne

### 1. Toggle czyszczenia Scoreboardu
* **Problem / Założenie:** W dotychczasowych wersjach plugin automatycznie i bezwzględnie czyścił wszystkie instancje `Objective` oraz `Team` w głównym scoreboardzie serwera. Dla specyficznych trybów gry użytkownicy oczekiwali utrzymania swoich statystyk pomiędzy grami.
* **Wdrożenie:** 
  * W `config.yml` dodano sekcję `scoreboard` z opcją `clear-on-reset` domyślnie ustawioną na `false` (wcześniejsze, ciche zachowanie odpowiadało `true`).
  * W klasie `Main.java` owinięto logikę usuwającą cele instrukcją warunkową opartą o buforowaną flagę `clearScoreboardOnReset`.
  * Utworzono nową komendę `/wr scoreboard <true/false/on/off/status>`, obsługiwaną przez standardową heurystykę `isEnableAlias()` i `isDisableAlias()`. 
  * W przypadku wywołania komendy bez argumentów, flaga ulega natychmiastowej negacji (toggle) i natychmiastowemu zapisowi na dysk `saveConfig()`.
  * **Nowość:** Komenda dodatkowo iteruje po wszystkich połączonych graczach. Wyłączenie scoreboardu od razu "chowa" go przed graczami ustawiając `player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard())`, a włączenie przywraca główną tablicę `getMainScoreboard()`. Zabezpiecza to przed zacinaniem się elementów interfejsu (sidebar, belowname). Dołączanie nowych graczy (PlayerJoinEvent) również otrzymało ten warunek zabezpieczający.
  * Integracja z backupami została zachowana: jeśli scoreboard nie ulega wyczyszczeniu, mechanizm backupu po prostu zapisze jego obecny stan, a przy załadowaniu kopii zostanie on bezpiecznie nadpisany na stan z punktu wykonania backupu (zgodnie z systemem `players.yml` i natywnym zachowaniem serwera).

### 2. Kompatybilność komend i uprawnienia
* Zarejestrowano nową uprawnienie: `worldreset.scoreboard` (przypisane do grupy `worldreset.*` / `op` jako domyślny status).
* Dodano klucze lokalizacyjne: `wr_scoreboard`, `scoreboard_status`, `scoreboard_enabled`, `scoreboard_disabled` do `messages_en.yml` oraz `messages_pl.yml`.

### 3. Poprawki dokumentacji
* Plik `description.md` został uaktualniony do wersji 1.7. 
* Dodano brakujące wpisy do spisu poleceń, dotyczące funkcji Auto Give (`/wr give boat`, `/wr give wood`), które nie posiadały własnej reprezentacji w tabeli `Commands and Permissions`.

### 4. Rewolucja Uprawnień (Granular Permissions)
* Zrezygnowano z globalnych, nadrzędnych uprawnień dla modułów (np. `worldreset.limbo`) na rzecz bardzo precyzyjnych sub-uprawnień (np. `worldreset.limbo.self`, `worldreset.timer.config`).
* Wdrożono całkowicie angielski i udoskonalony plik `plugin.yml`.
* Zachowano **wsteczną kompatybilność**: dawne uprawnienia `worldreset.modul` działają teraz jako "parent nodes", które dziedziczą z uprawnień typu gwiazdka (wildcards: `worldreset.modul.*`), z których dziedziczą uprawnienia właściwe. Dzięki temu administratorzy aktualizujący wtyczkę z dnia na dzień nie odnotują problemów z dostępem.

### 5. Statystyka Śmierci, Reset Punktacji i Shared Lives (Limit Zgonów)
* Wprowadzono nową, trwałą statystykę gracza w pliku `records.yml`: `deaths`, która zlicza zgony od początku istnienia serwera.
* Dodano mechanikę **Shared Lives (Limit zgonów dla autoresetu)**. Dodano komendę `/wr autoreset after <limit>`. Po osiągnięciu łącznego limitu śmierci dla bieżącego podejścia (run), świat jest natychmiast resetowany bez pytania. Domyślny limit w configu to `1`.
* Zaktualizowano natywny moduł Minecraft Scoreboard, wprowadzając nowy objective `wr_deaths`, `wr_run_deaths` i `wr_lives_left`, które odświeżają się na bieżąco.
* Zintegrowano nowe statystyki w `PlaceholderAPI` (`%worldreset_deaths%`, `%worldreset_run_deaths%`, `%worldreset_lives_left%`).
* Dodano komendę administracyjną `/wr scoreboard reset [gracz]`, służącą do wyzerowania permanentnych statystyk i rekordów życiowych dla konkretnego gracza lub wszystkich ujętych w pliku `records.yml`, połączoną z natywnym kasowaniem tablic serwerowych. Zabezpieczono komendę nowym uprawnieniem `worldreset.scoreboard.admin`.
