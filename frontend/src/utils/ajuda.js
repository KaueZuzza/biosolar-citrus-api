/* Ajuda "?" para quem não conhece agricultura, automação ou programação.
 * Uso: <button type="button" class="ajuda" data-ajuda="chave"></button>
 * Passar o mouse ou focar mostra a explicação; clicar fixa/solta; Esc fecha. */
(function () {
  var TEXTOS = {
    // Visão geral
    reservatorio: ['Nível do reservatório', 'Quanta água há na caixa-d\'água central que abastece todos os aspersores, em % da capacidade. Abaixo de 15% o sistema desliga todas as bombas para não secar a reserva.'],
    umidade: ['Umidade do solo', 'Quanta água existe na terra onde estão as árvores. Abaixo de 30% o talhão entra em atenção; abaixo de 25% o sistema liga a irrigação sozinho.'],
    aspersores: ['Aspersores ativos', 'Quantos aspersores (os "chuveiros" que molham o pomar) estão ligados agora. Cada um tem sua própria bomba, que gasta água do reservatório e energia.'],
    indice: ['Índice Hidro-Energético', 'Uma nota de 0 a 100 que resume a saúde da fazenda: água guardada (50%), solo úmido (30%) e uso de energia solar (20%). Acima de 70 está bom; abaixo de 40 há risco.'],
    energia: ['Energia das bombas', 'Potência que as bombas usam agora, em kW (quilowatts). O percentual mostra quanto vem dos painéis solares: quanto mais solar, menor a conta de luz.'],
    decisao: ['Motor de decisão', 'O sistema escolhe a situação mais importante do momento e explica em linguagem simples o que aconteceu, onde, por quê e o que ele fez sozinho.'],
    volume: ['Volume', 'Quantidade de água no reservatório em metros cúbicos. 1 m³ = 1.000 litros.'],
    consumo: ['Consumo', 'Quanta água as bombas ligadas estão tirando do reservatório por hora (m³/h).'],
    recarga: ['Recarga', 'Água que entra no reservatório vinda do poço, cuja bomba funciona com energia solar. Por isso a recarga é maior ao meio-dia e zero à noite.'],
    tendencia: ['Tendência', 'Quanto o nível do reservatório sobe (+) ou desce (−) por hora. "p.p./h" significa pontos percentuais por hora: de 50% para 48% é −2 p.p.'],
    autonomia: ['Autonomia', 'Tempo estimado até o reservatório chegar a 15% (o limite de segurança) se o consumo continuar igual. "Estável" quando a água não está diminuindo.'],
    protecao: ['Proteção hídrica', 'Trava de segurança: com o reservatório abaixo de 15%, nenhuma bomba liga, nem pelo sistema nem por uma pessoa. Ela só libera quando o nível volta a 20%.'],
    'status-talhao': ['O que significam as cores', '🟢 Normal: o solo tem água suficiente. 🟡 Atenção: está secando (abaixo de 30%). 🔴 Crítico: abaixo de 25%; o sistema liga a irrigação sozinho, se houver água no reservatório.'],

    // Talhões
    'grafico-umidade': ['Gráfico de umidade', 'Mostra como a umidade de cada talhão mudou ao longo do tempo. Linhas descendo = solo secando; subindo = irrigação funcionando. A linha tracejada é o limite crítico.'],
    evapotranspiracao: ['Perda de água no sol', 'Quanto de umidade o solo perde por hora no sol mais forte, pela evaporação e pelas plantas (evapotranspiração). Solos arenosos perdem mais.'],
    prioridade: ['Prioridade', 'Ordem de atendimento da irrigação automática: 1 (alta) é atendido primeiro. Use 1 para culturas mais sensíveis à falta de água.'],

    // Irrigação
    controle: ['Aspersores e bombas', 'Aqui uma pessoa pode ligar ou desligar cada aspersor. O servidor confere as regras de segurança e pode recusar o comando, explicando o motivo.'],
    regras: ['Regras de automação', 'O sistema segue 4 regras em ordem de importância. Se duas entram em conflito, vale a de número menor: proteger a água (P1) sempre vem primeiro.'],
    acionamentos: ['Acionamentos', 'Quantas vezes cada aspersor foi ligado automaticamente pelo sistema e quantas vezes por uma pessoa (comando manual).'],
    modo: ['Modo do aspersor', 'Automático: ligado pelo sistema por causa da umidade baixa. Manual: ligado por uma pessoa.'],

    // Monitoramento
    alertas: ['Alertas ativos', 'Situações que pedem atenção agora. Eles somem sozinhos quando o problema é resolvido.'],
    'grafico-reservatorio': ['Gráfico do reservatório', 'Como o nível de água mudou com o tempo. A faixa amarela é a zona de atenção (15% a 30%) e a vermelha, a de bloqueio (abaixo de 15%).'],
    historico: ['Histórico de eventos', 'Tudo o que aconteceu, gravado no banco de dados: ações do sistema (Automação), de pessoas (Operador), mudanças de cadastro e testes (Simulação).'],
    saude: ['Saúde do sistema', 'Mostra se cada parte está funcionando: o servidor (API), o banco de dados, o simulador, os sensores de umidade e as bombas.'],

    // Relatórios
    relatorio: ['Exportar e compartilhar', 'PDF: relatório completo com gráficos, para ler ou imprimir. Excel: planilha com uma aba por assunto, filtros e números prontos para análise. WhatsApp: mensagem curta com o status de agora. E-mail: o servidor envia o PDF e/ou o Excel. Tudo vem do banco de dados no momento do clique.'],
    'wpp-numero': ['Número do WhatsApp', 'Opcional. Com DDD, só números ou do jeito que preferir, ex.: (91) 98888-7777. Em branco, o WhatsApp abre para você escolher o contato ou o grupo.'],
    'email-para': ['Destinatários', 'Um ou mais e-mails separados por vírgula (máximo 5). O e-mail sai do servidor da fazenda; nenhuma senha passa pelo navegador.'],
    citrus: ['Citrus, o assistente', 'Toque no microfone e fale como falaria com uma pessoa: \"Citrus, quero o status\", \"como está o reservatório?\", \"mostre o talhão C\", \"gere um relatório\". Ele consulta os dados reais da fazenda e responde em texto e em voz. Também liga ou desliga um aspersor, sempre com as mesmas regras de segurança do painel. Sem microfone, digite a pergunta.'],
    periodo: ['Indicadores do período', 'Totais acumulados desde a última vez que o cenário foi restaurado: água e energia gastas, níveis do reservatório e quantas irrigações ocorreram.'],

    // Gestão
    cadastro: ['Cadastro de talhões', 'Talhão é uma área do pomar com a mesma cultura. Cada um tem uma bomba com aspersor e um sensor de umidade. O que você cadastrar aqui fica salvo no banco e passa a ser irrigado automaticamente.'],
    codigo: ['Código do talhão', 'Identificação curta e única (ex.: E, T5, NORTE1), só com letras e números. Não pode ser alterada depois; a bomba recebe o nome MB-código.'],
    solo: ['Tipo de solo', 'Informação agronômica do talhão. Solos arenosos seguram menos água que os argilosos.'],
    area: ['Área', 'Tamanho do talhão em hectares. 1 hectare (ha) = 10.000 m², mais ou menos um campo de futebol.'],
    'umidade-inicial': ['Umidade inicial', 'Umidade com que o talhão começa quando o cenário de demonstração é restaurado.'],
    'limite-critico': ['Limite crítico', 'Abaixo deste valor o sistema liga a irrigação sozinho (regra P2). É fixo em 25%, conforme o regulamento.'],
    'limite-atencao': ['Limite de atenção', 'Abaixo deste valor o talhão fica amarelo (atenção) e o sistema avisa que ele está secando.'],
    'umidade-alvo': ['Irrigar até (alvo)', 'Quando a irrigação automática liga, ela para ao atingir este valor, para não gastar água e energia à toa.'],
    vazao: ['Vazão da bomba', 'Quanta água a bomba puxa do reservatório por hora, em m³ (1 m³ = 1.000 litros).'],
    potencia: ['Potência da bomba', 'Energia que a bomba consome enquanto está ligada, em kW (quilowatts).'],
    ganho: ['Ganho do aspersor', 'Quanto a umidade do solo sobe por hora com o aspersor ligado. Precisa ser maior que a perda no sol, senão o solo nunca se recupera.'],
    capacidade: ['Capacidade', 'Quanta água cabe no reservatório cheio, em m³ (1 m³ = 1.000 litros).'],
    'nivel-inicial': ['Nível inicial', 'Nível com que o reservatório começa quando o cenário de demonstração é restaurado.'],
    kwp: ['Potência da usina (kWp)', 'Potência máxima que os painéis solares geram ao meio-dia com sol pleno. "kWp" é quilowatt-pico.'],
    banco: ['Banco de dados', 'Onde tudo fica guardado (PostgreSQL): o cadastro, o estado da fazenda e o histórico. Se ele cair, a automação continua funcionando e grava tudo quando ele voltar.'],

    // Mapa da Fazenda
    'mapa-fazenda': ['Mapa da Fazenda', 'Foto de satélite real da região de Capitão Poço (PA). Arraste para mover e use a roda do mouse ou os botões + e − para aproximar. Cada talhão aparece colorido pela umidade atual (verde, amarelo ou vermelho), com os dados vindos do servidor. No ícone de camadas (canto superior direito) dá para trocar para o mapa de ruas e ligar ou desligar os nomes e o limite do município.'],
    'posicao-ilustrativa': ['Posição ilustrativa ou área desenhada', 'Enquanto você não marcar onde o talhão fica de verdade, ele aparece como um quadrado tracejado com a área cadastrada, perto de Capitão Poço: é só uma ilustração. Selecione o talhão, clique em "Desenhar área" e clique no mapa nos cantos do talhão, em sequência. A área real fica salva no banco de dados.'],
    'fontes-publicas': ['Dados públicos', 'Informações oficiais consultadas pelo servidor: IBGE (município, limites e produção agrícola da PAM) e Open-Meteo (tempo, chuva e evapotranspiração). Se a internet cair, o painel avisa que estão indisponíveis; nada é inventado no lugar.'],
    'agente-agricola': ['Agente agrícola', 'Um assistente que junta os dados da fazenda (umidade, irrigação, solo, reservatório) com dados públicos (clima e produção da região) e explica o que está acontecendo e quais cuidados tomar. Cada informação mostra de onde veio: dado do sistema, fonte pública, estimativa (sempre com a base do cálculo) ou orientação geral. Ele funciona por regras e dados reais: não inventa números.'],

    // Demonstração
    velocidade: ['Velocidade do tempo', 'Acelera o relógio da fazenda para mostrar horas de funcionamento em poucos minutos. Em 1×, 1 segundo real equivale a 1 minuto na fazenda.'],
    restaurar: ['Restaurar cenário', 'Volta a fazenda aos níveis iniciais cadastrados (umidade de cada talhão e nível do reservatório), desliga os aspersores e limpa o histórico. O cadastro é mantido.']
  };

  var pop = null;
  var botaoAtual = null;
  var fixo = false;

  function criarPop() {
    pop = document.createElement('div');
    pop.className = 'ajuda-pop';
    pop.id = 'ajuda-pop';
    pop.setAttribute('role', 'tooltip');
    pop.hidden = true;
    document.body.appendChild(pop);
  }

  function prepararBotao(btn) {
    if (btn.__ajudaPronto) return;
    btn.__ajudaPronto = true;
    var t = TEXTOS[btn.getAttribute('data-ajuda')];
    if (!btn.getAttribute('aria-label')) btn.setAttribute('aria-label', 'Ajuda: ' + (t ? t[0] : 'explicação'));
    btn.setAttribute('aria-expanded', 'false');
  }

  function posicionar(btn) {
    var r = btn.getBoundingClientRect();
    var largura = pop.offsetWidth, altura = pop.offsetHeight;
    var x = Math.min(Math.max(8, r.left + r.width / 2 - largura / 2), window.innerWidth - largura - 8);
    var y = r.bottom + 8;
    if (y + altura > window.innerHeight - 8) y = Math.max(8, r.top - altura - 8);
    pop.style.left = Math.round(x) + 'px';
    pop.style.top = Math.round(y) + 'px';
  }

  function mostrar(btn, fixar) {
    var t = TEXTOS[btn.getAttribute('data-ajuda')];
    if (!t) return;
    if (!pop) criarPop();
    // Dentro de um <dialog> aberto o balão precisa estar no mesmo "top layer" para aparecer por cima
    var destino = btn.closest('dialog[open]') || document.body;
    if (pop.parentNode !== destino) destino.appendChild(pop);
    if (botaoAtual && botaoAtual !== btn) soltar(botaoAtual);
    botaoAtual = btn;
    fixo = !!fixar;
    pop.innerHTML = '<b>' + BS.fmt.esc(t[0]) + '</b>' + BS.fmt.esc(t[1]);
    pop.hidden = false;
    btn.setAttribute('aria-describedby', 'ajuda-pop');
    btn.setAttribute('aria-expanded', 'true');
    posicionar(btn);
  }

  function soltar(btn) {
    btn.removeAttribute('aria-describedby');
    btn.setAttribute('aria-expanded', 'false');
  }

  function esconder() {
    if (!pop || pop.hidden) return;
    pop.hidden = true;
    fixo = false;
    if (botaoAtual) soltar(botaoAtual);
    botaoAtual = null;
  }

  document.addEventListener('mouseover', function (e) {
    var btn = e.target.closest && e.target.closest('.ajuda');
    if (btn) { prepararBotao(btn); if (!fixo || botaoAtual !== btn) mostrar(btn, false); }
  });
  document.addEventListener('mouseout', function (e) {
    var btn = e.target.closest && e.target.closest('.ajuda');
    if (btn && btn === botaoAtual && !fixo) esconder();
  });
  document.addEventListener('focusin', function (e) {
    var btn = e.target.closest && e.target.closest('.ajuda');
    if (btn) { prepararBotao(btn); mostrar(btn, false); }
  });
  document.addEventListener('focusout', function (e) {
    if (e.target === botaoAtual && !fixo) esconder();
  });
  document.addEventListener('click', function (e) {
    var btn = e.target.closest && e.target.closest('.ajuda');
    if (btn) {
      e.preventDefault();
      e.stopPropagation();   // não aciona o elemento em volta (ex.: cartão clicável)
      prepararBotao(btn);
      if (fixo && botaoAtual === btn) esconder(); else mostrar(btn, true);
      return;
    }
    if (fixo) esconder();
  }, true);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && pop && !pop.hidden) { esconder(); }
  });
  window.addEventListener('scroll', function () { if (botaoAtual && !pop.hidden) posicionar(botaoAtual); }, true);
  window.addEventListener('resize', esconder);

  BS.ajuda = {
    /** Marca o botão "?" para uso em HTML gerado por componentes. */
    botao: function (chave) { return '<button type="button" class="ajuda" data-ajuda="' + chave + '"></button>'; },
    preparar: function (raiz) { BS.dom.$$('.ajuda', raiz).forEach(prepararBotao); },
    textos: TEXTOS
  };

  document.addEventListener('DOMContentLoaded', function () { BS.ajuda.preparar(document); });
  if (document.readyState !== 'loading') BS.ajuda.preparar(document);
})();
