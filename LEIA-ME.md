# Prumo PDF para Android

PDFs e OCR no próprio aparelho, para telefones, tablets e dobráveis. Mesmo app do Prumo para iPhone
(cateixeira1/prumo-pdf-ios) e Windows (cateixeira1/prumo-pdf-windows): mesma biblioteca em pasta comum,
mesmo `.prumo/library.json`, mesmos catálogos de idiomas.

O plano completo, com as etapas, está em [PLANO.md](PLANO.md).

## Versão atual: 0.2.0 (etapa 2, motores e páginas)

- PDFium (o mesmo motor do Prumo para Windows) compilado no app: binários de
  github.com/bblanchon/pdfium-binaries, versão fixa (chromium/8138) e conferida pelo SHA-256 no Gradle;
  a ligação em C++ fica em `app/src/main/cpp`.
- Leitor próprio: páginas nítidas ao aproximar (redesenhadas com mais pixels), miniaturas das páginas
  (ao lado em tablet e dobrável, embaixo no telefone), busca no documento sem precisar de acentos, com os
  trechos destacados e "3 de 17", e PDFs com senha (a senha fica só na memória, não é gravada).
- Organizar páginas: tocar seleciona; tocar e segurar arrasta, com rolagem automática perto das bordas;
  girar, excluir, desfazer, ver a página grande (toque duplo ou "Ver página").
- Remover e extrair páginas escolhendo na grade (tocar, ou deslizar o dedo de lado sobre várias) ou
  digitando "1, 3-5, 10-fim"; dividir em partes; combinar PDFs (com a ordem); incluir páginas de outro PDF
  no início, no fim ou depois de uma página.
- "Desfazer última alteração" na tela do PDF (a versão anterior fica guardada).
- Cópia sem senha de um PDF com senha ("Remover senha").
- Busca da tela inicial também no texto dos PDFs; "Sem OCR" mostra os PDFs com páginas só de imagem
  (mesmas regras do iPhone).
- Seleção de vários PDFs nas listas (tocar ou deslizar o dedo de lado), para combinar, mover,
  compartilhar, favoritar ou excluir.

### 0.1.0 (etapa 1, base)

- Tela inicial com todas as funções, listas, pastas, cartões com miniatura, estrela e menu ⋮, importar,
  "Abrir com", "Compartilhar", excluir com "Desfazer", duas colunas em tablet e dobrável, 12 idiomas.

## Onde ficam os arquivos

Na pasta "Prumo PDF" dentro da área do app no aparelho. A biblioteca guarda os favoritos e as ligações
de OCR em `.prumo/library.json`, no mesmo formato dos outros apps. Nada é enviado para a internet: o app
nem pede permissão de internet.

## Compilar

Android Studio (ou só o JDK 17+ e o Android SDK com a API 37, o NDK 28.2.13676358 e o CMake 3.22.1).
Na primeira compilação, o Gradle baixa o PDFium do GitHub e confere o SHA-256:

    ./gradlew :app:assembleDebug        # app de teste
    ./gradlew :app:testDebugUnitTest    # testes de unidade
    python3 tools/i18n_check.py         # traduções

O GitHub Actions (`.github/workflows/android.yml`) faz tudo isso a cada mudança, roda os testes em três
emuladores (Pixel 8, Pixel Tablet e Pixel Fold, aberto e fechado), tira capturas em português, inglês,
alemão e japonês e publica:

- resultados e capturas na branch `ci-output`;
- o APK em Releases, "ultima-versao" (só quando todos os testes passam).

O APK de teste é assinado com a chave de teste do repositório (`ci/debug.keystore`, pública), para que
uma versão nova instale por cima da anterior. A publicação no Google Play (etapa 8) usa outra chave,
guardada nos segredos do GitHub.

## Idiomas

`app/src/main/assets/locales/*.json`: os mesmos catálogos do iPhone (chave = texto em português), mais
os textos que só o Android tem, guardados também em `tools/locales_android.json` e juntados por
`tools/merge_android_strings.py`. As traduções foram feitas sem revisão de quem fala cada língua.

Color Software
