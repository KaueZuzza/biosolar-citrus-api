package br.com.biosolar.citrus.util;

import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusReservatorio;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.TipoEvento;

/** Nomes em portugues dos codigos internos, para relatorios, planilhas e respostas do assistente. */
public final class Rotulos {

    private Rotulos() {
    }

    public static String tipo(TipoEvento tipo) {
        if (tipo == null) {
            return "";
        }
        return switch (tipo) {
            case IRRIGACAO_CRITICA -> "Irrigação automática";
            case IRRIGACAO_CONCLUIDA -> "Irrigação concluída";
            case PROTECAO_SATURACAO -> "Proteção contra saturação";
            case COMANDO_MANUAL -> "Comando manual";
            case COMANDO_RECUSADO -> "Comando recusado";
            case BLOQUEIO_EMERGENCIA -> "Bloqueio de emergência";
            case IRRIGACAO_BLOQUEADA -> "Irrigação bloqueada";
            case RECUPERACAO_SISTEMA -> "Recuperação do sistema";
            case ALERTA_UMIDADE -> "Alerta de umidade";
            case ALERTA_RESERVATORIO -> "Alerta do reservatório";
            case RESERVATORIO_ATUALIZADO -> "Reservatório atualizado";
            case SIMULACAO -> "Simulação";
            case CADASTRO -> "Cadastro";
            case SISTEMA -> "Sistema";
            case RELATORIO -> "Relatório";
        };
    }

    public static String severidade(Severidade s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case INFO -> "Informação";
            case SUCESSO -> "Sucesso";
            case ATENCAO -> "Atenção";
            case CRITICO -> "Crítico";
            case EMERGENCIA -> "Emergência";
        };
    }

    public static String origem(OrigemEvento o) {
        if (o == null) {
            return "";
        }
        return switch (o) {
            case AUTOMACAO -> "Automação";
            case OPERADOR -> "Operador";
            case SIMULACAO -> "Simulação";
            case SISTEMA -> "Sistema";
        };
    }

    public static String statusTalhao(StatusTalhao s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case NORMAL -> "Normal";
            case ATENCAO -> "Atenção";
            case CRITICO -> "Crítico";
        };
    }

    public static String statusTalhao(String codigo) {
        try {
            return statusTalhao(StatusTalhao.valueOf(codigo));
        } catch (IllegalArgumentException | NullPointerException e) {
            return codigo == null ? "" : codigo;
        }
    }

    public static String statusReservatorio(StatusReservatorio s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case NORMAL -> "Normal";
            case ATENCAO -> "Atenção";
            case CRITICO -> "Crítico";
        };
    }

    public static String statusReservatorio(String codigo) {
        try {
            return statusReservatorio(StatusReservatorio.valueOf(codigo));
        } catch (IllegalArgumentException | NullPointerException e) {
            return codigo == null ? "" : codigo;
        }
    }
}
