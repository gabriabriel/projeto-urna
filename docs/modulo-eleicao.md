# Módulos eleição e apuração

Responsável: Breno Freitas. Pacotes `com.eleicao.eleicao` e `com.eleicao.apuracao`.

- **eleicao:** cadastro da eleição (data, turno, horários) e seu ciclo de vida: abrir e encerrar a votação.
- **apuracao:** acompanhamento da votação (apuração parcial) e resultado final.

## 1. Arquitetura

```
eleicao/
├── api/          EleicaoApi (contrato para os outros módulos), EleicaoEncerradaEvent
├── controller/   EleicaoController
├── service/      EleicaoService (casos de uso do Chefe), EleicaoApiImpl (implementa EleicaoApi)
├── repository/   EleicaoRepository
├── domain/       Eleicao
└── dto/          EleicaoRequest, EleicaoResponse, SituacaoEleicaoResponse

apuracao/
├── controller/   ApuracaoController
├── service/      ApuracaoService, CalculoApuracao (cálculo puro de percentuais e vencedor)
├── repository/   ApuracaoRepository, ResultadoCandidatoRepository
├── domain/       Apuracao, ResultadoCandidato, SituacaoCargo, TipoApuracao
└── dto/          ApuracaoResponse, ResultadoCargoResponse, ResultadoCandidatoResponse
```

O status da eleição (`StatusEleicao`: `AGENDADA`, `ABERTA`, `ENCERRADA`) fica em `shared/domain`, pois é usado
por vários módulos.

### Comunicação com os outros módulos

| Módulo | Direção | Interface | Uso |
|---|---|---|---|
| `eleicao` | Oferece | `EleicaoApi` | `buscar`, `exigirAgendada`, `exigirAberta` (cadastro, votação e apuração) |
| `eleicao` | Publica | `EleicaoEncerradaEvent` | avisa que a votação foi encerrada |
| `eleicao` | Usa | `CandidatoApi` (cadastro) | contar candidatos por cargo (abrir, excluir, situação) |
| `apuracao` | Escuta | `EleicaoEncerradaEvent` | grava o resultado final no encerramento |
| `apuracao` | Usa | `EleicaoApi`, `CandidatoApi`, `EleitorApi`, `ContagemVotosApi` (votação), `UsuarioLogadoProvider` (auth) | status da eleição, candidatos, contagem de votos e quem está consultando |

O evento é **síncrono e na mesma transação** do encerramento: se a gravação do resultado falhar, o encerramento
também é desfeito. O módulo `eleicao` não conhece o módulo `apuracao`.

A associação candidato ↔ eleição é a própria coluna `candidato.eleicao_id` (módulo cadastro, `POST /candidatos`
com `eleicaoId`): não existe tabela de associação separada.

## 2. Endpoints

### Eleição (`/eleicoes`)

| Método | Rota | O que faz | Perfil | Status |
|---|---|---|---|---|
| POST | `/eleicoes` | Cadastra a eleição. Nasce `AGENDADA` | Chefe | 201 + `Location` |
| GET | `/eleicoes?status=` | Lista (filtro opcional por status) | qualquer logado | 200 |
| GET | `/eleicoes/{id}` | Consulta a eleição | qualquer logado | 200 |
| GET | `/eleicoes/{id}/situacao` | Status, quando abriu/encerrou e candidatos por cargo | qualquer logado | 200 |
| PUT | `/eleicoes/{id}` | Altera data, turno e horários (só `AGENDADA`) | Chefe | 200 |
| DELETE | `/eleicoes/{id}` | Exclui (só `AGENDADA` e sem candidatos) | Chefe | 204 |
| POST | `/eleicoes/{id}/abertura` | `AGENDADA` → `ABERTA` (exige 2+ candidatos) | Chefe | 200 |
| POST | `/eleicoes/{id}/encerramento` | `ABERTA` → `ENCERRADA` e grava o resultado final | Chefe | 200 |

Corpo de `POST` e `PUT`:

```json
{ "data": "2026-12-06", "turno": 1, "horarioAbertura": "08:00:00", "horarioEncerramento": "17:00:00" }
```

### Apuração (`/apuracao`)

| Método | Rota | O que faz | Status |
|---|---|---|---|
| GET | `/apuracao/eleicoes/{id}/parcial?cargo=` | Acompanhamento (eleição `ABERTA`), calculado na hora | 200 |
| GET | `/apuracao/eleicoes/{id}/resultado?cargo=` | Resultado final (eleição `ENCERRADA`), lido do que foi gravado | 200 |

`cargo` é opcional. Sem ele, vêm todos os cargos que têm candidatos. Exemplo de resposta:

```json
{
  "eleicaoId": 1, "statusEleicao": "ENCERRADA", "tipo": "FINAL",
  "apuradaEm": "2026-12-06T17:00:03", "totalVotos": 3,
  "cargos": [{
    "cargo": "GOVERNADOR", "totalVotos": 3, "situacao": "VENCEDOR",
    "vencedor": { "candidatoId": 2, "numero": 22, "nome": "Beto", "partidoSigla": "PL", "partidoNumero": 22,
                  "votos": 2, "percentual": 66.67, "maisVotado": true },
    "empatados": [],
    "candidatos": [ { "...": "Beto, 2 votos, 66.67" }, { "...": "Ana, 1 voto, 33.33" } ]
  }]
}
```

`situacao` do cargo: `VENCEDOR` (um mais votado), `EMPATE` (os empatados vêm em `empatados`), `SEM_VOTOS`,
`SEM_CANDIDATOS`.

## 3. Regras de negócio

| Regra | Onde | Erro |
|---|---|---|
| Data não pode ser passada; turno 1 ou 2; campos obrigatórios | `EleicaoRequest` (Bean Validation) | 400 |
| Encerramento depois da abertura | `EleicaoService` + `CHECK` no banco | 422 |
| Uma eleição por data e turno | `EleicaoService` + `UNIQUE (data, turno)` | 409 |
| Alterar ou excluir só com `AGENDADA` | `EleicaoService` | 422 |
| Excluir só sem candidatos | `EleicaoService` (via `CandidatoApi`) + FK no banco | 409 |
| Abrir só com `AGENDADA` e pelo menos 2 candidatos | `EleicaoService` | 422 |
| Encerrar só com `ABERTA` | `EleicaoService` | 422 |
| Não há volta de status | não existe transição para trás | — |
| Parcial só com `ABERTA` (não grava nada) | `ApuracaoService` via `EleicaoApi.exigirAberta` | 422 |
| Resultado final só com `ENCERRADA` | `ApuracaoService` | 422 |
| Candidato acompanha só o cargo da própria candidatura | `ApuracaoService` (regra de dados) | 403 |
| Resultado final gravado uma única vez | `ApuracaoService` (idempotente) + `UNIQUE (eleicao_id)` | — |
| Eleição inexistente | `EleicaoApi` / `EleicaoService` | 404 |

Mudanças de status usam `SELECT ... FOR UPDATE` (`EleicaoRepository.buscarParaAlterar`), para que duas requisições
simultâneas do Chefe não passem pela mesma checagem ao mesmo tempo.

### Quem vê o quê na apuração

| Quem | Parcial (eleição aberta) | Resultado final |
|---|---|---|
| Chefe da Seção | todos os cargos | todos os cargos |
| Eleitor | todos os cargos | todos os cargos |
| Eleitor que é candidato nesta eleição | **só o cargo da candidatura** (outro cargo → 403) | todos os cargos |

O resultado final é público para qualquer usuário logado, como no mundo real; a restrição do enunciado
("acompanhamento restrito ao cargo") se aplica ao acompanhamento durante a votação.

### Cálculo

- `percentual` = votos do candidato ÷ votos do cargo × 100, com 2 casas (arredondamento `HALF_UP`). Sem votos: `0.00`.
- `maisVotado` = tem o maior número de votos do cargo **e** esse número é maior que zero. Dois ou mais = empate.
- Candidatos ordenados do mais para o menos votado; em igualdade, pelo número.

## 4. Testes

| Tipo | Classe | O que cobre |
|---|---|---|
| Unitário | `EleicaoServiceTest` | CRUD, validação de horários, duplicidade, bloqueio fora de `AGENDADA`, abrir (0, 1 e 2 candidatos), encerrar e publicação do evento |
| Unitário | `EleicaoApiImplTest` | contrato `exigirAgendada` / `exigirAberta` / 404 |
| Unitário | `ApuracaoServiceTest` | parcial por perfil, restrição do candidato (403), resultado antes do encerramento (422), gravação e leitura do resultado final, idempotência |
| Unitário | `CalculoApuracaoTest` | percentuais, vencedor, empate, sem votos, sem candidatos |
| Integração | `EleicaoApuracaoIT` | fluxo completo com auth, cadastro e votação reais no MySQL; casos de borda; perfis 401/403 |
