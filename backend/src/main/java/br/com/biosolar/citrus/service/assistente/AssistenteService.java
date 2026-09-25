package br.com.biosolar.citrus.service.assistente;

import static br.com.biosolar.citrus.util.Formatador.horas;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.dto.AcionamentoRequest;
import br.com.biosolar.citrus.dto.AcionamentoResponse;
import br.com.biosolar.citrus.dto.AlertaDTO;
import br.com.biosolar.citrus.dto.AssistenteDTO;
import br.com.biosolar.citrus.dto.AssistenteDTO.Acao;
import br.com.biosolar.citrus.dto.AssistenteDTO.Intencao;
import br.com.biosolar.citrus.dto.DecisaoDTO;
import br.com.biosolar.citrus.dto.EnergiaDTO;
import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.IndiceDTO;
import br.com.biosolar.citrus.dto.RelatorioDTO;
import br.com.biosolar.citrus.dto.ReservatorioDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.service.AcionamentoService;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.RelatorioService;
import br.com.biosolar.citrus.service.TelemetriaService;
import br.com.biosolar.citrus.util.Rotulos;

/**
 * "Citrus": assistente operacional por voz/texto. Interpreta o comando em portugues (com ou sem a palavra
 * "Citrus"), consulta o estado real da fazenda (automacao + PostgreSQL) e responde em texto e em uma versao
 * adequada para leitura em voz. Acoes (ligar/desligar aspersor) passam pelas MESMAS regras de seguranca do
 * comando manual do painel: o assistente nunca contorna o bloqueio de emergencia.
 */
@Service
public class AssistenteService {

    private static final Logger log = LoggerFactory.getLogger(AssistenteService.class);

    public static final String NAO_ENTENDI = "Não consegui entender esse comando. Tente perguntar sobre o "
            + "reservatório, talhões, irrigação, alertas ou relatório.";

    private static final Pattern PALAVRA_ATIVACAO = Pattern.compile("\\b(ok |ola |oi |ei |hey )?(citrus|citros|citrous|sitrus|sitros|citru|citrix|citrux)\\b");
    private static final Pattern DESLIGAR = Pattern.compile("\\b(desliga|desligar|desligue|pare|parar|interrompa|interromper|desative|desativar|encerre|encerrar|suspenda|suspender)\\b");
    private static final Pattern LIGAR = Pattern.compile("\\b(liga|ligar|ligue|acione|acionar|aciona|ative|ativar|ativa|irrigue|irrigar|inicie|iniciar|comece|comecar)\\b");
    private static final Pattern OBJETO_IRRIGACAO = Pattern.compile("\\b(aspersor|aspersores|bomba|bombas|irrigacao|irrigar|irrigue|agua|motobomba)\\b");
    private static final Set<String> PALAVRAS_TALHAO = Set.of("talhao", "talhoes", "lote", "setor", "quadra");
    /** Palavras que podem vir entre "talhao" e o codigo ("talhao do B", "talhao numero 2"). */
    private static final Set<String> CONECTIVOS = Set.of("do", "da", "o", "numero", "n", "nr");
    /** Nomes das letras e numeros como o reconhecimento de voz costuma transcrever ("talhão cê", "talhão dois"). */
    private static final Map<String, String> SOLETRADO = Map.ofEntries(
            Map.entry("ah", "A"), Map.entry("be", "B"), Map.entry("ce", "C"), Map.entry("se", "C"),
            Map.entry("sei", "C"), Map.entry("de", "D"), Map.entry("dei", "D"), Map.entry("eh", "E"), Map.entry("efe", "F"),
            Map.entry("ge", "G"), Map.entry("gue", "G"), Map.entry("aga", "H"), Map.entry("jota", "J"), Map.entry("ca", "K"),
            Map.entry("ele", "L"), Map.entry("eme", "M"), Map.entry("ene", "N"), Map.entry("pe", "P"), Map.entry("que", "Q"),
            Map.entry("erre", "R"), Map.entry("esse", "S"), Map.entry("te", "T"), Map.entry("ve", "V"), Map.entry("xis", "X"),
            Map.entry("ze", "Z"), Map.entry("um", "1"), Map.entry("dois", "2"), Map.entry("tres", "3"),
            Map.entry("quatro", "4"), Map.entry("cinco", "5"), Map.entry("seis", "6"), Map.entry("sete", "7"),
            Map.entry("oito", "8"));
    private static final Set<TipoEvento> EVENTOS_RELEVANTES = EnumSet.of(TipoEvento.IRRIGACAO_CRITICA,
            TipoEvento.IRRIGACAO_CONCLUIDA, TipoEvento.PROTECAO_SATURACAO, TipoEvento.COMANDO_MANUAL,
            TipoEvento.COMANDO_RECUSADO, TipoEvento.BLOQUEIO_EMERGENCIA, TipoEvento.IRRIGACAO_BLOQUEADA,
            TipoEvento.RECUPERACAO_SISTEMA, TipoEvento.ALERTA_UMIDADE, TipoEvento.ALERTA_RESERVATORIO,
            TipoEvento.CADASTRO, TipoEvento.RELATORIO);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final TelemetriaService telemetriaService;
    private final AcionamentoService acionamentoService;
    private final RelatorioService relatorioService;
    private final EventoRepository eventoRepository;
    private final EstadoFazendaService estado;
    private final Clock clock;

    public AssistenteService(TelemetriaService telemetriaService, AcionamentoService acionamentoService,
                             RelatorioService relatorioService, EventoRepository eventoRepository,
                             EstadoFazendaService estado, Clock clock) {
        this.telemetriaService = telemetriaService;
        this.acionamentoService = acionamentoService;
        this.relatorioService = relatorioService;
        this.eventoRepository = eventoRepository;
        this.estado = estado;
        this.clock = clock;
    }

    public AssistenteDTO.Exemplos exemplos() {
        return new AssistenteDTO.Exemplos(
                List.of("Citrus, quero o status", "Como está o reservatório?", "Quais talhões estão críticos?",
                        "Tem algum alerta?", "Como está a irrigação?", "Mostre o talhão C", "O que está acontecendo?",
                        "Gere um relatório", "Relatório de hoje"),
                List.of("Ligar o aspersor do talhão B", "Desligar o aspersor do talhão B"));
    }

    public AssistenteDTO.Resposta interpretar(String textoOriginal) {
        String comando = normalizar(textoOriginal);
        TelemetriaDTO tel = telemetriaService.obterTelemetria();
        Optional<String> talhao = identificarTalhao(comando, tel.talhoes());
        boolean mencionaTalhao = contem(comando, "talhao", "talhoes");

        if (DESLIGAR.matcher(comando).find() && (OBJETO_IRRIGACAO.matcher(comando).find() || talhao.isPresent())) {
            return acionar(comando, tel, talhao, false);
        }
        if (LIGAR.matcher(comando).find() && (OBJETO_IRRIGACAO.matcher(comando).find() || talhao.isPresent())) {
            return acionar(comando, tel, talhao, true);
        }
        if (contem(comando, "relatorio", "exportar", "exporte", "planilha", "excel", "pdf")) {
            return relatorio(comando, tel);
        }
        if (talhao.isPresent()) {
            return talhao(comando, tel, talhao.get());
        }
        if (contem(comando, "critic")) {
            return criticos(comando, tel);
        }
        if (mencionaTalhao && contem(comando, "mostre", "mostrar", "mostra", "abra", "abrir", "detalhe", "ver ")) {
            return resposta(Intencao.TALHAO, comando, "Qual talhão? Os talhões ativos são "
                    + listaIds(tel.talhoes()) + ". Diga, por exemplo: mostre o talhão " + primeiroId(tel) + ".",
                    new Acao("NAVEGAR", "talhoes", null, "Ver talhões"), false);
        }
        if (contem(comando, "alerta", "aviso", "problema", "risco", "perigo")) {
            return alertas(comando, tel);
        }
        if (contem(comando, "reservatorio", "caixa d agua", "caixa dagua", "nivel", "agua")) {
            return reservatorio(comando, tel);
        }
        if (contem(comando, "irriga", "aspersor", "bomba", "regando", "regar")) {
            return irrigacao(comando, tel);
        }
        if (contem(comando, "acontecendo", "acontece", "novidade", "ultimos eventos", "o que houve", "o que aconteceu")) {
            return acontecendo(comando, tel);
        }
        if (contem(comando, "energia", "solar", "placa", "usina", "eletric")) {
            return energia(comando, tel);
        }
        if (contem(comando, "indice", "hidro energetico", "hidroenergetico")) {
            return indice(comando, tel);
        }
        if (contem(comando, "ajuda", "comandos", "o que voce", "pode fazer", "como funciona", "o que posso")) {
            return ajuda(comando);
        }
        if (contem(comando, "status", "situacao", "como esta a fazenda", "como estao", "como esta tudo", "resumo",
                "visao geral", "panorama", "tudo bem", "estado da fazenda") || mencionaTalhao) {
            return status(comando, tel);
        }
        return new AssistenteDTO.Resposta(false, Intencao.DESCONHECIDO, comando, NAO_ENTENDI, paraFala(NAO_ENTENDI),
                null, false, clock.instant());
    }

    // ---- Respostas ----------------------------------------------------------------------------------------

    private AssistenteDTO.Resposta status(String comando, TelemetriaDTO t) {
        ReservatorioDTO r = t.reservatorio();
        List<AlertaDTO> alertas = alertasRelevantes(t);
        int ligados = (int) t.talhoes().stream().filter(TalhaoDTO::aspersorLigado).count();
        StringBuilder sb = new StringBuilder();
        sb.append("O reservatório está em ").append(inteiro(r.nivel())).append('%');
        if (r.bloqueioEmergencia()) {
            sb.append(", com bloqueio de emergência ativo");
        }
        sb.append(". Existem ").append(t.talhoes().size()).append(t.talhoes().size() == 1 ? " talhão monitorado, " : " talhões monitorados, ")
                .append(ligados).append(ligados == 1 ? " aspersor ativo e " : " aspersores ativos e ")
                .append(descreverAlertas(alertas)).append(". ");
        sb.append(frasesSistema(t));
        return resposta(Intencao.STATUS, comando, sb.toString(), new Acao("NAVEGAR", "visao-geral", null, "Ver visão geral"), false);
    }

    private AssistenteDTO.Resposta reservatorio(String comando, TelemetriaDTO t) {
        ReservatorioDTO r = t.reservatorio();
        StringBuilder sb = new StringBuilder();
        sb.append("O reservatório está em ").append(inteiro(r.nivel())).append("% (")
                .append(Rotulos.statusReservatorio(r.status()).toLowerCase(PT_BR)).append("), com ")
                .append(Math.round(r.volumeM3())).append(" de ").append(Math.round(r.capacidadeM3())).append(" m³. ");
        if (r.bloqueioEmergencia()) {
            sb.append("O bloqueio de emergência está ativo: todas as bombas ficam desligadas até o nível voltar a ")
                    .append(inteiro(r.limiteRearme())).append("%. ");
        } else if (r.consumoM3h() > 0) {
            sb.append("As bombas estão consumindo ").append(Math.round(r.consumoM3h())).append(" m³/h");
            sb.append(r.autonomiaHoras() == null ? ". " : "; no ritmo atual, a autonomia é de " + horas(r.autonomiaHoras()) + ". ");
        } else {
            sb.append("Nenhuma bomba está puxando água agora. ");
        }
        if (!r.bloqueioEmergencia()) {
            sb.append("Abaixo de ").append(inteiro(r.limiteCritico())).append("% todas as bombas são bloqueadas.");
        }
        return resposta(Intencao.RESERVATORIO, comando, sb.toString().trim(),
                new Acao("NAVEGAR", "visao-geral", null, "Ver reservatório"), false);
    }

    private AssistenteDTO.Resposta criticos(String comando, TelemetriaDTO t) {
        List<TalhaoDTO> criticos = t.talhoes().stream().filter(x -> x.status() == StatusTalhao.CRITICO).toList();
        List<TalhaoDTO> atencao = t.talhoes().stream().filter(x -> x.status() == StatusTalhao.ATENCAO).toList();
        StringBuilder sb = new StringBuilder();
        if (criticos.isEmpty()) {
            sb.append("Nenhum talhão está crítico agora. ");
            if (atencao.isEmpty()) {
                sb.append("Todos os ").append(t.talhoes().size()).append(" talhões estão em situação normal.");
            } else {
                sb.append("Em atenção: ")
                        .append(atencao.stream().map(x -> x.nome() + " com " + inteiro(x.umidade()) + "%")
                                .collect(Collectors.joining(", "))).append('.');
            }
        } else {
            sb.append(criticos.size()).append(criticos.size() == 1 ? " talhão crítico: " : " talhões críticos: ");
            sb.append(criticos.stream().map(x -> x.nome() + " com " + inteiro(x.umidade()) + "%"
                    + (x.irrigacaoBloqueada() ? " (irrigação bloqueada pela proteção do reservatório)"
                            : x.aspersorLigado() ? " (irrigação automática em andamento)" : ""))
                    .collect(Collectors.joining("; "))).append('.');
        }
        return resposta(Intencao.TALHOES_CRITICOS, comando, sb.toString(),
                new Acao("NAVEGAR", "talhoes", null, "Ver talhões"), false);
    }

    private AssistenteDTO.Resposta alertas(String comando, TelemetriaDTO t) {
        List<AlertaDTO> alertas = alertasRelevantes(t);
        String texto;
        if (alertas.isEmpty()) {
            texto = "Não há alertas ativos no momento. " + frasesSistema(t);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(alertas.size() == 1 ? "Há 1 alerta ativo: " : "Há " + alertas.size() + " alertas ativos: ");
            List<String> itens = new ArrayList<>();
            for (AlertaDTO a : alertas.stream().limit(3).toList()) {
                String nivel = Rotulos.severidade(a.nivel()).toLowerCase(PT_BR);
                boolean repetido = a.titulo().toLowerCase(PT_BR).contains(nivel) || a.titulo().toLowerCase(PT_BR).contains("crític");
                itens.add(a.titulo() + (repetido ? "" : " (" + nivel + ")") + ": " + primeiraMinuscula(tirarPonto(a.mensagem())));
            }
            sb.append(String.join("; ", itens)).append('.');
            if (alertas.size() > 3) {
                sb.append(" E mais ").append(alertas.size() - 3).append(" na seção Monitoramento.");
            }
            texto = sb.toString();
        }
        return resposta(Intencao.ALERTAS, comando, texto, new Acao("NAVEGAR", "monitoramento", null, "Ver alertas"), false);
    }

    private AssistenteDTO.Resposta irrigacao(String comando, TelemetriaDTO t) {
        List<TalhaoDTO> ligados = t.talhoes().stream().filter(TalhaoDTO::aspersorLigado).toList();
        StringBuilder sb = new StringBuilder();
        if (t.reservatorio().bloqueioEmergencia()) {
            sb.append("A irrigação está bloqueada pela proteção do reservatório (").append(inteiro(t.reservatorio().nivel()))
                    .append("%). ");
        } else if (ligados.isEmpty()) {
            sb.append("Nenhum aspersor está ligado agora. ");
        } else {
            sb.append(ligados.size() == 1 ? "1 aspersor ligado: " : ligados.size() + " aspersores ligados: ");
            sb.append(ligados.stream().map(x -> x.nome() + " (" + (x.modoAcionamento() == ModoAcionamento.MANUAL
                    ? "manual" : "automático") + ", umidade " + inteiro(x.umidade()) + "%)").collect(Collectors.joining(", ")))
                    .append(". ");
        }
        Optional<long[]> contagens = contagensPeriodo();
        contagens.ifPresent(c -> sb.append("No período foram ").append(c[0])
                .append(c[0] == 1 ? " irrigação automática e " : " irrigações automáticas e ").append(c[1])
                .append(c[1] == 1 ? " comando manual." : " comandos manuais."));
        return resposta(Intencao.IRRIGACAO, comando, sb.toString().trim(),
                new Acao("NAVEGAR", "irrigacao", null, "Ver irrigação"), false);
    }

    private AssistenteDTO.Resposta talhao(String comando, TelemetriaDTO t, String id) {
        TalhaoDTO x = t.talhoes().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
        StringBuilder sb = new StringBuilder();
        sb.append(x.nome()).append(" (").append(x.culturaRotulo()).append(' ').append(x.variedade()).append("): umidade ")
                .append(inteiro(x.umidade())).append("%, ").append(switch (x.status()) {
                    case NORMAL -> "situação normal";
                    case ATENCAO -> "em atenção";
                    case CRITICO -> "crítico";
                }).append(". ");
        if (x.aspersorLigado()) {
            sb.append("Aspersor ligado (").append(x.modoAcionamento() == ModoAcionamento.MANUAL ? "manual" : "automático")
                    .append("), irrigando até ").append(inteiro(x.umidadeAlvo())).append("%. ");
        } else if (x.irrigacaoBloqueada()) {
            sb.append("Irrigação bloqueada pela proteção do reservatório. ");
        } else {
            sb.append("Aspersor desligado. ");
            if (x.horasAteCritico() != null && x.status() != StatusTalhao.CRITICO) {
                sb.append("No ritmo atual, fica crítico em cerca de ").append(horas(x.horasAteCritico())).append(". ");
            }
        }
        sb.append("Limite crítico: ").append(inteiro(x.limiteCritico())).append('%').append('.');
        return resposta(Intencao.TALHAO, comando, sb.toString(),
                new Acao("ABRIR_TALHAO", "talhoes", x.id(), "Abrir " + x.nome()), false);
    }

    private AssistenteDTO.Resposta acionar(String comando, TelemetriaDTO t, Optional<String> talhao, boolean ligar) {
        Intencao intencao = ligar ? Intencao.LIGAR_ASPERSOR : Intencao.DESLIGAR_ASPERSOR;
        if (talhao.isEmpty()) {
            return resposta(intencao, comando, "De qual talhão? Diga, por exemplo: " + (ligar ? "ligar" : "desligar")
                    + " o aspersor do talhão " + primeiroId(t) + ". Talhões ativos: " + listaIds(t.talhoes()) + ".",
                    new Acao("NAVEGAR", "irrigacao", null, "Ver irrigação"), false);
        }
        AcionamentoResponse r = acionamentoService.acionar(new AcionamentoRequest(talhao.get(), ligar));
        String texto = r.sucesso()
                ? (r.motivo().equals("COMANDO_EXECUTADO") ? "Pronto. " : "") + r.mensagem()
                : "Não posso fazer isso agora: " + r.mensagem();
        return resposta(intencao, comando, texto, new Acao("ATUALIZAR", "irrigacao", talhao.get(), "Ver irrigação"),
                r.sucesso() && "COMANDO_EXECUTADO".equals(r.motivo()));
    }

    private AssistenteDTO.Resposta relatorio(String comando, TelemetriaDTO t) {
        Acao abrir = new Acao("ABRIR_EXPORTACAO", "relatorios", null, "Abrir exportação");
        RelatorioDTO r;
        try {
            if (estado.isBancoEmEspera()) {
                throw new IllegalStateException("banco em espera");
            }
            r = relatorioService.gerar();
        } catch (RuntimeException e) {
            log.warn("Relatório indisponível para o assistente: {}", e.getMessage());
            return resposta(Intencao.RELATORIO, comando, "Não consegui ler o histórico no banco de dados agora. "
                    + "A automação continua funcionando; tente gerar o relatório de novo em instantes.", abrir, false);
        }
        StringBuilder sb = new StringBuilder();
        Instant inicio = r.periodo().inicio();
        boolean hoje = comando.contains("hoje");
        LocalDate diaInicio = inicio.atZone(clock.getZone()).toLocalDate();
        if (hoje && diaInicio.isBefore(LocalDate.now(clock))) {
            sb.append("O período atual começou em ").append(DIA_HORA.format(inicio.atZone(clock.getZone())))
                    .append(", então o relatório cobre desde então. ");
        } else {
            sb.append("Relatório do período iniciado ").append(hoje || diaInicio.equals(LocalDate.now(clock)) ? "hoje às "
                    + HORA.format(inicio.atZone(clock.getZone())) : "em " + DIA_HORA.format(inicio.atZone(clock.getZone())))
                    .append(" (").append(r.periodo().duracaoMinutos()).append(" min). ");
        }
        sb.append("Reservatório agora em ").append(inteiro(r.reservatorio().atual())).append('%');
        if (r.reservatorio().minimo() != null) {
            sb.append(", mínimo de ").append(inteiro(r.reservatorio().minimo())).append('%');
        }
        sb.append(". ");
        RelatorioDTO.Contagens c = r.contagens();
        sb.append(c.irrigacoesAutomaticas()).append(c.irrigacoesAutomaticas() == 1 ? " irrigação automática, " : " irrigações automáticas, ")
                .append(c.comandosManuais()).append(c.comandosManuais() == 1 ? " comando manual e " : " comandos manuais e ")
                .append(c.bloqueiosEmergencia() == 0 ? "nenhum bloqueio de emergência. "
                        : c.bloqueiosEmergencia() + (c.bloqueiosEmergencia() == 1 ? " bloqueio de emergência. " : " bloqueios de emergência. "));
        sb.append(r.talhoesCriticos().isEmpty() ? "Nenhum talhão chegou ao nível crítico. "
                : "Chegaram ao nível crítico: " + String.join(", ", r.talhoesCriticos()) + ". ");
        sb.append("Abri a área de exportação: escolha PDF, Excel, WhatsApp ou e-mail.");
        return resposta(Intencao.RELATORIO, comando, sb.toString(), abrir, false);
    }

    private AssistenteDTO.Resposta acontecendo(String comando, TelemetriaDTO t) {
        DecisaoDTO d = t.decisao();
        StringBuilder sb = new StringBuilder();
        if (d != null) {
            sb.append(tirarPonto(d.titulo())).append(". ").append(terminarComPonto(d.oQue())).append(' ');
            if (d.acaoAutomatica() != null && !d.acaoAutomatica().isBlank()) {
                sb.append("Ação automática: ").append(primeiraMinuscula(terminarComPonto(d.acaoAutomatica()))).append(' ');
            }
        }
        List<EventoDTO> recentes = eventosRecentes();
        if (!recentes.isEmpty()) {
            sb.append("Últimos eventos: ").append(recentes.stream()
                    .map(e -> HORA.format(e.instante().atZone(clock.getZone())) + ", " + tirarPonto(e.titulo()))
                    .collect(Collectors.joining("; "))).append('.');
        }
        String texto = sb.toString().trim();
        return resposta(Intencao.O_QUE_ACONTECE, comando, texto.isEmpty() ? frasesSistema(t) : texto,
                new Acao("NAVEGAR", "monitoramento", null, "Ver histórico"), false);
    }

    private AssistenteDTO.Resposta energia(String comando, TelemetriaDTO t) {
        EnergiaDTO e = t.energia();
        StringBuilder sb = new StringBuilder();
        if (e.consumoKw() > 0) {
            sb.append("As bombas consomem ").append(decimal(e.consumoKw())).append(" kW agora e a usina solar gera ")
                    .append(decimal(e.geracaoSolarKw())).append(" kW, cobrindo ").append(Math.round(e.coberturaSolar()))
                    .append("% do consumo. ");
        } else {
            sb.append("Nenhuma bomba está ligada; a usina solar gera ").append(decimal(e.geracaoSolarKw())).append(" kW agora. ");
        }
        double solar = e.energiaConsumidaKwh() > 0 ? e.energiaSolarKwh() / e.energiaConsumidaKwh() * 100 : 100;
        sb.append("No período, as bombas usaram ").append(decimal(e.energiaConsumidaKwh())).append(" kWh, ")
                .append(Math.round(solar)).append("% de origem solar.");
        return resposta(Intencao.ENERGIA, comando, sb.toString(), new Acao("NAVEGAR", "visao-geral", null, "Ver indicadores"), false);
    }

    private AssistenteDTO.Resposta indice(String comando, TelemetriaDTO t) {
        IndiceDTO i = t.indice();
        StringBuilder sb = new StringBuilder("O Índice Hidro-Energético está em ").append(i.valor()).append(" de 100: ")
                .append(i.rotulo().toLowerCase(PT_BR)).append(". ");
        if (i.fatores() != null && !i.fatores().isEmpty()) {
            sb.append(i.fatores().stream().map(f -> f.nome() + " " + Math.round(f.valor()))
                    .collect(Collectors.joining(", "))).append('.');
        }
        if (i.travaEmergencia()) {
            sb.append(" Está limitado a 25 pelo bloqueio de emergência.");
        }
        return resposta(Intencao.INDICE, comando, sb.toString().trim(), new Acao("NAVEGAR", "relatorios", null, "Ver índice"), false);
    }

    private AssistenteDTO.Resposta ajuda(String comando) {
        String texto = "Eu consulto a fazenda em tempo real. Pergunte, por exemplo: quero o status; como está o "
                + "reservatório?; quais talhões estão críticos?; tem algum alerta?; como está a irrigação?; mostre o "
                + "talhão C; o que está acontecendo?; gere um relatório. Também posso ligar ou desligar o aspersor de um "
                + "talhão, sempre respeitando as regras de segurança.";
        return resposta(Intencao.AJUDA, comando, texto, null, false);
    }

    // ---- Apoio ----------------------------------------------------------------------------------------------

    private AssistenteDTO.Resposta resposta(Intencao intencao, String comando, String texto, Acao acao, boolean executou) {
        return new AssistenteDTO.Resposta(true, intencao, comando, texto, paraFala(texto), acao, executou, clock.instant());
    }

    private static List<AlertaDTO> alertasRelevantes(TelemetriaDTO t) {
        return t.alertas().stream().filter(a -> a.nivel() != Severidade.INFO).toList();
    }

    private static String descreverAlertas(List<AlertaDTO> alertas) {
        if (alertas.isEmpty()) {
            return "nenhum alerta";
        }
        long graves = alertas.stream().filter(a -> a.nivel() == Severidade.CRITICO || a.nivel() == Severidade.EMERGENCIA).count();
        long atencao = alertas.size() - graves;
        if (alertas.size() == 1) {
            return graves == 1 ? "1 alerta crítico" : "1 alerta de atenção";
        }
        if (graves == 0) {
            return alertas.size() + " alertas de atenção";
        }
        if (atencao == 0) {
            return alertas.size() + " alertas críticos";
        }
        return alertas.size() + " alertas (" + graves + (graves == 1 ? " crítico e " : " críticos e ") + atencao + " de atenção)";
    }

    private static String frasesSistema(TelemetriaDTO t) {
        // So a primeira frase da descricao: a contagem de talhoes ja foi dita na resposta
        String motivo = primeiraMinuscula(terminarComPonto(t.statusSistema().descricao().split("\\. ")[0]));
        return switch (t.statusSistema().codigo()) {
            case NORMAL -> "O sistema está operando normalmente.";
            case ATENCAO -> "O sistema está em atenção: " + motivo;
            case RISCO_HIDRICO -> "Risco hídrico: " + motivo;
            case EMERGENCIA -> "Emergência: " + motivo;
        };
    }

    private Optional<long[]> contagensPeriodo() {
        if (estado.isBancoEmEspera()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new long[] {eventoRepository.countByTipo(TipoEvento.IRRIGACAO_CRITICA),
                eventoRepository.countByTipo(TipoEvento.COMANDO_MANUAL)});
        } catch (RuntimeException e) {
            log.warn("Contagens indisponíveis para o assistente: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<EventoDTO> eventosRecentes() {
        if (estado.isBancoEmEspera()) {
            return List.of();
        }
        try {
            return eventoRepository.findAllByOrderByIdDesc(PageRequest.of(0, 40)).stream()
                    .filter(e -> EVENTOS_RELEVANTES.contains(e.getTipo())).limit(3).map(EventoDTO::de).toList();
        } catch (RuntimeException e) {
            log.warn("Eventos indisponíveis para o assistente: {}", e.getMessage());
            return List.of();
        }
    }

    /** Minusculas, sem acentos e pontuacao, sem a palavra de ativacao ("Citrus, quero o status" -> "quero o status"). */
    static String normalizar(String texto) {
        String n = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("d'agua", "d agua");
        n = n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        n = PALAVRA_ATIVACAO.matcher(n).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return n;
    }

    /** Talhao citado no comando: pelo codigo ("talhao c", "talhao ce", "talhao 2") ou pelo nome cadastrado. */
    static Optional<String> identificarTalhao(String comando, List<TalhaoDTO> talhoes) {
        Map<String, String> porCodigo = talhoes.stream()
                .collect(Collectors.toMap(t -> t.id().toLowerCase(Locale.ROOT), TalhaoDTO::id, (a, b) -> a));
        String[] palavras = comando.split(" ");
        for (int i = 0; i < palavras.length; i++) {
            if (!PALAVRAS_TALHAO.contains(palavras[i])) {
                continue;
            }
            // O codigo vem logo depois de "talhao" ou depois de um conectivo ("talhao do B", "talhao numero 2")
            for (int j = i + 1; j < Math.min(palavras.length, i + 3); j++) {
                String codigo = codigo(palavras[j], porCodigo);
                if (codigo != null) {
                    return Optional.of(codigo);
                }
                if (!CONECTIVOS.contains(palavras[j])) {
                    break;
                }
            }
        }
        // Nome cadastrado dito por extenso (ex.: "talhao norte"), do mais longo para o mais curto
        return talhoes.stream()
                .sorted((a, b) -> Integer.compare(b.nome().length(), a.nome().length()))
                .filter(t -> {
                    String nome = normalizar(t.nome());
                    return nome.length() >= 3 && (" " + comando + " ").contains(" " + nome + " ");
                })
                .map(TalhaoDTO::id).findFirst();
    }

    private static String codigo(String palavra, Map<String, String> porCodigo) {
        String codigo = porCodigo.get(palavra);
        if (codigo == null && SOLETRADO.containsKey(palavra)) {
            codigo = porCodigo.get(SOLETRADO.get(palavra).toLowerCase(Locale.ROOT));
        }
        return codigo;
    }

    /** Versao para leitura em voz: unidades por extenso e sem simbolos. */
    static String paraFala(String texto) {
        return texto.replace("m³/h", " metros cúbicos por hora").replace("m³", " metros cúbicos")
                .replace("kWh", " quilowatts-hora").replace(" kW", " quilowatts").replace("p.p./h", " pontos por hora")
                .replace("%", " por cento").replace("~", "cerca de ").replace("×", " vezes")
                .replaceAll("(\\d) h\\b", "$1 horas").replaceAll("(\\d) min\\b", "$1 minutos")
                .replaceAll("[\\p{So}\\p{Cn}]", "").replaceAll(" {2,}", " ").trim();
    }

    private static boolean contem(String comando, String... termos) {
        for (String t : termos) {
            if (comando.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** Arredonda para baixo: 24,9% e dito "24%" (nunca "25%"), coerente com a regra "abaixo de 25%". */
    private static long inteiro(double valor) {
        return (long) Math.floor(valor + 1e-9);
    }

    private static String decimal(double v) {
        return String.format(PT_BR, "%.1f", v);
    }

    private static String listaIds(List<TalhaoDTO> talhoes) {
        List<String> ids = talhoes.stream().map(TalhaoDTO::id).toList();
        if (ids.isEmpty()) {
            return "nenhum";
        }
        if (ids.size() == 1) {
            return ids.get(0);
        }
        return String.join(", ", ids.subList(0, ids.size() - 1)) + " e " + ids.get(ids.size() - 1);
    }

    private static String primeiroId(TelemetriaDTO t) {
        return t.talhoes().isEmpty() ? "A" : t.talhoes().get(0).id();
    }

    private static String tirarPonto(String s) {
        if (s == null) {
            return "";
        }
        String r = s.trim();
        while (r.endsWith(".")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static String terminarComPonto(String s) {
        String r = tirarPonto(s);
        return r.isEmpty() ? r : r + ".";
    }

    private static String primeiraMinuscula(String s) {
        if (s == null || s.length() < 2 || Character.isUpperCase(s.charAt(1))) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
