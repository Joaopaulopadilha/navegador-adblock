# Navegador

Navegador Android simples, feito em Kotlin com WebView, com bloqueio de anúncios e foco em assistir vídeos no YouTube.

## Recursos

- Bloqueio de anúncios por lista de domínios e padrões de URL, com contador por página
- Script de bloqueio de anúncios do YouTube
- Reprodução em segundo plano (serviço em primeiro plano com notificação)
- Abas, navegação anônima (perfil separado, nada é salvo)
- Página inicial configurável: atalhos rápidos (arrastar para reordenar), YouTube, Google ou endereço próprio
- Pesquisas recentes, com opção de apagar uma a uma
- Tema claro, escuro ou do sistema
- Site para computador, lembrado por site
- O botão voltar do sistema navega no histórico e nunca fecha o navegador

## Como compilar

Requisitos: JDK 17+ e Android SDK (plataforma 37).

```
./gradlew assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. A build release usa a chave de debug; é para uso pessoal.
Para distribuir de verdade, configure sua própria assinatura.

## Aviso

- Projeto pessoal, não auditado. Não há indicador de cadeado/HTTPS na barra de endereço e não há atualização automática.
- Bloquear anúncios no YouTube pode ir contra os termos de uso do serviço. Use por sua conta.

## Créditos

- `app/src/main/assets/blocklist.txt` é derivada da lista "Peter Lowe's Ad and tracking server list"
  (https://pgl.yoyo.org/adservers/). Consulte o site para os termos de uso da lista.

## Licença

MIT. Veja [LICENSE](LICENSE).