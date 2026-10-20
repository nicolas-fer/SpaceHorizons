package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Rotation;

/** Grade do retrato: serialização (repetições e NBT esparso) ida e volta e giro horizontal. */
class PortraitGridTest {
    /** Grade 3 x 4 x 5 com um padrão reconhecível (cada célula não vazia tem um índice que depende da posição). */
    private static PortraitGrid padrao() {
        PortraitGrid g = PortraitGrid.empty(3, 4, 5);
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 3; x++) {
                    if ((x + 2 * y + 3 * z) % 4 != 0) {
                        g.set(x, y, z, 1 + (x + y * 3 + z * 5) % 7);
                    }
                }
            }
        }
        return g;
    }

    @Test
    void repeticoesDeRedeIdaEVoltaPreservamAGrade() {
        PortraitGrid original = padrao();
        ByteBuf buf = Unpooled.buffer();
        original.writeRuns(buf);
        PortraitGrid lida = PortraitGrid.readRuns(buf, 3, 4, 5, 7);
        assertEquals(original, lida);
        assertEquals(0, buf.readableBytes(), "leu tudo o que foi escrito");
    }

    @Test
    void repeticoesComprimemTrechosVazios() {
        PortraitGrid g = PortraitGrid.empty(8, 8, 8);
        g.set(0, 0, 0, 1);
        g.set(7, 7, 7, 2);
        ByteBuf buf = Unpooled.buffer();
        g.writeRuns(buf);
        assertTrue(buf.readableBytes() < 12, "512 células viram poucas repetições, deu " + buf.readableBytes() + " bytes");
        assertEquals(g, PortraitGrid.readRuns(buf, 8, 8, 8, 2));
    }

    @Test
    void repeticoesMalformadasSaoRecusadas() {
        ByteBuf soma = Unpooled.buffer();
        net.minecraft.network.VarInt.write(soma, 1);
        net.minecraft.network.VarInt.write(soma, 100);   // mais células do que a grade tem
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.readRuns(soma, 2, 2, 2, 5));

        ByteBuf indice = Unpooled.buffer();
        net.minecraft.network.VarInt.write(indice, 9);   // índice acima da paleta
        net.minecraft.network.VarInt.write(indice, 8);
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.readRuns(indice, 2, 2, 2, 5));

        ByteBuf zero = Unpooled.buffer();
        net.minecraft.network.VarInt.write(zero, 1);
        net.minecraft.network.VarInt.write(zero, 0);     // repetição vazia (loop infinito se aceita)
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.readRuns(zero, 2, 2, 2, 5));
    }

    @Test
    void nbtEsparsoIdaEVoltaGuardaPosicaoEmLongEIndiceDePaleta() {
        PortraitGrid original = padrao();
        ListTag tag = original.toSparseTag();
        assertEquals(original.nonEmptyCount(), tag.size());
        CompoundTag primeiro = tag.getCompoundOrEmpty(0);
        assertTrue(primeiro.contains("Pos") && primeiro.contains("State"));
        assertEquals(original, PortraitGrid.fromSparseTag(3, 4, 5, tag));
    }

    @Test
    void nbtEsparsoComPosicaoForaDaCaixaOuIndiceInvalidoEhRecusado() {
        ListTag fora = new ListTag();
        CompoundTag entrada = new CompoundTag();
        entrada.putLong("Pos", net.minecraft.core.BlockPos.asLong(9, 0, 0));
        entrada.putInt("State", 1);
        fora.add(entrada);
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.fromSparseTag(3, 3, 3, fora));

        ListTag semIndice = new ListTag();
        CompoundTag e2 = new CompoundTag();
        e2.putLong("Pos", net.minecraft.core.BlockPos.asLong(0, 0, 0));
        e2.putInt("State", 0);
        semIndice.add(e2);
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.fromSparseTag(3, 3, 3, semIndice));
    }

    @Test
    void tamanhoAbsurdoEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.empty(129, 257, 129));
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.empty(200, 200, 200), "8 milhões de células passam do teto de 4.194.304");
        assertThrows(IllegalArgumentException.class, () -> PortraitGrid.empty(-1, 2, 2));
    }

    // ---- giro horizontal ----

    @Test
    void quartosDeVoltaVemDoYawArredondadoParaOMultiploDe90() {
        assertEquals(0, PortraitGrid.quarterTurnsFromYaw(0.0));
        assertEquals(0, PortraitGrid.quarterTurnsFromYaw(44.9));
        assertEquals(1, PortraitGrid.quarterTurnsFromYaw(45.1));
        assertEquals(1, PortraitGrid.quarterTurnsFromYaw(90.0));
        assertEquals(2, PortraitGrid.quarterTurnsFromYaw(180.0));
        assertEquals(2, PortraitGrid.quarterTurnsFromYaw(-180.0));
        assertEquals(3, PortraitGrid.quarterTurnsFromYaw(-90.0));
        assertEquals(3, PortraitGrid.quarterTurnsFromYaw(270.0));
        assertEquals(0, PortraitGrid.quarterTurnsFromYaw(360.0 + 10.0));
        assertEquals(1, PortraitGrid.quarterTurnsFromYaw(-270.0));
    }

    @Test
    void umQuartoDeVoltaLevaONorteParaOLesteComoRotationClockwise90() {
        // grade fina em z (1 x 1 x 3): a célula do extremo norte (z = 0) deve ir para o extremo leste (x maior) depois de 1 quarto
        PortraitGrid g = PortraitGrid.empty(1, 1, 3);
        g.set(0, 0, 0, 1);   // norte
        PortraitGrid girada = g.rotated(1);
        assertEquals(3, girada.sizeX);
        assertEquals(1, girada.sizeZ);
        assertEquals(1, girada.get(2, 0, 0), "o norte virou leste (x = 2)");
        assertEquals(Direction.EAST, Rotation.values()[1].rotate(Direction.NORTH), "e é o que Rotation faz com a direção");
        assertEquals(Rotation.CLOCKWISE_90, Rotation.values()[1]);
        assertEquals(Rotation.CLOCKWISE_180, Rotation.values()[2]);
        assertEquals(Rotation.COUNTERCLOCKWISE_90, Rotation.values()[3]);
    }

    @Test
    void asQuatroPosicoesDeUmaCelulaDeCantoSeguemORelogio() {
        // célula no canto noroeste (x = 0, z = 0) de uma grade 4 x 1 x 2: NO -> NE -> SE -> SO -> NO
        PortraitGrid g = PortraitGrid.empty(4, 1, 2);
        g.set(0, 0, 0, 1);
        PortraitGrid r1 = g.rotated(1);   // 2 x 1 x 4
        assertEquals(2, r1.sizeX);
        assertEquals(4, r1.sizeZ);
        assertEquals(1, r1.get(1, 0, 0), "noroeste -> nordeste");
        PortraitGrid r2 = g.rotated(2);   // 4 x 1 x 2
        assertEquals(1, r2.get(3, 0, 1), "noroeste -> sudeste");
        PortraitGrid r3 = g.rotated(3);   // 2 x 1 x 4
        assertEquals(1, r3.get(0, 0, 3), "noroeste -> sudoeste");
    }

    @Test
    void girarQuatroQuartosDevolveAGradeEGirarPraLaEDePraCaEhIdentidade() {
        PortraitGrid g = padrao();
        PortraitGrid volta = g.rotated(1).rotated(1).rotated(1).rotated(1);
        assertEquals(g, volta);
        assertEquals(g, g.rotated(1).rotated(3));
        assertEquals(g, g.rotated(2).rotated(2));
        assertEquals(g.rotated(2), g.rotated(1).rotated(1));
        assertEquals(g, g.rotated(0));
        assertEquals(g.rotated(3), g.rotated(-1));
        assertEquals(g.nonEmptyCount(), g.rotated(1).nonEmptyCount());
    }

    @Test
    void girarMudaOTamanhoSoNosQuartosImpares() {
        PortraitGrid g = padrao();   // 3 x 4 x 5
        assertEquals(5, g.rotated(1).sizeX);
        assertEquals(3, g.rotated(1).sizeZ);
        assertEquals(3, g.rotated(2).sizeX);
        assertEquals(4, g.rotated(2).sizeY);
        assertEquals(5, g.rotated(3).sizeX);
        assertNotEquals(g, g.rotated(1));
    }

    @Test
    void girosAleatoriosPreservamOConteudoEAsAlturas() {
        Random random = new Random(7);
        for (int t = 0; t < 20; t++) {
            int sx = 1 + random.nextInt(6);
            int sy = 1 + random.nextInt(6);
            int sz = 1 + random.nextInt(6);
            PortraitGrid g = PortraitGrid.empty(sx, sy, sz);
            for (int i = 0; i < 12; i++) {
                g.set(random.nextInt(sx), random.nextInt(sy), random.nextInt(sz), 1 + random.nextInt(5));
            }
            for (int turns = 0; turns < 4; turns++) {
                PortraitGrid r = g.rotated(turns);
                assertEquals(g.nonEmptyCount(), r.nonEmptyCount());
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            int rx = PortraitGrid.rotatedX(turns, x, z, sx, sz);
                            int rz = PortraitGrid.rotatedZ(turns, x, z, sx, sz);
                            assertEquals(g.get(x, y, z), r.get(rx, y, rz));
                        }
                    }
                }
            }
        }
    }
}
