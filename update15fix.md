# WorldReset v1.5fix - Dziennik Poprawek (Changelog)

Z dumą prezentujemy wydanie **1.5fix** dla wtyczki **WorldReset**! Ta wersja eliminuje kluczowe błędy techniczne, logiczne i rozgrywkowe zidentyfikowane w wersji 1.5. Zapewnia to pełną stabilność w wymagających środowiskach serwerowych (w tym przy zoptymalizowanych flagach startowych) oraz usprawnia codzienne działanie mechanizmów speedrunowych.

---

## 🛠️ Wprowadzone Poprawki i Ulepszenia

### 1. Rozwiązanie blokady plików przy Aikar's Flags (`session.lock` / regiony)
* **Problem:** Na serwerach korzystających z zoptymalizowanych flag startowych (np. flagi Aikara), opcja `-XX:+DisableExplicitGC` całkowicie blokowała wywołanie `System.gc()`. W efekcie JVM trzymał aktywne uchwyty do plików regionów `.mca` oraz `session.lock` po wyładowaniu światów, przez co próba ich usunięcia przy `/wr reset` kończyła się fiaskiem i wczytywaniem starej mapy z dysku.
* **Naprawiono:**
  * Wprowadzono bezpieczne opóźnienie **20 ticków (1 sekunda)** pomiędzy wyładowaniem światów a ich usuwaniem i generowaniem nowych. Dzięki temu asynchroniczny zapis chunków silnika Spigot/Paper w tle zdąży się w pełni zakończyć, a system operacyjny zwolni blokady.
  * Zaimplementowano ulepszoną weryfikację w metodzie `unloadWorld`. Jeśli wyładowanie się nie powiodło, wtyczka wymusza zapis (`w.save()`) i ponawia próbę.

### 2. Pełne odblokowanie granularnego systemu uprawnień
* **Problem:** Globalny check permisji `worldreset.admin` na początku metody obsługującej komendy blokował każdego gracza bez statusu admina/opa przed użyciem jakiejkolwiek funkcji – nawet jeśli miał przypisane uprawnienie (np. `worldreset.limbo`). Ponadto wiadomość o braku uprawnień wysyłała się z niesformatowanym placeholderem `{permission}`.
* **Naprawiono:** Usunięto globalny blokujący check. Od teraz każda subcommand posiada własne, precyzyjne sprawdzanie uprawnień za pomocą metody `hasPerm`, która poprawnie zastępuje placeholder `{permission}` nazwą brakującego uprawnienia. Komendy `reload`, `silent` oraz `death` otrzymały swoje dedykowane zabezpieczenia.

### 3. Usunięcie blokady w trybie Adventure (Adventure Mode Lock)
* **Problem:** Gracze, którzy wylogowali się z serwera, będąc w Limbo (czyli w trybie Adventure), a weszli ponownie po wystartowaniu gry, zostawali przeteleportowani do świata gry, ale silnik pomijał ich inicjalizację do trybu Survival ze względu na błędny check `!p.hasPlayedBefore()`.
* **Naprawiono:** Przebudowano zdarzenie `onJoin` w taki sposób, aby każdy gracz przenoszony z Limbo lub domyślnego świata do świata gry był zawsze poprawnie inicjowany metodą `setupGamePlayer(...)` (otrzymując Survival, uleczenie i wyczyszczenie ekwipunku startowego).

### 4. Zabezpieczenie asynchronicznych operacji przed crashami (Spawn Shifter)
* **Problem:** Jeśli silnik gry rzucił wyjątkiem podczas wyszukiwania struktur w Spawn Shifterze (np. przy rzadkich strukturach lub specyficznym generatorze mapy), wtyczka rzucała `RuntimeException`, co natychmiastowo crashowało asynchroniczne zadanie resetu światów i blokowało serwer.
* **Naprawiono:** Zastąpiono rzucanie błędu bezpiecznym przechwytywaniem wyjątku i zalogowaniem ostrzeżenia w konsoli (`warning`), analogicznie do bezpiecznego wyszukiwania biomów.

### 5. Likwidacja zaciętego timera (Timer Freeze)
* **Problem:** Jeśli gracz dołączył po wystartowaniu timera w trybie indywidualnym i nie posiadał wpisu w mapie `playerStartTimes`, stoper na pasku akcji przy każdym ticku pobierał aktualny czas jako domyślny, przez co stoper stał w miejscu i wyświetlał `0:00`.
* **Naprawiono:** Dodano mechanizm automatycznej inicjalizacji i zapisu czasu startowego gracza w mapie `playerStartTimes` w momencie wykrycia jego braku podczas pobierania czasu live oraz w wątku stopera.

### 6. Trwałe zapamiętywanie trudności świata
* **Problem:** Ustawiona w grze trudność świata przed resetem była gubiona po reboocie lub przeładowaniu serwera, ponieważ wtyczka przechowywała ją tylko w pamięci RAM.
* **Naprawiono:** Wdrożono zapisywanie trudności świata pod kluczem `world.difficulty` w pliku `config.yml` przy każdym wywołaniu resetu, co pozwala na jej bezbłędne odtworzenie przy ponownym wczytywaniu.

### 7. Zapobieganie błędom NPE przy wczytywaniu pustego seeda
* **Problem:** Włączenie opcji `seed.use-fixed` bez podania wartości w `seed.value` wywoływało nieobsługiwany wyjątek `NullPointerException`, wstrzymujący generowanie świata.
* **Naprawiono:** Wprowadzono bezpieczną weryfikację poprawności wartości seeda z fallbackiem do losowego seeda w razie wykrycia pustej wartości.

---

## 🚀 Jak zaktualizować plugin?
1. Podmień plik `.jar` w folderze `plugins/` na nową wersję: **`WorldReset-1.5fix.jar`**.
2. Zrestartuj serwer. Wtyczka automatycznie zaktualizuje pliki i wystartuje bez żadnych ostrzeżeń o blokadach!
