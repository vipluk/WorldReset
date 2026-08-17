# WorldReset v1.7 - Dziennik Aktualizacji (Dla dewelopera)

Wydanie **1.7** dla wtyczki **WorldReset** stanowi oficjalne, stabilne wydanie produkcyjne, które pomyślnie przeszło fazę testów beta oraz wprowadza integrację z nowoczesnym systemem telemetrycznym **FastStats.dev**.

---

## 🛠️ Zmiany Techniczne i Architektoniczne

### 1. Zakończenie fazy Beta i stabilizacja wydania
* Po udanych testach wersji `1.7beta` (obejmujących m.in. granularny system uprawnień, trwałą statystykę śmierci graczy `records.yml`, mechanizm Shared Lives oraz zarządzanie cyklem życia scoreboardu `/wr scoreboard`) i braku zgłoszeń błędów ze strony użytkowników, wydanie zostało promowane do oficjalnej wersji stabilnej **1.7**.
* Ujednolicono metadane wersji w `pom.xml`, `plugin.yml`, `config.yml` oraz dokumentacji projektu.

### 2. Integracja z platformą FastStats.dev
* **Zależności i Repozytorium Maven (`pom.xml`):**
  * Dodano dedykowane repozytorium wydawnicze FastStats: `https://repo.faststats.dev/releases`.
  * Wdrożono zależność: `dev.faststats.metrics:bukkit` w wersji `0.29.4` ze zakresem `compile`.
* **Shading i Relokacja Pakietów (`maven-shade-plugin`):**
  * Skonfigurowano sekcję `<relocations>` w wtyczce `maven-shade-plugin`, dokonując relokacji przestrzeni nazw:
    ```xml
    <relocation>
        <pattern>dev.faststats</pattern>
        <shadedPattern>org.example.worldreset.faststats</shadedPattern>
    </relocation>
    ```
  * Zapobiega to konfliktom klas (`NoSuchMethodError` / kolizje wersji) w środowiskach wielowtyczkowych na serwerach Paper/Spigot.
* **Cykl życia telemetrii (`Main.java`):**
  * W klasie głównej zainicjalizowano pole kontekstu metryk:
    ```java
    private final BukkitContext fastStatsContext = new BukkitContext.Factory(this, "77f1c93e67dcb0f9222df2278006c23b")
            .metrics(Metrics.Factory::create)
            .create();
    ```
  * W metodzie `onEnable()` dodano wywołanie `fastStatsContext.ready();` rozpoczynające asynchroniczne wysyłanie metryk po pełnym załadowaniu wtyczki.
  * W metodzie `onDisable()` dodano procedurę `fastStatsContext.shutdown();`, gwarantującą poprawne i czyste zamknięcie puli wątków telemetrii przy wyłączaniu bądź przeładowaniu serwera.
