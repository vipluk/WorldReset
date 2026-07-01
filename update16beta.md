# WorldReset v1.6 - Oficjalny Dziennik Zmian (Changelog)

Z dumą prezentujemy wersję **1.6** pluginu **WorldReset**! Ta aktualizacja wprowadza zaawansowany system automatycznych cyklicznych resetów świata (**AutoReset**), ulepszone zarządzanie widocznością elementów HUD, integrację z natywnym paskiem lokalizatora Minecrafta oraz szereg usprawnień komend.

---

## 🌟 Główne Nowości (Major Features)

### 1. Moduł Automatycznego Resetu (AutoReset Scheduler)
Dodaliśmy kompletny, inteligentny i zautomatyzowany mechanizm zaplanowanych resetów mapy:
* **Elastyczna Konfiguracja:** Administratorzy mogą zdefiniować czas trwania odliczania bezpośrednio w pliku `config.yml` lub bezpośrednio z poziomu gry komendą.
* **Wsparcie dla Formatów Czasowych:** Wbudowany parser bez problemu rozumie jednostki czasu takie jak sekundy (`s`), minuty (`m`) oraz godziny (`h`) (np. `30s`, `15m`, `2h`), co eliminuje potrzebę ręcznego przeliczania na sekundy.
* **Tryb Pętli (Loop Mode):** Opcjonalny tryb zapętlenia autoresetu sprawia, że po każdym automatycznym zresetowaniu świata licznik natychmiast startuje od nowa na zdefiniowany czas (idealne rozwiązanie dla serwerów publicznych działających autonomicznie).

### 2. Prezentacja Licznika na Ekranie (Persisting Paused Timer)
* **Zawsze na oku:** Czasomierz wyświetla się na pasku akcji (Action Bar) wszystkich połączonych graczy.
* **Kolorystyka stanu odliczania:**
  * Gdy odliczanie trwa, zegar świeci na złoto/żółto (lub zmienia kolor na migający czerwony `§c§l`, gdy do resetu zostało mniej niż 10 sekund).
  * Gdy odliczanie zostanie wstrzymane (pauza), zegar **nie znika z ekranu**, lecz zmienia swój kolor na ciemnoszary (`§8`/`§7`). Pozwala to graczom na bieżąco kontrolować stan serwera bez konieczności wpisywania komend.
* **Kompatybilność ze stoperem speedrunowym:** Jeśli na serwerze uruchomiony jest jednocześnie stoper speedrunowy, oba liczniki wyświetlą się estetycznie obok siebie w jednej linii paska akcji (oddzielone eleganckim separatorem `|`).

### 3. Komenda `/wr autoreset` i Tab-Completer
Wprowadziliśmy nową, wszechstronną podkomendę wraz z granularnym systemem uprawnień (`worldreset.autoreset`):
* `/wr autoreset <czas>` (np. `/wr autoreset 30m`) – ustawia czas do resetu i uruchamia odliczanie.
* `/wr autoreset start` – wznawia wstrzymane odliczanie.
* `/wr autoreset stop` – zatrzymuje odliczanie (pauza, czas ciemnieje na ekranie).
* `/wr autoreset loop` – przełącza tryb zapętlenia (loop) włączony/wyłączony.
* `/wr autoreset disable` – wyłącza całkowicie licznik i usuwa go z ekranu graczy.
* **Tab-Completion:** Pełne wsparcie dla uzupełniania argumentów z podpowiedziami najpopularniejszych czasów (`60s`, `5m`, `1h`) i akcji na czacie.

---

## 🧭 Natywny Locator Bar (zastąpienie kompasu)

### Całkowite zastąpienie własnego kompasu
Stary system kompasu (pasek BossBar z dynamicznym radarem graczy i kolorowymi kropkami) został **całkowicie usunięty** na rzecz natywnego mechanizmu Minecrafta:
* **`/wr compass`** – przełącza natywny Locator Bar Minecrafta (1.21.6+) — działa jako toggle bez argumentu.
* **`/wr compass enable/disable`** – jawne włączenie lub wyłączenie.
* Ustawienie jest automatycznie stosowane do wszystkich światów gry (`game_world`, `game_world_nether`, `game_world_the_end`) oraz po każdym resecie i ładowaniu świata.
* Brak własnych overlayów — czysta, waniliowa funkcja Minecrafta.

---

## 🎛️ Widoczność Elementów HUD (Timer & AutoReset)

### Nowe opcje `visible` w konfiguracji
Dodano możliwość ukrycia poszczególnych elementów HUD bez ich dezaktywacji:
* **`timer.visible`** – kontroluje widoczność stopera speedrunowego na pasku akcji.
* **`autoreset.visible`** – kontroluje widoczność odliczania autoresetu na pasku akcji.

### Nowe komendy
* `/wr timer visible true/false` – pokazuje/ukrywa stoper (bez zatrzymywania).
* `/wr autoreset visible true/false` – pokazuje/ukrywa odliczanie autoresetu (bez zatrzymywania).
* Pominięcie wartości `true/false` automatycznie **przełącza** aktualny stan (toggle).

---

## 🔍 Ulepszenia Komendy `/wr filter`

### Podgląd aktywnych filtrów
Wywołanie samego `/wr filter` (bez argumentów) wyświetla teraz:
* Aktualnie aktywny filtr struktury i/lub biomu.
* Aktualny seed świata (fixed lub losowy).
* Jeżeli żaden filtr nie jest aktywny, pojawia się stosowna informacja.

---

## 🛠️ Pozostałe Zmiany i Usprawnienia

* **System Opóźnień Limbo (Countdown):** Nowy system odliczania przy wchodzeniu/wychodzeniu z Limbo.
  * `/wr limbo <sekundy>` — ręczne wejście/wyjście z Limbo z wizualnym odliczaniem na ekranie (title) i dźwiękiem.
  * `/wr limbo delay <wejście_sek> <wyjście_sek>` — ustawia globalne opóźnienia dla AUTOMATYCZNYCH przejść (reset, start gry). Gracze widzą countdown ale mogą dalej grać.
  * Odliczanie adaptuje wyświetlane interwały do długości: krótkie (≤5s) — co sekundę, średnie (≤15s) — 10, 5, 4, 3, 2, 1, długie (>30s) — 30, 20, 15, 10, 5...1.
  * Ostatnie 5 sekund — dźwięk pling, ostatnie 3 — wyższy ton + czerwony kolor.
  * Konfiguracja: `limbo.delay-in` i `limbo.delay-out` w `config.yml`.
* Komendy `/wr silent`, `/wr death` oraz `/wr compass` działają teraz konsekwentnie jako **toggle** — wpisanie komendy bez argumentów odwraca aktualny stan.
* Zaktualizowano globalne menu pomocy `/wr help` oraz `/wr ?` w obu językach (angielskim i polskim).
* Wyeliminowano zbędne wczytywanie danych co sekundę (liście leagów itp.) powiązane z poprzednią implementacją kompasu.

---

## 🚀 Jak zaktualizować plugin?
1. Podmień plik `.jar` w folderze `plugins/` na wersję: **`WorldReset-1.6beta.jar`**.
2. Zrestartuj serwer – plik `config.yml` oraz pliki językowe zostaną automatycznie zaktualizowane o nowe sekcje.
