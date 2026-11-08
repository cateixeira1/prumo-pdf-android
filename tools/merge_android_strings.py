"""
Junta os textos de tools/locales_android.json (só do Android) aos catálogos de app/src/main/assets/locales.
Os catálogos começaram como cópia dos do Prumo para iPhone (scripts/locales); rodar de novo não muda nada.
Uso: python3 tools/merge_android_strings.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOC = ROOT / "app" / "src" / "main" / "assets" / "locales"
LANGS = ["en", "es", "fr", "de", "ja", "zh-CN", "zh-TW", "nb", "sv", "da", "fi"]

extra = json.loads((ROOT / "tools" / "locales_android.json").read_text(encoding="utf-8"))
extra.pop("_leia", None)
for i, lang in enumerate(LANGS):
    p = LOC / f"{lang}.json"
    cat = json.loads(p.read_text(encoding="utf-8"))
    for k, values in extra.items():
        assert len(values) == len(LANGS), k
        cat[k] = values[i]
    p.write_text(json.dumps(cat, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(lang, len(cat))
