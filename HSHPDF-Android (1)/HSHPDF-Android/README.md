# HSHPDF para Android (nativo: Kotlin + Jetpack Compose + MuPDF)

**Criador da fonte:** Henrique de Souza Henrique
**Coautor e assistente de IA:** Claude (Anthropic)

## Versão 0.2 — o que tem
**Leitura**
- Abre **PDF, EPUB, XPS, CBZ, CBR (RAR) e imagens**; também por "Abrir com" e "Compartilhar" de outros apps.
- Rolagem contínua, zoom com dois dedos / toque duplo, miniaturas (menu ⋮), buscar texto (PDF/EPUB), ir para a página.
- **Tela cheia**: esconde hora, bateria e botões do sistema (deslize na borda para ver por um instante). Liga/desliga no menu ⋮.
- **Somente leitura** (menu ⋮; opcional automático para quadrinhos/EPUB em Ajustes).

**Edição (botão "Editar")** — tudo desfaz/refaz (↶ ↷), tudo fica editável:
- Selecionar (mover, redimensionar pelas alças, mudar cor/opacidade/espessura, duplicar, excluir).
- Lápis suave, marca-texto, **borracha precisa** (corta o traço só onde passa).
- Formas: retângulo, elipse, **círculo**, linha, seta, **seta curva**, polígono, polilinha (com preenchimento).
- Texto, **notas post-it** (5 cores, redimensionável), **carimbos** (prontos, criar de texto, importar imagem), **imagem com opacidade**.
- **Salvamento automático**: ao reabrir o arquivo, suas edições voltam (menu ⋮ → "Descartar edições" volta ao original).

**Páginas (botão "Páginas")**: grade com seleção múltipla (toque longo): girar, excluir, duplicar, mover, extrair,
inserir em branco, **inserir só algumas páginas de outro arquivo** (ex.: 1-5,8), desfazer/refazer.

**Exportar / converter (menu ⋮ → Exportar)**: PDF, CBZ, EPUB (layout fixo) ou ZIP de imagens; todas as páginas,
só as selecionadas ou um intervalo; **qualidade JPEG e tamanho** à escolha (resolve o problema dos arquivos inchados).
**Lote** (tela inicial): vários arquivos de uma vez → PDF/CBZ/EPUB, ou juntar tudo em um só. **Imagens → PDF** e **Novo em branco** na tela inicial.

**Outros**: abas (vários documentos), captura de tela de uma área (salvar/compartilhar/copiar), som de conclusão, tema claro/escuro e cor de destaque.

## O que NÃO tem (por limitação técnica, não por esquecimento)
- Editar o texto original do PDF; PDF → Word; senha/criptografia; OCR.
- PDFs exportados guardam cada página como **imagem JPEG** (sem texto selecionável). O arquivo original nunca é alterado.
- Anotações que já vêm dentro de um PDF aparecem, mas não são editáveis (só as feitas no app).
- Não cria CBR (RAR é formato fechado): use CBZ. CB7/7z ainda não.
- Busca de texto só em PDF/EPUB (CBZ/CBR são imagens). Realce de busca pode ficar deslocado em páginas giradas.

## Como gerar o APK sem instalar nada (GitHub Actions)
1. Crie um repositório no GitHub e envie **todo o conteúdo desta pasta** (inclusive a pasta oculta `.github`).
2. Aba **Actions** → "Compilar APK" roda sozinho (ou *Run workflow*). Leva uns 5–10 minutos.
3. Quando ficar verde, baixe o artefato **HSHPDF-apk** → dentro está `app-debug.apk`.
4. Passe para o celular e instale (permita "fontes desconhecidas"). Instalar por cima mantém seus dados.

## Se a compilação falhar
Escrito sem poder compilar no ambiente de criação. Abra a execução ❌ → passo vermelho → copie as linhas que começam
com `e:` e envie ao Claude; costuma ser correção de uma linha.

## Observações
- MuPDF é **AGPL**: ok para uso pessoal; se um dia distribuir o app, a licença exige abrir o código.
- APK "debug": assinado com chave de teste, instalável, suficiente para uso pessoal.
