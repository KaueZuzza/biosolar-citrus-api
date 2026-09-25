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
| Banco | PostgreSQL 17 + Flyway (migrações `V1` a `V3` em `db/migration`); H2 apenas em testes e no perfil de contingência |
| Frontend | HTML5 semântico, CSS (tokens de tema), JavaScript sem build, gráficos SVG próprios |
| Exportação | OpenPDF (PDF com gráficos vetoriais), Apache POI (Excel .xlsx), Spring Mail (envio SMTP pelo servidor) |
| Assistente | Citrus: reconhecimento de fala do navegador (Web Speech API, pt-BR) + interpretação e dados no servidor |
| Acessibilidade | ARIA, foco visível, navegação por teclado, Web Speech API, VLibras (opcional) |
| Testes | JUnit 5, AssertJ, Spring Boot Test + MockMvc, GreenMail (SMTP em memória) |

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
| GET | `/relatorio` · `/relatorio/csv` | Relatório operacional (JSON) e exportação CSV (dados brutos) |
| GET | `/exportacao/pdf` · `/exportacao/pdf?download=true` | **PDF** do relatório (logo, indicadores, reservatório, talhões, irrigação, alertas, eventos, gráficos, paginação) |
| GET | `/exportacao/excel` | **Planilha .xlsx** com as abas Resumo, Talhões, Reservatório, Irrigação, Eventos e Histórico |
| GET | `/exportacao/whatsapp` | Mensagem curta de status (texto + link `wa.me` pronto) |
| GET · POST | `/exportacao/email` | Situação do envio (sem expor credenciais) · envia PDF/Excel por e-mail pelo servidor. `400` e-mail inválido · `429` limite por hora · `502` SMTP recusou · `503` SMTP não configurado |
| POST | `/assistente/comando` | **Citrus**: `{"texto": "Citrus, quero o status"}` → resposta em texto, versão para voz e ação para o painel |
| GET | `/assistente/exemplos` | Perguntas sugeridas pelo Citrus |
| POST | `/simulacao/velocidade` · `/pausa` · `/umidade` · `/reservatorio` · `/emergencia` · `/restaurar` | Painel de demonstração: altera **condições físicas** no servidor; as decisões continuam com o motor de regras |
| GET · POST | `/talhoes` | **Cadastro**: lista (inclusive arquivados) e cadastra talhões. `201` criado · `400` dados inválidos · `409` código repetido/limite de 8 · `503` banco fora do ar |
| GET · PUT · DELETE | `/talhoes/{id}` | Consulta, edita (o código não muda) e **exclui da operação** (arquiva: o histórico é preservado) |
| POST · DELETE | `/talhoes/{id}/reativar` · `/talhoes/{id}/definitivo` | Reativa um talhão arquivado · remove do banco um talhão já arquivado |
| GET · PUT | `/configuracao` · `/configuracao/reservatorio` · `/configuracao/usina` | Reservatório (nome, capacidade, recarga, nível inicial) e usina solar (kWp); limites do regulamento somente leitura |

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
| Navegador | Edge ou Chrome atualizados (o reconhecimento de voz do Citrus só existe neles); Firefox funciona sem a voz |
| Internet | Na **primeira** execução (download do JDK, PostgreSQL e dependências Maven), para o VLibras e para o reconhecimento de voz |

### 1. Clonar e entrar

```bash
git clone https://github.com/KaueZuzza/biosolar-citrus-api.git biosolar-citrus
cd biosolar-citrus
```

> **Use uma pasta de caminho curto** (ex.: `C:\projetos\biosolar-citrus`) e, de preferência, **fora do OneDrive**.
> O Windows limita caminhos a 260 caracteres, e os arquivos mais longos do projeto (incluindo os do build)
> ocupam cerca de 100. Em pastas muito profundas o `git clone` falha com "Filename too long". Se precisar,
> habilite caminhos longos no Git: `git config --global core.longpaths true`.

### 2. Rodar

**Pelo VS Code (mais simples):**

1. Abra a pasta do projeto no VS Code e aceite instalar a extensão sugerida (**Extension Pack for Java**).
2. Pressione **F5** e escolha **“BioSolar Citrus (API + dashboard)”**.

O F5 roda antes a tarefa *BioSolar: preparar ambiente*, que:
- usa o **JDK 21 portátil** do projeto (mesmo com outro Java no sistema);
- na primeira vez, instala JDK 21 + PostgreSQL 17 sozinho (`setup-ambiente.ps1`, precisa de internet);
- liga o **PostgreSQL do projeto** na porta gravada no `.env` e corrige o `.env` se a porta mudou.

Quando o servidor sobe, o navegador abre em **http://localhost:8080** com a tela de carregamento, que confere API,
banco e telemetria. Para parar: botão **Stop** (■). Outras tarefas em *Terminal > Run Task…*: **rodar testes**,
**registrar banco no pgAdmin**, **iniciar pelo script** e o plano B **H2** (também disponível no F5).

**Pelo PowerShell (sem VS Code):**

```powershell
# 1. Prepara o ambiente uma única vez: JDK 21, Maven, PostgreSQL 17 e o banco "biosolar"
#    (instalado em %LOCALAPPDATA%\BioSolarDev, fora da pasta do projeto, sem administrador)
powershell -ExecutionPolicy Bypass -File .\scripts\setup-ambiente.ps1

# 2. Sobe o PostgreSQL local (se estiver parado) + API + dashboard
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

| Item | Valor (desenvolvimento local) |
|---|---|
| Host / porta | `localhost:5432` (ou a próxima porta livre, ver abaixo) |
| Banco | `biosolar` |
| Usuário / senha da aplicação | `biosolar` / `biosolar` |
| Superusuário do cluster portátil | `postgres` / `postgres` |

- Os scripts usam sempre o **JDK 21 portátil** do projeto, mesmo que o Windows tenha outro Java (ex.: 17) no `JAVA_HOME`/PATH.
- A configuração local fica no **`.env`** da raiz (criado a partir do `.env.example`, fora do Git). Se o computador
  **já tiver um PostgreSQL** na porta 5432, o PostgreSQL do BioSolar sobe na próxima porta livre (ex.: 5433) e o
  `.env` é atualizado sozinho. A API também lê o `.env` ao rodar direto pelo `mvnw` ou pela IDE.
- Na **primeira execução**, o Maven Wrapper baixa o Maven e as dependências (alguns minutos). O **Flyway cria as
  tabelas** e o backend **cria os dados iniciais** (4 talhões, reservatório em 67% e estado do simulador)
  automaticamente. Nada depende do banco de outro computador.

> Dica: para usar `.\scripts\iniciar.ps1` diretamente (sem `powershell -ExecutionPolicy Bypass -File`),
> libere scripts locais uma vez: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

### 3. Abrir e verificar

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

### Banco de dados no pgAdmin 4

O pgAdmin acessa **o mesmo PostgreSQL e o mesmo banco da aplicação** (não há banco separado):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pgadmin.ps1   # ou a tarefa do VS Code "BioSolar: registrar banco no pgAdmin"
```

O script lê a porta do `.env`, faz backup da configuração do pgAdmin e registra o servidor **BioSolar Citrus**
(grupo *BioSolar Citrus*). Se já estiver registrado, nada muda. No pgAdmin (reabra-o se estava aberto):

1. **Servers > BioSolar Citrus**: senha do usuário `biosolar` (a do `.env`); marque *Salvar senha*.
2. **Databases > biosolar > Schemas > public > Tables**: `talhao` (cadastro + estado de cada talhão, com bomba e
   aspersor), `reservatorio`, `estado_simulacao`, `evento`, `leitura_telemetria`, `leitura_talhao`.
   **Views**: `vw_aspersores` (bombas MB-x, aspersores e sensores SU-x) e `vw_leituras_talhao` (histórico de umidade).
3. **Relacionamentos**: botão direito em `biosolar` > **ERD For Database**. Cada tabela e coluna importante tem comentário.
4. **Dados**: botão direito na tabela > *View/Edit Data* > *All Rows* (F5 atualiza).

Sincronização nos dois sentidos:
- **Aplicação → pgAdmin**: o estado é gravado a cada segundo e cada ação vira um registro em `evento`.
- **pgAdmin → aplicação**: mudanças de **cadastro** (nome, cultura, limites, bomba, `ativo`, reservatório, usina, ou
  um talhão inserido) chegam à automação em até 5 s e geram o evento “Cadastro atualizado pelo banco de dados”.
  Colunas **operacionais** (umidade, aspersor, nível) são medidas pela automação e regravadas a cada ciclo: para
  mudá-las, use o Painel de demonstração. As restrições (`CHECK`) do banco recusam valores inválidos.

### Testes

```powershell
cd backend
.\mvnw.cmd test
```

42 testes: os 5 exigidos pelo regulamento, mais rearme, prioridade P2 > P4, fim da irrigação no alvo,
balanço hídrico, índice, CORS, retenção, testes ponta a ponta da API (MockMvc), cadastro (criar, validar, editar,
arquivar, reativar, excluir, sincronizar com o banco, restaurar), robustez da gravação no banco, **exportações** (PDF
com paginação, Excel com abas/filtros/formatos, mensagem do WhatsApp, envio de e-mail com anexos para um SMTP em
memória, validação e limite de envios) e **Citrus** (cada pergunta, talhão falado por letra, ligar/desligar
respeitando o bloqueio de emergência, comando desconhecido).

| Teste | Cenário | Resultado esperado |
|---|---|---|
| 1 | Reservatório > 15% | Comando manual funciona |
| 2 | Reservatório < 15% | Todas as bombas desligam |
| 3 | Umidade < 25% e reservatório > 15% | Aspersor liga automaticamente |
| 4 | Umidade < 25% e reservatório < 15% | Aspersor **não** liga (nem manualmente) |
| 5 | Após a emergência | Bombas permanecem desligadas até o rearme |

## Migração para outro computador

1. **Clonar:** `git clone https://github.com/KaueZuzza/biosolar-citrus-api.git biosolar-citrus`, depois `cd biosolar-citrus`.
2. **Instalar requisitos:** Git, navegador e (opcional) VS Code. O resto vem do passo 3.
3. **Configurar o banco:** `powershell -ExecutionPolicy Bypass -File .\scripts\setup-ambiente.ps1` (ou as opções B/C acima).
4. **Executar:** F5 no VS Code ou `powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1`.
5. **Verificar a API:** http://localhost:8080/saude, que deve mostrar `UP` no banco e no simulador.
6. **Abrir o dashboard:** http://localhost:8080, e antes da apresentação clicar em **↺ Restaurar cenário**.
7. **(Opcional) E-mail:** configure o SMTP no `.env` (seção "Envio por e-mail").

**O que NÃO é transferido pelo Git** (e não precisa ser):

| Item | Por quê |
|---|---|
| Banco de dados físico (`%LOCALAPPDATA%\BioSolarDev\pgdata`, `backend/data/`) | É recriado: Flyway (tabelas) e backend (dados iniciais) |
| JDK, Maven e PostgreSQL portáteis | Reinstalados pelo `setup-ambiente.ps1` |
| `backend/target/` | Gerado pelo build |
| `.env` (inclusive a senha do e-mail) | Configuração local; o modelo versionado é o `.env.example` |
| `backups/` | Dumps e cópias locais de segurança |
| Logs, temporários e configurações de IDE | Específicos de cada computador |

## Segurança e configuração

- **Credenciais:** `biosolar`/`biosolar` e `postgres`/`postgres` são **credenciais de desenvolvimento local**,
  não senhas reais. Para outros valores, use o `.env` (nunca versionado). Não há tokens nem chaves no projeto.
- **E-mail:** usuário e senha SMTP ficam só no `.env` do servidor; o navegador recebe apenas se o envio está
  configurado e o remetente. Envio limitado a 5 destinatários e a `BIOSOLAR_SMTP_LIMITE_HORA` envios por hora.
- **CORS:** por padrão aceita `http://localhost:*`, `http://127.0.0.1:*` (Live Server) e `null` (arquivo aberto
  com duplo clique). Origens externas são recusadas. Configure com `BIOSOLAR_CORS_ORIGENS`.
- **Endpoints de demonstração (`/simulacao/*`):** existem apenas para a apresentação (alteram condições físicas
  no servidor, e o motor de regras reage). Para desativá-los: `BIOSOLAR_CONTROLES_DEMO=false` (passam a responder `403`).
- **Validação:** entradas validadas (`@Valid`) e respostas padronizadas (`400`, `403`, `404`, `409`, `429`, `500`, `502`, `503`).
- **Retenção:** leituras de telemetria (gráficos) com mais de 24 h são removidas automaticamente; o **histórico de eventos é mantido**.

| Variável (`.env`) | Padrão |
|---|---|
| `BIOSOLAR_DB_URL` | `jdbc:postgresql://localhost:5432/biosolar` |
| `BIOSOLAR_DB_USUARIO` / `BIOSOLAR_DB_SENHA` | `biosolar` / `biosolar` |
| `BIOSOLAR_PG_SUPER_SENHA` | `postgres` (apenas o cluster portátil) |
| `BIOSOLAR_CORS_ORIGENS` | `http://localhost:[*],http://127.0.0.1:[*],null` |
| `BIOSOLAR_CONTROLES_DEMO` | `true` |
| `PORT` | `8080` |
| `BIOSOLAR_SMTP_*` | vazio (e-mail desativado); ver "Envio por e-mail" |

## Exportação e compartilhamento

Aba **Relatórios > 📤 Exportar e compartilhar**. Tudo é gerado **pelo servidor** a partir do PostgreSQL e do estado
atual da automação no momento do clique.

| Opção | O que gera | Como testar |
|---|---|---|
| **📄 PDF** | A4 com logo, período, status, 8 indicadores, decisão do sistema, reservatório (tabela + gráfico com faixas de risco), talhões (tabela + gráfico de umidade), irrigação e energia, alertas ativos, 15 eventos importantes e "Página X de Y" | **Abrir** (nova aba) ou **Baixar**; ou `http://localhost:8080/exportacao/pdf` |
| **📊 Excel** | `.xlsx` com 6 abas: **Resumo** (logo, indicadores, alertas, decisão, links), **Talhões**, **Reservatório** (série + quadro da situação atual), **Irrigação** (eventos + totais por talhão), **Eventos** e **Histórico** (umidade e aspersor de cada talhão). Cada aba tem uma Tabela do Excel com filtros, cabeçalho congelado, larguras ajustadas e valores reais (datas, %, números) | **Baixar**; abra no Excel e use os filtros dos cabeçalhos |
| **💬 WhatsApp** | Mensagem curta (data, reservatório, talhões, irrigação, alertas, sistema) com prévia no formato do WhatsApp | **Compartilhar** > **Abrir no WhatsApp** (opcional: número com DDD). No celular: **Outros apps…** ou **Enviar com o PDF** |
| **✉️ E-mail** | O servidor envia o PDF, o Excel ou os dois, com o resumo no corpo e a logo | Configure o SMTP (abaixo) > **Enviar** > destinatário, assunto, mensagem e anexo |

Nas planilhas, o histórico é amostrado uniformemente quando passa de 3.000 leituras (a nota de cada aba informa a
amostragem); nos gráficos do PDF, cada linha tem até 300 pontos. O CSV continua disponível em "CSV (dados brutos)".

### Envio por e-mail (SMTP no `.env`)

O e-mail sai **do servidor**: usuário e senha ficam só no `.env` (que não vai para o git) e nunca chegam ao navegador.
Acrescente ao `.env` e reinicie a API:

```properties
BIOSOLAR_SMTP_HOST=smtp.gmail.com
BIOSOLAR_SMTP_PORTA=587
BIOSOLAR_SMTP_USUARIO=seu.email@gmail.com
BIOSOLAR_SMTP_SENHA=senha-de-app-de-16-letras
# Opcionais: BIOSOLAR_SMTP_REMETENTE (padrão = usuário), BIOSOLAR_SMTP_NOME, BIOSOLAR_SMTP_STARTTLS=true,
# BIOSOLAR_SMTP_SSL=false (true para a porta 465), BIOSOLAR_SMTP_LIMITE_HORA=20
```

- **Gmail**: ative a verificação em duas etapas e crie uma **senha de app** (Conta Google > Segurança > Senhas de app).
- **Outlook/Microsoft 365**: `smtp.office365.com`, porta 587, STARTTLS (a conta precisa permitir SMTP autenticado).
- Sem `BIOSOLAR_SMTP_HOST`, a janela de e-mail avisa que o envio não está configurado (a API responde `503`).
- Proteções: até 5 destinatários por envio, 20 envios por hora (configurável), e-mails validados no servidor.
  Cada envio vira um evento *Relatório* no histórico, com o endereço mascarado (`g***@fazenda.com`).

## Citrus: assistente por voz

Clique em **🎙️** no topo e fale, por exemplo, **"Citrus, quero o status"**. O navegador transcreve a fala (Chrome ou
Edge; o reconhecimento de voz desses navegadores usa a internet). O **servidor** interpreta o comando, consulta os
dados reais e responde. A resposta aparece na conversa e, com **"Responder também em voz"**, é lida em voz alta.
Sem microfone, ou em um navegador sem reconhecimento de voz, digite a pergunta no mesmo painel. O painel mostra
**🎙️ Ouvindo…**, **🔎 Consultando os dados…** e **🔊 Respondendo…**.

| Pergunta | O que o Citrus faz |
|---|---|
| "Citrus, quero o status" | Reservatório, talhões, aspersores, alertas e situação do sistema |
| "Como está o reservatório?" | Nível, volume, consumo, autonomia e proteção |
| "Quais talhões estão críticos?" | Talhões críticos (ou em atenção) com a umidade; abre a aba Talhões |
| "Tem algum alerta?" | Alertas ativos; abre Monitoramento |
| "Como está a irrigação?" | Aspersores ligados (automático/manual) e totais do período |
| "Mostre o talhão C" | Resumo do talhão e **abre os detalhes** dele (entende "talhão cê", "talhão do B" e nomes cadastrados) |
| "Gere um relatório" / "Relatório de hoje" | Resumo do período e **abre a área de exportação** |
| "O que está acontecendo?" | Decisão atual do motor de regras e últimos eventos |
| "Ligar/desligar o aspersor do talhão B" | Executa **com as mesmas regras de segurança** do painel (recusa durante o bloqueio de emergência) |

Também responde sobre energia solar e sobre o índice. Quando não reconhece o comando, responde: "Não consegui entender
esse comando. Tente perguntar sobre o reservatório, talhões, irrigação, alertas ou relatório."
Pela API: `POST /assistente/comando` com `{"texto": "como está o reservatório?"}`.

## Demonstração (roteiro para a banca)

Use o botão **🎬 Demonstração** (topo da tela), que abre o painel ao lado do dashboard. Antes de apresentar, clique em
**↺ Restaurar cenário**: a fazenda volta aos **níveis iniciais cadastrados** (umidade inicial de cada talhão e nível
inicial do reservatório), os aspersores desligam e o histórico é reiniciado. O cadastro é mantido.

A interface é organizada em seções: **Visão geral** (status, indicadores, decisão, reservatório e resumo dos talhões),
**Talhões** (mapa e gráfico de umidade), **Irrigação** (aspersores, bombas e regras de automação), **Monitoramento**
(alertas, histórico, nível do reservatório e saúde do sistema), **Relatórios** (índice, indicadores do período e
exportações) e **Gestão** (cadastro de talhões, reservatório e usina). Os botões **?** explicam cada indicador em
linguagem simples.

1. **Situação normal**: reservatório em 67%, 🟢 operação normal, Motor de Decisão em "Operação equilibrada".
2. **Simulação**: selecione *Talhão C* e clique em **↓ Reduzir 5%** duas vezes (ou acelere para 15×).
3. **Evento crítico**: o Talhão C fica abaixo de 25% e o mapa fica 🔴.
4. **Automação**: o **backend** liga o aspersor (P2). O Motor de Decisão explica o quê, onde, por quê e o impacto.
5. **Reservatório**: clique em **↓ Reduzir 10%** cinco vezes (≈ 17%; o alerta de atenção aparece em 30%).
6. **Emergência**: clique em **↓ Reduzir 5%** (≈ 12%, abaixo de 15%) ou em **🚨 Simular emergência**.
7. **Bloqueio**: o backend desliga **todas** as bombas e o status passa para 🚨 EMERGÊNCIA.
8. **Tentativa manual**: em *Controle dos Aspersores*, clique em **Ligar**.
9. **O sistema recusa**: aparece **🚨 AÇÃO BLOQUEADA** com a mensagem do servidor (HTTP 409).
10. **Diferenciais**: pergunte ao **🎙️ Citrus** "o que está acontecendo?" e "gere um relatório"; exporte o PDF e o
    Excel, compartilhe pelo WhatsApp ou por e-mail; histórico, índice ("Como é calculado?") e ♿ acessibilidade.

Pela API, sem o navegador:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/simulacao/umidade -ContentType 'application/json' -Body '{"talhaoId":"C","delta":-7}'
Invoke-RestMethod http://localhost:8080/telemetria            # Talhão C: aspersorLigado = true (AUTOMATICO)
Invoke-RestMethod -Method Post http://localhost:8080/simulacao/emergencia
Invoke-RestMethod -Method Post http://localhost:8080/bombas/acionar -ContentType 'application/json' -Body '{"talhaoId":"B","ligado":true}'  # 409
```

## Acessibilidade

- **Central de Acessibilidade (♿)**: aumentar, diminuir e restaurar a fonte; tema claro, escuro ou do sistema; **alto contraste**; **escala de cinza**; **reduzir animações** (desliga ondas, jatos, pulsos e a animação dos gráficos; vale na hora e fica salvo no navegador).
- **Navegação**: link "Pular para o conteúdo", foco visível, ordem lógica, HTML semântico (`header`, `main`, `section`, `dl`, `dialog`), rótulos e ARIA (`aria-live` para status e emergências, `role="meter"` no reservatório e no índice).
- **Gráficos**: navegáveis por teclado (← →), com tooltip, legenda, rótulos diretos e **tabela de dados**. A paleta categórica foi validada para daltonismo.
- **Status nunca só por cor**: sempre ícone + texto (🟢 Normal, 🟡 Atenção, 🔴 Crítico, 🚨 Emergência).
- **🔊 Leitura em voz** (Web Speech API): "Ouvir status da fazenda" (Central de Acessibilidade), com anúncio opcional de emergências.
- **🎙️ Citrus**: perguntas por voz ou texto, respostas em texto e em voz, anunciadas também para leitores de tela.
- **🤟 Libras**: integração opcional com o **VLibras** (gov.br), carregado sob demanda.

## Conformidade com o regulamento

- **Nenhum dado operacional no navegador**: umidade, reservatório, bombas, aspersores, telemetria e estados vivem no servidor e no PostgreSQL. O `localStorage` guarda **apenas** preferências visuais (`biosolar.preferencias`: tema, fonte, contraste, cinza, movimento, voz, Libras).
- **Automação no backend**: `MotorRegras` + `SimuladorFazenda` (`@Scheduled`). O frontend não contém nenhuma regra de acionamento.
- **Segurança da API**: validação (`@Valid`), respostas HTTP adequadas (200/400/403/404/409/500), CORS configurável e nenhuma credencial no frontend (a senha do SMTP fica só no `.env` do servidor).

## Estrutura

```text
backend/
├── pom.xml · mvnw · mvnw.cmd
└── src/main/java/br/com/biosolar/citrus/
    ├── config/       CORS, agendamento, propriedades
    ├── controller/   Telemetria, Bomba, Historico, Indicadores, Relatorio, Simulacao, Talhao, Configuracao,
    │                 Exportacao, Assistente
    ├── dto/          records de resposta/requisição
    ├── exception/    ApiExceptionHandler + erros de domínio
    ├── model/        Fazenda (agregado), Talhao, Reservatorio, Evento, leituras, CadastroTalhao/Reservatorio
    ├── repository/   Spring Data JPA
    ├── service/      MotorRegras, MotorDecisao, Indice, Telemetria, Acionamento, Relatorio, Cadastro…
    │   ├── exportacao/  Coleta, RelatorioPdf (+ GraficoPdf), RelatorioExcel, Compartilhamento, Email
    │   └── assistente/  AssistenteService (Citrus)
    ├── simulation/   SimuladorFazenda, ModeloFisico, ModeloSolar, CenarioInicial
    └── util/
    src/main/resources/db/migration/   V1 tabelas · V2 índices/restrições · V3 cadastro, views e comentários
    src/main/resources/relatorio/      logos usadas no PDF, na planilha e no e-mail
frontend/
├── index.html
├── assets/           logos (cabeçalho, carregamento, ícone da aba)
└── src/
    ├── components/   abas, carregamento, statusGeral, motorDecisao, mapaTalhoes, resumoTalhoes, controleAspersores,
    │                 graficos, historico, alertas, periodo, gestao, exportacao, citrus…
    ├── services/     api, telemetria, bomba, evento, simulacao, relatorio, cadastro, exportacao, assistente
    ├── styles/       tokens.css (temas), app.css, secoes.css (abas, carregamento, ajuda, gestão, animações),
    │                 ferramentas.css (exportação e Citrus), print.css
    ├── utils/        format, dom, charts (SVG), voz, ajuda (botões ?)
    ├── config.js
    └── app.js
scripts/              setup-ambiente.ps1 · iniciar.ps1 · pgadmin.ps1 · ambiente.ps1 (funções comuns)
.vscode/              F5 (launch.json), tarefas (tasks.json), extensão recomendada
api.http              exemplos de todas as requisições (REST Client / IntelliJ)
.env.example          modelo das variáveis de ambiente (o .env não é versionado)
docker-compose.yml    PostgreSQL alternativo via Docker
```
