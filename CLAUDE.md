# Space Horizons

## STATUS.md é obrigatório

`STATUS.md` (na raiz) é o painel de onde o projeto está. Mantenha-o sempre atualizado:

- **Ao começar** uma etapa, fase ou spike: marque 🟡 em "Agora" e na tabela do tier.
- **Ao concluir**: marque ✅ só depois de verificado em jogo (autoteste ou print); código pronto sem verificar é 🧪. Tire de "Agora",
  ajuste "Próximos passos" e, se mudou, a "Última rodada verde" da regressão.
- **Ao surgir** pendência, risco ou decisão para o usuário: registre na seção correspondente; tire quando resolver.
- **Plano novo em `docs/`**: acrescente-o ao "Mapa da documentação".
- Atualize a data de "Última atualização" e commite o `STATUS.md` junto com a mudança que o motivou.

Ao mesclar trabalho de um agente, confira se ele atualizou o `STATUS.md`; se não, atualize antes do merge.

## Autotestes

Rodam em `run-test/` (nunca em `run/`, que tem os mundos de quem joga). Regressão em paralelo: `./gradlew regression --console=plain`.
