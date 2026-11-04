# WorldReset v2.0fix - Dziennik Poprawek Technicznych (Update Doc)

Dokumentacja techniczna dla deweloperów oraz agentów dotycząca wydania **WorldReset v2.0fix**. Wersja ta rozwiązuje problem desynchronizacji klienta z serwerem przy upadku w pustkę w świecie Limbo oraz gruntownie modernizuje mechanizm teleportacji w całym pluginie, wprowadzając architekturę **SafeTeleport**.

---

## 🛠️ Architektura i Przyczyna Problemu

### Analiza zgłoszonego błędu
* **Zgłoszenie:** Zgłoszone przez użytkownika **Yuzi** (błąd dotyczył desynchronizacji gracza po wpadnięciu w pustkę w Limbo przy słabszym łączu sieciowym).
* **Objaw:** Gracz ze średnią lub słabą jakością połączenia internetowego (*average/poor network condition*) po wpadnięciu w pustkę w Limbo zostaje przeteleportowany na spawn, lecz zostaje całkowicie zablokowany (zamrożony, brak możliwości ruchu i interakcji), a dla pozostałych graczy staje się niewidzialny (tzw. stan ducha / *ghost entity desync*).
* **Przyczyna źródłowa w silniku Paper / Protokole Minecraft:**
  1. **Teleport wewnątrz pętli zdarzenia obrażeń:** Dotychczasowe wywołanie `p.teleport(loc)` następowało synchronicznie bezpośrednio wewnątrz obsługi zdarzenia `onDamage` (`EntityDamageEvent.DamageCause.VOID`). Silnik Paper w trakcie przetwarzania obrażeń nie zakończył jeszcze tickowania encji, a wysłanie pakietu teleportacji w tym samym ułamku sekundy powodowało kolizję pakietów.
  2. **Zachowanie pędu spadania (Velocity & Fall Distance):** Kod nie resetował wektora prędkości (`p.setVelocity`) ani licznika upadku (`p.setFallDistance`). Przy opóźnieniach sieciowych gracz nadal przesyłał pakiety spadania w dół, a serwer co tick ponawiał zadawanie obrażeń od pustki i kolejne teleportacje.
  3. **Wyścig potwierdzeń transakcji teleportu:** Klient z opóźnieniem sieciowym nie nadążał z potwierdzaniem pakietów pozycji (`ClientboundPlayerPositionPacket`), w wyniku czego serwer traktował ruch gracza jako błędny (*moved wrongly*) i wyrejestrowywał encję gracza z widoku innych klientów (brak pakietów `ClientboundAddEntityPacket` / `ClientboundPlayerInfoUpdatePacket`).

---

## 🚀 Wprowadzone Rozwiązania i Modyfikacje w Kodzie

### 1. Zaimplementowanie silnika `safeTeleport`
W `Main.java` wprowadzono scentralizowaną procedurę bezpiecznej teleportacji:
* **Blokada współbieżności (`teleportsInFlight`):** Użyto `ConcurrentHashMap.newKeySet()` do śledzenia aktywnych teleportacji. Jeśli dla danego gracza trwa już procedura przenoszenia, kolejne żądania są ignorowane (zapobiega to pętli teleportacji podczas spadania w pustkę). Wbudowano 5-sekundowy bezpiecznik usuwający blokadę na wypadek zawieszenia ładowania chunków.
* **Separacja od pętli zdarzeń:** Teleportacja jest zawsze delegowana do wykonania w kolejnym ticku (`runTask`), co pozwala silnikowi czysto zakończyć bieżące zdarzenie.
* **Obsługa graczy martwych (Respawn Settle Delay):** W przypadku graczy martwych wymuszany jest respawn (`p.spigot().respawn()`), a sam teleport odraczany jest o `RESPAWN_SETTLE_TICKS` (2 ticki), dając serwerowi czas na zainicjalizowanie nowej instancji encji.
* **Zerowanie pędu i wyśrodkowanie pozycji:** Przed i po teleportacji wymuszane jest `p.setFallDistance(0f)` oraz `p.setVelocity(new Vector(0, 0, 0))`, a koordynaty są centrowane na środku bloku (`+0.5` na osi X i Z).
* **Asynchroniczny teleport (`teleportAsync`):** Użycie asynchronicznego API Paper gwarantuje brak zacięć głównego wątku przy ładowaniu chunków, a callback powrotny uruchamiany jest bezpiecznie w głównym wątku silnika.

### 2. Miejsca objęte modernizacją w `Main.java`
* **Obsługa pustki w Limbo (`onDamage`):**
  * Zmieniono priorytet na `EventPriority.LOWEST`, co pozwala przechwycić i anulować obrażenia od pustki zanim inne pluginy lub mechanizmy silnika zarejestrują śmierć.
  * Natychmiastowe zerowanie `fallDistance` i anulowanie zdarzenia, a następnie wywołanie `safeTeleport(p, getLimboSpawn(), null)`.
* **Wysłanie graczy do Limbo (`doSendAllToLimbo`):**
  * Zastąpiono synchroniczną pętlę wywołaniem `safeTeleport(p, spawn, () -> setupLimboPlayer(p))`.
* **Inicjalizacja gracza w grze (`setupGamePlayer`):**
  * Całość konfiguracji ekwipunku, trybu gry i efektów została przeniesiona do callbacku `onDone` po potwierdzeniu pomyślnej asynchronicznej teleportacji.
* **Wczytywanie kopii zapasowej (`restoreAction` oraz fallback bez `players.yml`):**
  * Przywracanie ekwipunku, pancerza i skrzyń kresu (Ender Chest) następuje dopiero po zakończeniu teleportacji, eliminując desynchronizację i błędy znikających przedmiotów.
* **Dołączanie do gry (`onJoin`):**
  * Teleportacje nowych graczy oraz osób wchodzących do Limbo odbywają się przez `safeTeleport`.
* **Powrót z Limbo (`restoreLimboState` oraz `/wr limbo`):**
  * Wszelkie teleportacje połączone z odzyskiwaniem stanu gracza są w pełni asynchroniczne i zabezpieczone.
* **Zwalnianie zasobów:**
  * W `onQuit`: usuwanie UUID gracza z `teleportsInFlight`.
  * W `onDisable`: czyszczenie całego zbioru `teleportsInFlight.clear()`.

---

## 📦 Weryfikacja Kompilacji

Projekt kompiluje się bezbłędnie na środowisku Java 21 / 25 z zachowaniem standardów Paper API:
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" clean package
```
Wyjściowy plik jar: `target/WorldReset-2.0fix.jar`.

