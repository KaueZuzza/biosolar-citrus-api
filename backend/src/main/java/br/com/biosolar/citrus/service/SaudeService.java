package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.pct;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.dto.SaudeDTO;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.simulation.SimuladorFazenda;

/** Verificacao de saude da API, do banco de dados, do simulador e dos dispositivos simulados. */
@Service
public class SaudeService {

    private static final String VERSAO = "1.0.0";
    private static final Duration ATRASO_MAXIMO = Duration.ofSeconds(5);

    private final DataSource dataSource;
    private final SimuladorFazenda simulador;
    private final EstadoFazendaService estado;
    private final BioSolarProperties properties;
    private final Clock clock;
    private final Instant iniciadaEm;

    public SaudeService(DataSource dataSource, SimuladorFazenda simulador, EstadoFazendaService estado,
                        BioSolarProperties properties, Clock clock) {
        this.dataSource = dataSource;
        this.simulador = simulador;
        this.estado = estado;
        this.properties = properties;
        this.clock = clock;
        this.iniciadaEm = clock.instant();
    }

    public SaudeDTO verificar() {
        Instant agora = clock.instant();
        SaudeDTO.Componente banco = verificarBanco();
        SaudeDTO.Componente simuladorStatus = verificarSimulador(agora);

        List<SaudeDTO.Dispositivo> sensores = new ArrayList<>();
        List<SaudeDTO.Dispositivo> atuadores = new ArrayList<>();
        estado.ler(f -> {
            for (Talhao t : f.getTalhoes()) {
                sensores.add(new SaudeDTO.Dispositivo("SU-" + t.getId(), "Sensor de umidade do solo", t.getNome(),
                        "ONLINE", pct(t.getUmidade())));
                String status = f.getReservatorio().isBloqueioEmergencia() ? "BLOQUEADO"
                        : t.isAspersorLigado() ? "LIGADO" : "DESLIGADO";
                atuadores.add(new SaudeDTO.Dispositivo(t.getBombaId(), "Motobomba + aspersor", t.getNome(), status,
                        t.getPotenciaBombaKw() + " kW"));
            }
            sensores.add(new SaudeDTO.Dispositivo("SN-R01", "Sensor de nível (ultrassônico)",
                    f.getReservatorio().getNome(), "ONLINE", pct(f.getReservatorio().getNivel())));
            sensores.add(new SaudeDTO.Dispositivo("PV-01", "Inversor fotovoltaico",
                    "Usina solar " + Math.round(f.getSimulacao().getPotenciaSolarPicoKw()) + " kWp", "ONLINE",
                    Math.round(f.geracaoSolarKw() * 10) / 10.0 + " kW"));
            return null;
        });

        String geral = "UP".equals(banco.status()) && !"ATRASADO".equals(simuladorStatus.status()) ? "UP" : "DEGRADADO";
        return new SaudeDTO(geral, VERSAO, agora, iniciadaEm, Duration.between(iniciadaEm, agora).toSeconds(),
                new SaudeDTO.Componente("UP", "API REST respondendo", null), banco, simuladorStatus, sensores,
                atuadores);
    }

    private SaudeDTO.Componente verificarBanco() {
        long inicio = System.nanoTime();
        try (Connection conexao = dataSource.getConnection()) {
            boolean valida = conexao.isValid(2);
            long latencia = (System.nanoTime() - inicio) / 1_000_000;
            DatabaseMetaData meta = conexao.getMetaData();
            String produto = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            return new SaudeDTO.Componente(valida ? "UP" : "DOWN", produto, latencia);
        } catch (Exception e) {
            return new SaudeDTO.Componente("DOWN", "Banco indisponível: " + e.getMessage(), null);
        }
    }

    private SaudeDTO.Componente verificarSimulador(Instant agora) {
        if (!properties.simulacao().habilitada()) {
            return new SaudeDTO.Componente("DESABILITADO", "Ciclo automático desabilitado por configuração", null);
        }
        Instant ultimo = simulador.getUltimoCiclo();
        if (ultimo == null) {
            return new SaudeDTO.Componente("INICIANDO", "Aguardando o primeiro ciclo", null);
        }
        long atraso = Duration.between(ultimo, agora).toMillis();
        boolean atrasado = atraso > ATRASO_MAXIMO.toMillis();
        return new SaudeDTO.Componente(atrasado ? "ATRASADO" : "UP",
                simulador.getCiclosExecutados() + " ciclos desde a inicialização", atraso);
    }
}
