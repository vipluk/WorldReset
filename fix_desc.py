import os

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

old_code = ".getDescription().getVersion()"
new_code = ".getPluginMeta().getVersion()"

code = code.replace(old_code, new_code)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

print("getDescription replaced!")
