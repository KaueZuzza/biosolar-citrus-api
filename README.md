# 🍊☀️ BioSolar Citrus API

**Automação Hidro-Energética de Pomares de Citros** · Hackathon V JTI 2026

> Um sistema de automação agrícola capaz de **monitorar → interpretar → automatizar → proteger → informar** os recursos hídricos de uma fazenda de citros.

---

## Sobre

O BioSolar Citrus é uma aplicação Full Stack com IoT simulado que equilibra, em tempo real,
**umidade do solo + disponibilidade hídrica + acionamento dos aspersores + consumo energético**
em um pomar com quatro talhões (laranja e limão) abastecidos por um reservatório central.

- **Backend (Java 21 + Spring Boot 3.5)**: API REST, simulador de sensores, motor de regras autônomo, motor de decisão e persistência em **PostgreSQL**.
- **Frontend (HTML + CSS + JavaScript puro)**: dashboard de automação agroindustrial, responsivo, acessível, com tema claro/escuro e **sem dependências externas** (funciona offline no dia da apresentação).

## Problema

No verão amazônico, a estiagem exige manejo hídrico preciso. Sem controle, a irrigação esgota o
reservatório, aumenta o consumo de água e de energia, expõe os motores a operação inadequada e
compromete a produtividade do pomar.

## Solução

Um **Centro Inteligente de Operação Hidro-Energética** em que o **servidor** é a fonte da verdade:

| Pergunta da operação | Onde o sistema responde |
|---|---|
| O que está acontecendo? Onde? Por quê? | **Motor de Decisão** (calculado no backend) |
| O sistema tomou alguma decisão automática? | Motor de Decisão + **Histórico de eventos** |
| Existe risco? O operador precisa agir? | Motor de Decisão, **Alertas ativos** e notificações |
| Qual o impacto da decisão? | Consumo de água/energia, cobertura solar e **autonomia do reservatório** |
| Como está a fazenda em um número? | **Índice Hidro-Energético** (fórmula aberta) |

## Arquitetura

```text
┌──────────────────────────── Navegador ────────────────────────────┐
│  Dashboard (frontend/)  consulta → exibe → envia comandos           │
│  polling: /telemetria 1,5 s · /eventos 3 s · /historico 5 s         │
│  localStorage: SOMENTE preferências visuais (tema, fonte, contraste)│
└───────────────────────────────┬────────────────────────────────────┘
                                │ HTTP/JSON (REST)
┌───────────────────────────────▼──────────── Spring Boot (Java 21) ─┐
│ controller/  →  service/  →  model/ (agregado Fazenda, entidades)   │
│                   │  MotorRegras (P1 > P2 > P3 > P4)                │
│                   │  MotorDecisaoService · IndiceHidroEnergetico     │
│ simulation/  SimuladorFazenda: a cada 1 s avança o tempo da fazenda │
│              minuto a minuto e reavalia as regras (como um CLP)     │
│ repository/ (Spring Data JPA)   exception/ (erros padronizados)     │
└───────────────────────────────┬────────────────────────────────────┘
                                │ JDBC + Flyway (schema versionado)
                     ┌──────────▼──────────┐
                     │  PostgreSQL 17      │  talhao · reservatorio · estado_simulacao
                     │  (banco "biosolar") │  evento · leitura_telemetria · leitura_talhao
                     └─────────────────────┘
```

**Por que a regra nunca depende do navegador:** o ciclo de simulação e o motor de regras rodam no
servidor (`@Scheduled`). Fechar ou recarregar a página não interrompe a automação. Ao reiniciar a
API, o estado é **restaurado do banco** e as regras de segurança são reavaliadas.

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16 (Web, Data JPA, Validation), Maven Wrapper |
| Banco | PostgreSQL 17 + Flyway (migração `V1__criar_tabelas.sql`); H2 apenas em testes e no perfil de contingência |
| Frontend | HTML5 semântico, CSS (tokens de tema), JavaScript sem build, gráficos SVG próprios |
| Acessibilidade | ARIA, foco visível, navegação por teclado, Web Speech API, VLibras (opcional) |
| Testes | JUnit 5, AssertJ, Spring Boot Test + MockMvc |

## Endpoints

Base: `http://localhost:8080`

| Método | Rota | Descrição |
|---|---|---|
| **GET** | **`/telemetria`** | Horário da leitura, reservatório, talhões (umidade, status, aspersor), bombas, energia, índice, **decisão**, alertas e eventos recentes |
| **POST** | **`/bombas/acionar`** | Comando manual do operador. Validado pelo servidor: `200` executado · `409` recusado por regra de segurança |
| GET | `/bombas` | Estado das 4 motobombas |
| GET | `/eventos?limite=50&desdeId=` | Histórico: irrigação automática, comandos manuais, bloqueios, alertas, recuperações |
| GET | `/status` | Resumo operacional (inclui texto para leitura em voz) |
| GET | `/historico?limite=240` | Série temporal para os gráficos |
| GET | `/indicadores` | Índice, autonomia, acionamentos por talhão, energia e água |
| GET | `/saude` | Status da API, do banco, do simulador, dos sensores e dos atuadores |
| GET | `/relatorio` · `/relatorio/csv` | Relatório operacional (JSON) e exportação CSV |
| POST | `/simulacao/velocidade` · `/pausa` · `/umidade` · `/reservatorio` · `/emergencia` · `/restaurar` | Painel de demonstração: altera **condições físicas** no servidor; as decisões continuam com o motor de regras |

### Exemplos

```http
POST /bombas/acionar
Content-Type: application/json

{ "talhaoId": "A", "ligado": true }
```

Resposta com o reservatório abaixo de 15% (**HTTP 409**):

```json
{
  "sucesso": false,
  "motivo": "BLOQUEIO_DE_EMERGENCIA",
  "mensagem": "Acionamento bloqueado devido ao nível crítico do reservatório (12,0%). O sistema de proteção hídrica impede o acionamento das bombas até o nível voltar a 20,0%.",
  "talhao": { "id": "A", "aspersorLigado": false, "...": "..." },
  "nivelReservatorio": 12.0,
  "bloqueioEmergencia": true
}
```

Outros motivos de recusa: `IRRIGACAO_CRITICA_EM_ANDAMENTO` (tentar desligar um talhão com umidade < 25%) e
`SOLO_SATURADO` (ligar com umidade ≥ 85%). Erros de validação retornam `400` com `detalhes`; talhão inexistente, `404`.

## Regras de negócio

### Reservatório

| Status | Faixa |
|---|---|
| 🟢 NORMAL | acima de 30% |
| 🟡 ATENÇÃO | entre 15% e 30% |
| 🔴 CRÍTICO / 🚨 EMERGÊNCIA | abaixo de 15%: **bloqueio de emergência** |

### Hierarquia (implementada em `service/MotorRegras.java`)

| Prioridade | Regra | Comportamento |
|---|---|---|
| **P1** | 🚨 Bloqueio de emergência | Reservatório **< 15%** ⇒ **todas as bombas desligadas** pelo servidor; nenhum acionamento (automático ou manual) é aceito. O rearme ocorre só quando o nível volta a **20%** (histerese, para evitar liga/desliga repetido em torno de 15%). |
| **P2** | 🔴 Irrigação crítica | Umidade **< 25%** ⇒ o servidor liga o aspersor do talhão, **desde que P1 não esteja ativa**. |
| **P3** | 🟡 Operação normal | A irrigação automática termina na umidade alvo (**40%**); qualquer aspersor desliga se o solo saturar (**85%**). |
| **P4** | 👤 Controle manual | Aceito somente se não violar P1 nem P2 (ex.: não é possível desligar um talhão em irrigação crítica). |

Exemplo do regulamento: *Talhão A = 12% e Reservatório = 10%* ⇒ ❌ o Talhão A **não** irriga, 🚨 o bloqueio fica ativo e todas as bombas ficam desligadas. O evento `IRRIGACAO_BLOQUEADA` explica o motivo.

> **Decisão técnica adicional (não é requisito original do desafio): histerese de rearme**
>
> - Limite crítico: **15%** (regra do regulamento, cumprida integralmente: abaixo de 15% todas as bombas são desligadas).
> - Limite de rearme: **20%**.
> - O limite de rearme evita ciclos repetitivos de bloqueio e reativação próximos ao limite crítico.
>
> Na prática, se o reservatório subir para 16–19% depois de uma emergência, o bloqueio continua ativo e o
> Motor de Decisão exibe "acima de 15%, mas ainda abaixo do nível de rearme (20%)". O valor fica em
> `Reservatorio.limiteRearme`.

O motor de regras roda **a cada minuto simulado**, mesmo com a simulação acelerada, e também logo após cada
comando ou alteração de cenário.

### Índice Hidro-Energético (IHE)

```text
IHE = 0,5 × H + 0,3 × S + 0,2 × E

H (hídrico) = nível do reservatório (0–100)
S (solo)    = média dos talhões: 0 ponto com umidade ≤ 15%, 100 pontos com umidade ≥ 40% (alvo), linear entre esses valores
E (energia) = % do consumo das bombas atendido pela usina solar (100 sem bombas ligadas)
Trava: com o bloqueio de emergência ativo, IHE ≤ 25
```

≥ 70 🟢 operação equilibrada · 40–69 🟡 atenção necessária · < 40 🔴 risco hídrico elevado.
O cenário inicial resulta em **81/100** (H = 67, S = 91, E = 100).

### Modelo da fazenda (determinístico, sem números aleatórios)

| Talhão | Cultura | Solo | Área | Umidade inicial | Perda no pico de sol |
|---|---|---|---|---|---|
| A | Laranja Pera Rio | Latossolo Amarelo | 12,5 ha | 62% | 1,3 %/h |
| B | Laranja Valência | Latossolo Amarelo | 10,8 ha | 41% | 1,4 %/h |
| C | Limão Tahiti | Neossolo Quartzarênico (arenoso) | 8,2 ha | 31% | 1,9 %/h |
| D | Limão Siciliano | Argissolo Vermelho-Amarelo | 7,6 ha | 57% | 1,6 %/h |

- A evapotranspiração acompanha a curva solar (nascer às 6h, pico ao meio-dia, pôr do sol às 18h).
- Cada talhão tem uma motobomba de **5,5 kW / 18 m³/h**. O aspersor adiciona 7 %/h de umidade.
- Reservatório de **800 m³** (inicia em 67%), com recarga de até 8 m³/h por um poço com bomba solar.
- Usina fotovoltaica de **45 kWp**.
- **Escala de tempo:** 1 s real = 1 min na fazenda (1×). O painel acelera para 5×, 15× ou 30×.

## Como executar em outro computador

### Requisitos

| Requisito | Observação |
|---|---|
| Windows 10/11 | Os scripts são PowerShell (já incluso no Windows) |
| Java 21 (JDK) | Instalado automaticamente pelo `setup-ambiente.ps1`, ou use um JDK 21 próprio (`JAVA_HOME`) |
| Maven | **Não precisa instalar**: o projeto usa o Maven Wrapper (`backend\mvnw.cmd`) |
| PostgreSQL 17 | Instalado automaticamente pelo `setup-ambiente.ps1` (portátil), via Docker ou uma instalação já existente |
| Git | Para clonar o repositório |
| Navegador | Edge, Chrome ou Firefox atualizados |
| Internet | Apenas na **primeira** execução (download do JDK, PostgreSQL e dependências Maven) e para o VLibras |

### 1. Clonar e entrar

```bash
git clone URL_DO_REPOSITORIO biosolar-citrus
cd biosolar-citrus
```

> **Use uma pasta de caminho curto** (ex.: `C:\projetos\biosolar-citrus`) e, de preferência, **fora do OneDrive**.
> O Windows limita caminhos a 260 caracteres, e os arquivos mais longos do projeto (incluindo os do build)
> ocupam cerca de 100. Em pastas muito profundas o `git clone` falha com "Filename too long". Se precisar,
> habilite caminhos longos no Git: `git config --global core.longpaths true`.

### 2. Preparar o ambiente (uma única vez)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-ambiente.ps1
```

Isso instala, **sem precisar de administrador**, o JDK 21, o Maven e o PostgreSQL 17 em
`%LOCALAPPDATA%\BioSolarDev` (fora da pasta do projeto) e cria o banco:

| Item | Valor (desenvolvimento local) |
|---|---|
| Host / porta | `localhost:5432` |
| Banco | `biosolar` |
| Usuário / senha da aplicação | `biosolar` / `biosolar` |
| Superusuário do cluster portátil | `postgres` / `postgres` |

> Se o notebook **já tiver um PostgreSQL** na porta 5432, o script avisa e para. Nesse caso, crie o banco
> no PostgreSQL existente (seção "Banco de dados") **ou** use outra porta: copie `.env.example` para `.env`,
> defina `BIOSOLAR_DB_URL=jdbc:postgresql://localhost:5433/biosolar` e rode o script de novo.

### 3. Iniciar

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

O script sobe o PostgreSQL portátil (se estiver parado) e a API. Na **primeira execução**, o Maven Wrapper
baixa o Maven e as dependências (alguns minutos). O **Flyway cria as tabelas** e o backend **cria os dados
iniciais** (4 talhões, reservatório em 67% e estado do simulador) automaticamente. Nada depende do banco
de outro computador.

> Dica: para usar `.\scripts\iniciar.ps1` diretamente (sem `powershell -ExecutionPolicy Bypass -File`),
> libere scripts locais uma vez: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

### 4. Abrir e verificar

| O quê | Endereço |
|---|---|
| **Dashboard** (servido pela própria API) | http://localhost:8080 |
| Telemetria | http://localhost:8080/telemetria |
| Saúde (API, banco, simulador) | http://localhost:8080/saude, que deve mostrar `"status":"UP"` e `"banco":{"status":"UP"...}` |
| Todos os endpoints | arquivo [`api.http`](api.http) (VS Code: extensão *REST Client*) |

Alternativas para o frontend: abrir `frontend/index.html` com o Live Server (porta 5500) ou dar duplo clique
no arquivo. Nos dois casos, a API em `http://localhost:8080` precisa estar rodando.

### Banco de dados

**Opção A (padrão):** PostgreSQL portátil criado pelo `setup-ambiente.ps1`, descrito acima.

**Opção B (Docker):**

```powershell
docker compose up -d        # cria banco biosolar / usuário biosolar / senha biosolar na porta 5432
cd backend
.\mvnw.cmd spring-boot:run
```

**Opção C (PostgreSQL já instalado):** crie o usuário e o banco (psql ou pgAdmin):

```sql
CREATE ROLE biosolar LOGIN PASSWORD 'biosolar';
CREATE DATABASE biosolar OWNER biosolar;
```

Depois rode `.\scripts\iniciar.ps1`. Se usar outra porta, usuário ou senha, informe no `.env` (modelo em `.env.example`).

**Plano B (sem PostgreSQL, contingência na apresentação):**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1 -H2
```

Usa H2 em arquivo (`backend/data/`, fora do Git), ainda persistido **no servidor**.

### Testes

```powershell
cd backend
.\mvnw.cmd test
```

19 testes: os 5 exigidos pelo regulamento, mais rearme, prioridade P2 > P4, fim da irrigação no alvo,
balanço hídrico, índice, CORS, retenção e testes ponta a ponta da API (MockMvc).

## Migração para outro computador

1. **Clonar:** `git clone URL_DO_REPOSITORIO biosolar-citrus`, depois `cd biosolar-citrus`.
2. **Instalar requisitos:** Git e navegador. O resto vem do passo 3.
3. **Configurar o banco:** `powershell -ExecutionPolicy Bypass -File .\scripts\setup-ambiente.ps1` (ou as opções B/C acima).
4. **Executar:** `powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1`.
5. **Verificar a API:** http://localhost:8080/saude, que deve mostrar `UP` no banco e no simulador.
6. **Abrir o dashboard:** http://localhost:8080, e antes da apresentação clicar em **↺ Restaurar cenário**.

**O que NÃO é transferido pelo Git** (e não precisa ser):

| Item | Por quê |
|---|---|
| Banco de dados físico (`%LOCALAPPDATA%\BioSolarDev\pgdata`, `backend/data/`) | É recriado: Flyway (tabelas) e backend (dados iniciais) |
| JDK, Maven e PostgreSQL portáteis | Reinstalados pelo `setup-ambiente.ps1` |
| `backend/target/` | Gerado pelo build |
| `.env` | Configuração local; o modelo versionado é o `.env.example` |
| Logs, temporários e configurações de IDE | Específicos de cada computador |

## Segurança e configuração

- **Credenciais:** `biosolar`/`biosolar` e `postgres`/`postgres` são **credenciais de desenvolvimento local**,
  não senhas reais. Para outros valores, use o `.env` (nunca versionado). Não há tokens nem chaves no projeto.
- **CORS:** por padrão aceita `http://localhost:*`, `http://127.0.0.1:*` (Live Server) e `null` (arquivo aberto
  com duplo clique). Origens externas são recusadas. Configure com `BIOSOLAR_CORS_ORIGENS`.
- **Endpoints de demonstração (`/simulacao/*`):** existem apenas para a apresentação (alteram condições físicas
  no servidor, e o motor de regras reage). Para desativá-los: `BIOSOLAR_CONTROLES_DEMO=false` (passam a responder `403`).
- **Validação:** entradas validadas (`@Valid`) e respostas padronizadas (`400`, `403`, `404`, `409`, `500`).
- **Retenção:** leituras de telemetria (gráficos) com mais de 24 h são removidas automaticamente; o **histórico de eventos é mantido**.

| Variável (`.env`) | Padrão |
|---|---|
| `BIOSOLAR_DB_URL` | `jdbc:postgresql://localhost:5432/biosolar` |
| `BIOSOLAR_DB_USUARIO` / `BIOSOLAR_DB_SENHA` | `biosolar` / `biosolar` |
| `BIOSOLAR_PG_SUPER_SENHA` | `postgres` (apenas o cluster portátil) |
| `BIOSOLAR_CORS_ORIGENS` | `http://localhost:[*],http://127.0.0.1:[*],null` |
| `BIOSOLAR_CONTROLES_DEMO` | `true` |
| `PORT` | `8080` |

| Teste | Cenário | Resultado esperado |
|---|---|---|
| 1 | Reservatório > 15% | Comando manual funciona |
| 2 | Reservatório < 15% | Todas as bombas desligam |
| 3 | Umidade < 25% e reservatório > 15% | Aspersor liga automaticamente |
| 4 | Umidade < 25% e reservatório < 15% | Aspersor **não** liga (nem manualmente) |
| 5 | Após a emergência | Bombas permanecem desligadas até o rearme |

## Demonstração (roteiro para a banca)

Use o **🎬 Painel de Simulação** do dashboard. Antes de apresentar, clique em **↺ Restaurar cenário**.

1. **Situação normal**: reservatório em 67%, 🟢 operação normal, Motor de Decisão em "Operação equilibrada".
2. **Simulação**: selecione *Talhão C* e clique em **↓ Reduzir 5%** duas vezes (ou acelere para 15×).
3. **Evento crítico**: o Talhão C fica abaixo de 25% e o mapa fica 🔴.
4. **Automação**: o **backend** liga o aspersor (P2). O Motor de Decisão explica o quê, onde, por quê e o impacto.
5. **Reservatório**: clique em **↓ Reduzir 10%** cinco vezes (≈ 17%; o alerta de atenção aparece em 30%).
6. **Emergência**: clique em **↓ Reduzir 5%** (≈ 12%, abaixo de 15%) ou em **🚨 Simular emergência**.
7. **Bloqueio**: o backend desliga **todas** as bombas e o status passa para 🚨 EMERGÊNCIA.
8. **Tentativa manual**: em *Controle dos Aspersores*, clique em **Ligar**.
9. **O sistema recusa**: aparece **🚨 AÇÃO BLOQUEADA** com a mensagem do servidor (HTTP 409).
10. **Diferenciais**: histórico, índice ("Como é calculado?"), relatório (PDF/CSV/WhatsApp), 🔊 voz e ♿ acessibilidade.

Pela API, sem o navegador:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/simulacao/umidade -ContentType 'application/json' -Body '{"talhaoId":"C","delta":-7}'
Invoke-RestMethod http://localhost:8080/telemetria            # Talhão C: aspersorLigado = true (AUTOMATICO)
Invoke-RestMethod -Method Post http://localhost:8080/simulacao/emergencia
Invoke-RestMethod -Method Post http://localhost:8080/bombas/acionar -ContentType 'application/json' -Body '{"talhaoId":"B","ligado":true}'  # 409
```

## Acessibilidade

- **Central de Acessibilidade (♿)**: aumentar, diminuir e restaurar a fonte; tema claro, escuro ou do sistema; **alto contraste**; **escala de cinza**; **reduzir animações** (e respeito a `prefers-reduced-motion`).
- **Navegação**: link "Pular para o conteúdo", foco visível, ordem lógica, HTML semântico (`header`, `main`, `section`, `dl`, `dialog`), rótulos e ARIA (`aria-live` para status e emergências, `role="meter"` no reservatório e no índice).
- **Gráficos**: navegáveis por teclado (← →), com tooltip, legenda, rótulos diretos e **tabela de dados**. A paleta categórica foi validada para daltonismo.
- **Status nunca só por cor**: sempre ícone + texto (🟢 Normal, 🟡 Atenção, 🔴 Crítico, 🚨 Emergência).
- **🔊 Leitura em voz** (Web Speech API): "Ouvir status da fazenda", com anúncio opcional de emergências.
- **🤟 Libras**: integração opcional com o **VLibras** (gov.br), carregado sob demanda.

## Conformidade com o regulamento

- **Nenhum dado operacional no navegador**: umidade, reservatório, bombas, aspersores, telemetria e estados vivem no servidor e no PostgreSQL. O `localStorage` guarda **apenas** preferências visuais (`biosolar.preferencias`: tema, fonte, contraste, cinza, movimento, voz, Libras).
- **Automação no backend**: `MotorRegras` + `SimuladorFazenda` (`@Scheduled`). O frontend não contém nenhuma regra de acionamento.
- **Segurança da API**: validação (`@Valid`), respostas HTTP adequadas (200/400/403/404/409/500), CORS configurável e nenhuma credencial no frontend.

## Estrutura

```text
backend/
├── pom.xml · mvnw · mvnw.cmd
└── src/main/java/br/com/biosolar/citrus/
    ├── config/       CORS, agendamento, propriedades
    ├── controller/   Telemetria, Bomba, Historico, Indicadores, Relatorio, Simulacao
    ├── dto/          records de resposta/requisição
    ├── exception/    ApiExceptionHandler + erros de domínio
    ├── model/        Fazenda (agregado), Talhao, Reservatorio, Evento, leituras
    ├── repository/   Spring Data JPA
    ├── service/      MotorRegras, MotorDecisao, Indice, Telemetria, Acionamento, Relatorio…
    ├── simulation/   SimuladorFazenda, ModeloFisico, ModeloSolar, CenarioInicial
    └── util/
    src/main/resources/db/migration/V1__criar_tabelas.sql
frontend/
├── index.html
└── src/
    ├── components/   statusGeral, motorDecisao, mapaTalhoes, controleAspersores, graficos, historico…
    ├── services/     api, telemetriaService, bombaService, eventoService, simulacaoService, relatorioService
    ├── styles/       tokens.css (temas), app.css, print.css
    ├── utils/        format, dom, charts (SVG), voz
    ├── config.js
    └── app.js
scripts/              setup-ambiente.ps1 · iniciar.ps1
api.http              exemplos de todas as requisições (REST Client / IntelliJ)
.env.example          modelo das variáveis de ambiente (o .env não é versionado)
docker-compose.yml    PostgreSQL alternativo via Docker
```
