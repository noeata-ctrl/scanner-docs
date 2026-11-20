# Scanner Docs

App Android para digitalizar documentos com a câmera e gerar PDFs, no estilo do CamScanner.

- Detecção automática das bordas e correção de perspectiva
- Ajuste manual dos cantos com lupa
- Filtros: Mágico, Original, Tons de cinza, Preto e branco, Clarear
- Importa fotos e PDFs; junta tudo num único PDF com várias folhas
- Reordenar, girar, recortar e excluir páginas
- Exporta PDF (A4, Carta ou tamanho da imagem) e JPG; salva em Downloads ou compartilha
- Funciona offline; nada sai do aparelho

## Instalar
Baixe `ScannerDocs.apk` na página **Releases** deste repositório, abra o arquivo no celular e permita "instalar apps desconhecidos" quando o Android pedir.

## Compilar
O GitHub Actions compila a cada push na branch `main` (`.github/workflows/build.yml`).
Localmente: `gradle assembleRelease` com o Android SDK instalado.
