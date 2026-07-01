# WorldReset v1.6fix - Dziennik Poprawek (Changelog)

Z dumą prezentujemy wydanie **1.6fix** dla wtyczki **WorldReset**! Ta wersja eliminuje kluczowe błędy logiczne i techniczne zidentyfikowane w wersji 1.6, wprowadzając pełny reset wszystkich elementów gry (osiągnięć, statystyk, skrzyń kresu, map i tablic wyników) oraz pełną integrację tych danych z systemem kopii zapasowych (backup).

---

## 🛠️ Wprowadzone Poprawki i Ulepszenia

### 1. Pełny reset osiągnięć (Advancements) i statystyk (Stats)
* **Problem:** Osiągnięcia i statystyki graczy były zapisywane w domyślnym świecie serwera (`world`) i nie ulegały wyczyszczeniu przy restarcie gry.
* **Naprawiono:** Wdrożono automatyczne czyszczenie plików w katalogach `world/stats/` oraz `world/advancements/` na dysku. Dla graczy połączonych (online) postępy są zerowane bezpośrednio w pamięci RAM serwera za pomocą API Bukkita.

### 2. Rozwiązanie problemu graczy offline (Offline Join Bug)
* **Problem:** Gracze, którzy wylogowali się przed resetem w świecie gry (`game_world`), po resecie logowali się z pełnym starym ekwipunkiem i na starych koordynatach (często dusząc się w blokach), gdyż wtyczka nie resetowała graczy offline.
* **Naprawiono:** Wyczyszczenie plików w `world/playerdata/` na dysku sprawia, że silnik traktuje powracających graczy jako nowych, prawidłowo teleportując ich na nowy punkt startowy z pustym ekwipunkiem i zresetowanym stanem.

### 3. Czyszczenie Skrzyń Kresu (Ender Chests)
* **Problem:** Zawartość Skrzyń Kresu nie była czyszczona, przez co gracze mogli przenosić przedmioty między grami.
* **Naprawiono:** Zaimplementowano czyszczenie Skrzyń Kresu online (`player.getEnderChest().clear()`) oraz offline poprzez usunięcie plików profilu gracza z dysku.

### 4. Resetowanie map papierowych i rajdów (katalog `world/data/`)
* **Problem:** Pliki map (`map_*.dat`) oraz rajdy (`raids.dat`) były gromadzone w folderze głównego świata, przez co nowo stworzona mapa pokazywała stary wyrenderowany świat, a rajdy mogły trwać nadal po resecie.
* **Naprawiono:** Wtyczka czyści teraz zawartość folderu `world/data/`, co powoduje, że licznik map zaczyna się od zera (ID 0) a aktywne rajdy zostają wyczyszczone.

### 5. Resetowanie i czyszczenie tablic wyników (Scoreboardów) w RAM
* **Problem:** Rejestrowane cele stopera i statystyk pozostawały w pamięci RAM serwera, co mogło prowadzić do wycieków pamięci i zaśmiecania tablicy wyników.
* **Naprawiono:** Podczas resetu wyrejestrowujemy wszystkie cele i drużyny z głównego scoreboardu serwera w pamięci RAM, co zapobiega powstawaniu duplikatów i starych wpisów.

### 6. Integracja z systemem Backupów i Przywracania kopii (w tym Skrzyń Kresu)
* **Problem:** 
  * Backup w wersji 1.6 ignorował postępy graczy, mapy i statystyki. 
  * Dodatkowo, przy wykonywaniu kopii zapasowej lub przywracaniu jej na aktywnym serwerze, wtyczka nie zapamiętywała ani nie przywracała zawartości Skrzyń Kresu (Ender Chests) graczy online w pamięci RAM. Prowadziło to do utraty przedmiotów ze Skrzyń Kresu podczas wczytywania kopii.
* **Naprawiono:** 
  * Foldery `playerdata`, `stats`, `advancements` oraz `data` są teraz kopiowane do i z backupu.
  * Przed rozpoczęciem backupu wtyczka wymusza zapis danych wszystkich połączonych graczy na dysk (`p.saveData()`), gwarantując poprawny stan plików.
  * Rozszerzono strukturę zapisu stanów graczy w pliku `players.yml` (będącym migawką pamięciową tworzoną przed resetem/wczytaniem kopii) o dane Ender Chestów. Przy wczytaniu backupu wtyczka automatycznie przywraca zawartość Skrzyni Kresu każdego gracza online bezpośrednio w pamięci RAM. Dzięki temu gracze nie muszą być wyrzucani z serwera podczas przywracania kopii. Zapewniono pełną wsteczną kompatybilność.

### 7. Pełna lokalizacja odliczania z opóźnieniem wyjścia (delay-out)
* **Problem:** Końcówka `(delay-out: Xs)` przy komendzie `/wr reset <in> <out>` była zakodowana na sztywno w języku angielskim.
* **Naprawiono:** Wprowadzono pełne wsparcie dla tłumaczeń za pomocą dedykowanego klucza językowego `reset-scheduled-with-delayout`. Pozwala to na pełną lokalizację komunikatu w języku polskim (`Reset zaplanowany za X sekund, wyjście za Y sekund.`) oraz angielskim (`Reset scheduled in X seconds, exit in Y seconds.`).

---

## 🚀 Jak zaktualizować plugin?
1. Podmień plik `.jar` w folderze `plugins/` na nową wersję: **`WorldReset-1.6fix.jar`**.
2. Zrestartuj serwer. Pliki konfiguracyjne i językowe zaktualizują się automatycznie.
