# Módulo votação

Responsável: Luis Felipe Coelho. Pacote `com.eleicao.votacao`.

Permite ao **Eleitor** consultar os candidatos de cada cargo, registrar a escolha e confirmar o voto.
Só o Eleitor acessa estas rotas (`/votacao/**`, regra em `RegrasDeAcesso`).

## Endpoints

| Método | Rota | O que faz | Status |
|---|---|---|---|
| GET | `/votacao/eleicoes/{eleicaoId}/cargos/{cargo}/candidatos` | Lista os candidatos do cargo, por número | 200 |
| POST | `/votacao/eleicoes/{eleicaoId}/voto/registro` | Confere a escolha e devolve o resumo. **Não grava** | 200 |
| POST | `/votacao/eleicoes/{eleicaoId}/voto/confirmacao` | Confere de novo e **grava** o voto. Não pode ser alterado | 201 |

Corpo de `registro` e `confirmacao`:

```json
{ "cargo": "GOVERNADOR", "candidatoId": 5 }
```

Os valores de `cargo` são `DEPUTADO_ESTADUAL`, `DEPUTADO_FEDERAL`, `SENADOR`, `GOVERNADOR` e `PRESIDENTE`.

## Fluxo de votação

1. O eleitor escolhe um candidato para o cargo e chama `registro`. O sistema devolve o resumo
   (candidato, número, partido), sem gravar nada.
2. Se o eleitor confirmar, chama `confirmacao` com o mesmo corpo. Só então o voto é gravado.

A confirmação revalida tudo, porque a eleição pode ter sido encerrada entre as duas chamadas.

## Regras de negócio

| Regra | Onde é verificada | Erro |
|---|---|---|
| A eleição precisa estar `ABERTA` | `EleicaoApi.exigirAberta` | 422 |
| Quem vota é um eleitor | perfil `ELEITOR` (auth) + `EleitorApi.buscarPorUsuario` | 403 / 404 |
| O candidato concorre nesta eleição | `VotacaoService.exigirCandidatura` | 422 |
| O candidato concorre ao cargo escolhido | `VotacaoService.exigirCandidatura` | 422 |
| Um voto por cargo, por eleição e turno | `VotacaoService` + `UNIQUE (eleicao_id, eleitor_id, cargo)` em `participacao` | 409 |
| Voto não pode ser alterado | não há endpoint de alteração | — |

## Sigilo do voto

A confirmação grava dois registros na mesma transação:

- `participacao` (eleição, eleitor, cargo, data/hora): impede voto duplicado, mas não sabe em quem o eleitor votou.
- `voto` (candidato, UUID aleatório): conta os votos, mas não tem `eleitor_id` nem data/hora.

O `ComprovanteVotoResponse` devolve só eleição, cargo e data/hora da confirmação. Ele não informa o candidato.

## Contagem para a apuração

`ContagemVotosService` implementa `ContagemVotosApi.contarPorCargo(eleicaoId, cargo)`, usada pelo módulo
`apuracao`. Devolve um item por candidato do cargo, inclusive os que estão com zero votos.

## Testes

- Unitários: `VotacaoServiceTest` (registro, confirmação, regras e a trava do banco) e `ContagemVotosServiceTest`.
- Integração: o fluxo completo (votar com a eleição aberta, voto bloqueado depois do encerramento) é coberto por
  `EleicaoApuracaoIT`, que usa o módulo `eleicao` real.
