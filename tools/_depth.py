"""Find the line where a Python source file's bracket depth returns to 0 too early."""
import sys

path = sys.argv[1]
lines = open(path, encoding="utf-8").read().split("\n")

BACKSLASH = chr(92)

depth = 0
in_str = None
esc = False
depths = {}

for n, line in enumerate(lines, 1):
    i = 0
    while i < len(line):
        ch = line[i]
        if in_str:
            if esc:
                esc = False
            elif ch == BACKSLASH:
                esc = True
            elif ch == in_str:
                in_str = None
        else:
            if ch in "\"'":
                in_str = ch
            elif ch == "#":
                break
            elif ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
        i += 1
    depths[n] = depth
    # Python does not continue a single-quoted string across a newline.
    in_str = None
    esc = False

for n in range(2, len(lines) + 1):
    if depths[n] == 0 and depths[n - 1] != 0:
        print(f"depth returned to 0 at line {n}: {lines[n-1][:100]}")
print("final depth:", depths.get(len(lines)))
