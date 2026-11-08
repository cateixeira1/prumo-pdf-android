"""
Verificação das traduções (roda no GitHub antes de compilar; também dá para rodar em qualquer computador com
Python 3: python3 tools/i18n_check.py). Mesma regra do Prumo para iPhone e Windows. Falha se:
1. algum texto da interface no código Kotlin não passa pela tradução (L/LF/LN) e não está em
   tools/i18n_ignore.txt;
2. algum texto passado a L/LF/LN não tem tradução em algum dos 11 idiomas (app/src/main/assets/locales);
3. alguma tradução tem um número de "{}" diferente do texto em português.
Com --missing, mostra só a lista dos textos sem tradução (um por linha), para completar os catálogos.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main" / "kotlin"
LOC = ROOT / "app" / "src" / "main" / "assets" / "locales"
LANGS = ["en", "es", "fr", "de", "ja", "zh-CN", "zh-TW", "nb", "sv", "da", "fi"]

STR = r'"((?:[^"\\]|\\.)*)"'
# L("..."), LF("...", ...), LN(n, "...", "...")
CALLS = [
    re.compile(r'\bL\(\s*' + STR + r'\s*\)'),
    re.compile(r'\bLF\(\s*' + STR),
    re.compile(r'\bLN\(\s*[^,()]+(?:\([^()]*\))?\s*,\s*' + STR + r'\s*,\s*' + STR),
]
# Texto que aparece na tela sem passar pela tradução.
RAW = [
    re.compile(r'\bText\(\s*' + STR),
    re.compile(r'\b(?:label|title|placeholder|message|confirm|subtitle)\s*=\s*' + STR),
    re.compile(r'\b(?:contentDescription)\s*=\s*' + STR),
]
LETTERS = re.compile(r"[A-Za-zÀ-ÿ]{2,}")


def unescape(s: str) -> str:
    return s.encode("utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8") if "\\" in s else s


def sources():
    """Código Kotlin sem as linhas de comentário (exemplos em comentários não são textos da interface)."""
    for p in sorted(SRC.rglob("*.kt")):
        lines = p.read_text(encoding="utf-8").splitlines()
        code = ["" if l.strip().startswith(("//", "*", "/*")) else l for l in lines]
        yield p, "\n".join(code)


def extract() -> dict:
    found = {}
    for p, text in sources():
        rel = str(p.relative_to(ROOT))
        for rx in CALLS:
            for m in rx.finditer(text):
                for g in m.groups():
                    if g is not None and "$" not in g:
                        found.setdefault(unescape(g), []).append(rel)
        for line in text.splitlines():
            if re.search(r"//\s*i18n\b", line):
                for g in re.findall(STR, line.split("//")[0]):
                    found.setdefault(unescape(g), []).append(rel)
    return found


def ignored() -> set:
    f = ROOT / "tools" / "i18n_ignore.txt"
    if not f.exists():
        return set()
    return {l.rstrip("\n") for l in f.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}


def unlocalized() -> list:
    out = []
    skip = ignored()
    for p, text in sources():
        rel = str(p.relative_to(ROOT))
        for n, line in enumerate(text.splitlines(), 1):
            code = line.split("//")[0]
            for rx in RAW:
                for m in rx.finditer(code):
                    s = unescape(m.group(1))
                    if "$" in s and not LETTERS.search(re.sub(r"\$\{[^}]*\}|\$\w+", "", s)):
                        continue
                    if not LETTERS.search(s) or s in skip:
                        continue
                    out.append(f"{rel}:{n}: {s!r}")
    return out


def main() -> int:
    only_missing = "--missing" in sys.argv
    strings = extract()
    problems = []
    missing = set()
    for lang in LANGS:
        cat = json.loads((LOC / f"{lang}.json").read_text(encoding="utf-8"))
        for s, where in sorted(strings.items()):
            if s not in cat:
                missing.add(s)
                problems.append(f"{lang}: falta tradução de {s!r} ({where[0]})")
        for k, v in cat.items():
            if k.count("{}") != v.count("{}"):
                problems.append(f"{lang}: {k!r} → {v!r} tem número diferente de {{}}")
    if only_missing:
        for s in sorted(missing):
            print(s)
        return 0
    problems = [f"sem tradução no código: {r}" for r in unlocalized()] + problems
    print(f"{len(strings)} textos da interface, {len(LANGS)} idiomas")
    for p in problems:
        print("ERRO:", p)
    print("OK" if not problems else f"{len(problems)} problema(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
