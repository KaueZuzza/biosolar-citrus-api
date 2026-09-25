/* Gestão: cadastro de talhões (com bomba, aspersor e sensor), reservatório e usina solar.
 * Tudo passa pela API e é gravado no PostgreSQL; o que é salvo aqui entra na automação na hora.
 * Excluir um talhão o arquiva (histórico preservado); a exclusão definitiva é uma segunda etapa. */
BS.componentes.gestao = (function () {
  var f = BS.fmt;
  var svc = BS.cadastroService;
  var talhoes = [];
  var carregado = false;         // evita mostrar "nenhum talhão" antes da primeira leitura
  var regras = null;
  var editando = null;          // código do talhão em edição (null = novo)
  var timer = null;

  // Padrão das motobombas e do manejo usados nos talhões do projeto (editáveis no formulário)
  var PADRAO_TECNICO = { limiteAtencao: 30, umidadeAlvo: 40, taxaEvapotranspiracao: 1.5, ganhoIrrigacao: 7,
    vazaoBombaM3h: 18, potenciaBombaKw: 5.5, prioridade: 2 };
  var NOMES_CAMPOS = { id: 'Código', nome: 'Nome', cultura: 'Cultura', variedade: 'Variedade', solo: 'Tipo de solo',
    areaHa: 'Área', plantas: 'Nº de plantas', prioridade: 'Prioridade', umidadeInicial: 'Umidade inicial',
    limiteAtencao: 'Atenção abaixo de', umidadeAlvo: 'Irrigar até (alvo)', taxaEvapotranspiracao: 'Perda no pico de sol',
    ganhoIrrigacao: 'Ganho do aspersor', vazaoBombaM3h: 'Vazão da bomba', potenciaBombaKw: 'Potência da bomba',
    capacidadeM3: 'Capacidade', vazaoRecargaM3h: 'Recarga do poço', nivelInicial: 'Nível inicial',
    potenciaSolarPicoKw: 'Potência da usina' };

  // ---------------------------------------------------------------------------------------------
  // Mensagens de resultado
  // ---------------------------------------------------------------------------------------------

  function mensagemDeErro(r) {
    var d = (r && r.dados) || {};
    var texto = d.mensagem || ('O servidor respondeu HTTP ' + (r ? r.status : '?') + '.');
    var detalhes = (d.detalhes || []).map(function (linha) {
      var i = linha.indexOf(':');
      var campo = i > 0 ? linha.slice(0, i) : '';
      return NOMES_CAMPOS[campo] ? NOMES_CAMPOS[campo] + linha.slice(i) : linha;
    });
    return { texto: texto, detalhes: detalhes };
  }

  function mostrarErros(id, r) {
    var el = document.getElementById(id);
    var e = mensagemDeErro(r);
    el.innerHTML = f.esc(e.texto) + (e.detalhes.length
      ? '<ul>' + e.detalhes.map(function (d) { return '<li>' + f.esc(d) + '</li>'; }).join('') + '</ul>' : '');
    el.hidden = false;
  }

  function aposAlteracao(titulo, descricao) {
    BS.toast({ severidade: 'SUCESSO', titulo: titulo, descricao: ' ' + descricao });
    BS.dom.anunciar(titulo + '. ' + descricao);
    carregar();
    BS.app.atualizarAgora();
  }

  async function executar(promessa, sucessoTitulo, sucessoDescricao) {
    try {
      var r = await promessa;
      if (r.ok) { aposAlteracao(sucessoTitulo, sucessoDescricao); return true; }
      var e = mensagemDeErro(r);
      BS.toast({ severidade: 'ATENCAO', titulo: 'Não foi possível concluir', descricao: ' ' + e.texto + ' ' + e.detalhes.join(' ') });
    } catch (err) {
      BS.toast({ severidade: 'CRITICO', titulo: 'Servidor indisponível', descricao: ' A alteração não foi enviada.' });
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // Lista de talhões
  // ---------------------------------------------------------------------------------------------

  async function carregar() {
    try {
      var r = await svc.listarTalhoes();
      if (!r.ok) {
        var e = mensagemDeErro(r);
        BS.dom.html('cad-lista', '<p class="vazio-lista">⚠️ ' + f.esc(e.texto) + '</p>');
        return;
      }
      talhoes = r.dados;
      carregado = true;
      render();
    } catch (err) {
      BS.dom.html('cad-lista', '<p class="vazio-lista">⚠️ Servidor indisponível: não foi possível ler o cadastro.</p>');
    }
  }

  function render() {
    var ativos = talhoes.filter(function (t) { return t.ativo; }).length;
    var maximo = regras ? regras.maximoTalhoes : 8;
    var novo = document.getElementById('cad-novo');
    novo.disabled = ativos >= maximo;
    novo.title = ativos >= maximo ? 'Limite de ' + maximo + ' talhões ativos atingido' : '';
    if (!carregado) return;

    if (!talhoes.length) {
      BS.dom.html('cad-lista', '<p class="vazio-lista">Nenhum talhão cadastrado. Use “Novo talhão”.</p>');
      return;
    }
    BS.dom.html('cad-lista', '<table class="dados"><caption class="sr-only">Talhões cadastrados no banco de dados</caption>' +
      '<thead><tr><th scope="col">Código</th><th scope="col">Nome</th><th scope="col">Cultura</th>' +
      '<th scope="col">Área</th><th scope="col">Prioridade</th><th scope="col">Bomba · sensor</th>' +
      '<th scope="col">Situação</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead><tbody>' +
      talhoes.map(function (t) {
        var acoes = t.ativo
          ? '<button class="btn btn-sm" type="button" data-acao="editar" data-id="' + f.esc(t.id) + '">✏️ Editar</button>' +
            '<button class="btn btn-sm" type="button" data-acao="excluir" data-id="' + f.esc(t.id) + '">🗑️ Excluir</button>'
          : '<button class="btn btn-sm" type="button" data-acao="reativar" data-id="' + f.esc(t.id) + '">↩ Reativar</button>' +
            '<button class="btn btn-sm" type="button" data-acao="editar" data-id="' + f.esc(t.id) + '">✏️ Editar</button>' +
            '<button class="btn btn-sm btn-danger" type="button" data-acao="definitivo" data-id="' + f.esc(t.id) + '">Excluir definitivamente</button>';
        return '<tr class="' + (t.ativo ? '' : 'arquivado') + '"><th scope="row">' + f.esc(t.id) + '</th>' +
          '<td>' + f.esc(t.nome) + '</td>' +
          '<td>' + (t.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + f.esc(t.culturaRotulo + ' ' + t.variedade) + '</td>' +
          '<td class="num">' + f.num(t.areaHa) + ' ha</td>' +
          '<td>' + t.prioridade + (t.prioridade === 1 ? ' · alta' : t.prioridade === 2 ? ' · média' : ' · baixa') + '</td>' +
          '<td>' + f.esc(t.bombaId + ' · ' + t.sensorId) + '</td>' +
          '<td>' + (t.ativo ? '<span class="pill ok">🟢 Em operação</span>' : '<span class="pill neutral">📦 Arquivado</span>') + '</td>' +
          '<td><div class="acoes-linha">' + acoes + '</div></td></tr>';
      }).join('') + '</tbody></table>');
  }

  function porId(id) {
    return talhoes.filter(function (t) { return t.id === id; })[0];
  }

  async function aoClicarLista(e) {
    var b = e.target.closest('button[data-acao]');
    if (!b || b.getAttribute('aria-busy') === 'true') return;
    var t = porId(b.getAttribute('data-id'));
    if (!t) return;
    var acao = b.getAttribute('data-acao');
    if (acao === 'editar') { abrirFormulario(t); return; }

    if (acao === 'excluir' && !window.confirm('Excluir o ' + t.nome + ' da operação?\n\nEle sai da automação e do painel ' +
        '(a bomba é desligada). O histórico é preservado e o talhão pode ser reativado depois.')) return;
    if (acao === 'definitivo' && !window.confirm('Excluir DEFINITIVAMENTE o ' + t.nome + '?\n\nO cadastro e as leituras de ' +
        'umidade deste talhão serão apagados do banco. Os eventos continuam no histórico, sem o vínculo com o talhão. ' +
        'Esta ação não pode ser desfeita.')) return;

    b.setAttribute('aria-busy', 'true');
    try {
      if (acao === 'excluir') await executar(svc.excluirTalhao(t.id), 'Talhão excluído da operação', t.nome + ' foi arquivado.');
      if (acao === 'reativar') await executar(svc.reativarTalhao(t.id), 'Talhão reativado', t.nome + ' voltou à automação.');
      if (acao === 'definitivo') await executar(svc.excluirDefinitivamente(t.id), 'Talhão removido', t.nome + ' foi apagado do banco.');
    } finally {
      b.removeAttribute('aria-busy');
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Formulário do talhão (novo / edição)
  // ---------------------------------------------------------------------------------------------

  function abrirFormulario(t) {
    var form = document.getElementById('form-talhao');
    form.reset();
    document.getElementById('cad-erros').hidden = true;
    editando = t ? t.id : null;
    var v = t || PADRAO_TECNICO;
    ['nome', 'variedade', 'solo'].forEach(function (c) { form.elements[c].value = t ? t[c] : ''; });
    ['areaHa', 'plantas', 'umidadeInicial'].forEach(function (c) { form.elements[c].value = t ? t[c] : ''; });
    ['limiteAtencao', 'umidadeAlvo', 'taxaEvapotranspiracao', 'ganhoIrrigacao', 'vazaoBombaM3h', 'potenciaBombaKw']
      .forEach(function (c) { form.elements[c].value = v[c]; });
    form.elements.prioridade.value = String(v.prioridade);
    form.elements.cultura.value = t ? t.cultura : 'LARANJA';
    form.elements.limiteCritico.value = t ? t.limiteCritico : (regras ? regras.limiteCriticoTalhao : 25);
    form.elements.id.value = t ? t.id : '';
    form.elements.id.readOnly = !!t;
    BS.dom.attr(form.elements.id, 'aria-readonly', t ? 'true' : null);
    document.getElementById('cad-nota-padrao').hidden = !!t;
    BS.dom.texto('dlg-cad-titulo', t ? '✏️ Editar ' + t.nome : '➕ Novo talhão');
    BS.dom.texto('cad-salvar', t ? 'Salvar alterações' : 'Cadastrar talhão');
    BS.dom.abrirDialogo(document.getElementById('dlg-cadastro'));
    (t ? form.elements.nome : form.elements.id).focus();
  }

  function numero(form, campo) {
    var v = form.elements[campo].value;
    return v === '' ? null : Number(v);
  }

  async function salvarTalhao(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var botao = document.getElementById('cad-salvar');
    if (botao.getAttribute('aria-busy') === 'true') return;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    var dados = {
      id: form.elements.id.value.trim().toUpperCase(),
      nome: form.elements.nome.value.trim(),
      cultura: form.elements.cultura.value,
      variedade: form.elements.variedade.value.trim(),
      solo: form.elements.solo.value.trim(),
      areaHa: numero(form, 'areaHa'),
      plantas: numero(form, 'plantas'),
      prioridade: numero(form, 'prioridade'),
      umidadeInicial: numero(form, 'umidadeInicial'),
      limiteAtencao: numero(form, 'limiteAtencao'),
      umidadeAlvo: numero(form, 'umidadeAlvo'),
      taxaEvapotranspiracao: numero(form, 'taxaEvapotranspiracao'),
      ganhoIrrigacao: numero(form, 'ganhoIrrigacao'),
      vazaoBombaM3h: numero(form, 'vazaoBombaM3h'),
      potenciaBombaKw: numero(form, 'potenciaBombaKw')
    };
    botao.setAttribute('aria-busy', 'true');
    try {
      var r = editando ? await svc.atualizarTalhao(editando, dados) : await svc.criarTalhao(dados);
      if (r.ok) {
        document.getElementById('dlg-cadastro').close();
        aposAlteracao(editando ? 'Cadastro atualizado' : 'Talhão cadastrado',
          dados.nome + (editando ? ': as novas regras já valem na automação.' : ' entrou na automação.'));
      } else {
        mostrarErros('cad-erros', r);
      }
    } catch (err) {
      mostrarErros('cad-erros', { status: 0, dados: { mensagem: 'Servidor indisponível: o talhão não foi salvo.' } });
    } finally {
      botao.removeAttribute('aria-busy');
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Reservatório e usina
  // ---------------------------------------------------------------------------------------------

  function preencherConfig(cfg) {
    regras = cfg.regras;
    var r = cfg.reservatorio;
    BS.dom.texto('cfg-regras', 'Limites de segurança do regulamento (fixos): bloqueio abaixo de ' + f.pct(r.limiteCritico, 0) +
      ', rearme em ' + f.pct(r.limiteRearme, 0) + ', atenção abaixo de ' + f.pct(r.limiteAtencao, 0) +
      '. Irrigação crítica abaixo de ' + f.pct(cfg.regras.limiteCriticoTalhao, 0) + ' de umidade; até ' +
      cfg.regras.maximoTalhoes + ' talhões ativos.');
    var fr = document.getElementById('form-reservatorio');
    if (!fr.__editado) {
      fr.elements.nome.value = r.nome;
      fr.elements.capacidadeM3.value = r.capacidadeM3;
      fr.elements.vazaoRecargaM3h.value = r.vazaoRecargaM3h;
      fr.elements.nivelInicial.value = r.nivelInicial;
    }
    var fu = document.getElementById('form-usina');
    if (!fu.__editado) fu.elements.potenciaSolarPicoKw.value = cfg.usina.potenciaSolarPicoKw;
  }

  async function carregarConfig() {
    try {
      var r = await svc.configuracao();
      if (r.ok) { preencherConfig(r.dados); render(); }
      var s = await BS.telemetriaService.saude();
      if (s.ok) {
        var b = s.dados.banco;
        BS.dom.texto('bd-status', b.status === 'UP'
          ? '🟢 Conectado · ' + String(b.detalhe).split(' ').slice(0, 2).join(' ') + (b.latenciaMs !== null ? ' · ' + b.latenciaMs + ' ms' : '')
          : '🔴 Indisponível: a automação segue em memória; o cadastro fica bloqueado até o banco voltar.');
      }
    } catch (e) { /* conexão tratada pelo indicador da API */ }
  }

  async function salvarConfig(e, tipo) {
    e.preventDefault();
    var form = e.currentTarget;
    var botao = form.querySelector('[type="submit"]');
    if (botao.getAttribute('aria-busy') === 'true') return;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    document.getElementById('cfg-erros').hidden = true;
    botao.setAttribute('aria-busy', 'true');
    try {
      var r = tipo === 'reservatorio'
        ? await svc.salvarReservatorio({
          nome: form.elements.nome.value.trim(),
          capacidadeM3: numero(form, 'capacidadeM3'),
          vazaoRecargaM3h: numero(form, 'vazaoRecargaM3h'),
          nivelInicial: numero(form, 'nivelInicial')
        })
        : await svc.salvarUsina({ potenciaSolarPicoKw: numero(form, 'potenciaSolarPicoKw') });
      if (r.ok) {
        form.__editado = false;
        preencherConfig(r.dados);
        aposAlteracao(tipo === 'reservatorio' ? 'Reservatório atualizado' : 'Usina solar atualizada', 'Dados gravados no banco.');
      } else {
        mostrarErros('cfg-erros', r);
      }
    } catch (err) {
      mostrarErros('cfg-erros', { status: 0, dados: { mensagem: 'Servidor indisponível: nada foi salvo.' } });
    } finally {
      botao.removeAttribute('aria-busy');
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Ciclo de vida
  // ---------------------------------------------------------------------------------------------

  function aoMudarAba(nome) {
    clearInterval(timer);
    if (nome !== 'gestao') return;
    carregar();
    carregarConfig();
    // Enquanto a aba estiver aberta, reflete também mudanças feitas direto no banco (ex.: pgAdmin)
    timer = setInterval(function () { carregar(); carregarConfig(); }, 10000);
  }

  function iniciar() {
    document.getElementById('cad-novo').addEventListener('click', function () { abrirFormulario(null); });
    document.getElementById('cad-lista').addEventListener('click', aoClicarLista);
    document.getElementById('form-talhao').addEventListener('submit', salvarTalhao);
    ['form-reservatorio', 'form-usina'].forEach(function (id) {
      var form = document.getElementById(id);
      form.addEventListener('input', function () { form.__editado = true; });
      form.addEventListener('submit', function (e) { salvarConfig(e, id === 'form-reservatorio' ? 'reservatorio' : 'usina'); });
    });
    BS.componentes.abas.aoMudar(aoMudarAba);
    // A seção pode já estar aberta (ex.: página aberta em #gestao) antes deste registro
    if (BS.componentes.abas.atual() === 'gestao') aoMudarAba('gestao');
    else carregarConfig();   // regras (limite crítico, máximo de talhões) para o formulário
  }

  return {
    iniciar: iniciar,
    /** Abre a edição de um talhão (atalho a partir dos detalhes no mapa). */
    editar: async function (id) {
      BS.componentes.abas.ir('gestao');
      if (!porId(id)) await carregar();
      var t = porId(id);
      if (t) abrirFormulario(t);
    }
  };
})();
