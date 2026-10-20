package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** O orçamento por tick da conversão em lotes: por unidades, por tempo e a divisão de um trabalho em ticks. */
class ConversionBudgetTest {
    @Test
    void acabaPelasUnidades() {
        AtomicLong clock = new AtomicLong();
        ConversionBudget b = new ConversionBudget(10, 0, clock::get);
        assertFalse(b.exhausted());
        b.spend(9);
        assertFalse(b.exhausted());
        b.spend(1);
        assertTrue(b.exhausted());
    }

    @Test
    void acabaPeloTempoMesmoComUnidadesSobrando() {
        AtomicLong clock = new AtomicLong();
        ConversionBudget b = new ConversionBudget(1_000_000, 5, clock::get);
        clock.set(4_999_999L);
        assertFalse(b.exhausted());
        clock.set(5_000_000L);
        assertTrue(b.exhausted());
    }

    @Test
    void semTetoDeTempoQuandoZero() {
        AtomicLong clock = new AtomicLong();
        ConversionBudget b = new ConversionBudget(5, 0, clock::get);
        clock.set(Long.MAX_VALUE / 2);
        assertFalse(b.exhausted());
    }

    /** O laço dos jobs: gasta 1 por bloco e para quando o orçamento do tick acaba; N blocos levam ceil(N / lote) ticks e nenhum se perde nem repete. */
    @Test
    void umTrabalhoSeDivideEmTicksSemPerderNemRepetir() {
        int total = 4_003;
        int perTick = 1_500;
        boolean[] done = new boolean[total];
        int cursor = 0;
        int ticks = 0;
        while (cursor < total) {
            ConversionBudget b = new ConversionBudget(perTick, 0, () -> 0L);
            int thisTick = 0;
            while (cursor < total && !b.exhausted()) {
                assertFalse(done[cursor], "bloco repetido " + cursor);
                done[cursor++] = true;
                b.spend(1);
                thisTick++;
            }
            assertTrue(thisTick <= perTick);
            ticks++;
        }
        assertEquals(3, ticks);
        for (boolean d : done) {
            assertTrue(d);
        }
    }

    @Test
    void configuracaoGlobalTemPisoENaoNegativa() {
        int units = ConversionBudget.unitsPerTick();
        int millis = ConversionBudget.millisPerTick();
        try {
            ConversionBudget.configure(0, -5);
            assertEquals(1, ConversionBudget.unitsPerTick());
            assertEquals(0, ConversionBudget.millisPerTick());
            ConversionBudget.configure(2500, 15);
            assertEquals(2500, ConversionBudget.unitsPerTick());
            assertEquals(15, ConversionBudget.millisPerTick());
        } finally {
            ConversionBudget.configure(units, millis);
        }
    }
}
