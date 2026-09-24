package br.com.biosolar.citrus.simulation;

import java.time.Instant;

import org.springframework.stereotype.Component;

import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Talhao;

/**
 * Simula os sensores e o processo fisico em um passo de tempo (deterministico, sem ruido aleatorio):
 * <ul>
 *   <li>umidade: cai com a evapotranspiracao (maior com sol forte) e sobe com o aspersor ligado;</li>
 *   <li>reservatorio: perde a vazao das bombas ligadas e recebe a recarga do poco solar;</li>
 *   <li>energia: consumo das bombas, parte atendida pela usina solar.</li>
 * </ul>
 */
@Component
public class ModeloFisico {

    public void avancar(Fazenda fazenda, double minutos, Instant agora) {
        double horas = minutos / 60.0;

        // Taxas calculadas no inicio do passo (estado dos atuadores e horario atual)
        double consumoAgua = fazenda.consumoAguaM3h() * horas;
        double recarga = fazenda.recargaM3h() * horas;
        double consumoKw = fazenda.consumoEnergiaKw();
        double solarUtilKw = Math.min(consumoKw, fazenda.geracaoSolarKw());

        for (Talhao t : fazenda.getTalhoes()) {
            t.setUmidade(t.getUmidade() + fazenda.variacaoUmidadePorHora(t) * horas);
            t.setUltimaAtualizacao(agora);
        }

        Reservatorio r = fazenda.getReservatorio();
        r.ajustarVolume(recarga - consumoAgua);
        r.setUltimaAtualizacao(agora);

        EstadoSimulacao sim = fazenda.getSimulacao();
        sim.acumular(consumoKw * horas, solarUtilKw * horas, consumoAgua);
        sim.avancarRelogio(minutos);
    }
}
