# Prumo PDF para Android

PDFs e OCR no próprio aparelho, para telefones, tablets e dobráveis. Mesmo app do Prumo para iPhone
(cateixeira1/prumo-pdf-ios) e Windows (cateixeira1/prumo-pdf-windows): mesma biblioteca em pasta comum,
mesmo `.prumo/library.json`, mesmos catálogos de idiomas.

O plano completo, com as etapas, está em [PLANO.md](PLANO.md).

## Versão atual: 0.1.0 (etapa 1, base)

- Tela inicial com todas as funções (as que ainda não chegaram ao Android avisam isso ao tocar).
- Listas: Todos os PDFs, Recentes, Sem OCR, Com OCR, Favoritos; pastas e subpastas; busca pelo nome.
- Cartões com miniatura, número de páginas, estrela de favorito e menu ⋮ (ler, compartilhar, favoritar,
  renomear, mover, duplicar, excluir com "Desfazer"). Toque longo também abre o menu.
- Importar pelo seletor do Android, "Abrir com o Prumo PDF" e "Compartilhar" de outros apps.
- Pastas: criar, renomear, excluir (com "Desfazer"); "Adicionar PDFs" do aparelho ou de outra parte do
  Prumo (mover ou copiar), oferecido logo ao criar a pasta.
- Leitor simples (rolagem, pinça para zoom, toque duplo). Provisório: o leitor com PDFium vem na etapa 2.
- Telas grandes: duas colunas (lista à esquerda, documento à direita) em tablet e dobrável aberto; no
  dobrável em pé, cada coluna fica de um lado da dobradiça.
- 12 idiomas, com o idioma do app em Ajustes (no Android 13 ou mais novo, também em Configurações › Apps).
- Tema claro e escuro, seguindo o aparelho.

## Onde ficam os arquivos

Na pasta "Prumo PDF" dentro da área do app no aparelho. A biblioteca guarda os favoritos e as ligações
de OCR em `.prumo/library.json`, no mesmo formato dos outros apps. Nada é enviado para a internet: o app
nem pede permissão de internet.

## Compilar

Android Studio (ou só o JDK 17+ e o Android SDK com a API 37):

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
