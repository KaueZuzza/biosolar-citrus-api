package br.com.biosolar.citrus.simulation;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import br.com.biosolar.citrus.model.Cultura;
import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Talhao;

/**
 * Cenario de referencia da fazenda (usado na primeira inicializacao e ao restaurar a demonstracao).
 *
 * <p>Premissas de dimensionamento:
 * <ul>
 *   <li>Espacamento 7 x 4 m para laranja (~357 plantas/ha) e 6 x 4 m para limao (~416 plantas/ha).</li>
 *   <li>Motobomba de 5,5 kW (7,5 cv) por talhao: 18 m3/h a ~60 mca com rendimento de ~60%.</li>
 *   <li>Reservatorio de 800 m3: as 4 bombas juntas consomem 72 m3/h (~9 p.p. por hora).</li>
 *   <li>Poco tubular com bomba solar: recarga de ate 8 m3/h ao meio-dia.</li>
 *   <li>Usina fotovoltaica de 45 kWp: cobre as 4 bombas (22 kW) nas horas de sol.</li>
 *   <li>Evapotranspiracao maior no solo arenoso (Talhao C), que retem menos agua.</li>
 * </ul>
 */
public final class CenarioInicial {

    public static final double POTENCIA_SOLAR_PICO_KW = 45.0;
    public static final double NIVEL_INICIAL_RESERVATORIO = 67.0;

    private CenarioInicial() {
    }

    public static Fazenda criar(Instant agora, LocalDateTime relogioSimulado) {
        List<Talhao> talhoes = List.of(
                new Talhao("A", Cultura.LARANJA, "Pera Rio", "Latossolo Amarelo (argiloso)",
                        12.5, 4460, 2, 62.0, 1.3, agora),
                new Talhao("B", Cultura.LARANJA, "Valência", "Latossolo Amarelo (argiloso)",
                        10.8, 3850, 2, 41.0, 1.4, agora),
                new Talhao("C", Cultura.LIMAO, "Tahiti", "Neossolo Quartzarênico (arenoso)",
                        8.2, 3410, 1, 31.0, 1.9, agora),
                new Talhao("D", Cultura.LIMAO, "Siciliano", "Argissolo Vermelho-Amarelo",
                        7.6, 3160, 1, 57.0, 1.6, agora));

        Reservatorio reservatorio = new Reservatorio("Reservatório Central R-01", 800,
                NIVEL_INICIAL_RESERVATORIO, 8.0, agora);
        EstadoSimulacao simulacao = new EstadoSimulacao(relogioSimulado, POTENCIA_SOLAR_PICO_KW, agora);

        return new Fazenda(talhoes, reservatorio, simulacao);
    }
}
