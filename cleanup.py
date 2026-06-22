import os
import re

def strip_comments(code):
    # Regex to match string literals or comments
    # We capture strings in group 1, and comments in group 2 or 3
    # That way we can replace comments with nothing, and strings with themselves
    pattern = re.compile(
        r'(".*?"|\'.*?\')|(/\*.*?\*/)|(//.*?$)',
        re.DOTALL | re.MULTILINE
    )
    
    def replacer(match):
        if match.group(1) is not None:
            # It's a string, keep it
            return match.group(1)
        else:
            # It's a comment, remove it
            return ""

    return pattern.sub(replacer, code)

def main():
    root_dir = r"C:\Users\Shiro\AndroidStudioProjects\Monomail\app\src\main\java"
    count = 0
    for subdir, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                path = os.path.join(subdir, file)
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                new_content = strip_comments(content)
                
                # Remove extra blank lines created by comment removal
                new_content = re.sub(r'\n\s*\n', '\n\n', new_content)
                
                if new_content != content:
                    with open(path, "w", encoding="utf-8") as f:
                        f.write(new_content)
                    count += 1
    print(f"Cleaned {count} files.")

if __name__ == "__main__":
    main()
