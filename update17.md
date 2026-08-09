# WorldReset v1.7 - Dziennik Aktualizacji (Dla dewelopera)

Wydanie **1.7** dla wtyczki **WorldReset** wprowadza nową kontrolę nad cyklem życia tablic wyników (scoreboard) podczas resetowania świata, zachowując przy tym wsteczną kompatybilność i standardy zachowania wtyczki.

---

## 🛠️ Zmiany Architektoniczne i Logiczne

### 1. Toggle czyszczenia Scoreboardu
* **Problem / Założenie:** W dotychczasowych wersjach plugin automatycznie i bezwzględnie czyścił wszystkie instancje `Objective` oraz `Team` w głównym scoreboardzie serwera. Dla specyficznych trybów gry użytkownicy oczekiwali utrzymania swoich statystyk pomiędzy grami.
* **Wdrożenie:** 
  * W `config.yml` dodano sekcję `scoreboard` z opcją `clear-on-reset` domyślnie ustawioną na `false` (wcześniejsze, ciche zachowanie odpowiadało `true`).
  * W klasie `Main.java` owinięto logikę usuwającą cele instrukcją warunkową opartą o buforowaną flagę `clearScoreboardOnReset`.
  * Utworzono nową komendę `/wr scoreboard <true/false/on/off/status>`, obsługiwaną przez standardową heurystykę `isEnableAlias()` i `isDisableAlias()`. 
  * W przypadku wywołania komendy bez argumentów, flaga ulega natychmiastowej negacji (toggle) i natychmiastowemu zapisowi na dysk `saveConfig()`.
  * Integracja z backupami została zachowana: jeśli scoreboard nie ulega wyczyszczeniu, mechanizm backupu po prostu zapisze jego obecny stan, a przy załadowaniu kopii zostanie on bezpiecznie nadpisany na stan z punktu wykonania backupu (zgodnie z systemem `players.yml` i natywnym zachowaniem serwera).

### 2. Kompatybilność komend i uprawnienia
* Zarejestrowano nową uprawnienie: `worldreset.scoreboard` (przypisane do grupy `worldreset.*` / `op` jako domyślny status).
* Dodano klucze lokalizacyjne: `wr_scoreboard`, `scoreboard_status`, `scoreboard_enabled`, `scoreboard_disabled` do `messages_en.yml` oraz `messages_pl.yml`.

### 3. Poprawki dokumentacji
* Plik `description.md` został uaktualniony do wersji 1.7. 
* Dodano brakujące wpisy do spisu poleceń, dotyczące funkcji Auto Give (`/wr give boat`, `/wr give wood`), które nie posiadały własnej reprezentacji w tabeli `Commands and Permissions`.
