with open("feature/inbox/src/main/java/com/shrivatsav/monomail/feature/inbox/InboxScreen.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "Column(modifier = Modifier.padding(26.dp)) {" in line:
        start_idx = i
    if "            // Performance warning dialog for adding 4th+ account" in line:
        end_idx = i - 2
        break

if start_idx != -1 and end_idx != -1:
    print(f"Found from {start_idx} to {end_idx}")
else:
    print("Not found")

