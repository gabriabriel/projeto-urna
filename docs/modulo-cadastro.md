# Módulo Cadastro (eleitores, candidatos e partidos)

Pacote `com.eleicao.cadastro`. Responsável pelo cadastro de eleitores e de candidaturas e pela consulta dos
partidos. É o módulo que mantém as informações dos participantes da eleição.

## 1. Funcionalidades

| Caso de uso | Perfil | Endpoint |
|---|---|---|
| Cadastrar, listar, consultar, alterar e excluir eleitores | Chefe da Seção | `/eleitores` |
| Cadastrar, alterar e excluir candidaturas | Chefe da Seção | `/candidatos` |
| Listar e consultar candidatos (filtros por eleição e cargo) | Qualquer usuário logado | `GET /candidatos` |
| Listar e consultar partidos | Qualquer usuário logado | `GET /partidos` |

Os **partidos** são os 30 partidos registrados no TSE e entram por migration (V3), sem CRUD: a manutenção de
partidos está fora do escopo do sistema, e a API apenas os consulta.

## 2. Arquitetura

```
cadastro/
├── controller/   EleitorController, CandidatoController, PartidoController
├── service/      EleitorService, CandidatoService, PartidoService (regras de negócio)
│                 EleitorApiImpl, CandidatoApiImpl (implementam os contratos para outros módulos)
├── repository/   EleitorRepository, CandidatoRepository, PartidoRepository
├── domain/       Eleitor, Candidato, Partido (entidades JPA)
├── dto/          *Request e *Response (entrada e saída da API)
└── api/          EleitorApi, CandidatoApi (interfaces públicas do módulo)
```

### Comunicação com os outros módulos

O módulo só conversa com outros módulos por interfaces (`api/`), nunca pelos repositórios ou serviços deles.

| Direção | Interface | Uso |
|---|---|---|
| Usa | `UsuarioApi` (módulo `auth`) | criar, alterar e remover o login do eleitor; a senha chega em texto puro e só o `auth` gera o hash BCrypt |
| Usa | `EleicaoApi` (módulo `eleicao`) | exigir que a eleição exista e esteja `AGENDADA` antes de mexer em candidaturas |
| Oferece | `EleitorApi` | `buscar(eleitorId)` e `buscarPorUsuario(usuarioId)` (votação e apuração) |
| Oferece | `CandidatoApi` | `buscar`, `listarPorEleicaoECargo` e `buscarPorEleitorEEleicao` (votação e apuração) |

As entidades devolvidas por `EleitorApi` e `CandidatoApi` já vêm com as associações carregadas
(`@EntityGraph`), pois a aplicação opera com `open-in-view` desligado.

## 3. Modelo de dados

```
usuario 1 ──── 0..1 eleitor 1 ──── 0..N candidato N ──── 1 partido
                                              N
                                              │
                                              1
                                           eleicao
```

**partido**: `id`, `numero` (único; número da legenda no TSE), `sigla` (única), `nome` (único).

**eleitor**: `id`, `usuario_id` (FK, único), `titulo_eleitor` (12 dígitos, único), `cpf` (11 dígitos, único).

**candidato**: `id`, `eleitor_id` (FK), `eleicao_id` (FK), `partido_id` (FK), `cargo`, `numero`.

| Restrição | Garantia |
|---|---|
| Um eleitor concorre a um único cargo por eleição | `UNIQUE (eleitor_id, eleicao_id)` |
| Número único por cargo dentro da eleição | `UNIQUE (eleicao_id, cargo, numero)` |
| Número entre 1 e 99999 | `CHECK` em `candidato.numero` |
| Só um eleitor pode ser candidato | `FK candidato.eleitor_id → eleitor.id` |
| CPF e título únicos | `UNIQUE` em `eleitor` |

**Candidato como eleitor com candidatura.** Não existe perfil `CANDIDATO`: o candidato é um eleitor (tem login com
perfil `ELEITOR` e uma linha em `eleitor`) que possui uma linha em `candidato` para a eleição. Assim, o candidato
também vota, como na vida real, e o acompanhamento restrito ao próprio cargo é decidido pela existência da candidatura.

## 4. Regras de negócio

Todas as regras ficam na camada de serviço; os controllers apenas recebem, validam o formato e devolvem a resposta.

**Eleitor**

- Título (12 dígitos) e CPF (11 dígitos) são únicos (409).
- O cadastro cria o login (perfil `ELEITOR`) e o eleitor **na mesma transação**: se um falhar, nenhum é gravado.
- Na alteração, manter o próprio CPF ou título não é conflito. A senha não é alterada por esta rota.
- Não se exclui eleitor que é candidato (409) nem eleitor que já votou (409): neste caso a chave estrangeira de
  `participacao` impede o `DELETE`, e o serviço converte o erro em uma resposta clara. O eleitor é removido antes do
  usuário, pois a FK `eleitor.usuario_id` exige essa ordem.

**Candidato**

- Cadastro, alteração e exclusão só são permitidos com a eleição **AGENDADA** (422).
- Eleitor, partido e eleição devem existir (404).
- Um eleitor tem **uma candidatura por eleição** (409).
- O **número do candidato começa com o número do partido**: partido 13 → 13, 131, 13456... (422).
- O número é único por cargo dentro da eleição (409); o mesmo número pode existir em cargos diferentes.
- A alteração troca partido, cargo e número; o eleitor e a eleição não mudam (para corrigir, exclui-se a
  candidatura e cadastra-se de novo).
- A exclusão não consulta votos: como só ocorre com a eleição agendada, e uma eleição nunca volta a esse estado,
  o candidato ainda não pode ter recebido votos.

## 5. Endpoints

### Eleitores (`/eleitores`)

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/eleitores` | Cadastra eleitor e login | 201 + `Location` | 400, 409 |
| GET | `/eleitores` | Lista em ordem alfabética | 200 | |
| GET | `/eleitores/{id}` | Consulta | 200 | 404 |
| PUT | `/eleitores/{id}` | Altera nome, login, título e CPF | 200 | 400, 404, 409 |
| DELETE | `/eleitores/{id}` | Exclui | 204 | 404, 409 |

### Candidatos (`/candidatos`)

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/candidatos` | Cadastra candidatura | 201 + `Location` | 400, 404, 409, 422 |
| GET | `/candidatos?eleicaoId=&cargo=` | Lista (filtros opcionais) | 200 | |
| GET | `/candidatos/{id}` | Consulta | 200 | 404 |
| PUT | `/candidatos/{id}` | Altera partido, cargo e número | 200 | 400, 404, 409, 422 |
| DELETE | `/candidatos/{id}` | Exclui | 204 | 404, 422 |

Cargos: `DEPUTADO_ESTADUAL`, `DEPUTADO_FEDERAL`, `SENADOR`, `GOVERNADOR`, `PRESIDENTE`.

### Partidos (`/partidos`)

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| GET | `/partidos` | Lista por número da legenda | 200 | |
| GET | `/partidos/{id}` | Consulta | 200 | 404 |

### Códigos de status

| Código | Quando |
|---|---|
| 400 | Dados inválidos (validação de campos, JSON malformado, cargo inexistente) |
| 401 | Sem token, token inválido ou expirado |
| 403 | Perfil sem permissão para a rota |
| 404 | Recurso não encontrado |
| 409 | Conflito (duplicidade, vínculo que impede a exclusão) |
| 422 | Regra de negócio violada (eleição não agendada, número fora do prefixo do partido) |

As respostas de erro seguem um formato único (`timestamp`, `status`, `erro`, `mensagem`, `caminho`, `detalhes`),
produzido por um `GlobalExceptionHandler` que converte `NotFoundException`, `ConflictException` e
`BusinessRuleException`.

## 6. Controle de acesso

A autorização por perfil é configurada no módulo `auth` (`RegrasDeAcesso`); os serviços do cadastro não conhecem
perfis e tratam apenas as regras de negócio.

| Rota | Perfil |
|---|---|
| `/eleitores/**` | Chefe da Seção |
| `POST`, `PUT`, `DELETE` em `/candidatos` | Chefe da Seção |
| `GET /candidatos`, `GET /partidos` | Qualquer usuário logado (o eleitor precisa consultá-los para votar) |

## 7. Exemplos de requisição

Cadastro de eleitor (`POST /eleitores`):

```json
{
  "nome": "Maria Silva",
  "login": "maria",
  "senha": "senha123",
  "tituloEleitor": "123456789012",
  "cpf": "12345678901"
}
```

Resposta (201): a senha nunca é devolvida.

```json
{ "id": 1, "nome": "Maria Silva", "login": "maria", "tituloEleitor": "123456789012", "cpf": "12345678901" }
```

Cadastro de candidato (`POST /candidatos`), pelo PT (partido 4, número 13):

```json
{ "eleitorId": 1, "eleicaoId": 1, "partidoId": 4, "cargo": "GOVERNADOR", "numero": 13 }
```

Resposta (201):

```json
{ "id": 1, "eleitorId": 1, "nome": "Maria Silva", "partidoSigla": "PT", "partidoNumero": 13,
  "cargo": "GOVERNADOR", "numero": 13, "eleicaoId": 1 }
```

O CPF e o título não aparecem na resposta do candidato, pois qualquer usuário logado pode consultá-la.

## 8. Testes

| Classe | Tipo | Cobertura |
|---|---|---|
| `EleitorServiceTest` | unitário (14) | criação, duplicidade de CPF e título, alteração (inclusive manter o próprio CPF), exclusão com candidatura e com votação, ordem delete → flush → remoção do usuário |
| `CandidatoServiceTest` | unitário (24 métodos, 31 execuções) | criação, regras do número e do partido (testes parametrizados), eleição não agendada, referências inexistentes, alteração, exclusão e os quatro filtros de listagem |
| `PartidoServiceTest` | unitário (3) | listagem ordenada e consulta por id |
| `CadastroIT` | integração (12) | requisições HTTP reais com JWT sobre MySQL em container: perfis (401 e 403), BCrypt no banco, atomicidade do cadastro, regras do candidato, eleição aberta, exclusão com chave estrangeira |

Os testes unitários usam mocks (Mockito) e verificam somente a regra de negócio. Os de integração sobem o Spring
completo com MySQL real (Testcontainers) e verificam as camadas, o banco e a segurança em conjunto. Comandos:
`mvn test` (unitários) e `mvn verify` (inclui integração, que exige o Docker em execução).

## 9. Decisões de projeto

| Decisão | Motivo |
|---|---|
| Regra de negócio apenas no service | Pode ser reutilizada e testada sem subir o servidor; o controller só traduz HTTP |
| DTOs na API, entidades no domínio | Evita expor dados sensíveis (CPF, hash) e desacopla a API do modelo do banco |
| Comunicação por interfaces (`api/`) | Inversão de dependência: o módulo é testado com mocks sem depender da implementação dos demais |
| Validação do número do candidato no service | A regra depende do partido, que só o service carrega; o DTO conhece apenas os campos da requisição |
| Verificação no service **e** `UNIQUE` no banco | O service devolve mensagens claras; o banco é a barreira final contra requisições simultâneas |
| Criação do login e do eleitor na mesma transação | Atomicidade: nunca existe login órfão nem eleitor sem login |
| Partidos por migration, com número da legenda | Dados de referência estáveis; o número alimenta a regra de numeração dos candidatos |
| Perfil `stub` provisório (`cadastro/dev`) | Permitiu executar o módulo antes da integração do módulo `eleicao`; **removido** quando a `EleicaoApi` real entrou |
