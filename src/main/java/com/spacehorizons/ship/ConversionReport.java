package com.spacehorizons.ship;

import java.util.List;
import java.util.Locale;

/**
 * O resultado medido de uma conversão (spike S3), guardado em memória e escrito no log com o prefixo {@code [ship-conv]}.
 *
 * @param direction "entidade -> blocos" ou "blocos -> entidade"
 * @param shipId a nave (id do registro)
 * @param blocks blocos não-ar do retrato
 * @param blockEntities block entities
 * @param items itens nos inventários (fora das fornalhas)
 * @param jobTicks ticks do servidor em que o job trabalhou
 * @param startupMs custo da parte síncrona de iniciar (alocar célula, romaneio de entrada)
 * @param jobMsTotal soma do tempo gasto pelo job em todos os ticks
 * @param jobMsMax o maior tempo de um tick só
 * @param wallMs do início ao fim (relógio de parede)
 * @param serverTickMsAvg tempo médio (ms) do tick completo do servidor durante o job (amostra do tick anterior a cada passo)
 * @param serverTickMsMax o maior
 * @param packetBytes tamanho do retrato no pacote de spawn (RocketPortrait.write)
 * @param losses diferenças em relação ao romaneio de entrada (vazio = nada se perdeu)
 * @param notes avisos que não são perda (fornalhas, passageiros...)
 * @param ok terminou sem erro
 * @param error a mensagem do erro, se não terminou
 * @param blocksPerTick o orçamento usado (unidades por tick) e o teto de tempo por tick
 */
public record ConversionReport(String direction, int shipId, int blocks, int blockEntities, int items, int jobTicks, double startupMs, double jobMsTotal,
        double jobMsMax, double wallMs, double serverTickMsAvg, double serverTickMsMax, int packetBytes, List<String> losses, List<String> notes, boolean ok,
        String error, int blocksPerTick, int millisPerTick) {
    public String line() {
        return String.format(Locale.ROOT, "%s nave %d: %d blocos, %d block entities, %d itens | %d ticks de job (orçamento %d/tick, teto %d ms), início síncrono %.1f ms, "
                + "job total %.1f ms (máx %.1f ms/tick, média %.1f), parede %.0f ms | tick do servidor durante: média %.1f ms, máx %.1f ms | pacote do retrato %d bytes | "
                + "perdas: %s%s%s", this.direction, this.shipId, this.blocks, this.blockEntities, this.items, this.jobTicks, this.blocksPerTick, this.millisPerTick,
                this.startupMs, this.jobMsTotal, this.jobMsMax, this.jobTicks == 0 ? 0.0 : this.jobMsTotal / this.jobTicks, this.wallMs, this.serverTickMsAvg,
                this.serverTickMsMax, this.packetBytes, this.losses.isEmpty() ? "nenhuma" : String.join("; ", this.losses),
                this.notes.isEmpty() ? "" : " | avisos: " + String.join("; ", this.notes), this.ok ? "" : " | ERRO: " + this.error);
    }
}
