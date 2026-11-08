"""
Gera os PDFs de teste que os testes no emulador usam (app/src/androidTest/assets). Rodar de novo só se
mudar o que eles precisam ter: python3 tools/make_test_pdfs.py (precisa do pikepdf).

- protegido.pdf: 2 páginas com texto, criptografia AES-256 (a mesma do Prumo para Windows), senha "prumo123".
"""
from pathlib import Path

import pikepdf

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "androidTest" / "assets"


def page(pdf: pikepdf.Pdf, text: str) -> pikepdf.Dictionary:
    font = pdf.make_indirect(pikepdf.Dictionary(Type=pikepdf.Name.Font, Subtype=pikepdf.Name.Type1, BaseFont=pikepdf.Name.Helvetica))
    content = f"BT /F1 18 Tf 72 760 Td ({text}) Tj ET".encode("latin-1")
    return pikepdf.Dictionary(
        Type=pikepdf.Name.Page,
        MediaBox=[0, 0, 595, 842],
        Resources=pikepdf.Dictionary(Font=pikepdf.Dictionary(F1=font)),
        Contents=pdf.make_stream(content),
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    pdf = pikepdf.new()
    for t in ["Documento protegido - pagina um com texto suficiente", "Segunda pagina do documento protegido"]:
        pdf.pages.append(pikepdf.Page(page(pdf, t)))
    pdf.save(OUT / "protegido.pdf", encryption=pikepdf.Encryption(user="prumo123", owner="prumo123-dono", R=6))
    print("ok", OUT / "protegido.pdf")


if __name__ == "__main__":
    main()
