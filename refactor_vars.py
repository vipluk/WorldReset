import os
import json

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"
EN_YAML = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\resources\messages_en.yml"
PL_YAML = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\resources\messages_pl.yml"

replacements = [
    {
        "old": 'sender.sendMessage(isPl ? "§cNieznana komenda: §e" + topic + "§c. Użyj §e/wr help §cpo pełną listę." : "§cUnknown command: §e" + topic + "§c. Use §e/wr help §cfor full list.");',
        "new": 'sender.sendMessage(getMsg("cmd-unknown").replace("{v1}", String.valueOf(topic)));',
        "key": "cmd-unknown",
        "pl": "&cNieznana komenda: &e{v1}&c. Użyj &e/wr help &cpo pełną listę.",
        "en": "&cUnknown command: &e{v1}&c. Use &e/wr help &cfor full list."
    },
    {
        "old": 'String msg = isPl ? "§eReset zaplanowany za §c" + delayIn + "s§e..." : "§eReset scheduled in §c" + delayIn + "s§e...";',
        "new": 'String msg = getMsg("reset-scheduled").replace("{v1}", String.valueOf(delayIn));',
        "key": "reset-scheduled",
        "pl": "&eReset zaplanowany za &c{v1}s&e...",
        "en": "&eReset scheduled in &c{v1}s&e..."
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNie znaleziono gracza: §e" + arg2 : "§cPlayer not found: §e" + arg2);',
        "new": 'sender.sendMessage(getMsg("player-not-found").replace("{v1}", String.valueOf(arg2)));',
        "key": "player-not-found",
        "pl": "&cNie znaleziono gracza: &e{v1}",
        "en": "&cPlayer not found: &e{v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNie znaleziono gracza: §e" + arg1 : "§cPlayer not found: §e" + arg1);',
        "new": 'sender.sendMessage(getMsg("player-not-found").replace("{v1}", String.valueOf(arg1)));',
        "key": "player-not-found2",
        "pl": "&cNie znaleziono gracza: &e{v1}",
        "en": "&cPlayer not found: &e{v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aDrewno pod ziemią: włączone (" + amount + ")" : "§aWood if underground: enabled (" + amount + ")");',
        "new": 'sender.sendMessage(getMsg("wood-enabled").replace("{v1}", String.valueOf(amount)));',
        "key": "wood-enabled",
        "pl": "&aDrewno pod ziemią: włączone ({v1})",
        "en": "&aWood if underground: enabled ({v1})"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aDrewno pod ziemią: " + amount : "§aWood if underground: " + amount);',
        "new": 'sender.sendMessage(getMsg("wood-amount").replace("{v1}", String.valueOf(amount)));',
        "key": "wood-amount",
        "pl": "&aDrewno pod ziemią: {v1}",
        "en": "&aWood if underground: {v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aTryb timera ustawiony na: §e" + mode : "§aTimer mode set to: §e" + mode);',
        "new": 'sender.sendMessage(getMsg("timer-mode-set").replace("{v1}", String.valueOf(mode)));',
        "key": "timer-mode-set",
        "pl": "&aTryb timera ustawiony na: &e{v1}",
        "en": "&aTimer mode set to: &e{v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aZasięg timera ustawiony na: §e" + scope : "§aTimer scope set to: §e" + scope);',
        "new": 'sender.sendMessage(getMsg("timer-scope-set").replace("{v1}", String.valueOf(scope)));',
        "key": "timer-scope-set",
        "pl": "&aZasięg timera ustawiony na: &e{v1}",
        "en": "&aTimer scope set to: &e{v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNieprawidłowy typ encji: " + args[3] : "§cInvalid entity type: " + args[3]);',
        "new": 'sender.sendMessage(getMsg("invalid-entity").replace("{v1}", String.valueOf(args[3])));',
        "key": "invalid-entity",
        "pl": "&cNieprawidłowy typ encji: {v1}",
        "en": "&cInvalid entity type: {v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNieprawidłowy advancement: " + args[3] : "§cInvalid advancement: " + args[3]);',
        "new": 'sender.sendMessage(getMsg("invalid-advancement").replace("{v1}", String.valueOf(args[3])));',
        "key": "invalid-advancement",
        "pl": "&cNieprawidłowy advancement: {v1}",
        "en": "&cInvalid advancement: {v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNieprawidłowy materiał bloku: " + args[3] : "§cInvalid block material: " + args[3]);',
        "new": 'sender.sendMessage(getMsg("invalid-block").replace("{v1}", String.valueOf(args[3])));',
        "key": "invalid-block",
        "pl": "&cNieprawidłowy materiał bloku: {v1}",
        "en": "&cInvalid block material: {v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNieprawidłowy materiał przedmiotu: " + args[3] : "§cInvalid item material: " + args[3]);',
        "new": 'sender.sendMessage(getMsg("invalid-item").replace("{v1}", String.valueOf(args[3])));',
        "key": "invalid-item",
        "pl": "&cNieprawidłowy materiał przedmiotu: {v1}",
        "en": "&cInvalid item material: {v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aCel timera ustawiony: §e" + goalType + " -> " + goalValue : "§aTimer goal set: §e" + goalType + " -> " + goalValue);',
        "new": 'sender.sendMessage(getMsg("timer-goal-set").replace("{v1}", String.valueOf(goalType)).replace("{v2}", String.valueOf(goalValue)));',
        "key": "timer-goal-set",
        "pl": "&aCel timera ustawiony: &e{v1} -> {v2}",
        "en": "&aTimer goal set: &e{v1} -> {v2}"
    },
    {
        "old": 'sender.sendMessage((isPl ? "§7Użyj §e/wr backup list " + (page + 1) + " §7aby zobaczyć następną stronę." : "§7Use §e/wr backup list " + (page + 1) + " §7for next page."));',
        "new": 'sender.sendMessage(getMsg("backup-list-next").replace("{v1}", String.valueOf(page + 1)));',
        "key": "backup-list-next",
        "pl": "&7Użyj &e/wr backup list {v1} &7aby zobaczyć następną stronę.",
        "en": "&7Use &e/wr backup list {v1} &7for next page."
    },
    {
        "old": 'sender.sendMessage(isPl ? "§cNieprawidłowy numer! Użyj 1-" + dirs.length : "§cInvalid number! Use 1-" + dirs.length);',
        "new": 'sender.sendMessage(getMsg("invalid-number-range").replace("{v1}", String.valueOf(dirs.length)));',
        "key": "invalid-number-range",
        "pl": "&cNieprawidłowy numer! Użyj 1-{v1}",
        "en": "&cInvalid number! Use 1-{v1}"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aUsunięto §e" + toDelete + " §anajstarszych kopii." : "§aDeleted §e" + toDelete + " §aoldest backup(s).");',
        "new": 'sender.sendMessage(getMsg("deleted-oldest").replace("{v1}", String.valueOf(toDelete)));',
        "key": "deleted-oldest",
        "pl": "&aUsunięto &e{v1} &anajstarszych kopii.",
        "en": "&aDeleted &e{v1} &aoldest backup(s)."
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aWszystkie §e" + count + " §akopii usunięte." : "§aAll §e" + count + " §abackup(s) deleted.");',
        "new": 'sender.sendMessage(getMsg("deleted-all").replace("{v1}", String.valueOf(count)));',
        "key": "deleted-all",
        "pl": "&aWszystkie &e{v1} &akopii usunięte.",
        "en": "&aAll &e{v1} &abackup(s) deleted."
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aLimit kopii ustawiony na: §e" + limit + " §7(zachowaj ostatnie " + limit + ")" : "§aBackup limit set to: §e" + limit + " §7(keep last " + limit + " backups)");',
        "new": 'sender.sendMessage(getMsg("backup-limit-set").replace("{v1}", String.valueOf(limit)));',
        "key": "backup-limit-set",
        "pl": "&aLimit kopii ustawiony na: &e{v1} &7(zachowaj ostatnie {v1})",
        "en": "&aBackup limit set to: &e{v1} &7(keep last {v1} backups)"
    },
    {
        "old": 'sender.sendMessage(isPl ? "§aLimit kopii ustawiony na: §e" + limit : "§aBackup limit set to: §e" + limit);',
        "new": 'sender.sendMessage(getMsg("backup-limit-set-basic").replace("{v1}", String.valueOf(limit)));',
        "key": "backup-limit-set-basic",
        "pl": "&aLimit kopii ustawiony na: &e{v1}",
        "en": "&aBackup limit set to: &e{v1}"
    }
]

def add_translation(key, pl_text, en_text, pl_appends, en_appends):
    if key == "player-not-found2":
        return # duplicate key
    pl_appends.append(f'{key}: "{pl_text}"')
    en_appends.append(f'{key}: "{en_text}"')

def main():
    with open(JAVA_FILE, "r", encoding="utf-8") as f:
        code = f.read()

    pl_appends = []
    en_appends = []

    count = 0
    for r in replacements:
        if r["old"] in code:
            code = code.replace(r["old"], r["new"])
            add_translation(r["key"], r["pl"], r["en"], pl_appends, en_appends)
            count += 1
        else:
            print(f"NOT FOUND: {r['old']}")

    with open(JAVA_FILE, "w", encoding="utf-8") as f:
        f.write(code)

    if pl_appends:
        with open(PL_YAML, "a", encoding="utf-8") as f:
            f.write("\n" + "\n".join(pl_appends) + "\n")
    if en_appends:
        with open(EN_YAML, "a", encoding="utf-8") as f:
            f.write("\n" + "\n".join(en_appends) + "\n")

    print(f"Replaced {count} lines with variables!")

if __name__ == "__main__":
    main()
