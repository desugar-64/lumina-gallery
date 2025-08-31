**FILE OPS RULE:** Always check size first: `wc -l <file>`

**SIZE POLICY:**
- <100 lines: safe to read fully
- 100-699 lines: targeted slices only 
- ≥700 lines: NEVER read fully, use search + partial reads

**CORE COMMANDS:**
```bash
# Find & search before reading
rg -n -l 'pattern' <dir>          # locate files
rg -n -C5 'pattern' <file>        # search with context

# Partial reading only
head -n 50 <file>                 # top slice
sed -n '100,150p' <file>          # specific range
awk 'NR>=100 && NR<=150' <file>   # range alternative

# Minimal edits
sed -i '42s/.*/new line/' <file>  # single line replace
sed -i '100i\new line' <file>     # insert at line
```

**FLOW:** 1) Check size 2) Search/locate 3) Read targeted slices 4) Edit minimally

**AVOID:** Never `cat` files >100 lines, never read without size check