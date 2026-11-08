"""
Gera os PDFs de teste que os testes no emulador usam (app/src/androidTest/assets). Rodar de novo só se
mudar o que eles precisam ter: python3 tools/make_test_pdfs.py (precisa do pikepdf).

- protegido.pdf: 2 páginas com texto, criptografia AES-256 (a mesma do Prumo para Windows), senha "prumo123".
- contrato.pdf: 6 páginas de texto; "Cláusula décima" só na página 3 (busca no documento).
- numerado.pdf: 3 páginas com "N / 3" no rodapé (conferir a ordem depois de organizar).

Usam a fonte Helvetica padrão do PDF, com texto que qualquer leitor extrai. (Os PDFs gerados no
Android pelo PdfDocument guardam o texto da fonte do sistema como desenho: o PDFium não acha o texto.)
"""
from pathlib import Path

import pikepdf

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "androidTest" / "assets"


def esc(t: str) -> str:
    return t.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def page(pdf: pikepdf.Pdf, text: str, lines=(), footer: str = "") -> pikepdf.Dictionary:
    font = pdf.make_indirect(pikepdf.Dictionary(
        Type=pikepdf.Name.Font, Subtype=pikepdf.Name.Type1, BaseFont=pikepdf.Name.Helvetica,
        Encoding=pikepdf.Name.WinAnsiEncoding))
    ops = [f"BT /F1 18 Tf 72 760 Td ({esc(text)}) Tj ET"]
    y = 720
    for line in lines:
        ops.append(f"BT /F1 10 Tf 72 {y} Td ({esc(line)}) Tj ET")
        y -= 15
    if footer:
        ops.append(f"BT /F1 10 Tf 280 40 Td ({esc(footer)}) Tj ET")
    content = "\n".join(ops).encode("cp1252")
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

    body = [
        "As partes acima identificadas têm entre si justo e acertado o presente instrumento,",
        "que se regerá pelas cláusulas seguintes e pelas condições descritas neste documento.",
        "O pagamento será feito até o quinto dia útil de cada mês, por transferência bancária.",
        "Em caso de atraso, incidirá multa de dois por cento e juros de um por cento ao mês.",
    ]
    pdf = pikepdf.new()
    for n in range(1, 7):
        lines = [body[(i + n) % len(body)] for i in range(36)]
        if n == 3:
            lines[5] = "Cláusula décima: o contrato vale por trinta meses."
        pdf.pages.append(pikepdf.Page(page(pdf, "Contrato de locação", lines, f"{n} / 6")))
    pdf.save(OUT / "contrato.pdf")
    print("ok", OUT / "contrato.pdf")

    pdf = pikepdf.new()
    for n in range(1, 4):
        pdf.pages.append(pikepdf.Page(page(pdf, f"Recibo página {n}", body, f"{n} / 3")))
    pdf.save(OUT / "numerado.pdf")
    print("ok", OUT / "numerado.pdf")


if __name__ == "__main__":
    main()
