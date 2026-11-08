# Prumo PDF para Android: plano do projeto

Atualizado em 08/11/2026. Meta: o mesmo Prumo do iPhone (1.14.0) e do Windows (1.15.0), com tudo no
aparelho, os mesmos arquivos e o mesmo formato de biblioteca, em telefones, tablets e dobráveis.

## Linguagem e motores

| Parte | Escolha | Por quê |
|---|---|---|
| Linguagem | **Kotlin** (app) e C/C++ pelo NDK (motores de PDF) | Kotlin é a linguagem recomendada pelo Google para Android; os motores de PDF são bibliotecas em C/C++ |
| Interface | **Jetpack Compose** + Material 3, com o visual do Prumo (cartões cinza, ícones coloridos) | Uma só interface que se adapta a telefone, tablet e dobrável |
| Telas grandes e dobráveis | Classes de tamanho de janela + `androidx.window` (dobradiça e posturas) | Duas colunas (lista + documento) em tablet e dobrável aberto; nada cai sobre a dobradiça |
| Ler e desenhar PDF, texto, busca, destaques | **PDFium** (o mesmo do Windows, via pypdfium2) | Mesmo resultado nos dois apps; o `PdfRenderer` do Android não abre PDF com senha antes do Android 15 e não tem busca nem seleção de texto nas versões antigas |
| Gravar PDF (páginas, senha AES-256, otimizar, comprimir) | **qpdf** (o mesmo do Windows, via pikepdf) | Mesmo motor, mesmos arquivos; o código do qpdf já inclui um script para compilar com o NDK (`build-scripts/build-android-ndk`) |
| OCR | **Tesseract** (Tesseract4Android, Apache 2.0), os mesmos dados de idioma do Windows | Roda no aparelho, sem enviar nada. O ML Kit do Google foi descartado: a própria Google informa que ele envia dados de uso (aparelho, app, desempenho) |
| Páginas curvas | **ONNX Runtime** com o mesmo `uvdoc.onnx` do Windows | Mesmo modelo, mesmo resultado |
| Assinatura digital | BouncyCastle (CMS/PAdES) + Android Keystore | A chave do A1 fica no Keystore, sem poder ser exportada, com digital ou PIN a cada uso |
| Arquivos e nuvem | Pasta da biblioteca + Storage Access Framework (seletor do sistema) | O usuário escolhe de onde importar e para onde exportar |

Versões usadas na etapa 1 (as estáveis em 08/11/2026): Android Gradle Plugin 9.5.1, Gradle 9.8.2,
Kotlin 2.4.21, Compose BOM 2026.09.00, `targetSdk` 36 (exigência do Google Play desde 31/08/2026),
`minSdk` 26 (Android 8.0).

## Formatos iguais aos dos outros apps

- Biblioteca = uma pasta comum com os PDFs e subpastas. Nada proprietário: os PDFs abrem em qualquer programa.
- `.prumo/library.json` com os mesmos campos (`path`, `id`, `favorite`, `pending_ocr`, `is_ocr_result`,
  `ocr_copy_id`, `source_id`) e a mesma regra de junção: cada aparelho relê antes de gravar e mantém o que o
  outro mudou.
- `.prumo/resumos/` para os resumos (etapa 4).
- Catálogos de idiomas = os mesmos `locales/*.json` do iPhone e do Windows (chave em português, `{}` para a
  parte variável). Português, inglês, espanhol, francês, alemão, japonês, chinês simplificado e
  tradicional (Taiwan), norueguês, sueco, dinamarquês e finlandês.

## Etapas

Ordem igual à escolhida para o Windows: primeiro as funções, segurança no fim. Cada etapa termina com os
testes rodando no GitHub e o APK publicado em Releases ("ultima-versao").

1. **Base (0.1.0)**: projeto, GitHub Actions (compilar, testes, traduções, emulador de telefone, tablet e
   dobrável com capturas, APK em Releases); biblioteca em pasta com `.prumo/library.json`; tela inicial com
   todas as funções; listas (Todos, Recentes, Sem OCR, Com OCR, Favoritos), pastas, busca por nome;
   cartões com miniatura, estrela e menu ⋮ (renomear, mover, duplicar, favoritar, compartilhar, excluir com
   desfazer); importar pelo seletor do Android, "Abrir com" e "Compartilhar com o Prumo"; "Adicionar PDFs"
   nas pastas; leitor simples; Ajustes (idioma, versão, Color Software); 12 idiomas; tema claro e escuro.
2. **Motores e páginas (0.2.0)**: PDFium e qpdf compilados no CI; leitor próprio com PDFium (zoom, miniaturas,
   busca no PDF, PDF com senha); organizar páginas (arrastar com rolagem automática, girar, excluir,
   desfazer, ver página grande); remover, extrair, dividir, combinar, incluir páginas de outro PDF;
   seleção de vários deslizando o dedo; busca por texto na biblioteca.
3. **OCR (0.3.0)**: Tesseract em fila (continua com o app em segundo plano, com aviso na barra), cópia
   "- OCR", páginas para revisar, idioma do OCR seguindo o app.
4. **Leitor completo (0.4.0)**: destaques /Highlight nas 5 cores, barra de seleção, desfazer/refazer,
   modo Texto, resumo, cortar e girar (CropBox), numerar páginas.
5. **Nuvem e cópia de segurança (0.5.0)**: biblioteca numa pasta escolhida; teste de cada nuvem (Google
   Drive, OneDrive, Dropbox, outras) para ver qual deixa o Prumo usar uma pasta inteira; sincronização com
   iPhone e Windows pelo `.prumo/library.json`; cópia de segurança e restauração no mesmo formato; a
   biblioteca aparecendo no app Arquivos do Android.
6. **Escanear, converter, comprimir e páginas curvas (0.6.0)**: escanear com a câmera (como no iPhone;
   CameraX com recorte e correção de perspectiva no aparelho, sem o scanner do Google Play, que depende
   dos serviços do Google); fotos e imagens em PDF; texto e HTML; Word/Excel/
   PowerPoint (a definir: o Android não tem o Office para converter, e cada caminho tem limites);
   comprimir médio e alto com as mesmas regras; correção de curvatura com o UVDoc.
7. **Segurança e assinatura (0.7.0)**: bloqueio do app (digital, rosto ou PIN do aparelho), proteger e
   remover senha do PDF (AES-256), aviso das cópias sem senha, assinatura PAdES com A1 no Keystore, níveis
   P1/P2/P3, conferência das assinaturas com o pyHanko no CI.
8. **Publicação (1.0.0)**: chave de envio guardada nos segredos do GitHub, pacote AAB, ficha do Google Play
   (seção "Segurança dos dados": nenhuma coleta), teste interno e checklist de telas grandes do Google.

## Pontos em aberto (não confirmados)

- **Nuvem**: o Android só deixa um app usar uma pasta inteira de uma nuvem se o app daquela nuvem permitir
  ("árvore" no Storage Access Framework). Poucos provedores permitem. No iPhone, Google Drive e OneDrive
  não permitiram. Vai ser testado na etapa 5; se não der, fica a cópia de segurança (arquivo por arquivo,
  que todas aceitam) e a sincronização só onde for possível.
- **A3 (token)**: no Android depende de leitor USB e do driver do fabricante do token. Não está no plano;
  a etapa 7 explica no app, como no iPhone.
- **Office**: sem o Word/Excel/PowerPoint no aparelho, a conversão fica limitada (decisão na etapa 6).
- **Licença**: este repositório foi criado com a licença AGPL-3.0; o iPhone e o Windows usam MIT. As
  bibliotecas escolhidas acima funcionam com as duas. Se a ideia for a mesma licença dos outros apps, é só
  trocar o arquivo LICENSE.

## Testes no GitHub

- `i18n`: todo texto da interface tem tradução nos 11 idiomas (mesma regra do iPhone e do Windows).
- Testes de unidade (JVM): biblioteca, `.prumo/library.json`, nomes, idiomas.
- Testes no emulador (Android 15) em três aparelhos: telefone (Pixel 8), tablet (Pixel Tablet) e dobrável
  (Pixel Fold), com capturas de tela em português, inglês, alemão e japonês.
- Resultados na branch `ci-output`; APK em Releases "ultima-versao".
