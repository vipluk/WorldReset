import os

def main():
    filepath = r"c:\Users\vipluk\.gemini\antigravity-ide\scratch\WorldRest\src\main\java\org\example\worldreset\Main.java"
    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.readlines()
    
    out = []
    for i, line in enumerate(lines):
        if "isPl ?" in line or "isPl?" in line or "isPl  ?" in line:
            out.append(f"{i+1}: {line.strip()}")
            
    with open("lines_to_translate.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(f"Found {len(out)} lines.")

if __name__ == "__main__":
    main()
