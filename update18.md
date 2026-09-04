# WorldReset v1.8 - Dziennik Aktualizacji (Dla dewelopera)

Wydanie **1.8** dla wtyczki **WorldReset** koncentruje się na zaawansowanej kontroli uprawnień i prywatności komend, optymalizacji doświadczenia graczy nieposiadających uprawnień administratora, bezpiecznym zarządzaniu stanem sesji w poczekalni (limbo) oraz wyeliminowaniu krytycznych luk w wykrywaniu zdarzeń portali dla modułu Speedrun.

---

## 🛠️ Zmiany Techniczne i Architektoniczne

### 1. Granularna kontrola podpowiedzi Tab (`onTabComplete`)
* **Problem:** Klawisz `Tab` zwracał wszystkim graczom pełną listę komend i argumentów, w tym administracyjne polecenia resetu, kopii zapasowych i konfiguracji.
* **Wdrożenie:** Zaimplementowano dynamiczną filtrację opartą o `sender.hasPermission(...)` dla wszystkich 4 poziomów argumentów:
  * **Argument 1:** Zwykły gracz otrzymuje w podpowiedziach wyłącznie komendy, do których posiada jawne uprawnienia (np. `limbo`, `help`), ukrywając komendy administracyjne.
  * **Argumenty 2, 3 i 4:** Wprowadzono kontekstowe filtrowanie parametrów w oparciu o uprawnienia podrzędne (m.in. rozróżnienie `worldreset.filter.use` od `worldreset.filter.config`, `worldreset.timer.use` od `worldreset.timer.config`, a także uprawnienia administracyjne do resetowania statystyk tablicy wyników).
  * Zachowano integrację z dynamicznymi rejestrami Bukkit (`Registry.MATERIAL`, `Registry.STRUCTURE`, `Registry.BIOME`).

### 2. Filtrowanie menu pomocy (`/wr help`)
* **Problem:** Komenda pomocy wyświetlała wszystkie moduły serwerowe niezależnie od roli gracza na serwerze.
* **Wdrożenie:** Zaktualizowano procedurę `sendFullHelp(CommandSender sender)`, dzieląc pomoc na 4 bloki uprawnień (`game`, `timer_autoreset`, `world`, `system`). Gracz widzi wyłącznie kategorie i instrukcje, do których posiada jawne uprawnienia.

### 3. Odblokowanie komendy głównej dla zwykłych graczy
* **Problem:** Wpis `permission: worldreset.admin` w `plugin.yml` powodował odrzucenie komendy `/wr` na poziomie silnika Bukkit zanim wtyczka mogła zweryfikować uprawnienia podkomendy.
* **Wdrożenie:** Usunięto nadrzędny węzeł uprawnienia z `plugin.yml`, pozostawiając kontrolę bezpieczeństwa dedykowanym uprawnieniom per-podkomenda w `Main.java`.

### 4. Precyzyjne wykrywanie przejść przez portale w module Speedrun
* **Problem:** Wykorzystanie `EntityPortalEnterEvent` powodowało natychmiastowe zatrzymanie stopera w chwili dotknięcia bloku portalu Netheru (przed upływem 4-sekundowego czasu oczekiwania). Ponadto zwykłe teleportacje komendą `/tp` mogły fałszywie zaliczać cel.
* **Wdrożenie:**
  * Zastąpiono detekcję listenerem `PlayerTeleportEvent` na poziomie priorytetu `EventPriority.MONITOR` z flagą `ignoreCancelled = true`.
  * Wprowadzono eliminację fałszywych wygranych po komendach `/tp`, perłach kresu czy odrodzeniach.
  * Zapewniono prawidłową identyfikację wyjścia z Kresu przez centralną fontannę jako cel `OVERWORLD`.
  * Dodano weryfikację przynależności światów początkowego i docelowego do aktywnej instancji rozgrywki (`gameWorldName`).

### 5. Blokada komendy Limbo dla graczy martwych (`playersDeathLocked`)
* **Problem:** Przeniesienie gracza znajdującego się na ekranie śmierci do poczekalni limbo skutkowało zacięciem klienta gry i błędami ekwipunku.
* **Wdrożenie:** Dodano zbiór `playersDeathLocked` aktywowany przy `PlayerDeathEvent` i zwalniany przy `PlayerRespawnEvent`, `PlayerQuitEvent` oraz procedurze resetu świata. Próba przeniesienia martwego gracza do poczekalni zwraca zlokalizowany komunikat `player-dead`.

### 6. Bezpieczne zachowanie stanu sesji i poczekalni przy wyjściu gracza
* **Problem:** Rozłączenie gracza w poczekalni kasowało dane jego zapisanego ekwipunku ze świata gry (`limboSavedStates`), a ponowne dołączenie wyrzucało go na punkt startowy gry.
* **Wdrożenie:**
  * W procedurze `onJoin` zabezpieczono graczy w świecie limbo, uniemożliwiając niechcianą teleportację na spawn gry przy ponownym logowaniu.
  * W procedurze `onQuit` usunięto kasowanie `limboSavedStates` oraz indywidualnych czasów stopera, zachowując przy tym anulowanie aktywnych zadań odliczań (`activeCountdowns`), czyszczenie blokad oraz natychmiastową resynchronizację tablic wyników (`syncAllScoreboards`).

### 7. Automatyczne kierowanie nowych graczy do Limbo (`/wr limbo newplayers`)
* **Problem:** W trakcie trwania próby speedrunowej lub rozgrywki, nowi gracze wchodzący na serwer lądowali bezpośrednio w świecie gry na spawnie, ingerując w trwającą sesję.
* **Wdrożenie:**
  * Wprowadzono opcję konfiguracyjną `limbo.new-players-to-limbo` (domyślnie `false`).
  * W procedurze `onJoin` dodano weryfikację: jeśli runda trwa, a dołącza nowy gracz, zostaje on skierowany bezpośrednio do poczekalni Limbo i otrzymuje dedykowany komunikat o konieczności zaczekania na kolejny reset.
  * Udostępniono komendę `/wr limbo newplayers [enable|disable|status]` z pełnym wsparciem podpowiedzi Tab.

### 8. Start serwera bezpośrednio w trybie Limbo (`/wr limbo startup`)
* **Problem:** Po restarcie lub uruchomieniu serwera wtyczka natychmiast uznawała grę za gotową, wpuszczając graczy na mapę przed decyzją administratora o rozpoczęciu rozgrywki.
* **Wdrożenie:**
  * Wprowadzono opcję konfiguracyjną `limbo.start-in-limbo` (domyślnie `false`).
  * W procedurze `onEnable()` serwer inicjalizuje stan `isWaitingStartupInLimbo = true`, a cykliczne zadanie Limbo wyświetla na ekranie tytuł oczekiwania na start gry. Wszyscy łączący się gracze lądują w Limbo.
  * Gra startuje automatycznie po zainicjowaniu resetu (`/wr reset`) lub po ręcznym zwolnieniu poczekalni (`/wr limbo`).
  * Udostępniono komendę `/wr limbo startup [enable|disable|status]` oraz komendę podglądu `/wr limbo status`.

### 9. Integracja z platformą bStats
* Wdrożono bibliotekę telemetryczną `org.bstats:bstats-bukkit:3.2.1` ze strefą cienia (`maven-shade-plugin`) relokowaną do pakietu `org.example.worldreset.bstats`, zapobiegając konfliktom z innymi wtyczkami.
* W procedurze `onEnable()` w `Main.java` zainicjalizowano instancję `new org.bstats.bukkit.Metrics(this, 33834)`.

### 10. Nowa komenda startu gry z poczekalni: `/wr start`
* Umożliwia natychmiastowe wypuszczenie wszystkich graczy oczekujących w Limbo (np. po starcie serwera w trybie `start-in-limbo`) bezpośrednio na spawn świata gry bez konieczności ponownego generowania mapy (`/wr reset`).
* Czyści stan `isWaitingStartupInLimbo`, ustawia `isGameReady = true` i opcjonalnie uruchamia stoper speedrunu.
* Zabezpieczono komendę przed przypadkowym wywołaniem, gdy rozgrywka już trwa (odsyła wówczas do `/wr reset`).

### 11. System ustawień lokalnych vs globalnych (Language & Silent)
* **Lokalny język:** Komenda `/wr language [en|de|pl]` (lub `/wr lang`) pozwala każdemu graczowi bez uprawnień OP wybrać preferowany język komunikatów pluginu lub przełączać go cyklicznie.
* **Globalny język:** Administratorzy zarządzają domyślnym językiem serwera w `config.yml` za pomocą `/wr languageall [en|de|pl]`.
* **Lokalne wyciszenie:** Komenda `/wr silent [enable|disable|status]` pozwala każdemu graczowi wyciszyć ogłoszenia o resecie świata tylko dla siebie.
* **Globalne wyciszenie:** Administratorzy zarządzają ogłoszeniami serwera za pomocą `/wr silentall [enable|disable|status]`.

### 12. Trwałość preferencji graczy (`userdata.yml`) i zasada pierwszeństwa
* Wybory graczy dotyczące języka i trybu cichego zapisywane są trwale w pliku `userdata.yml` w oparciu o UUID.
* Wprowadzono zasadę jurysdykcji: gracze, którzy ręcznie wybrali swoje ustawienia, nie są nadpisywani przez globalne komendy administratora (`ALL`), zachowując pełną personalizację.

### 13. Pełna lokalizacja, eliminacja błędów językowych i dostosowanie odmowy uprawnień
* **Naprawa komunikatu o braku uprawnień:** Cała obsługa komend dynamicznie rejestruje kontekst wywołującego gracza, dzięki czemu odmowa uprawnień (`Brak uprawnień!`) oraz błędy składni są zawsze wysyłane w języku gracza, a nie w domyślnym języku serwera.
* **Szczegółowa pomoc komend:** Polecenia `/wr <komenda> help` oraz `/wr help <komenda>` w pełni respektują język gracza.
* **Spolszczenie statusów:** Wszystkie komendy statusowe (`limbo`, `startup`, `newplayers`, `silent`, `silentall`) zamiast sztywnych angielskich słów `Enabled`/`Disabled` wyświetlają przetłumaczone etykiety (`Włączone`/`Wyłączone`).
* **Wielojęzyczne ogłoszenia serwera:** Kluczowe powiadomienia (o resecie, starcie gry, wygenerowaniu mapy czy zakończeniu speedrunu) są tłumaczone w locie i docierają do każdego gracza w jego wybranym języku.
* **Eliminacja hardkodowanych tekstów:** Wszystkie pozostałe angielskie napisy (m.in. brak załadowanego świata, komunikaty dla konsoli) przeniesiono do plików `messages_en.yml` i `messages_pl.yml`.

### 14. Pełna integracja języka niemieckiego (`de`) i cykliczny przełącznik języków
* **Kompletna translacja:** Utworzono plik `messages_de.yml` zawierający pełne tłumaczenie wszystkich 340 kluczy konfiguracyjnych na język niemiecki (włącznie z komunikatami czatu, tytułami na ekranie, action barem, menu pomocy oraz odmowami uprawnień).
* **Cykliczny przełącznik (`en` ➔ `de` ➔ `pl` ➔ `en`):**
  * Wywołanie `/wr language` bez parametrów działa jako natychmiastowy przełącznik języka dla danego gracza.
  * Zaimplementowano regułę dziedziczenia: jeśli gracz nigdy wcześniej nie konfigurował własnego języka, wtyczka sprawdza globalny język serwera z `config.yml` jako punkt wyjścia i przełącza go na kolejny język dla tego gracza, zapisując wybór do `userdata.yml`.
  * Analogiczny przełącznik cykliczny zaimplementowano dla administratorów w komendzie globalnej `/wr languageall` (zapisuje nowy język do `config.yml` i natychmiast przeładowuje konfigurację).
* **Ergonomia autouzupełniania Tab (`onTabComplete`):**
  * Ukryto aliasy `/wr lang` oraz `/wr langall` z podpowiedzi głównego polecenia `/wr <tab>` oraz z menu pomocy `/wr help <tab>`, aby zapobiegać dublowaniu poleceń w interfejsie.
  * Po wpisaniu aliasu `/wr lang <tab>` lub `/wr langall <tab>` silnik precyzyjnie podpowiada dostępne kody języków: `["de", "en", "pl"]`.
* **Wielojęzyczne ekrany statusowe i PlaceholderAPI:**
  * Zlokalizowano 6 wewnętrznych ekranów statusowych (`limbo`, `seed`, `filter`, `templates`, `autoreset`, `backup`) z podziałem na `pl`, `de` i `en`.
  * Rozszerzenie PlaceholderAPI (`WorldResetExpansion`) wspiera niemieckie nazwy poziomów trudności (`Friedlich`, `Einfach`, `Normal`, `Schwer`), celów speedrunu (`Portal`, `Tötung`, `Fortschritt`, `Blockabbau`, `Gegenstand`) oraz fallback pustych wartości (`Keine`).

### 15. Dynamiczne powiadomienia o celu na ekranie i czacie przy starcie stopera
* **Wizualna informacja na ekranie:** Przy starcie stopera (zarówno po komendzie `/wr timer start`, jak i przy automatycznym rozpoczęciu biegu) na ekranie graczy pojawia się tytuł `⏱ Timer wystartował!` wraz z podtytułem precyzującym cel biegu.
* **Naturalne opisy celów:** Zaimplementowano generator czytelnych poleceń w 3 językach (np. `Wejdź pierwszy do Netheru` / `Zabij: Ender Dragon` / `Zdobądź postęp: ...`), eliminując surowe kody techniczne Minecrafta.
* **Informacja na czacie:** Czat wyświetla dodatkowy wiersz ze spersonalizowaną nazwą celu dla każdego gracza w jego wybranym języku.
* **Czysty komunikat wygranej:** Zachowano zwięzłą formę ogłoszenia zwycięzcy i nowego rekordu życiowego (PB) bez duplikowania nazwy celu.

### 16. Bezpieczny mechanizm aktualizacji plików językowych (`.yml.old`)
* **Problem:** Zwykłe dopisywanie brakujących kluczy uniemożliwiało odświeżenie zmodyfikowanych lub skorygowanych domyślnych tłumaczeń na dysku serwera.
* **Wdrożenie:** Zmodernizowano procedurę `updateResourceFile`:
  * Jeśli w pliku na dysku brakuje choćby jednego klucza względem najnowszego pliku z JAR, stary plik jest automatycznie archiwizowany jako `<nazwa>.old` (np. `messages_de.yml.old`).
  * Na jego miejsce instalowana jest pełna, aktualna wersja szablonu z JAR.
  * Konsola serwera rejestruje informację o zabezpieczeniu dotychczasowego pliku i zaktualizowaniu konfiguracji.

### 17. Optymalizacja poziomu trudności i eliminacja spamu w konsoli
* **Problem:** Funkcja `syncScoreboard` odświeżana przy każdym starcie, pauzie i finiszu stopera wywoływała `getServerDifficulty()`, odczytując plik `server.properties` z dysku i wypisując do konsoli informację o poziomie trudności dla każdego gracza online z osobna.
* **Wdrożenie:**
  * Usunięto zbędne logowanie informacyjne z `getServerDifficulty()`.
  * W `syncScoreboard` oraz rozszerzeniu PlaceholderAPI wprowadzono odczyt poziomu trudności bezpośrednio z załadowanego w pamięci RAM świata gry (`gameWorld.getDifficulty()`), redukując liczbę operacji wejścia/wyjścia (I/O) do zera podczas rozgrywki.

---

### ❤️ Podziękowania dla Społeczności
Wydanie 1.8 powstało dzięki zgłoszeniom i wkładowi użytkownika **[@yuzzzie](https://github.com/yuzzzie)**, którego propozycje zmian stały się fundamentem usprawnień kontroli dostępu i stabilności poczekalni w tej wersji.
