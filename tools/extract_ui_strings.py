"""Extract every user-visible string literal from the compiled Kotlin UI.

The workbook must quote the app VERBATIM, so the catalog is generated from the
source rather than retyped - a retyped label is how a tester ends up looking for
a button that says something else.
"""
import glob
import re

OUT = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
       r"\system_modelling_audit\ui_strings.txt")
SRC = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)\IAS-ALGOCOM"
       r"\momompurin-live-manager\app\src\main\java\ph\edu\umak\momompurin")

BSLASH_U = chr(92) + "u"      # the two characters \ u
BSLASH_N = chr(92) + "n"

strings = set()
for path in glob.glob(SRC + "/**/*.kt", recursive=True):
    text = open(path, encoding="utf-8").read()
    for m in re.finditer(r'"((?:[^"\\\n]|' + re.escape(chr(92)) + r'.){2,140})"', text):
        t = m.group(1)
        if BSLASH_U in t or "$" in t or t.startswith("%"):
            continue
        if BSLASH_N in t:
            t = t.split(BSLASH_N)[0]
        strings.add(t.strip())

# Keep things that look like UI copy rather than routes/identifiers.
ui = []
for t in strings:
    if len(t) < 3:
        continue
    if re.fullmatch(r"[a-z0-9/{}._-]+", t):      # routes, ids, snake tokens
        continue
    if t.startswith(("owner/", "my-orders", "ph.edu")):
        continue
    if not re.search(r"[A-Za-z]{3}", t):
        continue
    ui.append(t)

ui.sort()
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(ui))
print(len(ui), "strings ->", OUT)
