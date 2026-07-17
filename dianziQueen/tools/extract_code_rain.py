import re
import pathlib

root = pathlib.Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/dianziqueen/app/CodeRainPhrases.kt"
raw_dir = root / "app/src/main/res/raw"
text = src.read_text(encoding="utf-8")
for name in ["RAW_BEFORE", "RAW_AFTER"]:
    m = re.search(rf'private const val {name} = """(.*?)"""', text, re.S)
    if not m:
        raise SystemExit(f"missing {name}")
    lines = [ln.strip() for ln in m.group(1).splitlines() if ln.strip()]
    out = raw_dir / (name.replace("RAW_", "code_rain_").lower() + ".txt")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{name}: {len(lines)} -> {out.name}")
