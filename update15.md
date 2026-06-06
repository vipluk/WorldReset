# WorldReset v1.5 - Oficjalny Dziennik Zmian (Changelog)

Z dumą prezentujemy wersję **1.5** pluginu **WorldReset**! Ta aktualizacja to największy krok w rozwoju projektu, wprowadzający zaawansowany system szablonów map, kompletną integrację z PlaceholderAPI opartą na wbudowanej bazie danych, a także kluczowe poprawki stabilności i bezpieczeństwa rozgrywki.

---

## 🌟 Główne Nowości (Major Features)

### 1. Szablony Map i Światów (World Templates)
Koniec z ograniczeniem do losowych seedów! Teraz możesz wczytywać gotowe, autorskie mapy po każdym resecie:
* **Inteligentny Autodetektor Formatów (Nowość!):** Plugin automatycznie rozpoznaje i klasyfikuje foldery map wewnątrz `WorldReset_Templates/` na podstawie ich zawartości (obecność pliku `level.dat`) oraz nazwy (wyszukując słowa kluczowe `nether` i `end`). Nie musisz już zmieniać nazwy głównego folderu na `overworld`!
* **Automatyczna konwersja światów Singleplayer (Nowość!):** Możesz wrzucić swój świat z gry jednoosobowej (np. folder `moj_swiat` zawierający `level.dat`, `region`, oraz podfoldery `DIM-1` i `DIM1`) w całości do katalogu szablonów! Plugin podczas resetu automatycznie wykryje podfoldery wymiarów, wyodrębni je i przeniesie do osobnych folderów wymaganych przez Spigota/Paper (`game_world_nether` oraz `game_world_the_end`), kopiując przy tym plik `level.dat` i czyszcząc zbędne pliki z folderu głównego.
* **Wygodne Szablony Multiplayer:** W przypadku tradycyjnej struktury 3 osobnych folderów dla każdego z wymiarów, plugin skopiuje je bezpośrednio, dopasowując je do nazwy gry.
* **Ochrona przed konfliktami UUID:** Podczas kopiowania szablonu plugin automatycznie usuwa plik `uid.dat`, co zapobiega crashom silnika Bukkit.
* **System awaryjny (Fail-safe):** Jeśli wgrany szablon jest uszkodzony (brak pliku `level.dat`), plugin automatycznie anuluje procedurę, poinformuje o tym w konsoli i wygeneruje standardowy świat z seeda, gwarantując, że serwer zawsze bezpiecznie wystartuje.
* **Zachowanie punktu spawnu:** Przy wczytaniu szablonu plugin pomija filtry biomów i wymuszanie bezpiecznego spawnu, pozwalając graczom startować dokładnie tam, gdzie zaplanował to twórca mapy.
* **Wsparcie dla ścieżek bezwzględnych i relatywnych (Nowość!):** Folder z szablonami nie musi już znajdować się wewnątrz folderu wtyczki! Administratorzy sieci serwerów mogą teraz zdefiniować ścieżkę relatywną (np. `../../szablony`) lub bezwzględną (np. `D:\SharedTemplates` lub `/var/shared_templates`), co umożliwia łatwe współdzielenie tych samych szablonów na wielu instancjach serwerowych.

### 2. Integracja z PlaceholderAPI (PAPI) & Baza Danych `records.yml`
Plugin stał się pełnoprawną platformą speedrunową dzięki integracji ze statystykami i zewnętrznymi tablicami wyników:
* **Miękka zależność (Soft-Depend):** Integracja aktywuje się automatycznie, gdy na serwerze jest obecne PlaceholderAPI – w przeciwnym wypadku plugin działa standardowo bez żadnych błędów.
* **Wbudowana baza danych:** Plugin zapisuje próby, ukończenia oraz życiówki w pliku `records.yml`, który nie znika po resecie światów ani restarcie serwera.
* **Tabela Rekordów Serwera (Leaderboards):** Wdrożono zaawansowany algorytm zarządzający rankingiem Top 10 najlepszych czasów serwera (zapisuje nick, czas, datę oraz seed, na którym rekord został pobity).
* **Zestaw 22 dynamicznych zmiennych:** Pełne wsparcie dla czasu na żywo (`_ms`, `_raw`, `_status`), celów, rekordów osobistych (PB) oraz tabeli highscore.
* **Natywna synchronizacja z 25 celami Scoreboardu Minecraft (Nowość!):** Jeśli wolisz czysty tryb "plug-and-play" bez żadnych zewnętrznych pluginów, wtyczka automatycznie tworzy i na bieżąco synchronizuje aż 25 różnych celów (objectives) w natywnym systemie scoreboardów Minecrafta! Administratorzy serwera mają do dyspozycji statystyki w każdej możliwej jednostce czasu (milisekundy `ms`, sekundy `sec`/`pb`, minuty `min` oraz bardzo precyzyjne ticki gry Minecraft `ticks` - 20 ticków na 1 sekundę):
  * **Ogólne:** `wr_attempts` (próby), `wr_completions` (ukończenia), `wr_win_ratio` (wskaźnik zwycięstw %), `wr_top_rank` (miejsce w rankingu).
  * **Rekordy Życiowe (PB):** `wr_pb_ms`, `wr_pb` / `wr_pb_sec`, `wr_pb_min`, `wr_pb_ticks`.
  * **Rekordy Serwera (Top 1):** `wr_top1_ms`, `wr_top1_sec`, `wr_top1_min`, `wr_top1_ticks`.
  * **Czas Ostatniego Biegu:** `wr_last_ms`, `wr_last_sec`, `wr_last_min`, `wr_last_ticks`.
  * **Trwający Bieg (Na Żywo):** `wr_run_ms`, `wr_run_sec`, `wr_run_min`, `wr_run_ticks` (odświeżany co 0.5 sekundy podczas gry!).
  * **Średni Czas Ukończenia (Average):** `wr_avg_ms`, `wr_avg` / `wr_avg_sec`, `wr_avg_min`, `wr_avg_ticks`.
  * **Skumulowany Łączny Czas:** `wr_total_time_ms`, `wr_total_time_sec`, `wr_total_time_min`, `wr_total_time_ticks`.
  Możesz ich użyć bezpośrednio w grze za pomocą komendy np. `/scoreboard objectives setdisplay list wr_pb`, aby wyświetlić rekordy życiowe obok graczy pod TAB, lub `/scoreboard objectives setdisplay sidebar wr_run_sec` dla licznika czasu trwającego biegu na żywo!

### 3. Dynamiczne Rejestry Biomów i Struktur (Future-Proof)
Zlikwidowaliśmy sztywne listy struktur i biomów w kodzie Javy na rzecz dynamicznego skanowania rejestrów Bukkit API podczas startu serwera:
* **Pełne wsparcie dla nowości:** Plugin od razu i bez żadnych aktualizacji kodu wspiera **`TRIAL_CHAMBERS` (Minecraft 1.21)**, **`PALE_GARDEN` (Minecraft 1.21.4)** oraz wszelkie customowe biomy z modów czy datapacków!
* **Tab-Completion:** Zmienne są alfabetycznie sortowane na czacie podczas wpisywania komendy, co zapewnia estetyczny i premium wygląd.
* **Koniec fałszywych błędów:** Wyeliminowano uciążliwy komunikat o „nieprawidłowym biomie” przy próbie filtracji rzadszych biomów.

### 4. Szybkie Czyszczenie Filtrów (`/wr filter clear`)
Dodano nową, wygodną komendę, która za jednym razem całkowicie resetuje i wyłącza filtry biomów i struktur:
* **Komenda:** `/wr filter clear` (w pełni wspierana przez system Tab-completion w drugim argumencie).

### 5. Dedykowane Cele Stopera: Zniszczenie Bloku (BLOCK) oraz Zdobycie Przedmiotu (ITEM)
Wprowadziliśmy dwa zupełnie nowe, niesamowicie elastyczne typy celów dla wyzwań speedrunowych:
* **Cel BLOCK (Zniszczenie bloku):** Stoper zatrzymuje się w momencie, gdy gracz zniszczy określony typ bloku na świecie (np. `minecraft:obsidian` lub `obsidian`).
* **Cel ITEM (Zdobycie przedmiotu):** Stoper zatrzyma się w momencie, gdy gracz zdobędzie określony przedmiot do swojego ekwipunku w jakikolwiek sposób (wykopanie, wycraftowanie, handel, wyjęcie ze skrzyni, itp. – np. `minecraft:diamond` lub `diamond`).
* **Wygodna tolerancja namespace'ów:** Nowy algorytm sprawdzania celów automatycznie dopasowuje wartości wpisane w configu, niezależnie od tego, czy administrator podał pełny namespace (np. `minecraft:sponge`), czy tylko prostą nazwę (`sponge`).

### 6. Wygodne Podpowiedzi Komend i Pomoc w Grze (TAB & Help Menu)
Maksymalnie ułatwiliśmy codzienne korzystanie z komend wtyczki:
* **Inteligentne uzupełnianie TAB dla nowych celów:** Gra od teraz w pełni podpowiada argumenty `BLOCK` oraz `ITEM` przy wpisywaniu `/wr timer goal`. Co więcej, po ich wybraniu, gra **dynamicznie pobierze z rejestrów i podpowie nazwy wszystkich bloków i przedmiotów z Minecrafta** (wraz z filtrowaniem przestarzałych wersji), eliminując ryzyko literówki!
* **Dedykowane komendy pomocy:** Wpisanie `/wr help` lub `/wr ?` wyświetla teraz bezpośrednio w grze pełną, estetyczną listę z objaśnieniem każdej dostępnej komendy w wybranym języku serwera (polskim lub angielskim).
* **Automatyczne generowanie przewodników (`scoreboard.yml` i `placeholderapi.yml`):** Obie kompletne dokumentacje są od teraz automatycznie zapisywane w katalogu wtyczki przy jej starcie, aby administrator miał do nich wygodny dostęp na dysku serwera bez rozpakowywania pliku JAR!

---

## 🛠️ Poprawki Błędów i Optymalizacje (Bugfixes & Refactoring)

### 1. Bezpieczne Przejście przez Portale (Safe Portal Travel)
* **Problem:** Ręczne mnożenie i dzielenie współrzędnych `x8` / `/8` w `PlayerPortalEvent` bezpośrednio w `e.setTo()` omijało wbudowane mechanizmy Bukkit. Gracze często dusili się w skale (netherracku) lub spadali do lawy, bo serwer nie generował ramy portalu wyjściowego.
* **Naprawiono:** Zaimplementowano bezpieczne klonowanie lokacji wyjściowej silnika i podmianę tylko świata docelowego. Teraz silnik Paper/Spigot samodzielnie i w 100% bezpiecznie odnajduje lub buduje ramę portalu.

### 2. Wsparcie dla Mobów, Przedmiotów i Pereł w Portalach
* **Nowość:** Dodano zdarzenie `EntityPortalEvent` dla wszystkich bytów niebędących graczami.
* **Efekt:** Wyrzucone przedmioty, moby (np. Stridery) oraz rzucone perły kresu (`EnderPearl`) przechodzą teraz prawidłowo i płynnie między Twoimi wymiarami gry (zamiast znikać lub trafiać do domyślnych światów serwera).

### 3. Usunięto Blokowanie Plików na Systemach Windows
* **Problem:** Na serwerach Windows system rygorystycznie blokował pliki regionów i plik `session.lock` po wyładowaniu świata (`unloadWorld`), przez co rekurencyjne usuwanie światów nie działało i reset generował tę samą mapę.
* **Naprawiono:** Wdrożono wymuszone odśmiecanie pamięci `System.gc()` zaraz po wyładowaniu światów, co zwalnia wszystkie uchwyty plików w JVM i umożliwia bezbłędny reset na systemach Windows.
* **Przejrzystość:** Wszystkie potencjalne błędy zapisu/usuwania katalogów są teraz czytelnie logowane w konsoli jako `warning` zamiast cichego wyciszania wyjątków.

### 4. Poprawna Lokalizacja `server.properties`
* **Problem:** Metoda wczytująca trudność serwera szukała pliku konfiguracyjnego o jeden katalog za wysoko (`getParent()`), co skutkowało brakiem wczytywania trudności i cichym błędem.
* **Naprawiono:** Uproszczono ścieżkę do `new File("server.properties")`, co zapewnia 100% stabilności na każdym serwerze.

---

## 🚀 Jak zaktualizować plugin?
1. Podmień stary plik `.jar` w folderze `plugins/` na nowy: [WorldReset-1.5.jar](file:///c:/Users/vipluk/IdeaProjects/WorldReset/target/WorldReset-1.5.jar).
2. Zrestartuj serwer – plugin automatycznie wygeneruje nowy folder `WorldReset_Templates/` oraz zaktualizuje konfigurację `config.yml`.
3. (Opcjonalnie) Zapoznaj się z nowym plikiem [placeholderapi.yml](file:///c:/Users/vipluk/IdeaProjects/WorldReset/src/main/resources/placeholderapi.yml) wewnątrz jar lub w źródłach, aby skonfigurować swój scoreboard!
