# Joguinho - Defesa Espacial (Android 2D)

Shooter vertical: arraste para mover, o tiro e automatico.
- 3 naves (2 desbloqueaveis por recorde), ondas infinitas, chefe a cada 5 ondas
- Itens: escudo, arma (ate nivel 5), reparo, bomba, estrelas
- Combo multiplica a pontuacao; recorde salvo no aparelho

## Gerar o APK no GitHub
1. Crie um repositorio e envie o CONTEUDO desta pasta (Pastinha) na raiz
   (o arquivo .github/workflows/build.yml precisa ficar na raiz do repo).
2. Aba Actions > "Build APK" (roda sozinho no push, ou clique Run workflow).
3. Ao terminar, baixe o artefato "Joguinho-APK" (Joguinho.apk).
   Se criar uma tag (ex: v1.0), o APK tambem vai para Releases.
4. No celular, permita "instalar apps desconhecidos" e instale.

Build local: Android Studio (abrir esta pasta) ou `gradle assembleDebug` (Gradle 8.9, JDK 17).
