import re
import os

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"
EN_YAML = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\resources\messages_en.yml"
PL_YAML = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\resources\messages_pl.yml"

en_appends = []
pl_appends = []

def add_translation(key, pl_text, en_text):
    pl_text = pl_text.replace("§", "&").replace('"', '\\"')
    en_text = en_text.replace("§", "&").replace('"', '\\"')
    pl_appends.append(f'{key}: "{pl_text}"')
    en_appends.append(f'{key}: "{en_text}"')

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

# Pattern 1: isPl ? "PL" : "EN"
pattern1 = re.compile(r'isPl \? "([^"]+)" : "([^"]+)"')
count = 1

def replacer1(match):
    global count
    pl_val = match.group(1)
    en_val = match.group(2)
    key = f"auto-msg-{count}"
    count += 1
    add_translation(key, pl_val, en_val)
    return f'getMsg("{key}")'

code = pattern1.sub(replacer1, code)

# Pattern 2: (isPl ? "PL" : "EN")
pattern2 = re.compile(r'\(isPl \? "([^"]+)" : "([^"]+)"\)')
def replacer2(match):
    global count
    pl_val = match.group(1)
    en_val = match.group(2)
    key = f"auto-msg-{count}"
    count += 1
    add_translation(key, pl_val, en_val)
    return f'getMsg("{key}")'

code = pattern2.sub(replacer2, code)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

if pl_appends:
    with open(PL_YAML, "a", encoding="utf-8") as f:
        f.write("\n" + "\n".join(pl_appends) + "\n")
if en_appends:
    with open(EN_YAML, "a", encoding="utf-8") as f:
        f.write("\n" + "\n".join(en_appends) + "\n")

print(f"Replaced {count - 1} simple string matches!")
