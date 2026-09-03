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

---

### ❤️ Podziękowania dla Społeczności
Wydanie 1.8 powstało dzięki zgłoszeniom i wkładowi użytkownika **[@yuzzzie](https://github.com/yuzzzie)**, którego propozycje zmian stały się fundamentem usprawnień kontroli dostępu i stabilności poczekalni w tej wersji.
