import os

JAVA_FILE = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"

with open(JAVA_FILE, "r", encoding="utf-8") as f:
    code = f.read()

# Methods to remove
methods_to_remove = [
    "startAsyncWaterBiomeSearch",
    "findSafeSpawnInBiome",
    "findBiomeLocation",
    "savePlayerStates",
    "isHeightmapHitValid"
]

def remove_method(code, method_name):
    idx = code.find(f"private ")
    while idx != -1:
        end_idx = code.find("(", idx)
        if end_idx != -1:
            header = code[idx:end_idx]
            if method_name in header and "{" in code[end_idx:]:
                # Found the start of the method
                start_brace = code.find("{", end_idx)
                
                # We want to remove any javadoc / annotations before it too
                # Let's search backward for the last '}' or ';' or empty line
                start_delete = idx
                while start_delete > 0:
                    if code[start_delete-1] == '}':
                        break
                    if code[start_delete-1:start_delete+3] == "/**":
                        start_delete -= 1
                        break
                    start_delete -= 1
                
                # If there are annotations or comments right above it, this is a bit tricky
                # Just start deleting from the line containing 'private' for simplicity
                line_start = code.rfind("\n", 0, idx) + 1
                
                brace_count = 1
                current = start_brace + 1
                while current < len(code) and brace_count > 0:
                    if code[current] == '{':
                        brace_count += 1
                    elif code[current] == '}':
                        brace_count -= 1
                    current += 1
                
                if brace_count == 0:
                    # Found the end
                    end_delete = current
                    
                    # Also include the trailing newline if present
                    if end_delete < len(code) and code[end_delete] == '\n':
                        end_delete += 1
                    
                    # To be safe and clean javadocs, let's actually search backwards line by line from `line_start`
                    # If lines are purely annotations or javadoc, include them
                    lines_before = code[:line_start].split('\n')
                    while len(lines_before) > 0:
                        prev_line = lines_before[-1].strip()
                        if prev_line.startswith("@") or prev_line.startswith("/**") or prev_line.startswith("*") or prev_line == "":
                            line_start -= (len(lines_before[-1]) + 1)
                            lines_before.pop()
                        else:
                            break
                            
                    print(f"Removed {method_name}")
                    return code[:line_start] + "\n" + code[end_delete:]
        idx = code.find("private ", idx + 1)
    return code

for m in methods_to_remove:
    code = remove_method(code, m)

# Remove @SuppressWarnings("deprecation") at the class level (around line 54)
code = code.replace('@SuppressWarnings("deprecation")\npublic class Main', 'public class Main')

with open(JAVA_FILE, "w", encoding="utf-8") as f:
    f.write(code)

print("Cleanup complete!")
