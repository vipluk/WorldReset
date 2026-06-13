import re

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    lines = f.readlines()

out_lines = []
for i, line in enumerate(lines):
    if "boolean isPl =" in line or "boolean isPl2 =" in line:
        var_name = "isPl2" if "isPl2" in line else "isPl"
        
        is_used = False
        for j in range(i + 1, min(i + 300, len(lines))):
            # If the line is another declaration of the same var, don't count it as a usage
            if f"boolean {var_name} =" in lines[j]:
                continue
                
            if re.search(rf'\b{var_name}\b', lines[j]):
                is_used = True
                break
                
        if not is_used:
            print(f"Removing unused variable at line {i+1}: {line.strip()}")
            continue 
            
    out_lines.append(line)

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.writelines(out_lines)

print("Done removing unused variables!")
